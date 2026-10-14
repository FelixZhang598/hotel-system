package com.example.hotel.order;

import static com.example.hotel.order.OrderContracts.*;

import com.example.hotel.common.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
  private final JdbcTemplate jdbc;

  public OrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private org.springframework.jdbc.core.RowMapper<StoredOrder> stored() {
    return (r, i) ->
        new StoredOrder(
            r.getLong("id"),
            r.getLong("user_id"),
            r.getLong("room_type_id"),
            LocalDate.parse(r.getString("check_in_date")),
            LocalDate.parse(r.getString("check_out_date")),
            r.getString("status"),
            r.getString("request_hash"));
  }

  public StoredOrder get(long id) {
    return jdbc.query("SELECT * FROM orders WHERE id=?", stored(), id).stream()
        .findFirst()
        .orElseThrow(ApiException::missing);
  }

  public Optional<StoredOrder> byKey(long user, String key) {
    return jdbc
        .query("SELECT * FROM orders WHERE user_id=? AND idempotency_key=?", stored(), user, key)
        .stream()
        .findFirst();
  }

  public long insert(
      long user,
      CreateOrderRequest r,
      String key,
      String hash,
      long price,
      long total,
      String now) {
    jdbc.update(
        "INSERT INTO"
            + " orders(order_no,user_id,room_type_id,check_in_date,check_out_date,guest_count,price_snapshot_cents,total_cents,status,idempotency_key,request_hash,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?,'CONFIRMED',?,?,?,?)",
        "QA-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
        user,
        r.roomTypeId(),
        r.checkIn().toString(),
        r.checkOut().toString(),
        r.guestCount(),
        price,
        total,
        key,
        hash,
        now,
        now);
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    for (LocalDate date = r.checkIn(); date.isBefore(r.checkOut()); date = date.plusDays(1))
      jdbc.update("INSERT INTO order_nights VALUES(?,?,'HELD')", id, date.toString());
    return id;
  }

  public void event(long id, long actor, String from, String to, String reason, String now) {
    jdbc.update(
        "INSERT INTO order_events(order_id,operator_id,from_status,to_status,reason,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        actor,
        from,
        to,
        reason,
        now);
  }

  public void transition(long id, String from, String to, String now) {
    if (jdbc.update(
            "UPDATE orders SET status=?,updated_at=? WHERE id=? AND status=?", to, now, id, from)
        != 1) throw ApiException.conflict("ORDER_STATE_CONFLICT", "订单状态已变化");
  }

  public OrderView view(long id) {
    var stay =
        jdbc
            .query(
                "SELECT s.*,r.room_number FROM check_in_records s JOIN rooms r ON r.id=s.room_id"
                    + " WHERE s.order_id=?",
                (r, i) ->
                    new StayView(
                        r.getLong("room_id"),
                        r.getString("room_number"),
                        r.getString("guest_name"),
                        r.getString("guest_phone"),
                        r.getString("checked_in_at"),
                        r.getString("checked_out_at")),
                id)
            .stream()
            .findFirst()
            .orElse(null);
    var events =
        jdbc.query(
            "SELECT * FROM order_events WHERE order_id=? ORDER BY id",
            (r, i) ->
                new EventView(
                    r.getString("from_status"),
                    r.getString("to_status"),
                    r.getString("reason"),
                    r.getString("created_at")),
            id);
    return jdbc
        .query(
            "SELECT o.*,t.name room_type_name FROM orders o JOIN room_types t ON"
                + " t.id=o.room_type_id WHERE o.id=?",
            (r, i) ->
                new OrderView(
                    r.getLong("id"),
                    r.getString("order_no"),
                    r.getLong("room_type_id"),
                    r.getString("room_type_name"),
                    LocalDate.parse(r.getString("check_in_date")),
                    LocalDate.parse(r.getString("check_out_date")),
                    r.getInt("guest_count"),
                    r.getLong("price_snapshot_cents"),
                    r.getLong("total_cents"),
                    r.getString("status"),
                    r.getString("created_at"),
                    stay,
                    events),
            id)
        .stream()
        .findFirst()
        .orElseThrow(ApiException::missing);
  }

  public PageResult<OrderView> list(Long user, OrderFilter filter) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (user != null) {
      where.append(" AND user_id=?");
      args.add(user);
    }
    if (filter.status() != null) {
      where.append(" AND status=?");
      args.add(filter.status().name());
    }
    if (filter.orderNo() != null && !filter.orderNo().isEmpty()) {
      where.append(" AND order_no=?");
      args.add(filter.orderNo());
    }
    if (filter.checkIn() != null) {
      where.append(" AND check_in_date=?");
      args.add(filter.checkIn().toString());
    }
    long total =
        jdbc.queryForObject("SELECT COUNT(*) FROM orders" + where, Long.class, args.toArray());
    args.add(filter.size());
    args.add((long) (filter.page() - 1) * filter.size());
    var ids =
        jdbc.queryForList(
            "SELECT id FROM orders" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            Long.class,
            args.toArray());
    return new PageResult<>(
        ids.stream().map(this::view).toList(), filter.page(), filter.size(), total);
  }
}
