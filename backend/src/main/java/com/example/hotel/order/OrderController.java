package com.example.hotel.order;

import static com.example.hotel.order.OrderContracts.*;

import com.example.hotel.auth.UserRepository;
import com.example.hotel.common.PageResult;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService service;
  private final UserRepository users;

  public OrderController(OrderService service, UserRepository users) {
    this.service = service;
    this.users = users;
  }

  @PostMapping
  public ResponseEntity<OrderView> create(
      Authentication auth,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody CreateOrderRequest request) {
    var result = service.create(users.current(auth), key, request);
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.order());
  }

  @GetMapping
  public PageResult<OrderView> list(
      Authentication auth,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) String orderNo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate checkIn) {
    return service.list(users.current(auth), new OrderFilter(page, size, status, orderNo, checkIn));
  }

  @GetMapping("/{id}")
  public OrderView detail(Authentication auth, @PathVariable long id) {
    return service.detail(users.current(auth), id);
  }

  @PostMapping("/{id}/cancel")
  public OrderView cancel(Authentication auth, @PathVariable long id) {
    return service.cancel(users.current(auth), id);
  }
}
