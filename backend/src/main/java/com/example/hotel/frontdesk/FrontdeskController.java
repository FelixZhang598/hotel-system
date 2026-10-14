package com.example.hotel.frontdesk;

import static com.example.hotel.frontdesk.StayContracts.*;
import static com.example.hotel.order.OrderContracts.*;

import com.example.hotel.auth.UserRepository;
import com.example.hotel.common.PageResult;
import com.example.hotel.order.OrderService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/frontdesk")
public class FrontdeskController {
  private final StayService stays;
  private final OrderService orders;
  private final UserRepository users;

  public FrontdeskController(StayService stays, OrderService orders, UserRepository users) {
    this.stays = stays;
    this.orders = orders;
    this.users = users;
  }

  @GetMapping("/orders")
  public PageResult<OrderView> list(
      Authentication auth,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) String orderNo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate checkIn) {
    return orders.list(users.current(auth), new OrderFilter(page, size, status, orderNo, checkIn));
  }

  @GetMapping("/orders/{id}")
  public OrderView detail(Authentication auth, @PathVariable long id) {
    return orders.detail(users.current(auth), id);
  }

  @GetMapping("/orders/{id}/available-rooms")
  public List<RoomView> available(Authentication auth, @PathVariable long id) {
    return stays.availableRooms(users.current(auth), id);
  }

  @PostMapping("/orders/{id}/check-in")
  public OrderView checkIn(
      Authentication auth, @PathVariable long id, @RequestBody CheckInRequest request) {
    return stays.checkIn(users.current(auth), id, request);
  }

  @PostMapping("/orders/{id}/check-out")
  public OrderView checkOut(Authentication auth, @PathVariable long id) {
    return stays.checkOut(users.current(auth), id);
  }

  @PostMapping("/orders/{id}/cancel")
  public OrderView cancel(Authentication auth, @PathVariable long id) {
    return orders.cancel(users.current(auth), id);
  }

  @PostMapping("/orders/{id}/no-show")
  public OrderView noShow(Authentication auth, @PathVariable long id) {
    return stays.noShow(users.current(auth), id);
  }

  @GetMapping("/rooms")
  public List<RoomView> rooms(Authentication auth) {
    return stays.rooms(users.current(auth));
  }

  @PostMapping("/rooms/{id}/clean")
  public RoomView clean(Authentication auth, @PathVariable long id) {
    return stays.clean(users.current(auth), id);
  }
}
