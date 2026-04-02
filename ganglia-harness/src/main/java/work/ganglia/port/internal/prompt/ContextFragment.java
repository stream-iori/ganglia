package work.ganglia.port.internal.prompt;

/**
 * A piece of context with a specific priority, mandatory flag, and cache stability.
 *
 * @param cacheable true if this fragment is stable across turns (can be cached); false if it
 *     changes frequently
 */
public record ContextFragment(
    String name, String content, int priority, boolean isMandatory, boolean cacheable) {
  // --- STATIC FACTORIES ---
  public static ContextFragment mandatory(String name, String content, int priority) {
    return new ContextFragment(name, content, priority, true, true);
  }

  public static ContextFragment mandatory(
      String name, String content, int priority, boolean cacheable) {
    return new ContextFragment(name, content, priority, true, cacheable);
  }

  public static ContextFragment prunable(String name, String content, int priority) {
    return new ContextFragment(name, content, priority, false, false);
  }

  public static ContextFragment prunable(
      String name, String content, int priority, boolean cacheable) {
    return new ContextFragment(name, content, priority, false, cacheable);
  }

  // Convenience: cacheable prunable (for semi-stable content)
  public static ContextFragment cacheable(String name, String content, int priority) {
    return new ContextFragment(name, content, priority, false, true);
  }

  // --- MANDATORY LAYERS (Never Pruned - The "Soul") ---
  public static final int PRIORITY_PERSONA = 10;
  public static final int PRIORITY_MANDATES = 11;
  public static final int PRIORITY_WORKFLOW = 20;
  public static final int PRIORITY_GUIDELINES = 21;
  public static final int PRIORITY_TOOLS = 22;

  // --- PRUNABLE LAYERS (Dynamic State - The "World") ---
  public static final int PRIORITY_SKILLS = 40;
  public static final int PRIORITY_PLAN = 49;
  public static final int PRIORITY_ENVIRONMENT = 50;
  public static final int PRIORITY_MEMORY = 60;
}
