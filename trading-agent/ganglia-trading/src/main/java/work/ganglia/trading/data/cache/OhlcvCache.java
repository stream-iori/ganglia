package work.ganglia.trading.data.cache;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * File-based OHLCV cache with look-ahead bias guard. Caches raw CSV data per symbol with a 5-year
 * rolling window. All I/O is async via Vert.x filesystem.
 */
public class OhlcvCache {
  private static final Logger logger = LoggerFactory.getLogger(OhlcvCache.class);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final Vertx vertx;
  private final String cacheDir;

  public OhlcvCache(Vertx vertx, String cacheDir) {
    this.vertx = vertx;
    this.cacheDir = cacheDir;
  }

  /**
   * Get cached OHLCV data for a symbol, filtered to prevent look-ahead bias.
   *
   * @return the filtered CSV string, or null if not cached
   */
  public Future<String> get(String symbol, String currDate) {
    String path = resolvePath(symbol);
    return vertx
        .fileSystem()
        .exists(path)
        .compose(
            exists -> {
              if (!exists) return Future.succeededFuture(null);
              return vertx
                  .fileSystem()
                  .readFile(path)
                  .map(buf -> filterByDate(buf.toString(), currDate));
            })
        .recover(
            err -> {
              logger.debug("Cache miss for {}: {}", symbol, err.getMessage());
              return Future.succeededFuture(null);
            });
  }

  /** Store OHLCV CSV data in the cache. */
  public Future<Void> put(String symbol, String csvData) {
    String path = resolvePath(symbol);
    return vertx
        .fileSystem()
        .mkdirs(cacheDir)
        .compose(
            v -> vertx.fileSystem().writeFile(path, io.vertx.core.buffer.Buffer.buffer(csvData)))
        .onFailure(
            err -> logger.warn("Failed to cache OHLCV for {}: {}", symbol, err.getMessage()));
  }

  private String resolvePath(String symbol) {
    LocalDate today = LocalDate.now();
    LocalDate start = today.minusYears(5);
    return cacheDir
        + "/"
        + symbol
        + "-ohlcv-"
        + start.format(DATE_FMT)
        + "-"
        + today.format(DATE_FMT)
        + ".csv";
  }

  /** Filter CSV rows where date > currDate to prevent look-ahead bias. */
  static String filterByDate(String csv, String currDate) {
    if (currDate == null || currDate.isBlank()) return csv;

    String[] lines = csv.split("\n");
    if (lines.length == 0) return csv;

    // Keep header + rows where date <= currDate
    StringBuilder sb = new StringBuilder();
    sb.append(lines[0]).append('\n'); // header

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) continue;
      // Date is the first CSV column
      int comma = line.indexOf(',');
      String dateStr = comma > 0 ? line.substring(0, comma).trim() : line;
      if (dateStr.compareTo(currDate) <= 0) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString();
  }
}
