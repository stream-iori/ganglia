package work.ganglia.port.internal.memory;

/**
 * SPI interface for providing a complete memory system. Implementations are discovered via {@link
 * java.util.ServiceLoader}.
 */
public interface MemorySystemProvider {

  /**
   * Creates and assembles a complete memory system from the given configuration.
   *
   * @param config The memory system configuration.
   * @return A fully assembled {@link MemorySystem}.
   */
  MemorySystem create(MemorySystemConfig config);
}
