package work.ganglia.util;

import java.util.Map;

/** Type-safe accessors for untyped metadata maps ({@code Map<String, Object>}). */
public final class MetadataAccessor {

  private MetadataAccessor() {}

  /**
   * Returns a boolean from metadata, accepting {@code Boolean}, {@code String}, or {@code Number}.
   */
  public static boolean getBoolean(Map<String, Object> metadata, String key, boolean defaultValue) {
    Object value = metadata.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.intValue() != 0;
    }
    return Boolean.parseBoolean(value.toString());
  }

  /** Returns an int from metadata, accepting {@code Number} or {@code String}. */
  public static int getInt(Map<String, Object> metadata, String key, int defaultValue) {
    Object value = metadata.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** Returns a String from metadata, or the default if absent or not a String. */
  public static String getString(Map<String, Object> metadata, String key, String defaultValue) {
    Object value = metadata.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof String s) {
      return s;
    }
    return value.toString();
  }
}
