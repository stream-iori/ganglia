package work.ganglia.port.internal.prompt;

/**
 * A piece of context with a specific priority and mandatory flag.
 */
public record ContextFragment(
    String name,
    String content,
    int priority,
    boolean isMandatory
) {
    public static final int PRIORITY_PERSONA = 1;
    public static final int PRIORITY_MANDATES = 2;
    public static final int PRIORITY_TECH_STACK = 3;
    public static final int PRIORITY_ENVIRONMENT = 4;
    public static final int PRIORITY_SKILLS = 5;
    public static final int PRIORITY_PLAN = 6;
    public static final int PRIORITY_MEMORY = 10;
}
