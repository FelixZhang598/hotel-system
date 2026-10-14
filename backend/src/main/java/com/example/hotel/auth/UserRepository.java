package com.example.hotel.auth;

import com.example.hotel.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository implements UserDetailsService {
  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE username=?",
            (r, i) ->
                User.withUsername(r.getString("username"))
                    .password(r.getString("password_hash"))
                    .roles(r.getString("role"))
                    .build(),
            username)
        .stream()
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
  }

  public CurrentUser current(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
      throw new ApiException(401, "UNAUTHORIZED", "请先登录");
    return jdbc
        .query(
            "SELECT id,username,role FROM users WHERE username=?",
            (r, i) -> new CurrentUser(r.getLong(1), r.getString(2), r.getString(3)),
            auth.getName())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "请先登录"));
  }
}
