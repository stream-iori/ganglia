package work.ganglia.infrastructure.internal.memory;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

public class TokenCounter {
  private final EncodingRegistry registry;
  private final Encoding encoding;

  public TokenCounter() {
    this.registry = Encodings.newDefaultEncodingRegistry();
    this.encoding = registry.getEncoding(EncodingType.CL100K_BASE); // GPT-4/3.5 default
  }

  public int count(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return encoding.countTokens(text);
  }
}
