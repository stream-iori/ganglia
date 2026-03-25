package work.ganglia.port.internal.doctor;

/** Result of a single doctor check. */
public record CheckResult(String name, Status status, Severity severity, String message) {

  public enum Status {
    PASSED,
    WARNING,
    ERROR
  }

  public enum Severity {
    /** Non-critical — system works without it but with reduced functionality. */
    OPTIONAL,
    /** Important — system works but some features may not function correctly. */
    RECOMMENDED,
    /** Critical — system may not work at all without this dependency. */
    REQUIRED
  }

  public static CheckResult passed(String name, String message) {
    return new CheckResult(name, Status.PASSED, Severity.OPTIONAL, message);
  }

  public static CheckResult warning(String name, String message) {
    return new CheckResult(name, Status.WARNING, Severity.OPTIONAL, message);
  }

  public static CheckResult error(String name, String message) {
    return new CheckResult(name, Status.ERROR, Severity.REQUIRED, message);
  }

  public static CheckResult passed(String name, Severity severity, String message) {
    return new CheckResult(name, Status.PASSED, severity, message);
  }

  public static CheckResult warning(String name, Severity severity, String message) {
    return new CheckResult(name, Status.WARNING, severity, message);
  }

  public static CheckResult error(String name, Severity severity, String message) {
    return new CheckResult(name, Status.ERROR, severity, message);
  }
}
