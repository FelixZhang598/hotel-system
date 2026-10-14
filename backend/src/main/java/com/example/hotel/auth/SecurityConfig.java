package com.example.hotel.auth;

import com.example.hotel.common.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static void error(
      ObjectMapper mapper,
      HttpServletRequest req,
      HttpServletResponse res,
      int status,
      String code,
      String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    mapper.writeValue(res.getOutputStream(), new ApiError(code, message, RequestIdFilter.id(req)));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, UserRepository users, ObjectMapper mapper)
      throws Exception {
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers(
                    "/api/meta", "/api/auth/csrf", "/api/auth/login", "/api/room-types", "/error")
                .permitAll()
                .requestMatchers("/api/frontdesk/**")
                .hasRole("FRONTDESK")
                .requestMatchers("/api/orders/**")
                .hasRole("GUEST")
                .anyRequest()
                .authenticated());
    http.csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()));
    http.formLogin(
        f ->
            f.loginProcessingUrl("/api/auth/login")
                .successHandler(
                    (req, res, auth) -> {
                      res.setContentType("application/json;charset=UTF-8");
                      mapper.writeValue(res.getOutputStream(), users.current(auth));
                    })
                .failureHandler(
                    (req, res, e) -> error(mapper, req, res, 401, "UNAUTHORIZED", "账号或密码错误")));
    http.logout(
        l ->
            l.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (req, res, ex) -> error(mapper, req, res, 401, "UNAUTHORIZED", "请先登录"))
                .accessDeniedHandler(
                    (req, res, ex) ->
                        error(mapper, req, res, 403, "FORBIDDEN", "权限不足或 CSRF token 无效")));
    http.requestCache(c -> c.disable());
    return http.build();
  }
}
