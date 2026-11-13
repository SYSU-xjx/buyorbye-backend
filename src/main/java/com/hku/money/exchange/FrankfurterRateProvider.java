package com.hku.money.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** ECB-only historical rates. No personal or proposal data is sent to the provider. */
@Component
public class FrankfurterRateProvider implements RateProvider {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper json;
  private final Clock clock;
  private final String base;

  private record Cached(Instant expires, Optional<Rate> value) {}

  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public FrankfurterRateProvider(
      ObjectMapper json,
      Clock clock,
      @Value("${app.exchange.base-url:https://api.frankfurter.dev}") String base) {
    this.json = json;
    this.clock = clock;
    this.base = base.replaceAll("/+$", "");
  }

  public Optional<Rate> fetch(String currency, LocalDate date) {
    if (currency == null || !currency.matches("[A-Z]{3}")) return Optional.empty();
    if ("HKD".equals(currency)) return Optional.of(new Rate(BigDecimal.ONE, date, "HKD_IDENTITY"));
    String key = currency + ":" + date;
    var cached = cache.get(key);
    if (cached != null && cached.expires().isAfter(clock.instant())) return cached.value();
    Optional<Rate> result = Optional.empty();
    try {
      var uri =
          URI.create(
              base
                  + "/v2/providers/ecb/rate/"
                  + currency.toLowerCase(Locale.ROOT)
                  + "/hkd?date="
                  + date);
      var pending =
          client.sendAsync(
              HttpRequest.newBuilder(uri)
                  .timeout(Duration.ofSeconds(4))
                  .header("Accept", "application/json")
                  .GET()
                  .build(),
              info -> new LimitedBody());
      HttpResponse<byte[]> response;
      try {
        response = pending.get(4, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
      if (response.statusCode() == 200) {
        var node = json.readTree(response.body());
        BigDecimal value = node.path("rate").decimalValue().setScale(10, RoundingMode.HALF_UP);
        LocalDate sourceDate = LocalDate.parse(node.path("date").asText());
        if (currency.equalsIgnoreCase(node.path("base").asText())
            && "HKD".equalsIgnoreCase(node.path("quote").asText())
            && value.signum() > 0
            && value.precision() <= 20
            && !sourceDate.isAfter(date))
          result = Optional.of(new Rate(value, sourceDate, "FRANKFURTER_ECB"));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .debug("No ECB rate for {} on {}", currency, date);
    }
    if (cache.size() > 512) cache.clear();
    cache.put(
        key, new Cached(clock.instant().plusSeconds(result.isPresent() ? 3600 : 300), result));
    return result;
  }

  /** Bound both response memory and the complete request/body deadline. */
  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final java.util.concurrent.CompletableFuture<byte[]> body =
        new java.util.concurrent.CompletableFuture<>();
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private java.util.concurrent.Flow.Subscription subscription;

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return body;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    public void onNext(List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if (bytes.size() + (long) buffer.remaining() > 65536) {
          subscription.cancel();
          body.completeExceptionally(new java.io.IOException("Rate response exceeds 64KB"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
    }

    public void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }
}
