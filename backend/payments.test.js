const test = require("node:test");
const assert = require("node:assert/strict");
const payments = require("./payments");

function product(overrides = {}) {
  return { price: 15000, stock: 5, active: true, sellerId: "seller-1", ...overrides };
}

function line(quantity, unitPrice = 15000, productId = "food") {
  return {
    productId,
    quantity,
    unitPrice,
    lineTotal: unitPrice * quantity,
    sellerId: "seller-1"
  };
}

function order(lines, overrides = {}) {
  const subtotal = lines.reduce((sum, item) => sum + item.lineTotal, 0);
  return { lines, subtotal, total: subtotal, paymentStatus: "Pendiente de pago", ...overrides };
}

function acceptedBody(overrides = {}) {
  return {
    success: true,
    data: {
      x_ref_payco: "123456",
      x_id_invoice: "ORD-1",
      x_amount: "30000.00",
      x_currency_code: "COP",
      x_cod_response: 1,
      x_response: "Aceptada",
      x_test_request: "TRUE",
      ...overrides
    }
  };
}

function check(body, expectedCents = 3000000) {
  return payments.decideVerification({
    thrown: false,
    httpOk: true,
    json: body,
    reference: "123456",
    orderId: "ORD-1",
    expectedCents,
    testMode: true
  });
}

test("rechaza un precio distinto al de Firestore", () => {
  const lines = [line(2, 1)];
  const result = payments.quoteOrder(order(lines, { subtotal: 2, total: 2 }), new Map([["food", product()]]));
  assert.equal(result.ok, false);
  assert.equal(result.error, "price");
});

test("rechaza un producto que no existe", () => {
  const lines = [line(1)];
  const result = payments.quoteOrder(order(lines), new Map());
  assert.equal(result.ok, false);
  assert.equal(result.error, "product");
});

test("rechaza existencias insuficientes", () => {
  const lines = [line(4)];
  const result = payments.quoteOrder(order(lines), new Map([["food", product({ stock: 3 })]]));
  assert.equal(result.ok, false);
  assert.equal(result.error, "stock");
});

test("suma dos líneas del mismo producto antes de mirar el stock", () => {
  const lines = [line(2), line(2)];
  const result = payments.quoteOrder(order(lines), new Map([["food", product({ stock: 4 })]]));
  assert.equal(result.ok, true);
  assert.equal(result.total, 60000);
  assert.deepEqual(result.groups, [{ productId: "food", quantity: 4, unitCents: 1500000 }]);
});

test("dos líneas del mismo producto no pasan si la suma supera el stock", () => {
  const lines = [line(2), line(3)];
  const result = payments.quoteOrder(order(lines), new Map([["food", product({ stock: 4 })]]));
  assert.equal(result.error, "stock");
});

function memoryStore(entries) {
  const store = new Map(entries);
  return {
    store,
    tx() {
      const staged = new Map();
      return {
        async get(ref) {
          const data = staged.has(ref.path) ? staged.get(ref.path) : store.get(ref.path);
          return {
            exists: data !== undefined,
            get(field) { return data ? data[field] : undefined; },
            data() { return data; }
          };
        },
        update(ref, data) {
          const current = staged.get(ref.path) || store.get(ref.path) || {};
          staged.set(ref.path, { ...current, ...data });
        },
        commit() {
          for (const [path, data] of staged) store.set(path, data);
        }
      };
    }
  };
}

function refs() {
  return {
    orderRef: { path: "orders/ORD-1" },
    productRef(id) { return { path: `products/${id}` }; }
  };
}

test("dos aprobaciones del mismo pedido descuentan el inventario una sola vez", async () => {
  const lines = [line(2), line(1)];
  const memory = memoryStore([
    ["orders/ORD-1", order(lines)],
    ["products/food", product({ stock: 5 })]
  ]);
  const { orderRef, productRef } = refs();
  const firstTx = memory.tx();
  const first = await payments.approvalTransaction(firstTx, {
    orderRef, productRef, reference: "123456", testMode: true
  });
  firstTx.commit();
  const secondTx = memory.tx();
  const second = await payments.approvalTransaction(secondTx, {
    orderRef, productRef, reference: "999999", testMode: true
  });
  secondTx.commit();
  assert.equal(first.duplicate, false);
  assert.equal(second.duplicate, true);
  assert.equal(second.reference, "123456");
  assert.equal(memory.store.get("products/food").stock, 2);
  assert.equal(memory.store.get("orders/ORD-1").paymentStatus, "Aprobado");
});

test("un pedido aprobado con otra referencia no vuelve a descontar", async () => {
  const lines = [line(1)];
  const memory = memoryStore([
    ["orders/ORD-1", order(lines, { paymentStatus: "Aprobado", paymentRef: "111" })],
    ["products/food", product({ stock: 4 })]
  ]);
  const seen = payments.alreadyApproved(memory.store.get("orders/ORD-1"));
  assert.equal(seen.duplicate, true);
  assert.equal(seen.reference, "111");
  const tx = memory.tx();
  const result = await payments.approvalTransaction(tx, {
    ...refs(),
    reference: "222",
    testMode: true
  });
  tx.commit();
  assert.equal(result.duplicate, true);
  assert.equal(result.reference, "111");
  assert.equal(memory.store.get("products/food").stock, 4);
});

test("una respuesta sin referencia deja el resultado inconcluso", () => {
  const body = acceptedBody();
  delete body.data.x_ref_payco;
  const result = check(body);
  assert.equal(result.outcome, "inconclusive");
  assert.equal(result.error, "reference");
});

test("una moneda distinta de COP no aprueba el pago", () => {
  const result = check(acceptedBody({ x_currency_code: "USD" }));
  assert.equal(result.outcome, "rejected");
  assert.equal(result.error, "currency");
});

test("no asume COP cuando la moneda no viene", () => {
  const body = acceptedBody();
  delete body.data.x_currency_code;
  const result = check(body);
  assert.equal(result.outcome, "inconclusive");
  assert.equal(result.error, "currency");
});

test("un importe distinto al total del servidor no aprueba el pago", () => {
  const result = check(acceptedBody({ x_amount: "1.00" }));
  assert.equal(result.outcome, "rejected");
  assert.equal(result.error, "amount");
});

test("un error de comunicación no cambia el pedido a rechazado", () => {
  const result = payments.decideVerification({
    thrown: true,
    httpOk: false,
    json: null,
    reference: "123456",
    orderId: "ORD-1",
    expectedCents: 3000000,
    testMode: true
  });
  assert.equal(result.outcome, "inconclusive");
  assert.equal(result.error, "communication");
});

test("acepta la respuesta oficial solo si código, texto, referencia y prueba coinciden", () => {
  const result = check(acceptedBody());
  assert.equal(result.outcome, "approved");
  assert.equal(result.reference, "123456");
});

test("un código pendiente no se trata como rechazo ni como aprobación", () => {
  const result = check(acceptedBody({ x_cod_response: 3, x_response: "Pendiente" }));
  assert.equal(result.outcome, "inconclusive");
  assert.equal(result.error, "pending");
});

test("una respuesta rechazada queda registrada como rechazo", () => {
  const result = check(acceptedBody({ x_cod_response: 2, x_response: "Rechazada" }));
  assert.equal(result.outcome, "rejected");
  assert.equal(result.status, "Rechazado");
});

test("consulta la referencia en la validación oficial de ePayco", () => {
  assert.equal(
    payments.transactionUrl("68fb83729d094878e015be00"),
    "https://secure.epayco.co/validation/v1/reference/68fb83729d094878e015be00"
  );
});

test("el modo de prueba debe coincidir con la configuración del backend", () => {
  const result = check(acceptedBody({ x_test_request: "FALSE" }));
  assert.equal(result.outcome, "rejected");
  assert.equal(result.error, "test_mode");
});
