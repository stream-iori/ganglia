package work.ganglia.kernel.subagent.validation;

import java.time.Duration;

/**
 * Defines a validation suite to be run by RealityAnchorTask.
 *
 * @param name human-readable name (e.g., "unit-tests", "lint", "security-audit")
 * @param command shell command to execute (e.g., "mvn test", "npm run lint")
 * @param timeout per-suite timeout
 * @param blockOnFailure true = fail entire cycle; false = advisory only
 */
public record ValidationSuite(
    String name, String command, Duration timeout, boolean blockOnFailure) {

  public ValidationSuite {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Suite name must not be blank");
    }
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("Suite command must not be blank");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Suite timeout must be positive");
    }
  }
}
