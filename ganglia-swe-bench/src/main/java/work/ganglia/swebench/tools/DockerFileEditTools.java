package work.ganglia.swebench.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.core.model.SessionContext;
import work.ganglia.swebench.SandboxManager;
import work.ganglia.tools.ToolSet;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.images.builder.Transferable;

import java.util.List;
import java.util.Map;

public class DockerFileEditTools implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(DockerFileEditTools.class);
    private final Vertx vertx;
    private final SandboxManager sandbox;

    public DockerFileEditTools(Vertx vertx, SandboxManager sandbox) {
        this.vertx = vertx;
        this.sandbox = sandbox;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition(
                "replace_in_file",
                "Precisely replace a code block in a file inside Docker. Requires exact matching.",
                """
                {
                  "type": "object",
                  "properties": {
                    "file_path": { "type": "string" },
                    "old_string": { "type": "string" },
                    "new_string": { "type": "string" },
                    "expected_replacements": { "type": "integer", "default": 1 }
                  },
                  "required": ["file_path", "old_string", "new_string"]
                }
                """
            )
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("replace_in_file".equals(toolName)) {
            return replaceInFile(args);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> replaceInFile(Map<String, Object> args) {
        String filePath = (String) args.get("file_path");
        String oldString = (String) args.get("old_string");
        String newString = (String) args.get("new_string");

        Object expectedObj = args.getOrDefault("expected_replacements", 1);
        int expected;
        if (expectedObj instanceof Number) {
            expected = ((Number) expectedObj).intValue();
        } else if (expectedObj instanceof String) {
            expected = Integer.parseInt((String) expectedObj);
        } else {
            expected = 1;
        }

        return vertx.executeBlocking(() -> {
            try {
                // 1. Read file from container
                String content = sandbox.exec("cat", filePath);

                // 2. Perform replacement logic
                int actualCount = countOccurrences(content, oldString);
                if (actualCount == 0) return ToolInvokeResult.error("MATCH_FAILURE in Docker: " + filePath);
                if (actualCount != expected) return ToolInvokeResult.error("AMBIGUITY_FAILURE in Docker: found " + actualCount);

                String updatedContent = content.replace(oldString, newString);

                // 3. Generate Diff (using container's diff)
                String tempOld = "/tmp/old.txt";
                String tempNew = "/tmp/new.txt";
                sandbox.getContainer().copyFileToContainer(Transferable.of(content.getBytes()), tempOld);
                sandbox.getContainer().copyFileToContainer(Transferable.of(updatedContent.getBytes()), tempNew);

                String diff = sandbox.exec("diff", "-u", tempOld, tempNew);

                // 4. Write back to container
                sandbox.getContainer().copyFileToContainer(Transferable.of(updatedContent.getBytes()), filePath);

                return ToolInvokeResult.success("SUCCESS: Replaced in " + filePath, diff);
            } catch (Exception e) {
                return ToolInvokeResult.error("Docker FileEdit Error: " + e.getMessage());
            }
        });
    }

    private int countOccurrences(String text, String target) {
        if (target.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
