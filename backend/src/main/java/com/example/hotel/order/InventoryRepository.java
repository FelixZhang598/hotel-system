package com.example.hotel.order;

import com.example.hotel.common.ApiException;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
  private final JdbcTemplate jdbc;

  public InventoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public LocalDate first() {
    String value = jdbc.queryForObject("SELECT MIN(stay_date) FROM inventory_days", String.class);
    return value == null ? null : LocalDate.parse(value);
  }

  public LocalDate lastCheckout() {
    String value = jdbc.queryForObject("SELECT MAX(stay_date) FROM inventory_days", String.class);
    return value == null ? null : LocalDate.parse(value).plusDays(1);
  }

  public int available(long type, LocalDate in, LocalDate out) {
    int expected = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(in, out));
    return jdbc.query(
        "SELECT COUNT(*),MIN(total-reserved) FROM inventory_days WHERE room_type_id=? AND"
            + " stay_date>=? AND stay_date<?",
        r -> {
          if (!r.next() || r.getInt(1) != expected) throw ApiException.invalid("所选日期没有完整库存记录");
          return r.getInt(2);
        },
        type,
        in.toString(),
        out.toString());
  }

  public void reserve(long type, LocalDate in, LocalDate out) {
    for (LocalDate date = in; date.isBefore(out); date = date.plusDays(1)) {
      if (jdbc.update(
              "UPDATE inventory_days SET reserved=reserved+1 WHERE room_type_id=? AND stay_date=?"
                  + " AND reserved<total",
              type,
              date.toString())
          != 1) throw ApiException.conflict("INVENTORY_INSUFFICIENT", "所选日期房间已售罄");
    }
  }

  public void release(long orderId, long type, LocalDate today) {
    var dates =
        jdbc.queryForList(
            "SELECT stay_date FROM order_nights WHERE order_id=? AND stay_date>=? AND"
                + " allocation_status='HELD' ORDER BY stay_date",
            String.class,
            orderId,
            today.toString());
    for (String date : dates) {
      if (jdbc.update(
              "UPDATE order_nights SET allocation_status='RELEASED' WHERE order_id=? AND"
                  + " stay_date=? AND allocation_status='HELD'",
              orderId,
              date)
          != 1) throw ApiException.conflict("ORDER_STATE_CONFLICT", "库存释放状态已变化");
      if (jdbc.update(
              "UPDATE inventory_days SET reserved=reserved-1 WHERE room_type_id=? AND stay_date=?"
                  + " AND reserved>0",
              type,
              date)
          != 1) throw new IllegalStateException("Inventory accounting invariant violated");
    }
  }
}
