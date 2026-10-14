import assert from "node:assert/strict";
export { assert };
export function status(response, expected) {
  assert.equal(response.status, expected, JSON.stringify(response));
  return response.data;
}
export function invariants(db) {
  assert.equal(
    db
      .prepare(
        "SELECT COUNT(*) n FROM inventory_days WHERE reserved < 0 OR reserved > total",
      )
      .get().n,
    0,
    "inventory bounds",
  );
  assert.equal(
    db
      .prepare(
        "SELECT COUNT(*) n FROM inventory_days i WHERE i.reserved <> (SELECT COUNT(*) FROM order_nights n JOIN orders o ON o.id=n.order_id WHERE o.room_type_id=i.room_type_id AND n.stay_date=i.stay_date AND n.allocation_status='HELD')",
      )
      .get().n,
    0,
    "inventory ledger reconciliation",
  );
  assert.equal(
    db
      .prepare(
        "SELECT COUNT(*) n FROM (SELECT room_id FROM check_in_records WHERE checked_out_at IS NULL GROUP BY room_id HAVING COUNT(*)>1)",
      )
      .get().n,
    0,
    "single room occupant",
  );
  assert.equal(
    db
      .prepare(
        "SELECT COUNT(*) n FROM orders o JOIN check_in_records c ON c.order_id=o.id WHERE o.status IN ('CANCELLED','NO_SHOW') OR (o.status='CHECKED_IN' AND c.checked_out_at IS NOT NULL) OR (o.status='COMPLETED' AND c.checked_out_at IS NULL)",
      )
      .get().n,
    0,
    "state and stay consistency",
  );
}
