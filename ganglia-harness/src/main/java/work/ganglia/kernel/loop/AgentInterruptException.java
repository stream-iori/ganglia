package work.ganglia.kernel.loop;

public class AgentInterruptException extends RuntimeException {
  private final String prompt;
  private final String askId;

  public AgentInterruptException(String prompt) {
    this(prompt, null);
  }

  public AgentInterruptException(String prompt, String askId) {
    super(prompt);
    this.prompt = prompt;
    this.askId = askId;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getAskId() {
    return askId;
  }
}
