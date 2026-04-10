package work.ganglia.port.internal.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Scans memory content for potential security threats before persistence. Detects prompt injection
 * patterns, credential leaks, and invisible unicode characters.
 */
public class MemorySecurityScanner {

  /** Result of a security scan. */
  public record ScanResult(boolean isSafe, List<String> threats) {
    public static ScanResult safe() {
      return new ScanResult(true, List.of());
    }

    public static ScanResult unsafe(List<String> threats) {
      return new ScanResult(false, threats);
    }
  }

  // Prompt injection patterns
  private static final List<Pattern> INJECTION_PATTERNS =
      List.of(
          Pattern.compile(
              "(?i)(disregard|ignore|forget)\\s+(all|previous|prior|above)\\s+(instructions?|rules?|prompts?)"),
          Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)\\s+"),
          Pattern.compile("(?i)new\\s+instructions?:\\s*"),
          Pattern.compile("(?i)system\\s*prompt\\s*override"),
          Pattern.compile("(?i)\\bact\\s+as\\s+(if|though)\\s+you\\b"),
          Pattern.compile("(?i)from\\s+now\\s+on,?\\s+(you|your)\\s+"));

  // Credential patterns
  private static final List<Pattern> CREDENTIAL_PATTERNS =
      List.of(
          Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*\\S+"),
          Pattern.compile("(?i)(api[_-]?key|apikey|secret[_-]?key)\\s*[=:]\\s*\\S+"),
          Pattern.compile("(?i)(access[_-]?token|auth[_-]?token|bearer)\\s*[=:]\\s*\\S+"),
          Pattern.compile("(?i)(aws_secret|aws_access|private[_-]?key)\\s*[=:]\\s*\\S+"),
          Pattern.compile("-----BEGIN\\s+(RSA\\s+)?PRIVATE\\s+KEY-----"));

  // Invisible unicode ranges that could be used for steganographic injection
  private static final Pattern INVISIBLE_UNICODE =
      Pattern.compile("[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF]");

  /**
   * Scans content for security threats.
   *
   * @param content The content to scan.
   * @return A ScanResult indicating whether the content is safe and any threats found.
   */
  public ScanResult scan(String content) {
    if (content == null || content.isBlank()) {
      return ScanResult.safe();
    }

    List<String> threats = new ArrayList<>();

    for (Pattern pattern : INJECTION_PATTERNS) {
      if (pattern.matcher(content).find()) {
        threats.add("Potential prompt injection detected: " + pattern.pattern());
        break; // One injection match is enough
      }
    }

    for (Pattern pattern : CREDENTIAL_PATTERNS) {
      if (pattern.matcher(content).find()) {
        threats.add("Potential credential detected: " + pattern.pattern());
        break;
      }
    }

    if (INVISIBLE_UNICODE.matcher(content).find()) {
      threats.add("Invisible unicode characters detected");
    }

    return threats.isEmpty() ? ScanResult.safe() : ScanResult.unsafe(threats);
  }
}
