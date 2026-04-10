package work.ganglia.trading.graph;

import java.util.stream.Collectors;

import work.ganglia.kernel.subagent.CyclicManagerEngine.CycleContext;

/** Shared utility for building prior-cycle context strings used by graph builders. */
final class CycleContextUtil {

  private CycleContextUtil() {}

  /**
   * Builds a formatted prior-context string from the cycle context. Returns an empty string for
   * cycle 1 or when there are no previous reports.
   */
  static String buildPriorContext(CycleContext ctx) {
    if (ctx.cycleNumber() <= 1 || ctx.previousCycleReports().isEmpty()) {
      return "";
    }
    return "Previous round arguments to consider and counter:\n"
        + ctx.previousCycleReports().stream()
            .map(report -> "- " + report)
            .collect(Collectors.joining("\n"));
  }
}
