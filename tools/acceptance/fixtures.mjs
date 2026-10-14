import { DatabaseSync } from "node:sqlite";
import { resolve, sep } from "node:path";
export function openAcceptanceDatabase(path) {
  if (!path || !resolve(path).includes(`${sep}.runtime${sep}acceptance${sep}`))
    throw Error(
      "HOTEL_ACCEPTANCE_DB must be in .runtime/acceptance/; demo database is forbidden",
    );
  const db = new DatabaseSync(resolve(path));
  db.exec("PRAGMA foreign_keys=ON; PRAGMA busy_timeout=3000;");
  return db;
}
export function datePlus(day, n) {
  const d = new Date(day + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
