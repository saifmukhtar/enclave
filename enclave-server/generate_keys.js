#!/usr/bin/env node
/**
 * Enclave Cryptographic Key Generator & Rotator
 * Generates secure secrets and signs JWT tokens for self-hosted Supabase deployment.
 * Natively supports Node.js with ZERO external dependencies!
 */

const crypto = require('crypto');
const fs = require('fs');

function base64UrlEncode(str) {
  return Buffer.from(str)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function signJwt(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const base64Header = base64UrlEncode(JSON.stringify(header));
  const base64Payload = base64UrlEncode(JSON.stringify(payload));
  const signature = crypto
    .createHmac('sha256', secret)
    .update(`${base64Header}.${base64Payload}`)
    .digest('base64url');
  return `${base64Header}.${base64Payload}.${signature}`;
}

// 1. Generate secure base64 & hex keys
const jwtSecret = crypto.randomBytes(32).toString('base64');
const secretKeyBase = crypto.randomBytes(64).toString('hex');
const dbPassword = crypto.randomBytes(16).toString('hex');

// 2. Sign tokens (expiration far in the future: Year ~2036)
const exp = Math.round(new Date('2036-01-01').getTime() / 1000);

const anonPayload = {
  iss: "supabase",
  ref: "stub",
  role: "anon",
  iat: Math.round(Date.now() / 1000),
  exp: exp
};

const servicePayload = {
  iss: "supabase",
  ref: "stub",
  role: "service_role",
  iat: Math.round(Date.now() / 1000),
  exp: exp
};

const anonKey = signJwt(anonPayload, jwtSecret);
const serviceRoleKey = signJwt(servicePayload, jwtSecret);

const output = `================================================================================
🔐 ENCLAVE PRODUCTION RE-KEY GENERATOR
================================================================================

📍 [STEP 1] COPY THESE ENTRIES TO YOUR SERVER '.env' FILE:

JWT_SECRET=${jwtSecret}
SECRET_KEY_BASE=${secretKeyBase}
POSTGRES_PASSWORD=${dbPassword}
ANON_KEY=${anonKey}
SERVICE_ROLE_KEY=${serviceRoleKey}

--------------------------------------------------------------------------------

📍 [STEP 2] COPY THIS ENTRY TO YOUR CLIENT 'local.properties' FILE:

SUPABASE_KEY=${anonKey}

================================================================================
✅ Key generation complete! Copy these secure coordinates into place and deploy.
================================================================================
`;

fs.writeFileSync('enclave-secrets.txt', output);
console.log("✅ Secure keys generated and saved to 'enclave-secrets.txt'.");
console.log("\n================================================================================");
console.log("✅ Key generation complete! Copy these secure coordinates into place and deploy.");
console.log("================================================================================");
