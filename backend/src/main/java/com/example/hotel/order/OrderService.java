package com.example.hotel.order;

import static com.example.hotel.order.OrderContracts.*;

import com.example.hotel.auth.CurrentUser;
import com.example.hotel.catalog.*;
import com.example.hotel.common.*;
import com.example.hotel.database.WriteTransactions;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final OrderRepository orders;
  private final InventoryRepository inventory;
  private final RoomTypeRepository types;
  private final RoomTypeService catalog;
  private final WriteTransactions tx;
  private final HotelClock clock;

  public OrderService(
      OrderRepository orders,
      InventoryRepository inventory,
      RoomTypeRepository types,
      RoomTypeService catalog,
      WriteTransactions tx,
      HotelClock clock) {
    this.orders = orders;
    this.inventory = inventory;
    this.types = types;
    this.catalog = catalog;
    this.tx = tx;
    this.clock = clock;
  }

  public static void requireRole(CurrentUser actor, String role) {
    if (!actor.role().equals(role)) throw new ApiException(403, "FORBIDDEN", "角色没有该操作权限");
  }

  public StoredOrder owned(CurrentUser actor, long id) {
    StoredOrder order = orders.get(id);
    if (!actor.role().equals("FRONTDESK") && order.userId() != actor.id())
      throw ApiException.missing();
    return order;
  }

  public static void confirmed(StoredOrder order) {
    if (!order.status().equals("CONFIRMED"))
      throw ApiException.conflict("ORDER_STATE_CONFLICT", "订单已不处于已确认状态");
  }

  public CreateResult create(CurrentUser actor, String key, CreateOrderRequest request) {
    requireRole(actor, "GUEST");
    if (key == null || key.isBlank() || key.length() > 100)
      throw ApiException.invalid("Idempotency-Key 长度须为 1 到 100");
    if (request == null
        || request.roomTypeId() == null
        || request.roomTypeId() < 1
        || request.checkIn() == null
        || request.checkOut() == null
        || request.guestCount() == null
        || request.guestCount() < 1) throw ApiException.invalid("房型、日期和人数必填且有效");
    String hash = hash(request);
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          String now = clock.now().toString();
          var prior = orders.byKey(actor.id(), key);
          if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(hash))
              throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "该请求键已用于不同的预订参数");
            return new CreateResult(orders.view(prior.get().id()), false);
          }
          catalog.validateDates(request.checkIn(), request.checkOut(), today);
          var type = types.get(request.roomTypeId());
          if (request.guestCount() > type.capacity()) throw ApiException.invalid("人数超出房型容量");
          inventory.available(type.id(), request.checkIn(), request.checkOut());
          long total;
          try {
            total =
                Math.multiplyExact(
                    type.price(),
                    java.time.temporal.ChronoUnit.DAYS.between(
                        request.checkIn(), request.checkOut()));
          } catch (ArithmeticException e) {
            throw ApiException.invalid("订单金额超出范围");
          }
          inventory.reserve(type.id(), request.checkIn(), request.checkOut());
          long id = orders.insert(actor.id(), request, key, hash, type.price(), total, now);
          orders.event(id, actor.id(), null, "CONFIRMED", "住客预订", now);
          return new CreateResult(orders.view(id), true);
        });
  }

  private static String hash(CreateOrderRequest r) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (r.roomTypeId()
                              + "|"
                              + r.checkIn()
                              + "|"
                              + r.checkOut()
                              + "|"
                              + r.guestCount())
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public OrderView detail(CurrentUser actor, long id) {
    owned(actor, id);
    return orders.view(id);
  }

  public PageResult<OrderView> list(CurrentUser actor, OrderFilter filter) {
    if (filter.page() < 1 || filter.size() < 1 || filter.size() > 100)
      throw ApiException.invalid("页码至少为 1，每页数量须为 1 到 100");
    return orders.list(actor.role().equals("FRONTDESK") ? null : actor.id(), filter);
  }

  public OrderView cancel(CurrentUser actor, long id) {
    return tx.execute(
        () -> {
          LocalDate today = clock.today();
          String now = clock.now().toString();
          StoredOrder order = owned(actor, id);
          confirmed(order);
          if (actor.role().equals("GUEST")
              ? !today.isBefore(order.checkIn())
              : today.isAfter(order.checkIn()))
            throw ApiException.conflict("ORDER_STATE_CONFLICT", "已超过允许取消的日期");
          inventory.release(id, order.roomTypeId(), today);
          orders.transition(id, "CONFIRMED", "CANCELLED", now);
          orders.event(
              id,
              actor.id(),
              "CONFIRMED",
              "CANCELLED",
              actor.role().equals("GUEST") ? "住客取消" : "前台取消",
              now);
          return orders.view(id);
        });
  }
}
