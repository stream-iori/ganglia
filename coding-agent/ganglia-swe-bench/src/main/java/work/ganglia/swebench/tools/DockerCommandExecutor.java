package work.ganglia.swebench.tools;

import java.io.IOException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.swebench.SandboxManager;
import work.ganglia.util.VertxProcess;

/** Executes commands inside a Docker container using SandboxManager. */
public class DockerCommandExecutor implements CommandExecutor {
  private final Vertx vertx;
  private final SandboxManager sandbox;

  public DockerCommandExecutor(Vertx vertx, SandboxManager sandbox) {
    this.vertx = vertx;
    this.sandbox = sandbox;
  }

  @Override
  public Future<VertxProcess.Result> execute(
      String command, String workingDir, ExecutionContext context) {

    String dir = (workingDir != null && !workingDir.isEmpty()) ? workingDir : "/workspace";

    return vertx.executeBlocking(
        () -> {
          try {
            org.testcontainers.containers.Container.ExecResult result =
                sandbox.execRaw(dir, command);
            String output = result.getStdout();
            if (result.getStderr() != null && !result.getStderr().isEmpty()) {
              output += "\n" + result.getStderr();
            }
            return new VertxProcess.Result(result.getExitCode(), output);
          } catch (IOException | InterruptedException e) {
            return new VertxProcess.Result(1, "Docker execution failed: " + e.getMessage());
          }
        });
  }
}
