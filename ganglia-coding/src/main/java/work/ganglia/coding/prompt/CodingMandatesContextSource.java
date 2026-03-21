package work.ganglia.coding.prompt;

import work.ganglia.infrastructure.internal.prompt.context.MandatesContextSource;

public class CodingMandatesContextSource extends MandatesContextSource {
  @Override
  protected String getMandates() {
    return """
            1. **Security First**: Never introduce code that exposes or commits secrets/API keys.
            2. **No Unprompted Commits**: Do not stage or commit changes unless explicitly instructed by the user.
            3. **Technical Integrity**: Consolidate logic, avoid redundancy. Before modifying code, verify existing ecosystem tools (e.g. formatters/linters).
            4. **Testing**: Always run related tests after modifications. If adding new features or fixing bugs, add tests to verify the change.
            """;
  }
}
