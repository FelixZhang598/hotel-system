package com.example.hotel.frontdesk;

public final class StayContracts {
  private StayContracts() {}

  public record CheckInRequest(Long roomId, String guestName, String guestPhone) {}

  public record RoomView(
      long id,
      String roomNumber,
      long roomTypeId,
      String roomTypeName,
      String cleaningStatus,
      boolean occupied,
      boolean overdue,
      String currentOrderNo) {}
}
