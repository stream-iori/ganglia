package work.ganglia.coding.prompt;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.GuidelineContextSource;

public class CodingGuidelineSource implements GuidelineContextSource {
  private final String instructionFile;

  public CodingGuidelineSource() {
    this("GANGLIA.md");
  }

  public CodingGuidelineSource(String instructionFile) {
    this.instructionFile = instructionFile != null ? instructionFile : "GANGLIA.md";
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    String guidelines =
        """
             ## [Operational Guidelines]
             As a senior software engineer and peer programmer, adhere to these standards:

             ### Tone and Style
             - **Role**: Be professional, direct, and concise.
             - **High-Signal Output**: Focus on intent and technical rationale. Avoid conversational filler or mechanical tool-use narration.
             - **Brevity**: Aim for minimal text output (ideally fewer than 3 lines excluding tool calls).

             ### Engineering Standards
             - **Contextual Precedence**: Instructions in `%s` take absolute precedence over general defaults.
             - **Idiomatic Code**: Rigorously follow existing workspace conventions (naming, formatting, typing). Use available ecosystem tools (Linter, Formatter) automatically.
             - **Testing is Mandatory**: A change is only complete when verified by automated tests.
             - **Security**: Never expose or commit secrets, API keys, or sensitive credentials.

             ### Proactiveness
             - **Explain Before Acting**: Provide a concise explanation of intent before executing tools.
             - **Autonomy**: Clarify only if critically underspecified; otherwise, work towards the goal.
             """
            .formatted(instructionFile);

    return Future.succeededFuture(
        List.of(
            ContextFragment.mandatory(
                "Guidelines", guidelines, ContextFragment.PRIORITY_GUIDELINES)));
  }
}
