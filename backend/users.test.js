const test = require("node:test");
const assert = require("node:assert/strict");
const users = require("./users");

function createDb(initial, hooks = {}) {
  const docs = new Map(Object.entries(initial));
  const refs = new Map();

  function snap(path, data) {
    return {
      id: path.split("/").pop(),
      exists: data !== undefined,
      get(field) { return data ? data[field] : undefined; },
      data() { return data; },
      ref: { path }
    };
  }

  function refFor(id) {
    if (refs.has(id)) return refs.get(id);
    const path = `users/${id}`;
    const ref = {
      path,
      async get() { return snap(path, docs.get(path)); },
      async set(data, options) {
        if (hooks.failSet && hooks.failSet(id, data)) throw new Error("firestore down");
        const current = docs.get(path) || {};
        docs.set(path, options && options.merge ? { ...current, ...data } : { ...data });
      }
    };
    refs.set(id, ref);
    return ref;
  }

  return {
    docs,
    collection() {
      return {
        doc(id) { return refFor(id); },
        where(field, _op, value) { return { kind: "query", field, value }; }
      };
    },
    async runTransaction(fn) {
      const snapshot = new Map(docs);
      const writes = new Map();
      const deletes = new Set();
      const tx = {
        async get(target) {
          if (target.kind === "query") {
            const found = [...snapshot.entries()]
              .filter(([path, data]) => path.startsWith("users/") && data && data[target.field] === target.value)
              .map(([path, data]) => snap(path, data));
            return { docs: found };
          }
          if (deletes.has(target.path)) return snap(target.path, undefined);
          const data = writes.has(target.path) ? writes.get(target.path) : snapshot.get(target.path);
          return snap(target.path, data);
        },
        set(ref, data, options) {
          const current = writes.get(ref.path) || snapshot.get(ref.path) || {};
          writes.set(ref.path, options && options.merge ? { ...current, ...data } : { ...data });
          deletes.delete(ref.path);
        },
        delete(ref) {
          deletes.add(ref.path);
          writes.delete(ref.path);
        }
      };
      const result = await fn(tx);
      if (!result.lastAdmin && !result.missing) {
        for (const path of deletes) docs.delete(path);
        for (const [path, data] of writes) docs.set(path, data);
      }
      return result;
    }
  };
}

function authDouble() {
  const calls = [];
  return {
    calls,
    async createUser(data) {
      calls.push(["create", data.email]);
      return { uid: "new-admin" };
    },
    async deleteUser(uid) { calls.push(["delete", uid]); },
    async updateUser(uid, data) { calls.push(["update", uid, { ...data }]); }
  };
}

const admin = { role: "Administrador", uid: "admin-1" };

test("solo un administrador puede crear otra cuenta de administrador", async () => {
  const db = createDb({ "users/admin-1": { role: "Administrador", email: "admin@example.com" } });
  const auth = authDouble();
  const denied = await users.createAccount({
    auth,
    db,
    caller: { role: "Vendedor", uid: "seller-1" },
    input: { email: "nuevo.admin@example.com", password: "clave123", role: "Administrador", fullName: "Nueva" }
  });
  assert.equal(denied.status, 403);
  assert.equal(auth.calls.length, 0);

  const created = await users.createAccount({
    auth,
    db,
    caller: admin,
    input: { email: "nuevo.admin@example.com", password: "clave123", role: "Administrador", fullName: "Nueva", phone: "300" }
  });
  assert.equal(created.status, 200);
  assert.equal(created.body.uid, "new-admin");
  assert.equal(db.docs.get("users/new-admin").role, "Administrador");
  assert.equal(db.docs.get("users/new-admin").email, "nuevo.admin@example.com");
});

test("si Firestore no guarda la cuenta nueva, borra el usuario de Authentication", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "admin@example.com" }
  }, {
    failSet(id) { return id === "new-admin"; }
  });
  const auth = authDouble();
  const result = await users.createAccount({
    auth,
    db,
    caller: admin,
    input: { email: "nuevo.admin@example.com", password: "clave123", role: "Administrador", fullName: "Nueva" }
  });
  assert.equal(result.body.error, "sync_failed");
  assert.equal(result.body.restored, true);
  assert.equal(db.docs.has("users/new-admin"), false);
  assert.deepEqual(auth.calls, [
    ["create", "nuevo.admin@example.com"],
    ["delete", "new-admin"]
  ]);
});

test("un administrador no se elimina a sí mismo", async () => {
  const db = createDb({ "users/admin-1": { role: "Administrador", email: "admin@example.com" } });
  const auth = authDouble();
  const result = await users.deleteAccount({ auth, db, caller: admin, uid: "admin-1" });
  assert.equal(result.status, 400);
  assert.equal(db.docs.has("users/admin-1"), true);
  assert.equal(auth.calls.length, 0);
});

test("nadie puede borrar al último administrador", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "admin@example.com" }
  });
  const auth = authDouble();
  const result = await users.deleteAccount({
    auth,
    db,
    caller: { role: "Administrador", uid: "admin-2" },
    uid: "admin-1"
  });
  assert.equal(result.status, 409);
  assert.equal(result.body.error, "last_admin");
  assert.equal(db.docs.has("users/admin-1"), true);
  assert.equal(auth.calls.length, 0);
});

test("no degrada al último administrador", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "admin@example.com", fullName: "Valeria" }
  });
  const auth = authDouble();
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "admin-1", email: "admin@example.com", role: "Vendedor", fullName: "Valeria" }
  });
  assert.equal(result.body.error, "last_admin");
  assert.equal(db.docs.get("users/admin-1").role, "Administrador");
  assert.equal(auth.calls.length, 0);
});

test("con dos administradores sí elimina a uno y su cuenta", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/admin-2": { role: "Administrador", email: "b@example.com" }
  });
  const auth = authDouble();
  const result = await users.deleteAccount({ auth, db, caller: admin, uid: "admin-2" });
  assert.equal(result.body.deleted, true);
  assert.equal(db.docs.has("users/admin-2"), false);
  assert.deepEqual(auth.calls[0], ["delete", "admin-2"]);
});

test("el segundo borrado concurrente conserva al último administrador", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/admin-2": { role: "Administrador", email: "b@example.com" }
  });
  const auth = authDouble();
  const caller = { role: "Administrador", uid: "admin-9" };
  const first = await users.deleteAccount({ auth, db, caller, uid: "admin-2" });
  const second = await users.deleteAccount({ auth, db, caller, uid: "admin-1" });
  assert.equal(first.body.deleted, true);
  assert.equal(second.body.error, "last_admin");
  assert.equal(db.docs.get("users/admin-1").role, "Administrador");
});

test("si Authentication no borra, restaura el documento", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "c@example.com", fullName: "Camila" }
  });
  const auth = authDouble();
  auth.deleteUser = async () => {
    const error = new Error("auth down");
    error.code = "auth/network";
    throw error;
  };
  const result = await users.deleteAccount({ auth, db, caller: admin, uid: "seller-1" });
  assert.equal(result.body.error, "sync_failed");
  assert.equal(result.body.restored, true);
  assert.equal(db.docs.get("users/seller-1").email, "c@example.com");
  assert.equal(db.docs.get("users/seller-1").fullName, "Camila");
});

test("cambia el correo en Authentication y Firestore", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "viejo@example.com", fullName: "Camila" }
  });
  const auth = authDouble();
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: {
      uid: "seller-1",
      email: "nuevo@example.com",
      role: "Vendedor",
      fullName: "Camila Ruiz",
      phone: "300",
      storeName: "Tienda"
    }
  });
  assert.equal(result.status, 200);
  assert.equal(db.docs.get("users/seller-1").email, "nuevo@example.com");
  assert.equal(db.docs.get("users/seller-1").fullName, "Camila Ruiz");
  assert.deepEqual(auth.calls[0], ["update", "seller-1", { email: "nuevo@example.com" }]);
});

test("si el correo de Authentication falla, no deja el documento cambiado", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "viejo@example.com", fullName: "Camila" }
  });
  const auth = authDouble();
  auth.updateUser = async () => {
    const error = new Error("taken");
    error.code = "auth/email-already-exists";
    throw error;
  };
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "seller-1", email: "nuevo@example.com", role: "Comprador", fullName: "Otra" }
  });
  assert.equal(result.body.error, "duplicate_email");
  assert.equal(db.docs.get("users/seller-1").email, "viejo@example.com");
  assert.equal(db.docs.get("users/seller-1").role, "Vendedor");
  assert.equal(db.docs.get("users/seller-1").fullName, "Camila");
});

test("si Firestore no guarda el correo nuevo, revierte Authentication", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "viejo@example.com", fullName: "Camila" }
  }, {
    failSet(id, data) { return id === "seller-1" && data.email === "nuevo@example.com"; }
  });
  const auth = authDouble();
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "seller-1", email: "nuevo@example.com", role: "Vendedor", fullName: "Camila" }
  });
  assert.equal(result.body.error, "sync_failed");
  assert.equal(result.body.restored, true);
  assert.equal(db.docs.get("users/seller-1").email, "viejo@example.com");
  assert.deepEqual(auth.calls, [
    ["update", "seller-1", { email: "nuevo@example.com" }],
    ["update", "seller-1", { email: "viejo@example.com" }]
  ]);
});

test("la contraseña se aplica después de sincronizar el perfil", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "c@example.com", fullName: "Camila" }
  });
  const auth = authDouble();
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "seller-1", email: "c@example.com", role: "Vendedor", fullName: "Camila", password: "secreto1" }
  });
  assert.equal(result.status, 200);
  assert.deepEqual(auth.calls, [["update", "seller-1", { password: "secreto1" }]]);
});

test("con dos administradores sí cambia el rol de uno", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com", fullName: "Valeria" },
    "users/admin-2": { role: "Administrador", email: "b@example.com", fullName: "Otro" }
  });
  const auth = authDouble();
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "admin-2", email: "b@example.com", role: "Comprador", fullName: "Otro" }
  });
  assert.equal(result.status, 200);
  assert.equal(db.docs.get("users/admin-2").role, "Comprador");
  assert.equal(db.docs.get("users/admin-1").role, "Administrador");
  assert.equal(auth.calls.length, 0);
});

test("si la contraseña falla, no informa la actualización como exitosa", async () => {
  const db = createDb({
    "users/admin-1": { role: "Administrador", email: "a@example.com" },
    "users/seller-1": { role: "Vendedor", email: "c@example.com", fullName: "Camila" }
  });
  const auth = authDouble();
  auth.updateUser = async (uid, data) => {
    auth.calls.push(["update", uid, { ...data }]);
    if (data.password) {
      const error = new Error("password");
      error.code = "auth/weak-password";
      throw error;
    }
  };
  const result = await users.updateAccount({
    auth,
    db,
    caller: admin,
    input: { uid: "seller-1", email: "c@example.com", role: "Vendedor", fullName: "Camila", password: "secreto1" }
  });
  assert.equal(result.status, 409);
  assert.equal(result.body.error, "password_failed");
  assert.equal(db.docs.get("users/seller-1").email, "c@example.com");
});

test("sin token o sin rol administrador se rechaza la operación", async () => {
  const db = createDb({ "users/seller-1": { role: "Vendedor", email: "c@example.com" } });
  const auth = authDouble();
  const removed = await users.deleteAccount({
    auth, db, caller: { role: "Comprador", uid: "buyer-1" }, uid: "seller-1"
  });
  const updated = await users.updateAccount({
    auth, db, caller: null, input: { uid: "seller-1", email: "c@example.com", role: "Vendedor" }
  });
  assert.equal(removed.status, 403);
  assert.equal(updated.status, 401);
  assert.equal(db.docs.has("users/seller-1"), true);
  assert.equal(auth.calls.length, 0);
});
