package com.hku.money;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.ApiErrors;
import com.hku.money.security.*;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ResultErrorTest {
  @RestController
  static class Probe {
    @GetMapping("/fail")
    public void fail() {
      throw new IllegalStateException("private-database-details");
    }
  }

  @Test
  void unexpectedControllerErrorIsWrappedWithoutLeakingDetails() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ApiErrors()).build();
    mvc.perform(get("/fail"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("服务器内部错误"))
        .andExpect(content().json("{\"code\":500,\"message\":\"服务器内部错误\",\"data\":null}", true));
  }

  @Test
  void databaseFailureDuringAuthenticationUsesSameEnvelope() throws Exception {
    var tokens = mock(TokenService.class);
    var mapper = mock(AuthMapper.class);
    when(tokens.parse("valid")).thenReturn(new CurrentUser(1, 2));
    when(mapper.byId(1))
        .thenThrow(new DataAccessResourceFailureException("private-database-details"));
    var json = new ObjectMapper();
    var filter = new JwtFilter(tokens, mapper, Clock.systemUTC(), json);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid");
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(json.readTree(response.getContentAsString()).get("code").asInt()).isEqualTo(500);
    assertThat(json.readTree(response.getContentAsString()).get("data").isNull()).isTrue();
    assertThat(response.getContentAsString()).doesNotContain("private-database-details");
    verifyNoInteractions(chain);
  }
}
