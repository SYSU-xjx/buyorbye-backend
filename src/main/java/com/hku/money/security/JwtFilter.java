package com.hku.money.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {
  private final TokenService tokens;
  private final AuthMapper mapper;
  private final Clock clock;
  private final ObjectMapper json;

  public JwtFilter(TokenService tokens, AuthMapper mapper, Clock clock, ObjectMapper json) {
    this.tokens = tokens;
    this.mapper = mapper;
    this.clock = clock;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      CurrentUser principal = null;
      try {
        principal = tokens.parse(header.substring(7));
      } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
        /* Unauthenticated below. */
      }
      if (principal != null) {
        try {
          var user = mapper.byId(principal.userId());
          var session =
              mapper.activeSession(
                  principal.sessionId(), principal.userId(), LocalDateTime.now(clock));
          if (user != null && "ACTIVE".equals(user.accountStatus) && session != null)
            SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        } catch (DataAccessException e) {
          LoggerFactory.getLogger(JwtFilter.class)
              .error("Authentication database lookup failed", e);
          res.setStatus(500);
          res.setContentType("application/json;charset=UTF-8");
          json.writeValue(res.getOutputStream(), Result.failure(500, "服务器内部错误"));
          return;
        }
      }
    }
    chain.doFilter(req, res);
  }
}
