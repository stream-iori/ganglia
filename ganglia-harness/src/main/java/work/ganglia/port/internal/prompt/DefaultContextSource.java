package work.ganglia.port.internal.prompt;

/**
 * Marker interface for context sources that provide default behavior and can be replaced by
 * domain-specific implementations.
 *
 * <p>For example, the generic {@code PersonaContextSource} is a default source that coding modules
 * may replace with a {@code CodingPersonaContextSource}.
 */
public interface DefaultContextSource extends ContextSource {}
