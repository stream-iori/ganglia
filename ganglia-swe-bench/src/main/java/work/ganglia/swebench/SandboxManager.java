package work.ganglia.swebench;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SandboxManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SandboxManager.class);
    private GenericContainer<?> container;

    public void startSandbox() {
        // Use specialized image for astropy tasks
        String homeDir = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String pipCacheHost = homeDir + "/.cache/pip";

        // Robust discovery of git_mirrors
        String gitMirrorsHost = findGitMirrorsPath(currentDir);

        container = new GenericContainer<>(DockerImageName.parse("astropy-base"))
                .withCommand("tail", "-f", "/dev/null") // Keep container running
                .withFileSystemBind(pipCacheHost, "/root/.cache/pip")
                .withFileSystemBind(gitMirrorsHost, "/git_mirrors")
                .withWorkingDirectory("/workspace");

        // Propagate proxy env vars if they exist on host
        String httpsProxy = System.getenv("https_proxy");
        String httpProxy = System.getenv("http_proxy");
        String allProxy = System.getenv("all_proxy");

        // Translate localhost to OrbStack's host.docker.internal for Docker
        if (httpsProxy != null) {
            httpsProxy = httpsProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            container.withEnv("https_proxy", httpsProxy);
        }
        if (httpProxy != null) {
            httpProxy = httpProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            container.withEnv("http_proxy", httpProxy);
        }
        if (allProxy != null) {
            allProxy = allProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            container.withEnv("all_proxy", allProxy);
        }

        log.info("Starting Docker Sandbox with git_mirrors from: {}", gitMirrorsHost);
        container.start();
        log.info("Sandbox started with all dependencies pre-installed.");
    }

    private String findGitMirrorsPath(String startDir) {
        Path path = Path.of(startDir);
        while (path != null) {
            Path candidate = path.resolve(".ganglia/cache/git_mirrors");
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            path = path.getParent();
        }
        // Fallback to default if not found
        return startDir + "/.ganglia/cache/git_mirrors";
    }

    public void setupTaskEnvironment(SWEBenchTask task) throws Exception {
        log.info("Setting up task {} in sandbox", task.getInstanceId());

        String repoName = task.getRepo().replace("/", "__");
        String mirrorPath = "/git_mirrors/" + repoName;
        String repoUrl = "https://github.com/" + task.getRepo() + ".git";

        // 1. Ensure mirror exists on the host-side (executed inside container via mount)
        log.info("Checking git mirror for {}...", task.getRepo());

        // Use a simpler approach without nested bash -c to avoid quoting hell
        String checkResult = exec("ls", "-d", mirrorPath);
        if (checkResult == null || checkResult.isBlank() || checkResult.contains("No such file or directory")) {
            log.info("Mirror not found in /git_mirrors, cloning into {}...", mirrorPath);
            exec("git", "clone", "--mirror", repoUrl, mirrorPath);
        } else {
            log.info("Mirror found at {}, reusing existing objects.", mirrorPath);
        }

        // 2. Clone using reference
        log.info("Cloning repository using reference mirror...");
        exec("git", "clone", "--reference", mirrorPath, repoUrl, "repo");

        String repoDir = "/workspace/repo";
        execInDir(repoDir, "git", "checkout", task.getBaseCommit());

        // Automate installation of the repo using pip
        // --no-build-isolation is critical to use the pre-installed setuptools and numpy
        log.info("Installing repository in editable mode...");
        execInDir(repoDir, "pip", "install", "--no-build-isolation", "-e", ".");

        // Write problem statement to workspace
        container.copyFileToContainer(Transferable.of(task.getProblemStatement().getBytes()), "/workspace/problem.md");
    }

    public String exec(String... command) throws IOException, InterruptedException {
        return execInDir("/workspace", command);
    }

    public String execInDir(String dir, String... command) throws IOException, InterruptedException {
        // Build the command string
        String cmdStr = String.join(" ", command);

        // Ensure proxy env vars are available in the bash session
        StringBuilder bashCmd = new StringBuilder();
        String httpsProxy = System.getenv("https_proxy");
        String httpProxy = System.getenv("http_proxy");
        String allProxy = System.getenv("all_proxy");

        if (httpsProxy != null) {
            httpsProxy = httpsProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            bashCmd.append("export https_proxy=").append(httpsProxy).append("; ");
        }
        if (httpProxy != null) {
            httpProxy = httpProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            bashCmd.append("export http_proxy=").append(httpProxy).append("; ");
        }
        if (allProxy != null) {
            allProxy = allProxy.replace("127.0.0.1", "host.docker.internal").replace("localhost", "host.docker.internal");
            bashCmd.append("export all_proxy=").append(allProxy).append("; ");
        }

        bashCmd.append("cd ").append(dir).append(" && ").append(cmdStr);

        org.testcontainers.containers.Container.ExecResult result = container.execInContainer(
                "bash", "-c", bashCmd.toString()
        );

        if (result.getExitCode() != 0) {
            log.warn("Command failed: {} \nStderr: {}", cmdStr, result.getStderr());
            return result.getStderr();
        }
        return result.getStdout();
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
