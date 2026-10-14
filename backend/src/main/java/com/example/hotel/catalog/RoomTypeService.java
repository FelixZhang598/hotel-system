package com.example.hotel.catalog;

import com.example.hotel.common.*;
import com.example.hotel.order.InventoryRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoomTypeService {
  private final RoomTypeRepository types;
  private final InventoryRepository inventory;
  private final HotelClock clock;

  public RoomTypeService(
      RoomTypeRepository types, InventoryRepository inventory, HotelClock clock) {
    this.types = types;
    this.inventory = inventory;
    this.clock = clock;
  }

  public void validateDates(LocalDate in, LocalDate out, LocalDate today) {
    if (in == null || out == null || in.isBefore(today) || !out.isAfter(in))
      throw ApiException.invalid("入住不得早于酒店今天，离店必须晚于入住");
    LocalDate first = inventory.first(), last = inventory.lastCheckout();
    if (first == null || last == null || in.isBefore(first) || out.isAfter(last))
      throw ApiException.invalid("日期超出酒店可售区间");
  }

  public List<RoomTypeView> search(LocalDate in, LocalDate out) {
    validateDates(in, out, clock.today());
    return types.all().stream()
        .map(
            t ->
                new RoomTypeView(
                    t.id(),
                    t.name(),
                    t.description(),
                    t.capacity(),
                    t.price(),
                    inventory.available(t.id(), in, out)))
        .toList();
  }
}
