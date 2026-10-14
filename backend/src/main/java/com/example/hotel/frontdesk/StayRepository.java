package com.example.hotel.frontdesk;

import com.example.hotel.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StayRepository {
  private final JdbcTemplate jdbc;

  public StayRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(long order, long room, String name, String phone, String now) {
    jdbc.update(
        "INSERT INTO check_in_records(order_id,room_id,guest_name,guest_phone,checked_in_at)"
            + " VALUES(?,?,?,?,?)",
        order,
        room,
        name,
        phone,
        now);
  }

  public long checkout(long order, String now) {
    var rooms =
        jdbc.queryForList(
            "SELECT room_id FROM check_in_records WHERE order_id=? AND checked_out_at IS NULL",
            Long.class,
            order);
    if (rooms.size() != 1) throw ApiException.conflict("ORDER_STATE_CONFLICT", "订单没有有效入住记录");
    if (jdbc.update(
            "UPDATE check_in_records SET checked_out_at=? WHERE order_id=? AND checked_out_at IS"
                + " NULL",
            now,
            order)
        != 1) throw ApiException.conflict("ORDER_STATE_CONFLICT", "入住记录已变化");
    return rooms.getFirst();
  }
}
