package com.hku.money;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hku.money.exchange.FrankfurterRateProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FrankfurterRateProviderTest {
  @Test
  void requestsEcbHistoricalPairAndCachesValidatedResponse() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    server.createContext(
        "/v2/providers/ecb/rate/cny/hkd",
        exchange -> {
          calls.incrementAndGet();
          assertThat(exchange.getRequestURI().getQuery()).isEqualTo("date=2026-11-06");
          byte[] body =
              "{\"date\":\"2026-11-05\",\"base\":\"CNY\",\"quote\":\"HKD\",\"rate\":1.12345678901}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      var provider =
          new FrankfurterRateProvider(
              new ObjectMapper(),
              Clock.systemUTC(),
              "http://127.0.0.1:" + server.getAddress().getPort());
      var date = LocalDate.of(2026, 11, 6);
      var rate = provider.fetch("CNY", date).orElseThrow();
      assertThat(rate.rate()).isEqualByComparingTo("1.1234567890");
      assertThat(rate.date()).isEqualTo(date.minusDays(1));
      assertThat(rate.source()).isEqualTo("FRANKFURTER_ECB");
      assertThat(provider.fetch("CNY", date)).contains(rate);
      assertThat(calls).hasValue(1);
      assertThat(provider.fetch("HKD", date).orElseThrow().rate()).isEqualByComparingTo("1");
      assertThat(calls).hasValue(1);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsFutureDatedAndOversizedResponses() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String text =
              exchange.getRequestURI().getPath().contains("usd")
                  ? "x".repeat(70000)
                  : "{\"date\":\"2027-01-01\",\"base\":\"CNY\",\"quote\":\"HKD\",\"rate\":1.2}";
          byte[] body = text.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      var provider =
          new FrankfurterRateProvider(
              new ObjectMapper(),
              Clock.systemUTC(),
              "http://127.0.0.1:" + server.getAddress().getPort());
      assertThat(provider.fetch("CNY", LocalDate.of(2026, 11, 6))).isEmpty();
      assertThat(provider.fetch("USD", LocalDate.of(2026, 11, 6))).isEmpty();
    } finally {
      server.stop(0);
    }
  }
}
