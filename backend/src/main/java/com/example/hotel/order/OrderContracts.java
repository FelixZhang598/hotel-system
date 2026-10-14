package com.example.hotel.order;

import java.time.LocalDate;
import java.util.List;

public final class OrderContracts {
  private OrderContracts() {}

  public enum OrderStatus {
    CONFIRMED,
    CANCELLED,
    NO_SHOW,
    CHECKED_IN,
    COMPLETED
  }

  public record CreateOrderRequest(
      Long roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guestCount) {}

  public record OrderFilter(
      int page, int size, OrderStatus status, String orderNo, LocalDate checkIn) {}

  public record StayView(
      long roomId,
      String roomNumber,
      String guestName,
      String guestPhone,
      String checkedInAt,
      String checkedOutAt) {}

  public record EventView(String fromStatus, String toStatus, String reason, String createdAt) {}

  public record OrderView(
      long id,
      String orderNo,
      long roomTypeId,
      String roomTypeName,
      LocalDate checkIn,
      LocalDate checkOut,
      int guestCount,
      long nightlyPriceCents,
      long totalCents,
      String status,
      String createdAt,
      StayView stay,
      List<EventView> events) {}

  public record StoredOrder(
      long id,
      long userId,
      long roomTypeId,
      LocalDate checkIn,
      LocalDate checkOut,
      String status,
      String requestHash) {}

  public record CreateResult(OrderView order, boolean created) {}
}
