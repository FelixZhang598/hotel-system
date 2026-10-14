package com.example.hotel.catalog;

import com.example.hotel.common.HotelClock;
import com.example.hotel.order.InventoryRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RoomTypeController {
  private final RoomTypeService service;
  private final InventoryRepository inventory;
  private final HotelClock clock;

  public RoomTypeController(
      RoomTypeService service, InventoryRepository inventory, HotelClock clock) {
    this.service = service;
    this.inventory = inventory;
    this.clock = clock;
  }

  @GetMapping("/room-types")
  public List<RoomTypeView> search(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
    return service.search(checkIn, checkOut);
  }

  @GetMapping("/meta")
  public Map<String, Object> meta() {
    LocalDate today = clock.today();
    LocalDate first = inventory.first();
    return Map.of(
        "hotelName",
        "栖岸酒店",
        "today",
        today,
        "availableFrom",
        first == null || first.isBefore(today) ? today : first,
        "availableTo",
        inventory.lastCheckout());
  }
}
