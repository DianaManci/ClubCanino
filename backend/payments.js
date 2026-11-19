/**
 * Reglas de cobro de Club Canino. El total sale de los precios guardados en
 * Firestore. ePayco solo se acepta con los campos que documenta la pasarela:
 * x_ref_payco, x_id_invoice o x_id_factura, x_amount, x_currency_code,
 * x_cod_response, x_response y x_test_request.
 *
 * Códigos oficiales de x_cod_response: 1 aceptada, 2 rechazada, 3 pendiente,
 * 4 fallida, 6 reversada, 7 retenida, 8 iniciada, 9 caducada, 10 abandonada,
 * 11 cancelada. x_test_request llega como TRUE/FALSE o true/false.
 */

const PAYMENT_PENDING = "Pendiente de pago";
const PAYMENT_APPROVED = "Aprobado";
const PAYMENT_REJECTED = "Rechazado";
const PAYMENT_CANCELLED = "Cancelado";

const NEGATIVE_CODES = {
  2: PAYMENT_REJECTED,
  4: PAYMENT_REJECTED,
  6: PAYMENT_REJECTED,
  9: PAYMENT_REJECTED,
  10: PAYMENT_REJECTED,
  11: PAYMENT_CANCELLED
};

const OPEN_CODES = new Set(["3", "7", "8"]);

function cents(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return null;
  return Math.round(number * 100);
}

function pesos(totalCents) {
  return Number((totalCents / 100).toFixed(2));
}

function text(value) {
  if (value === undefined || value === null) return "";
  return String(value).trim();
}

function sameMoney(left, right) {
  return left !== null && right !== null && left === right;
}

function quoteOrder(order, productsById) {
  const lines = Array.isArray(order?.lines) ? order.lines : [];
  if (lines.length === 0) return { ok: false, error: "lines" };

  const groups = new Map();
  let totalCents = 0;

  for (const line of lines) {
    const productId = text(line?.productId);
    const quantity = Number(line?.quantity);
    if (!productId || !Number.isInteger(quantity) || quantity <= 0) {
      return { ok: false, error: "lines" };
    }
    const product = productsById.get(productId);
    if (!product) return { ok: false, error: "product" };
    if (product.active !== true) return { ok: false, error: "unavailable" };

    const sellerId = text(product.sellerId);
    const claimedSeller = text(line?.sellerId);
    if (!sellerId || (claimedSeller && claimedSeller !== sellerId)) {
      return { ok: false, error: "product" };
    }

    const realCents = cents(product.price);
    const claimedUnit = cents(line?.unitPrice);
    const claimedLine = cents(line?.lineTotal);
    if (realCents === null || realCents < 0) return { ok: false, error: "price" };
    const expectedLine = realCents * quantity;
    if (!sameMoney(claimedUnit, realCents) || !sameMoney(claimedLine, expectedLine)) {
      return { ok: false, error: "price" };
    }

    totalCents += expectedLine;
    groups.set(productId, (groups.get(productId) || 0) + quantity);
  }

  const grouped = [];
  for (const [productId, quantity] of groups) {
    const stock = Number(productsById.get(productId).stock);
    if (!Number.isFinite(stock) || stock < quantity) return { ok: false, error: "stock" };
    grouped.push({ productId, quantity, unitCents: cents(productsById.get(productId).price) });
  }

  const claimedSubtotal = cents(order?.subtotal);
  const claimedTotal = cents(order?.total);
  if (!sameMoney(claimedSubtotal, totalCents) || !sameMoney(claimedTotal, totalCents)) {
    return { ok: false, error: "price" };
  }

  return { ok: true, totalCents, subtotal: pesos(totalCents), total: pesos(totalCents), groups: grouped };
}

function parseTestRequest(value) {
  const flag = text(value).toLowerCase();
  if (flag === "true" || flag === "1") return true;
  if (flag === "false" || flag === "0") return false;
  return null;
}

function transactionData(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return null;
  if (body.data && typeof body.data === "object" && !Array.isArray(body.data)) return body.data;
  if (text(body.x_ref_payco) || text(body.x_cod_response) || text(body.x_response)) return body;
  return null;
}

function interpretEpayco(body, { reference, orderId, expectedCents, testMode }) {
  const data = transactionData(body);
  if (!data) return { outcome: "inconclusive", error: "incomplete" };

  const returnedRef = text(data.x_ref_payco);
  if (!returnedRef || returnedRef !== text(reference)) {
    return { outcome: "inconclusive", error: "reference" };
  }

  const invoice = text(data.x_id_invoice) || text(data.x_id_factura);
  if (!invoice || invoice !== text(orderId)) {
    return { outcome: "inconclusive", error: "invoice" };
  }

  const currency = text(data.x_currency_code).toUpperCase();
  if (!currency) return { outcome: "inconclusive", error: "currency" };

  if (data.x_amount === undefined || data.x_amount === null || data.x_amount === "") {
    return { outcome: "inconclusive", error: "amount" };
  }
  const amountCents = cents(data.x_amount);
  if (amountCents === null) return { outcome: "inconclusive", error: "amount" };

  const testFlag = parseTestRequest(data.x_test_request);
  if (testFlag === null) return { outcome: "inconclusive", error: "test_mode" };

  const code = text(data.x_cod_response);
  const responseText = text(data.x_response);
  if (!code) return { outcome: "inconclusive", error: "incomplete" };

  if (currency !== "COP") {
    return { outcome: "rejected", status: PAYMENT_REJECTED, error: "currency", reference: returnedRef, code };
  }
  if (amountCents !== expectedCents) {
    return { outcome: "rejected", status: PAYMENT_REJECTED, error: "amount", reference: returnedRef, code };
  }
  if (testFlag !== testMode) {
    return { outcome: "rejected", status: PAYMENT_REJECTED, error: "test_mode", reference: returnedRef, code };
  }

  const normalized = responseText.toLowerCase();
  if (OPEN_CODES.has(code) || normalized === "pendiente") {
    return { outcome: "inconclusive", error: "pending", reference: returnedRef, code };
  }
  if (code === "1" && normalized === "aceptada") {
    return { outcome: "approved", reference: returnedRef, code, response: responseText };
  }
  if (NEGATIVE_CODES[code]) {
    return {
      outcome: "rejected",
      status: NEGATIVE_CODES[code],
      error: "rejected",
      reference: returnedRef,
      code,
      response: responseText
    };
  }
  return { outcome: "inconclusive", error: "incomplete", reference: returnedRef };
}

function transactionUrl(reference) {
  const clean = text(reference);
  return `https://secure.epayco.co/validation/v1/reference/${encodeURIComponent(clean)}`;
}

function decideVerification(input) {
  if (input.thrown || input.httpOk !== true) {
    return { outcome: "inconclusive", error: "communication" };
  }
  return interpretEpayco(input.json, input);
}

function alreadyApproved(order) {
  if (text(order?.paymentStatus) !== PAYMENT_APPROVED) return null;
  return { approved: true, duplicate: true, reference: text(order.paymentRef) };
}

async function approvalTransaction(tx, { orderRef, productRef, reference, testMode }) {
  const fresh = await tx.get(orderRef);
  if (!fresh.exists) return { ok: false, error: "order" };
  const approved = alreadyApproved(fresh.data());
  if (approved) return { ok: true, duplicate: true, reference: approved.reference };
  if (text(fresh.get("paymentStatus")) !== PAYMENT_PENDING) return { ok: false, error: "state" };

  const lines = Array.isArray(fresh.get("lines")) ? fresh.get("lines") : [];
  const ids = [...new Set(lines.map((line) => text(line?.productId)).filter(Boolean))];
  const snaps = new Map();
  const products = new Map();
  for (const id of ids) {
    const ref = productRef(id);
    const snap = await tx.get(ref);
    snaps.set(id, { ref, snap });
    if (snap.exists) products.set(id, snap.data());
  }

  const quote = quoteOrder(fresh.data(), products);
  if (!quote.ok) return { ok: false, error: quote.error };

  for (const group of quote.groups) {
    const { snap } = snaps.get(group.productId);
    tx.update(snaps.get(group.productId).ref, { stock: Number(snap.get("stock")) - group.quantity });
  }
  tx.update(orderRef, {
    paymentStatus: PAYMENT_APPROVED,
    paymentRef: reference,
    paymentResult: "Aceptada",
    epaycoCode: "1",
    status: "Pendiente",
    testPayment: testMode,
    subtotal: quote.subtotal,
    total: quote.total,
    currency: "COP"
  });
  return { ok: true, duplicate: false, reference, total: quote.total };
}

async function rejectionTransaction(tx, orderRef, decision) {
  const fresh = await tx.get(orderRef);
  if (!fresh.exists) return { ok: false, error: "order" };
  const approved = alreadyApproved(fresh.data());
  if (approved) return { ok: true, duplicate: true, reference: approved.reference };
  if (text(fresh.get("paymentStatus")) !== PAYMENT_PENDING) return { ok: false, error: "state" };
  tx.update(orderRef, {
    paymentStatus: decision.status,
    paymentRef: decision.reference,
    paymentResult: decision.response || decision.error,
    epaycoCode: decision.code || ""
  });
  return { ok: true, duplicate: false };
}

module.exports = {
  PAYMENT_PENDING,
  PAYMENT_APPROVED,
  cents,
  pesos,
  quoteOrder,
  interpretEpayco,
  transactionUrl,
  decideVerification,
  alreadyApproved,
  approvalTransaction,
  rejectionTransaction
};
