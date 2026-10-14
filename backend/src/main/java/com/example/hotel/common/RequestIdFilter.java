package com.example.hotel.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
  public static String id(HttpServletRequest request) {
    Object id = request.getAttribute("requestId");
    if (id == null) {
      id = UUID.randomUUID().toString();
      request.setAttribute("requestId", id);
    }
    return id.toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Request-Id", id(req));
    chain.doFilter(req, res);
  }
}
