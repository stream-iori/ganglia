package work.ganglia.port.internal.memory;

/**
 * SPI interface for providing a complete memory system. Implementations are discovered via {@link
 * java.util.ServiceLoader}.
 */
public interface MemorySystemProvider {

  /**
   * A short identifier for this provider (e.g. "filesystem", "sqlite"). Used for selection when
   * multiple providers are on the classpath.
   *
   * @return the provider name, or {@code null} if selection by name is not needed.
   */
  default String name() {
    return null;
  }

  /**
   * Creates and assembles a complete memory system from the given configuration.
   *
   * @param config The memory system configuration.
   * @return A fully assembled {@link MemorySystem}.
   */
  MemorySystem create(MemorySystemConfig config);
}
