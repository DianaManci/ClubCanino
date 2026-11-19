/**
 * Backend de Club Canino. Conserva la llave privada de ePayco y la cuenta
 * de servicio de Firebase fuera del APK. No aprueba un pago si ePayco no
 * devuelve una transacción aceptada por el mismo valor y moneda.
 *
 * Esta etapa solo crea sesiones de prueba. EPAYCO_TEST=false no habilita cobros reales.
 */
const runtime = require("./runtime");

runtime.loadLocalEnv();

const express = require("express");
const admin = require("firebase-admin");
const payments = require("./payments");
const users = require("./users");

const port = Number(process.env.PORT || 8787);
const publicKey = process.env.EPAYCO_PUBLIC_KEY || "";
const privateKey = process.env.EPAYCO_PRIVATE_KEY || "";
const testMode = true;

let firebaseReady = false;
const credentialsReport = runtime.inspectCredentials();
console.log(
  "firebase_credentials configured=%s exists=%s readable=%s json=%s code=%s",
  credentialsReport.configured,
  credentialsReport.exists,
  credentialsReport.readable,
  credentialsReport.json,
  credentialsReport.code
);
if (credentialsReport.exists && credentialsReport.readable && credentialsReport.json) {
  try {
    if (!admin.apps.length) {
      admin.initializeApp({ credential: admin.credential.applicationDefault() });
    }
    firebaseReady = true;
  } catch (error) {
    firebaseReady = false;
    console.log(
      "firebase_credentials init_error name=%s code=%s",
      error.name || "",
      error.code || ""
    );
  }
}

const app = express();
app.use(express.json());

function status() {
  const ready = runtime.paymentReadiness();
  const missing = ready.missing.filter((item) => item !== "firebase_credentials");
  if (!firebaseReady) missing.push("firebase_credentials");
  return {
    ok: true,
    test: true,
    liveBlocked: ready.liveBlocked,
    payments: missing.length === 0,
    missing
  };
}

function database() {
  if (!firebaseReady) return null;
  return admin.firestore();
}

async function caller(req, db) {
  try {
    const header = req.get("authorization") || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : "";
    if (!token) return null;
    const decoded = await admin.auth().verifyIdToken(token);
    const profile = await db.collection("users").doc(decoded.uid).get();
    return { uid: decoded.uid, role: profile.get("role") || "" };
  } catch (_error) {
    return null;
  }
}

async function catalog(db, order) {
  const lines = Array.isArray(order?.lines) ? order.lines : [];
  const ids = [...new Set(lines.map((line) => String(line?.productId || "").trim()).filter(Boolean))];
  const products = new Map();
  for (const id of ids) {
    const snap = await db.collection("products").doc(id).get();
    if (snap.exists) products.set(id, snap.data());
  }
  return payments.quoteOrder(order, products);
}

async function epaycoToken() {
  const basic = Buffer.from(`${publicKey}:${privateKey}`).toString("base64");
  const response = await fetch("https://apify.epayco.co/login", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Basic ${basic}` },
    body: "{}"
  });
  if (!response.ok) return null;
  const json = await response.json();
  return json?.token || json?.data?.token || null;
}

app.get("/v1/health", (_req, res) => {
  res.json(status());
});

app.post("/v1/payments/session", async (req, res) => {
  try {
    const db = database();
    if (!status().payments || !db) return res.status(503).json({ error: "backend_not_configured" });
    const user = await caller(req, db);
    if (!user) return res.status(401).json({ error: "unauthorized" });
    const order = await db.collection("orders").doc(String(req.body.orderId || "")).get();
    if (!order.exists || order.get("buyerId") !== user.uid) {
      return res.status(403).json({ error: "order" });
    }
    if (order.get("paymentStatus") !== payments.PAYMENT_PENDING) {
      return res.status(409).json({ error: "state" });
    }
    const quote = await catalog(db, order.data());
    if (!quote.ok) return res.status(409).json({ error: quote.error });
    const token = await epaycoToken();
    if (!token) return res.status(502).json({ error: "epayco_login" });
    const created = await fetch("https://apify.epayco.co/payment/session/create", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        checkout_version: "2",
        name: "Club Canino",
        description: `Pedido ${order.id}`,
        currency: "COP",
        amount: quote.total,
        country: "CO",
        lang: "ES",
        invoice: order.id,
        test: testMode
      })
    });
    const json = await created.json();
    const sessionId = json?.data?.sessionId || "";
    if (!created.ok || !sessionId) return res.status(502).json({ error: "epayco_session" });
    res.json({ sessionId, test: true, amount: quote.total, currency: "COP" });
  } catch (_error) {
    res.status(500).json({ error: "session_failed" });
  }
});

app.post("/v1/payments/verify", async (req, res) => {
  try {
    const db = database();
    if (!status().payments || !db) {
      return res.status(503).json({ approved: false, error: "backend_not_configured" });
    }
    const user = await caller(req, db);
    if (!user) return res.status(401).json({ approved: false, error: "unauthorized" });
    const orderId = String(req.body.orderId || "");
    const reference = String(req.body.reference || "").trim();
    if (!orderId || !reference) return res.status(400).json({ approved: false, pending: true, error: "incomplete" });
    const orderRef = db.collection("orders").doc(orderId);
    const existing = await orderRef.get();
    if (!existing.exists || existing.get("buyerId") !== user.uid) {
      return res.status(403).json({ approved: false, error: "order" });
    }
    const duplicate = payments.alreadyApproved(existing.data());
    if (duplicate) return res.json(duplicate);
    if (existing.get("paymentStatus") !== payments.PAYMENT_PENDING) {
      return res.status(409).json({ approved: false, error: "state" });
    }
    const quote = await catalog(db, existing.data());
    if (!quote.ok) return res.status(409).json({ approved: false, pending: true, error: quote.error });
    let detailResponse;
    let detail = null;
    let thrown = false;
    try {
      detailResponse = await fetch(payments.transactionUrl(reference), {
        headers: { "Content-Type": "application/json" }
      });
      detail = await detailResponse.json();
    } catch (_error) {
      thrown = true;
    }
    const decision = payments.decideVerification({
      thrown,
      httpOk: Boolean(detailResponse?.ok),
      json: detail,
      reference,
      orderId,
      expectedCents: quote.totalCents,
      testMode
    });
    if (decision.outcome === "inconclusive") {
      return res.status(503).json({ approved: false, pending: true, error: decision.error });
    }
    if (decision.outcome === "rejected") {
      const rejected = await db.runTransaction((tx) => payments.rejectionTransaction(tx, orderRef, decision));
      if (rejected.duplicate) return res.json({ approved: true, duplicate: true, reference: rejected.reference });
      return res.json({ approved: false, reference: decision.reference, error: decision.error });
    }
    const approved = await db.runTransaction((tx) => payments.approvalTransaction(tx, {
      orderRef,
      productRef: (id) => db.collection("products").doc(id),
      reference,
      testMode
    }));
    if (!approved.ok) return res.status(409).json({ approved: false, pending: true, error: approved.error });
    res.json({ approved: true, reference: approved.reference, duplicate: Boolean(approved.duplicate) });
  } catch (_error) {
    res.status(503).json({ approved: false, pending: true, error: "communication" });
  }
});

app.post("/v1/admin/users/create", async (req, res) => {
  try {
    const db = database();
    if (!db) return res.status(503).json({ error: "backend_not_configured" });
    const user = await caller(req, db);
    const result = await users.createAccount({
      auth: admin.auth(),
      db,
      caller: user,
      input: req.body || {}
    });
    res.status(result.status).json(result.body);
  } catch (_error) {
    res.status(500).json({ error: "create_failed" });
  }
});

app.post("/v1/admin/users/delete", async (req, res) => {
  try {
    const db = database();
    if (!db) return res.status(503).json({ error: "backend_not_configured" });
    const user = await caller(req, db);
    const result = await users.deleteAccount({
      auth: admin.auth(),
      db,
      caller: user,
      uid: req.body.uid
    });
    res.status(result.status).json(result.body);
  } catch (_error) {
    res.status(500).json({ error: "delete_failed" });
  }
});

app.post("/v1/admin/users/update", async (req, res) => {
  try {
    const db = database();
    if (!db) return res.status(503).json({ error: "backend_not_configured" });
    const user = await caller(req, db);
    const result = await users.updateAccount({
      auth: admin.auth(),
      db,
      caller: user,
      input: req.body || {}
    });
    res.status(result.status).json(result.body);
  } catch (_error) {
    res.status(500).json({ error: "update_failed" });
  }
});

if (require.main === module) {
  app.listen(port, () => {
    const state = status();
    const detail = state.payments
      ? "listo para pagos de prueba"
      : `faltan ${state.missing.join(", ")}`;
    console.log(`Club Canino backend en el puerto ${port}: ${detail}`);
  });
}

module.exports = { app, status };
