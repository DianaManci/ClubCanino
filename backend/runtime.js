/**
 * Carga backend/.env sin imprimir secretos y deja los pagos en modo de prueba.
 * Una ruta relativa de la cuenta de servicio se resuelve junto a este archivo,
 * no según la carpeta desde la que se arrancó Node.
 */
const fs = require("fs");
const path = require("path");

const ENV_FILE = path.join(__dirname, ".env");

function loadLocalEnv(envFile = ENV_FILE) {
  if (typeof process.loadEnvFile !== "function") {
    throw new Error("Se necesita Node.js 20.12 o superior para leer backend/.env");
  }
  const envPath = path.resolve(envFile);
  if (fs.existsSync(envPath)) {
    process.loadEnvFile(envPath);
  }
  for (const name of ["EPAYCO_PUBLIC_KEY", "EPAYCO_PRIVATE_KEY", "EPAYCO_TEST", "GOOGLE_APPLICATION_CREDENTIALS"]) {
    if (process.env[name] !== undefined) {
      process.env[name] = String(process.env[name]).trim();
    }
  }
  const credentials = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (credentials && !path.isAbsolute(credentials)) {
    process.env.GOOGLE_APPLICATION_CREDENTIALS = path.resolve(path.dirname(envPath), credentials);
  }
}

function inspectCredentials() {
  const credentials = process.env.GOOGLE_APPLICATION_CREDENTIALS || "";
  const report = {
    configured: Boolean(credentials),
    exists: false,
    readable: false,
    json: false,
    code: ""
  };
  if (!credentials) return report;
  report.exists = fs.existsSync(credentials);
  if (!report.exists) return report;
  let text = "";
  try {
    fs.accessSync(credentials, fs.constants.R_OK);
    text = fs.readFileSync(credentials, "utf8");
    report.readable = true;
  } catch (error) {
    report.code = error.code || error.name || "read_error";
    return report;
  }
  try {
    const parsed = JSON.parse(text);
    report.json = Boolean(parsed) && typeof parsed === "object" && !Array.isArray(parsed);
  } catch (error) {
    report.code = error.name || "parse_error";
  }
  return report;
}

function paymentReadiness() {
  const liveRequested = String(process.env.EPAYCO_TEST || "true").toLowerCase() === "false";
  const hasKeys = Boolean(process.env.EPAYCO_PUBLIC_KEY) && Boolean(process.env.EPAYCO_PRIVATE_KEY);
  const credentials = process.env.GOOGLE_APPLICATION_CREDENTIALS || "";
  const credentialsFile = Boolean(credentials) && fs.existsSync(credentials);
  const missing = [];
  if (!hasKeys) missing.push("epayco_keys");
  if (!credentialsFile) missing.push("firebase_credentials");
  return {
    test: true,
    liveBlocked: liveRequested,
    payments: missing.length === 0,
    missing
  };
}

module.exports = {
  ENV_FILE,
  loadLocalEnv,
  inspectCredentials,
  paymentReadiness
};
