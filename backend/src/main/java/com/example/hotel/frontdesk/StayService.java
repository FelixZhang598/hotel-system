package com.example.hotel.frontdesk;

import static com.example.hotel.frontdesk.StayContracts.*;
import static com.example.hotel.order.OrderContracts.*;

import com.example.hotel.auth.CurrentUser;
import com.example.hotel.common.*;
import com.example.hotel.database.WriteTransactions;
import com.example.hotel.order.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StayService {
  private final OrderService service;
  private final OrderRepository orders;
  private final InventoryRepository inventory;
  private final RoomRepository rooms;
  private final StayRepository stays;
  private final WriteTransactions tx;
  private final HotelClock clock;

  public StayService(
      OrderService service,
      OrderRepository orders,
      InventoryRepository inventory,
      RoomRepository rooms,
      StayRepository stays,
      WriteTransactions tx,
      HotelClock clock) {
    this.service = service;
    this.orders = orders;
    this.inventory = inventory;
    this.rooms = rooms;
    this.stays = stays;
    this.tx = tx;
    this.clock = clock;
  }

  private StoredOrder arrival(CurrentUser actor, long id, LocalDate today) {
    OrderService.requireRole(actor, "FRONTDESK");
    StoredOrder order = service.owned(actor, id);
    OrderService.confirmed(order);
    if (!today.equals(order.checkIn()))
      throw ApiException.conflict("ORDER_STATE_CONFLICT", "仅可办理今日到店订单");
    return order;
  }

  public List<RoomView> availableRooms(CurrentUser actor, long id) {
    LocalDate today = clock.today();
    StoredOrder order = arrival(actor, id, today);
    return rooms.available(order.roomTypeId(), order.checkIn(), order.checkOut(), today);
  }

  public OrderView checkIn(CurrentUser actor, long id, CheckInRequest request) {
    OrderService.requireRole(actor, "FRONTDESK");
    if (request == null
        || request.roomId() == null
        || request.roomId() < 1
        || request.guestName() == null
        || request.guestPhone() == null) throw ApiException.invalid("房间、入住姓名及电话必填");
    String name = request.guestName().strip(), phone = request.guestPhone().strip();
    if (name.isEmpty()
        || name.length() > 80
        || phone.length() < 6
        || phone.length() > 30
        || !phone.matches("[0-9+ \\-]+"))
      throw ApiException.invalid("姓名须为 1 到 80 字符，电话须为 6 到 30 字符并仅含数字、加号、空格及连字符");
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          String now = clock.now().toString();
          StoredOrder order = arrival(actor, id, today);
          RoomView room = rooms.get(request.roomId(), today);
          if (room.roomTypeId() != order.roomTypeId()
              || room.occupied()
              || !room.cleaningStatus().equals("CLEAN")
              || rooms.overlap(room.id(), order.checkIn(), order.checkOut()))
            throw ApiException.conflict("ROOM_UNAVAILABLE", "该房间不满足入住条件，请刷新候选房间");
          stays.create(id, room.id(), name, phone, now);
          orders.transition(id, "CONFIRMED", "CHECKED_IN", now);
          orders.event(id, actor.id(), "CONFIRMED", "CHECKED_IN", "前台办理入住", now);
          return orders.view(id);
        });
  }

  public OrderView checkOut(CurrentUser actor, long id) {
    OrderService.requireRole(actor, "FRONTDESK");
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          String now = clock.now().toString();
          StoredOrder order = service.owned(actor, id);
          if (!order.status().equals("CHECKED_IN") || today.isBefore(order.checkOut()))
            throw ApiException.conflict("ORDER_STATE_CONFLICT", "仅允许在计划离店日及之后为已入住订单办理退房");
          long room = stays.checkout(id, now);
          orders.transition(id, "CHECKED_IN", "COMPLETED", now);
          rooms.mark(room, "DIRTY");
          orders.event(id, actor.id(), "CHECKED_IN", "COMPLETED", "前台办理退房", now);
          return orders.view(id);
        });
  }

  public OrderView noShow(CurrentUser actor, long id) {
    OrderService.requireRole(actor, "FRONTDESK");
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          String now = clock.now().toString();
          StoredOrder order = service.owned(actor, id);
          OrderService.confirmed(order);
          if (!today.isAfter(order.checkIn()))
            throw ApiException.conflict("ORDER_STATE_CONFLICT", "未到店只能在入住日之后标记");
          inventory.release(id, order.roomTypeId(), today);
          orders.transition(id, "CONFIRMED", "NO_SHOW", now);
          orders.event(id, actor.id(), "CONFIRMED", "NO_SHOW", "前台标记未到店", now);
          return orders.view(id);
        });
  }

  public List<RoomView> rooms(CurrentUser actor) {
    OrderService.requireRole(actor, "FRONTDESK");
    return rooms.all(clock.today());
  }

  public RoomView clean(CurrentUser actor, long id) {
    OrderService.requireRole(actor, "FRONTDESK");
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          RoomView room = rooms.get(id, today);
          if (room.occupied()) throw ApiException.conflict("ROOM_UNAVAILABLE", "占用中的房间不能完成清洁");
          rooms.mark(id, "CLEAN");
          return rooms.get(id, today);
        });
  }
}
