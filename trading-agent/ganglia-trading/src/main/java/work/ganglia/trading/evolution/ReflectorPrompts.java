package work.ganglia.trading.evolution;

/**
 * Prompt templates for the 4-step reflection process: Reasoning → Improvement → Summary → Query.
 *
 * <p>Each role receives its own decision context, the market situation, and trade outcome, then
 * produces a compressed lesson stored in role-isolated BM25 memory.
 */
public final class ReflectorPrompts {

  private ReflectorPrompts() {}

  /**
   * Builds the reflection prompt for a specific role.
   *
   * @param persona the agent persona (e.g. "BULL_RESEARCHER")
   * @param roleDecision the text this role produced during the pipeline
   * @param marketSituation a summary of the market conditions (from perception)
   * @param tradeOutcome the trade result description (e.g. "+5% gain", "-3% loss")
   * @return the full reflection prompt
   */
  public static String buildReflectionPrompt(
      String persona, String roleDecision, String marketSituation, String tradeOutcome) {
    return """
        You are a reflective analyst reviewing a past trading decision.

        ## Your Role
        %s

        ## Your Decision at the Time
        %s

        ## Market Situation
        %s

        ## Trade Outcome
        %s

        ## Reflection Instructions
        Perform the following 4-step analysis:

        ### Step 1: Reasoning
        What was the reasoning behind your decision? Was it sound given the available data?
        Identify any logical gaps, biases, or overlooked factors.

        ### Step 2: Improvement
        What would you do differently next time in a similar situation?
        Be specific about which signals you'd weigh more or less heavily.

        ### Step 3: Summary
        Compress your reflection into a single actionable lesson (1-2 sentences).
        Format: "When [situation pattern], [what to do/avoid] because [reason]."

        ### Step 4: Query
        What question would help retrieve this lesson in the future?
        Write a short search query describing the situation pattern.

        ## Output Format
        Respond EXACTLY in this format:

        REASONING: <your reasoning analysis>
        IMPROVEMENT: <what to change>
        SUMMARY: <compressed lesson>
        QUERY: <retrieval query>
        """
        .formatted(persona, roleDecision, marketSituation, tradeOutcome);
  }

  /** Extract the SUMMARY line from a reflection output. */
  public static String extractSummary(String reflectionOutput) {
    for (String line : reflectionOutput.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("SUMMARY:")) {
        return trimmed.substring("SUMMARY:".length()).trim();
      }
    }
    return reflectionOutput.length() > 500 ? reflectionOutput.substring(0, 500) : reflectionOutput;
  }

  /** Extract the QUERY line from a reflection output. */
  public static String extractQuery(String reflectionOutput) {
    for (String line : reflectionOutput.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("QUERY:")) {
        return trimmed.substring("QUERY:".length()).trim();
      }
    }
    return "";
  }
}
