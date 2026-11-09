/**
 * Sets a temporary password for an account, from the server: for someone who forgot theirs
 * on an install that sends no email (with email, "Forgot your password?" on the sign-in page
 * does this on its own). Prints the password once, to hand over; the account is signed out
 * everywhere, and its owner changes the password in "My account".
 *
 *   npm run reset-password -w @openturnly/server -- owner@example.com
 *   docker compose exec openturnly node apps/server/src/reset-password.ts owner@example.com
 *
 * With the embedded development database, stop the server first: only one process can open it.
 */

import path from "node:path";
import { createAuth } from "./auth.ts";
import { loadConfig } from "./config.ts";
import { connectPostgres, openEmbeddedPostgres } from "./db/client.ts";
import { resetForgottenPassword } from "./lib/password-reset.ts";

const address = process.argv[2]?.trim();
if (!address?.includes("@")) {
  console.error("Usage: reset-password <email>");
  process.exit(2);
}

const config = loadConfig();
const database = config.databaseUrl
  ? await connectPostgres(config.databaseUrl)
  : await openEmbeddedPostgres(path.join(config.dataDir, "pglite"));
try {
  const auth = createAuth({ db: database.db, config });
  const password = await resetForgottenPassword(auth, database.db, address);
  if (!password) {
    console.error(`No account has the email ${address}.`);
    process.exitCode = 1;
  } else {
    console.log(`New temporary password for ${address}: ${password}`);
    console.log("They're signed out everywhere. Ask them to change it in My account.");
  }
} finally {
  await database.close();
}
