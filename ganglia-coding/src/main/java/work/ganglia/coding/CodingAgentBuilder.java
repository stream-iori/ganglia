package work.ganglia.coding;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.prompt.CodingMandatesContextSource;
import work.ganglia.coding.prompt.CodingPersonaContextSource;
import work.ganglia.coding.prompt.CodingWorkflowSource;
import work.ganglia.coding.tool.BashFileSystemTools;
import work.ganglia.coding.tool.BashTools;
import work.ganglia.coding.tool.FileEditTools;
import work.ganglia.coding.tool.WebFetchTools;
import work.ganglia.coding.tool.util.LocalCommandExecutor;
import work.ganglia.infrastructure.internal.prompt.context.FileContextSource;
import work.ganglia.infrastructure.internal.prompt.context.MarkdownContextResolver;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.prompt.DefaultContextSource;
import work.ganglia.util.PathMapper;
import work.ganglia.util.PathSanitizer;

/**
 * Specialized builder for Coding Agents.
 *
 * <p>It wraps the core bootstrap process and automatically injects all coding tools and context
 * sources with support for custom path mapping and environment overrides.
 */
public class CodingAgentBuilder {

  private static final String DEFAULT_INSTRUCTION_FILE = "CODING.md";

  private final Vertx vertx;
  private BootstrapOptions baseOptions;
  private PathMapper internalPathMapper;
  private PathMapper externalPathMapper;
  private String instructionFile = DEFAULT_INSTRUCTION_FILE;
  private Predicate<ContextSource> contextFilter = s -> false;
  private final List<ContextSource> contextOverrides = new ArrayList<>();

  public CodingAgentBuilder() {
    this(Vertx.vertx());
  }

  public CodingAgentBuilder(Vertx vertx) {
    this.vertx = vertx;
    this.baseOptions = BootstrapOptions.defaultOptions();
    // Default filter to remove generic sources since we provide coding-specific ones
    this.contextFilter = s -> s instanceof DefaultContextSource;
  }

  public static CodingAgentBuilder create() {
    return new CodingAgentBuilder();
  }

  public static CodingAgentBuilder create(Vertx vertx) {
    return new CodingAgentBuilder(vertx);
  }

  public CodingAgentBuilder withOptions(BootstrapOptions options) {
    this.baseOptions = options;
    return this;
  }

  /** Sets the mapper for tools running inside the target environment (e.g., Bash in Docker). */
  public CodingAgentBuilder withInternalPathMapper(PathMapper mapper) {
    this.internalPathMapper = mapper;
    return this;
  }

  /**
   * Sets the mapper for tools running on the host but acting on the environment (e.g., FileEdit).
   */
  public CodingAgentBuilder withExternalPathMapper(PathMapper mapper) {
    this.externalPathMapper = mapper;
    return this;
  }

  /** Filters out existing context sources (e.g., to remove default EnvironmentSource). */
  public CodingAgentBuilder filterContextSources(Predicate<ContextSource> filter) {
    this.contextFilter = filter;
    return this;
  }

  /** Adds custom context sources. */
  public CodingAgentBuilder addContextSource(ContextSource source) {
    this.contextOverrides.add(source);
    return this;
  }

  /** Sets the instruction file to load as a context source. Defaults to "CODING.md". */
  public CodingAgentBuilder withInstructionFile(String file) {
    this.instructionFile = (file != null) ? file : DEFAULT_INSTRUCTION_FILE;
    return this;
  }

  public Future<Ganglia> bootstrap() {
    String projectRoot =
        baseOptions.projectRoot() != null
            ? baseOptions.projectRoot()
            : System.getProperty("user.dir");

    // Use default PathSanitizer if not provided
    PathMapper internalMapper =
        (internalPathMapper != null) ? internalPathMapper : new PathSanitizer(projectRoot);
    PathMapper externalMapper = (externalPathMapper != null) ? externalPathMapper : internalMapper;

    // 0. Resolve CommandExecutor
    CommandExecutor commandExecutor = baseOptions.commandExecutor();
    if (commandExecutor == null) {
      commandExecutor = new LocalCommandExecutor(vertx);
    }

    // 1. Prepare Tools
    List<ToolSet> codingToolSets = new ArrayList<>(baseOptions.extraToolSets());
    codingToolSets.add(new work.ganglia.coding.tool.NativeFileSystemTools(vertx, internalMapper));
    codingToolSets.add(new BashFileSystemTools(commandExecutor, internalMapper));
    codingToolSets.add(new BashTools(commandExecutor));
    codingToolSets.add(new FileEditTools(vertx, externalMapper));
    codingToolSets.add(new WebFetchTools(vertx));

    // 2. Prepare Context Sources
    List<ContextSource> codingSources = new ArrayList<>(baseOptions.extraContextSources());
    codingSources.add(new CodingWorkflowSource());
    codingSources.add(new CodingPersonaContextSource());
    codingSources.add(new CodingMandatesContextSource());

    // Apply filter to remove unwanted sources (like default EnvironmentSource)
    codingSources.removeIf(contextFilter);
    codingSources.addAll(contextOverrides);

    MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
    codingSources.add(new FileContextSource(vertx, resolver, instructionFile));

    // 3. Perform Bootstrap
    BootstrapOptions options =
        baseOptions.toBuilder()
            .extraToolSets(codingToolSets)
            .extraContextSources(codingSources)
            .build();

    return Ganglia.bootstrap(vertx, options);
  }

  // --- Static Legacy Methods for Compatibility ---

  /** Bootstraps a Ganglia instance with all coding tools and specific options. */
  public static Future<Ganglia> bootstrap(BootstrapOptions options) {
    return create(Vertx.vertx()).withOptions(options).bootstrap();
  }

  /** Bootstraps a Ganglia instance with all coding tools and context sources. */
  public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions baseOptions) {
    return create(vertx).withOptions(baseOptions).bootstrap();
  }
}
