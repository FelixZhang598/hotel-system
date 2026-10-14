package com.example.hotel.catalog;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomTypeRepository {
  public record RoomType(long id, String name, String description, int capacity, long price) {}

  private final JdbcTemplate jdbc;

  public RoomTypeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RoomType> all() {
    return jdbc.query(
        "SELECT * FROM room_types ORDER BY id",
        (r, i) ->
            new RoomType(
                r.getLong("id"),
                r.getString("name"),
                r.getString("description"),
                r.getInt("capacity"),
                r.getLong("nightly_price_cents")));
  }

  public RoomType get(long id) {
    return all().stream()
        .filter(t -> t.id() == id)
        .findFirst()
        .orElseThrow(com.example.hotel.common.ApiException::missing);
  }
}
