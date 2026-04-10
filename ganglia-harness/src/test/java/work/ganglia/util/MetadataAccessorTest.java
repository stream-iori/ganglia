package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MetadataAccessorTest {

  @Test
  void getBoolean_fromBoolean() {
    Map<String, Object> m = Map.of("flag", true);
    assertTrue(MetadataAccessor.getBoolean(m, "flag", false));
  }

  @Test
  void getBoolean_fromString() {
    Map<String, Object> m = Map.of("flag", "true");
    assertTrue(MetadataAccessor.getBoolean(m, "flag", false));
  }

  @Test
  void getBoolean_fromNumber() {
    Map<String, Object> m = Map.of("flag", 1);
    assertTrue(MetadataAccessor.getBoolean(m, "flag", false));

    Map<String, Object> m2 = Map.of("flag", 0);
    assertFalse(MetadataAccessor.getBoolean(m2, "flag", true));
  }

  @Test
  void getBoolean_missing_returnsDefault() {
    Map<String, Object> m = Map.of();
    assertTrue(MetadataAccessor.getBoolean(m, "flag", true));
    assertFalse(MetadataAccessor.getBoolean(m, "flag", false));
  }

  @Test
  void getInt_fromNumber() {
    Map<String, Object> m = Map.of("count", 42);
    assertEquals(42, MetadataAccessor.getInt(m, "count", 0));
  }

  @Test
  void getInt_fromLong() {
    Map<String, Object> m = Map.of("count", 42L);
    assertEquals(42, MetadataAccessor.getInt(m, "count", 0));
  }

  @Test
  void getInt_fromString() {
    Map<String, Object> m = Map.of("count", "7");
    assertEquals(7, MetadataAccessor.getInt(m, "count", 0));
  }

  @Test
  void getInt_invalidString_returnsDefault() {
    Map<String, Object> m = Map.of("count", "abc");
    assertEquals(-1, MetadataAccessor.getInt(m, "count", -1));
  }

  @Test
  void getInt_missing_returnsDefault() {
    Map<String, Object> m = Map.of();
    assertEquals(5, MetadataAccessor.getInt(m, "count", 5));
  }

  @Test
  void getString_fromString() {
    Map<String, Object> m = Map.of("name", "hello");
    assertEquals("hello", MetadataAccessor.getString(m, "name", "default"));
  }

  @Test
  void getString_fromNonString() {
    Map<String, Object> m = Map.of("name", 123);
    assertEquals("123", MetadataAccessor.getString(m, "name", "default"));
  }

  @Test
  void getString_missing_returnsDefault() {
    Map<String, Object> m = Map.of();
    assertEquals("default", MetadataAccessor.getString(m, "name", "default"));
  }

  @Test
  void getBoolean_nullValue_returnsDefault() {
    Map<String, Object> m = new HashMap<>();
    m.put("flag", null);
    assertFalse(MetadataAccessor.getBoolean(m, "flag", false));
  }

  @Test
  void getInt_nullValue_returnsDefault() {
    Map<String, Object> m = new HashMap<>();
    m.put("count", null);
    assertEquals(99, MetadataAccessor.getInt(m, "count", 99));
  }

  @Test
  void getString_nullValue_returnsDefault() {
    Map<String, Object> m = new HashMap<>();
    m.put("name", null);
    assertEquals("fallback", MetadataAccessor.getString(m, "name", "fallback"));
  }
}
