/**
 * Administración de cuentas. Authentication y Firestore no forman una sola
 * transacción: primero se reserva el cambio en Firestore y, si Authentication
 * falla, se restaura el documento. La contraseña se cambia al final porque
 * Firebase no permite leerla ni devolverla a su valor anterior.
 */

const ROLES = ["Comprador", "Vendedor", "Administrador"];
const ADMIN = "Administrador";

function status(code, body) {
  return { status: code, body };
}

function wouldRemoveLastAdmin(targetRole, adminIds, targetUid, nextRole) {
  if (targetRole !== ADMIN) return false;
  const others = adminIds.filter((id) => id !== targetUid);
  return others.length === 0 && nextRole !== ADMIN;
}

function validEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function lastAdminError() {
  const error = new Error("last_admin");
  error.code = "last_admin";
  return error;
}

async function adminIds(tx, db) {
  const query = await tx.get(db.collection("users").where("role", "==", ADMIN));
  return query.docs.map((doc) => doc.id);
}

async function restoreProfile(db, uid, previous) {
  await db.collection("users").doc(uid).set(previous);
}

async function createAccount({ auth, db, caller, input }) {
  if (!caller) return status(401, { error: "unauthorized" });
  if (caller.role !== ADMIN) return status(403, { error: "forbidden" });
  const email = String(input.email || "").trim();
  const password = String(input.password || "");
  const role = String(input.role || "").trim();
  const profile = {
    fullName: String(input.fullName || "").trim(),
    email,
    phone: String(input.phone || "").trim(),
    role,
    storeName: String(input.storeName || "").trim()
  };
  if (!profile.fullName || !validEmail(email)) return status(400, { error: "data" });
  if (!ROLES.includes(role)) return status(400, { error: "role" });
  if (password.length < 6) return status(400, { error: "password" });

  let created;
  try {
    created = await auth.createUser({ email, password });
  } catch (error) {
    const code = error && error.code === "auth/email-already-exists" ? "duplicate_email" : "create_failed";
    return status(409, { error: code });
  }
  try {
    await db.collection("users").doc(created.uid).set(profile);
  } catch (_error) {
    try {
      await auth.deleteUser(created.uid);
      return status(409, { error: "sync_failed", restored: true });
    } catch (_delete) {
      return status(409, { error: "sync_failed", restored: false });
    }
  }
  return status(200, { created: true, uid: created.uid });
}

async function deleteAccount({ auth, db, caller, uid }) {
  if (!caller) return status(401, { error: "unauthorized" });
  if (caller.role !== ADMIN) return status(403, { error: "forbidden" });
  const targetUid = String(uid || "").trim();
  if (!targetUid || targetUid === caller.uid) return status(400, { error: "uid" });

  let removed;
  try {
    removed = await db.runTransaction(async (tx) => {
      const ref = db.collection("users").doc(targetUid);
      const target = await tx.get(ref);
      if (!target.exists) return { missing: true };
      const ids = await adminIds(tx, db);
      if (wouldRemoveLastAdmin(target.get("role"), ids, targetUid, null)) {
        throw lastAdminError();
      }
      const data = target.data();
      tx.delete(ref);
      return { data };
    });
  } catch (error) {
    if (error && error.code === "last_admin") return status(409, { error: "last_admin" });
    return status(500, { error: "delete_failed" });
  }
  if (removed.missing) return status(404, { error: "not_found" });

  try {
    await auth.deleteUser(targetUid);
  } catch (error) {
    if (error && error.code === "auth/user-not-found") {
      return status(200, { deleted: true, authMissing: true });
    }
    try {
      await restoreProfile(db, targetUid, removed.data);
      return status(409, { error: "sync_failed", restored: true });
    } catch (_restore) {
      return status(409, { error: "sync_failed", restored: false });
    }
  }
  return status(200, { deleted: true });
}

async function updateAccount({ auth, db, caller, input }) {
  if (!caller) return status(401, { error: "unauthorized" });
  if (caller.role !== ADMIN) return status(403, { error: "forbidden" });
  const uid = String(input.uid || "").trim();
  const email = String(input.email || "").trim();
  const password = String(input.password || "");
  const role = String(input.role || "").trim();
  const profile = {
    fullName: String(input.fullName || "").trim(),
    phone: String(input.phone || "").trim(),
    role,
    storeName: String(input.storeName || "").trim()
  };
  if (!uid || !validEmail(email)) return status(400, { error: "data" });
  if (!ROLES.includes(role)) return status(400, { error: "role" });
  if (password && password.length < 6) return status(400, { error: "password" });

  let reserved;
  try {
    reserved = await db.runTransaction(async (tx) => {
      const ref = db.collection("users").doc(uid);
      const target = await tx.get(ref);
      if (!target.exists) return { missing: true };
      const ids = await adminIds(tx, db);
      if (wouldRemoveLastAdmin(target.get("role"), ids, uid, role)) {
        throw lastAdminError();
      }
      const data = target.data();
      tx.set(ref, profile, { merge: true });
      return { data };
    });
  } catch (error) {
    if (error && error.code === "last_admin") return status(409, { error: "last_admin" });
    return status(500, { error: "update_failed" });
  }
  if (reserved.missing) return status(404, { error: "not_found" });

  const before = reserved.data;
  const emailChanged = String(before.email || "").toLowerCase() !== email.toLowerCase();
  if (emailChanged) {
    try {
      await auth.updateUser(uid, { email });
    } catch (error) {
      await restoreQuietly(db, uid, before);
      const code = error && error.code === "auth/email-already-exists" ? "duplicate_email" : "sync_failed";
      return status(409, { error: code });
    }
  }

  try {
    await db.collection("users").doc(uid).set({ ...profile, email }, { merge: true });
  } catch (_error) {
    if (emailChanged) {
      try {
        await auth.updateUser(uid, { email: before.email });
      } catch (_revert) {
        return status(409, { error: "sync_failed", restored: false });
      }
    }
    const restored = await restoreQuietly(db, uid, before);
    return status(409, { error: "sync_failed", restored });
  }

  if (password) {
    try {
      await auth.updateUser(uid, { password });
    } catch (_error) {
      return status(409, { error: "password_failed", profileUpdated: true });
    }
  }
  return status(200, { updated: true });
}

async function restoreQuietly(db, uid, previous) {
  try {
    await restoreProfile(db, uid, previous);
  } catch (_error) {
    return false;
  }
  return true;
}

module.exports = {
  ROLES,
  ADMIN,
  wouldRemoveLastAdmin,
  createAccount,
  deleteAccount,
  updateAccount
};
