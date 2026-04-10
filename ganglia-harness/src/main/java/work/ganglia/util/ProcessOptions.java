package work.ganglia.util;

/** Options for process execution. */
public record ProcessOptions(String workingDir, long timeoutMs, long maxOutputSize) {
  public static ProcessOptions defaults() {
    return new ProcessOptions(null, 60000, 1024 * 1024);
  }

  public ProcessOptions withWorkingDir(String workingDir) {
    return new ProcessOptions(workingDir, this.timeoutMs, this.maxOutputSize);
  }

  public ProcessOptions withTimeout(long timeoutMs) {
    return new ProcessOptions(this.workingDir, timeoutMs, this.maxOutputSize);
  }

  public ProcessOptions withMaxOutputSize(long maxOutputSize) {
    return new ProcessOptions(this.workingDir, this.timeoutMs, maxOutputSize);
  }
}
