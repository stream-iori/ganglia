package work.ganglia.trading.data.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class OhlcvCacheTest {

  private static final String SAMPLE_CSV =
      "Date,Open,High,Low,Close,Volume\n"
          + "2024-01-02,100.0,105.0,99.0,104.0,1000000\n"
          + "2024-01-03,104.0,106.0,103.0,105.0,1100000\n"
          + "2024-01-04,105.0,107.0,104.0,106.0,900000\n"
          + "2024-01-05,106.0,108.0,105.0,107.0,950000\n";

  @Test
  void putAndGetRoundTrip(@TempDir Path tmpDir, Vertx vertx, VertxTestContext ctx) {
    OhlcvCache cache = new OhlcvCache(vertx, tmpDir.toString());

    cache
        .put("AAPL", SAMPLE_CSV)
        .compose(v -> cache.get("AAPL", "2024-12-31"))
        .onSuccess(
            csv -> {
              ctx.verify(
                  () -> {
                    assertNotNull(csv);
                    assertTrue(csv.contains("2024-01-02"));
                    assertTrue(csv.contains("2024-01-05"));
                    ctx.completeNow();
                  });
            })
        .onFailure(ctx::failNow);
  }

  @Test
  void getMissReturnsNull(@TempDir Path tmpDir, Vertx vertx, VertxTestContext ctx) {
    OhlcvCache cache = new OhlcvCache(vertx, tmpDir.toString());

    cache
        .get("NONEXISTENT", "2024-01-01")
        .onSuccess(
            csv -> {
              ctx.verify(
                  () -> {
                    assertNull(csv);
                    ctx.completeNow();
                  });
            })
        .onFailure(ctx::failNow);
  }

  @Test
  void filterByDateRemovesFutureRows() {
    String filtered = OhlcvCache.filterByDate(SAMPLE_CSV, "2024-01-03");
    assertTrue(filtered.contains("2024-01-02"));
    assertTrue(filtered.contains("2024-01-03"));
    assertFalse(filtered.contains("2024-01-04"));
    assertFalse(filtered.contains("2024-01-05"));
  }

  @Test
  void filterByDateNullPassesThrough() {
    assertEquals(SAMPLE_CSV, OhlcvCache.filterByDate(SAMPLE_CSV, null));
  }
}
