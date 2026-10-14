package com.example.hotel.common;

import java.time.*;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HotelClock {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private final Clock clock;

  public HotelClock(Environment env) {
    String fixed = env.getProperty("hotel.fixed-date", env.getProperty("HOTEL_FIXED_DATE"));
    String fixedInstant =
        env.getProperty("hotel.fixed-instant", env.getProperty("HOTEL_FIXED_INSTANT"));
    boolean acceptance = Arrays.asList(env.getActiveProfiles()).contains("acceptance");
    if (fixedInstant != null && acceptance) {
      clock = Clock.fixed(Instant.parse(fixedInstant), ZONE);
    } else if (fixed != null && acceptance) {
      clock = Clock.fixed(LocalDate.parse(fixed).atStartOfDay(ZONE).toInstant(), ZONE);
    } else {
      clock = Clock.system(ZONE);
    }
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }

  public Instant now() {
    return clock.instant();
  }
}
