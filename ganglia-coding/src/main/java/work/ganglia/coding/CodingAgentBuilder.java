package work.ganglia.coding;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.prompt.CodingGuidelineSource;
import work.ganglia.coding.prompt.CodingMandatesContextSource;
import work.ganglia.coding.prompt.CodingPersonaContextSource;
import work.ganglia.coding.prompt.CodingWorkflowSource;
import work.ganglia.coding.tool.*;
import work.ganglia.infrastructure.internal.prompt.context.FileContextSource;
import work.ganglia.infrastructure.internal.prompt.context.MarkdownContextResolver;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.util.PathSanitizer;

/**
 * It wraps the core bootstrap process and automatically injects all coding tools and context
 * sources.
 */
public class CodingAgentBuilder {

  /** Bootstraps a Ganglia instance with all coding tools and default options. */
  public static Future<Ganglia> bootstrap() {
    return bootstrap(Vertx.vertx(), BootstrapOptions.defaultOptions());
  }

  /** Bootstraps a Ganglia instance with all coding tools and specific options. */
  public static Future<Ganglia> bootstrap(BootstrapOptions options) {
    return bootstrap(Vertx.vertx(), options);
  }

  /** Bootstraps a Ganglia instance with all coding tools and context sources. */
  public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions baseOptions) {
    String projectRoot =
        baseOptions.projectRoot() != null
            ? baseOptions.projectRoot()
            : System.getProperty("user.dir");
    PathSanitizer sanitizer = new PathSanitizer(projectRoot);

    // 1. Prepare Tools
    List<ToolSet> codingToolSets = new ArrayList<>(baseOptions.extraToolSets());
    codingToolSets.add(new BashFileSystemTools(vertx, sanitizer));
    codingToolSets.add(new BashTools(vertx));
    codingToolSets.add(new FileEditTools(vertx, sanitizer));
    codingToolSets.add(new WebFetchTools(vertx));

    // 2. Prepare Context Sources
    List<ContextSource> codingSources = new ArrayList<>(baseOptions.extraContextSources());
    MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);

    // Load config to get instruction file
    work.ganglia.config.ConfigManager configManager =
        baseOptions.configPath() != null
            ? new work.ganglia.config.ConfigManager(vertx, baseOptions.configPath())
            : new work.ganglia.config.ConfigManager(vertx);
    if (baseOptions.overrideConfig() != null) {
      configManager.updateConfig(baseOptions.overrideConfig());
    }
    String instructionFile = configManager.getInstructionFile();

    codingSources.add(new CodingPersonaContextSource());
    codingSources.add(new CodingMandatesContextSource());
    codingSources.add(new CodingWorkflowSource());
    codingSources.add(new CodingGuidelineSource(instructionFile));
    codingSources.add(new FileContextSource(vertx, resolver, instructionFile));

    // 3. Assemble final BootstrapOptions
    BootstrapOptions finalOptions =
        baseOptions
            .withProjectRoot(projectRoot)
            .withExtraToolSets(codingToolSets)
            .withExtraContextSources(codingSources);

    return Ganglia.bootstrap(vertx, finalOptions);
  }
}
