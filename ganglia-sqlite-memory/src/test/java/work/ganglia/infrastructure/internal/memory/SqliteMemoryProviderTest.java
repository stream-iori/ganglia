package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SqliteMemoryProviderTest {

  @Test
  void providerNameIsSqlite() {
    SqliteMemoryProvider provider = new SqliteMemoryProvider();
    assertEquals("sqlite", provider.name());
  }
}
