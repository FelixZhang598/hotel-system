package com.example.hotel.frontdesk;

import static com.example.hotel.frontdesk.StayContracts.*;

import com.example.hotel.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomRepository {
  private final JdbcTemplate jdbc;

  public RoomRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RoomView> all(LocalDate today) {
    return jdbc.query(
        "SELECT r.*,t.name room_type_name,o.order_no,o.check_out_date FROM rooms r JOIN room_types"
            + " t ON t.id=r.room_type_id LEFT JOIN check_in_records s ON s.room_id=r.id AND"
            + " s.checked_out_at IS NULL LEFT JOIN orders o ON o.id=s.order_id ORDER BY"
            + " r.room_number",
        (r, i) -> {
          String no = r.getString("order_no"), out = r.getString("check_out_date");
          return new RoomView(
              r.getLong("id"),
              r.getString("room_number"),
              r.getLong("room_type_id"),
              r.getString("room_type_name"),
              r.getString("cleaning_status"),
              no != null,
              out != null && today.isAfter(LocalDate.parse(out)),
              no);
        });
  }

  public RoomView get(long id, LocalDate today) {
    return all(today).stream()
        .filter(r -> r.id() == id)
        .findFirst()
        .orElseThrow(ApiException::missing);
  }

  public boolean overlap(long room, LocalDate in, LocalDate out) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM check_in_records s JOIN orders o ON o.id=s.order_id WHERE"
                + " s.room_id=? AND o.check_in_date<? AND o.check_out_date>?",
            Integer.class,
            room,
            out.toString(),
            in.toString())
        > 0;
  }

  public List<RoomView> available(long type, LocalDate in, LocalDate out, LocalDate today) {
    return all(today).stream()
        .filter(
            r ->
                r.roomTypeId() == type
                    && !r.occupied()
                    && r.cleaningStatus().equals("CLEAN")
                    && !overlap(r.id(), in, out))
        .toList();
  }

  public void mark(long id, String cleaning) {
    jdbc.update("UPDATE rooms SET cleaning_status=? WHERE id=?", cleaning, id);
  }
}
