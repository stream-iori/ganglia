package work.ganglia.it.component.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import work.ganglia.infrastructure.internal.memory.SqliteMemoryProvider;
import work.ganglia.port.internal.memory.MemorySystemProvider;

/**
 * Verifies that the SPI provider selection mechanism works correctly: both providers are
 * discoverable, and named selection works.
 */
public class SqliteProviderSelectionIT {

  @Test
  void spiDiscoversBothProviders() {
    ServiceLoader<MemorySystemProvider> loader = ServiceLoader.load(MemorySystemProvider.class);
    long count = loader.stream().count();
    // Both filesystem and sqlite providers should be on the classpath
    assertEquals(2, count, "Expected 2 MemorySystemProviders (filesystem + sqlite)");
  }

  @Test
  void sqliteProviderHasCorrectName() {
    SqliteMemoryProvider provider = new SqliteMemoryProvider();
    assertEquals("sqlite", provider.name());
  }

  @Test
  void resolveByName_findsSqlite() {
    ServiceLoader<MemorySystemProvider> loader = ServiceLoader.load(MemorySystemProvider.class);
    MemorySystemProvider sqlite = null;
    for (MemorySystemProvider p : loader) {
      if ("sqlite".equals(p.name())) {
        sqlite = p;
        break;
      }
    }
    assertNotNull(sqlite, "Should find provider with name 'sqlite'");
  }

  @Test
  void resolveByName_findsFilesystem() {
    ServiceLoader<MemorySystemProvider> loader = ServiceLoader.load(MemorySystemProvider.class);
    MemorySystemProvider fs = null;
    for (MemorySystemProvider p : loader) {
      if ("filesystem".equals(p.name())) {
        fs = p;
        break;
      }
    }
    assertNotNull(fs, "Should find provider with name 'filesystem'");
  }
}
