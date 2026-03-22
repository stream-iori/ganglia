package work.ganglia.swebench;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class SandboxManager implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SandboxManager.class);
  private GenericContainer<?> container;

  public void startSandbox() {
    startSandbox(null);
  }

  public void startSandbox(String workspaceHostPath) {
    String homeDir = System.getProperty("user.home");
    String pipCacheHost = homeDir + "/.cache/pip";

    container =
        new GenericContainer<>(DockerImageName.parse("astropy-base"))
            .withCommand("tail", "-f", "/dev/null")
            .withFileSystemBind(pipCacheHost, "/root/.cache/pip")
            .withWorkingDirectory("/workspace");

    if (workspaceHostPath != null) {
      log.info("Mounting task workspace from {} to /workspace", workspaceHostPath);
      container.withFileSystemBind(
          workspaceHostPath, "/workspace", org.testcontainers.containers.BindMode.READ_WRITE);
    }

    // Proxy support
    String httpsProxy = System.getenv("https_proxy");
    if (httpsProxy != null) {
      container.withEnv(
          "https_proxy",
          httpsProxy
              .replace("localhost", "host.docker.internal")
              .replace("127.0.0.1", "host.docker.internal"));
    }
    String httpProxy = System.getenv("http_proxy");
    if (httpProxy != null) {
      container.withEnv(
          "http_proxy",
          httpProxy
              .replace("localhost", "host.docker.internal")
              .replace("127.0.0.1", "host.docker.internal"));
    }

    container.start();
    log.info("Sandbox started. Working directory is /workspace");
  }

  public void setupTaskEnvironment(SWEBenchTask task) throws Exception {
    log.info("Finalizing task environment in sandbox...");
    String repoDir = "/workspace/repo";

    // 1. Verify repo exists (should be mounted from host)
    String repoCheck = exec("ls", "-d", repoDir + "/.git");
    if (repoCheck == null || !repoCheck.contains(".git")) {
      throw new IllegalStateException("Repository not found in /workspace/repo. Mount failed?");
    }

    // 2. Install repository in editable mode inside container
    log.info("Installing repository in editable mode inside container...");
    execInDir(repoDir, "pip", "install", "--no-build-isolation", "-e", ".");

    // 3. Write problem statement
    container.copyFileToContainer(
        Transferable.of(task.getProblemStatement().getBytes()), "/workspace/problem.md");
  }

  public org.testcontainers.containers.Container.ExecResult execRaw(String dir, String... command)
      throws IOException, InterruptedException {
    String cmdStr = String.join(" ", command);
    return container.execInContainer("bash", "-c", "cd " + dir + " && " + cmdStr);
  }

  public String execInDir(String dir, String... command) throws IOException, InterruptedException {
    org.testcontainers.containers.Container.ExecResult result = execRaw(dir, command);
    if (result.getExitCode() != 0) {
      log.warn("Command failed: {} \nStderr: {}", String.join(" ", command), result.getStderr());
    }
    return result.getStdout();
  }

  public String exec(String... command) throws IOException, InterruptedException {
    return execInDir("/workspace", command);
  }

  public GenericContainer<?> getContainer() {
    return container;
  }

  @Override
  public void close() {
    if (container != null && container.isRunning()) {
      log.info("Stopping Docker Sandbox...");
      container.stop();
    }
  }
}
