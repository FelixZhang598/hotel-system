package com.example.hotel.database;

import com.example.hotel.common.HotelClock;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoSeeder {
  private final JdbcTemplate jdbc;
  private final WriteTransactions tx;
  private final HotelClock clock;
  private final PasswordEncoder passwords;

  public DemoSeeder(
      JdbcTemplate jdbc, WriteTransactions tx, HotelClock clock, PasswordEncoder passwords) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.clock = clock;
    this.passwords = passwords;
  }

  public void seed() {
    String hash = passwords.encode("Demo12345!");
    tx.execute(
        () -> {
          if (jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class) > 0) return null;
          LocalDate d = clock.today();
          String now = clock.now().toString();
          String[] names = {"guest1", "guest2", "frontdesk"};
          for (int i = 0; i < names.length; i++)
            jdbc.update(
                "INSERT INTO users VALUES(?,?,?,?)",
                i + 1,
                names[i],
                hash,
                i == 2 ? "FRONTDESK" : "GUEST");
          jdbc.update(
              "INSERT INTO room_types VALUES(1,'舒适大床房','温暖木色，安静好眠。一张 1.8"
                  + " 米大床。',2,32800),(2,'景观双床房','宽阔窗景，两张 1.2"
                  + " 米单人床。',2,42800),(3,'家庭套房','独立起居区，适合家庭同行。',4,68800)");
          for (int type = 1; type <= 3; type++)
            for (int n = 1; n <= 6; n++) {
              int id = type * 100 + n;
              jdbc.update(
                  "INSERT INTO rooms VALUES(?,?,?,?)",
                  id,
                  Integer.toString(id),
                  type,
                  id == 103 ? "DIRTY" : "CLEAN");
            }
          for (int type = 1; type <= 3; type++)
            for (LocalDate date = d.minusDays(7);
                !date.isAfter(d.plusDays(89));
                date = date.plusDays(1))
              jdbc.update("INSERT INTO inventory_days VALUES(?,?,6,0)", type, date.toString());
          order(1, 1, 1, d, d.plusDays(2), "CONFIRMED", null, null, now, d);
          order(2, 1, 1, d.minusDays(2), d, "CHECKED_IN", 101L, null, now, d);
          order(3, 2, 2, d.minusDays(1), d.plusDays(1), "CONFIRMED", null, null, now, d);
          order(4, 2, 2, d.minusDays(3), d.minusDays(1), "CHECKED_IN", 201L, null, now, d);
          order(5, 1, 3, d.plusDays(3), d.plusDays(5), "CONFIRMED", null, null, now, d);
          order(
              6,
              2,
              1,
              d.minusDays(3),
              d.minusDays(2),
              "COMPLETED",
              103L,
              d.minusDays(2).atStartOfDay(HotelClock.ZONE).toInstant().toString(),
              now,
              d);
          order(7, 1, 3, d.plusDays(4), d.plusDays(6), "CANCELLED", null, null, now, d);
          order(8, 2, 3, d.minusDays(4), d.minusDays(3), "NO_SHOW", null, null, now, d);
          order(9, 2, 2, d.minusDays(5), d.minusDays(4), "CONFIRMED", null, null, now, d);
          return null;
        });
  }

  private void order(
      long id,
      long user,
      long type,
      LocalDate in,
      LocalDate out,
      String status,
      Long room,
      String departed,
      String now,
      LocalDate today) {
    long price = type == 1 ? 32800 : type == 2 ? 42800 : 68800;
    LocalDate createdDate = in.minusDays(2);
    if (createdDate.isAfter(today.minusDays(1))) createdDate = today.minusDays(1);
    String created = createdDate.atStartOfDay(HotelClock.ZONE).toInstant().toString();
    jdbc.update(
        "INSERT INTO orders VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        "QA-DEMO-" + String.format("%04d", id),
        user,
        type,
        in.toString(),
        out.toString(),
        type == 3 ? 3 : 2,
        price,
        price * java.time.temporal.ChronoUnit.DAYS.between(in, out),
        status,
        "seed-" + id,
        "seed",
        created,
        now);
    for (LocalDate date = in; date.isBefore(out); date = date.plusDays(1)) {
      boolean released =
          status.equals("CANCELLED") || (status.equals("NO_SHOW") && !date.isBefore(today));
      jdbc.update(
          "INSERT INTO order_nights VALUES(?,?,?)",
          id,
          date.toString(),
          released ? "RELEASED" : "HELD");
      if (!released)
        jdbc.update(
            "UPDATE inventory_days SET reserved=reserved+1 WHERE room_type_id=? AND stay_date=?",
            type,
            date.toString());
    }
    jdbc.update(
        "INSERT INTO order_events(order_id,operator_id,from_status,to_status,reason,created_at)"
            + " VALUES(?,?,NULL,'CONFIRMED','演示预订',?)",
        id,
        user,
        created);
    if (room != null) {
      jdbc.update(
          "INSERT INTO"
              + " check_in_records(order_id,room_id,guest_name,guest_phone,checked_in_at,checked_out_at)"
              + " VALUES(?,?,?,?,?,?)",
          id,
          room,
          "演示住客" + id,
          "1380000000" + id,
          in.atStartOfDay(HotelClock.ZONE).toInstant().toString(),
          departed);
      jdbc.update(
          "INSERT INTO order_events(order_id,operator_id,from_status,to_status,reason,created_at)"
              + " VALUES(?,3,'CONFIRMED','CHECKED_IN','演示入住',?)",
          id,
          in.atStartOfDay(HotelClock.ZONE).toInstant().toString());
    }
    if (!status.equals("CONFIRMED") && !status.equals("CHECKED_IN"))
      jdbc.update(
          "INSERT INTO order_events(order_id,operator_id,from_status,to_status,reason,created_at)"
              + " VALUES(?,3,?,?,?,?)",
          id,
          status.equals("COMPLETED") ? "CHECKED_IN" : "CONFIRMED",
          status,
          "演示状态",
          now);
  }
}
