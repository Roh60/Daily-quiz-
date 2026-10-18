// Run only in a trusted developer environment with Firebase Admin credentials.
// Usage: ADMIN_UID=<firebase-auth-uid> ADMIN_ACTION=grant node functions/set-admin-claim.js
// To revoke: ADMIN_ACTION=revoke. This script never accepts an email or UID from an app client.
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

async function main() {
  const uid = process.env.ADMIN_UID;
  const action = process.env.ADMIN_ACTION || 'grant';
  const projectId = process.env.GCLOUD_PROJECT || process.env.FIREBASE_PROJECT_ID;
  if (!uid || !/^[A-Za-z0-9_-]{1,128}$/.test(uid)) throw new Error('Set ADMIN_UID to the exact Firebase Auth UID.');
  if (!['grant', 'revoke'].includes(action)) throw new Error('ADMIN_ACTION must be grant or revoke.');
  if (!projectId) throw new Error('Set GCLOUD_PROJECT or FIREBASE_PROJECT_ID.');
  initializeApp({ credential: applicationDefault(), projectId });
  const auth = getAuth();
  const user = await auth.getUser(uid);
  const claims = { ...(user.customClaims || {}) };
  if (action === 'grant') claims.admin = true;
  else delete claims.admin;
  await auth.setCustomUserClaims(uid, claims);
  console.log(`Admin claim ${action === 'grant' ? 'granted' : 'revoked'} for UID ${uid}. User must refresh/sign in again for new claims.`);
}

main().catch((error) => { console.error(error.message || error); process.exitCode = 1; });
