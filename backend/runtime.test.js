const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const runtime = require("./runtime");

const NAMES = ["EPAYCO_PUBLIC_KEY", "EPAYCO_PRIVATE_KEY", "EPAYCO_TEST", "GOOGLE_APPLICATION_CREDENTIALS"];

function snapshot() {
  return Object.fromEntries(NAMES.map((name) => [name, process.env[name]]));
}

function restore(saved) {
  for (const name of NAMES) {
    if (saved[name] === undefined) delete process.env[name];
    else process.env[name] = saved[name];
  }
}

function withEnv(fileText, check) {
  const saved = snapshot();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clubcanino-env-"));
  try {
    for (const name of NAMES) delete process.env[name];
    const envFile = path.join(dir, ".env");
    if (fileText !== null) fs.writeFileSync(envFile, fileText);
    runtime.loadLocalEnv(envFile);
    check(dir);
  } finally {
    restore(saved);
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test("un env ausente no lanza y reporta lo que falta", () => {
  withEnv(null, () => {
    const ready = runtime.paymentReadiness();
    assert.equal(ready.test, true);
    assert.equal(ready.payments, false);
    assert.deepEqual(ready.missing, ["epayco_keys", "firebase_credentials"]);
  });
});

test("resuelve la cuenta de servicio junto al env sin copiar su contenido", () => {
  withEnv(
    [
      "EPAYCO_PUBLIC_KEY=public-de-prueba",
      "EPAYCO_PRIVATE_KEY=private-de-prueba",
      "EPAYCO_TEST=true",
      "GOOGLE_APPLICATION_CREDENTIALS=./service-account.json"
    ].join("\n"),
    (dir) => {
      const credentials = path.join(dir, "service-account.json");
      fs.writeFileSync(credentials, "{}\n");
      const ready = runtime.paymentReadiness();
      assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, credentials);
      assert.equal(ready.payments, true);
      assert.equal(ready.test, true);
      assert.equal(ready.liveBlocked, false);
    }
  );
});

test("un pedido de modo real sigue obligado a prueba", () => {
  withEnv("EPAYCO_TEST=false\n", () => {
    const ready = runtime.paymentReadiness();
    assert.equal(ready.test, true);
    assert.equal(ready.liveBlocked, true);
    assert.equal(ready.payments, false);
  });
});
