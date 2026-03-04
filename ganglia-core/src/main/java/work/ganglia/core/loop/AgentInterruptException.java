package work.ganglia.core.loop;

public class AgentInterruptException extends RuntimeException {
    private final String prompt;

    public AgentInterruptException(String prompt) {
        super(prompt);
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }
}
