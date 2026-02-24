package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.util.ProcessTracker;
import me.stream.ganglia.tools.ToolSet;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A ToolSet that executes external scripts defined in a skill.
 */
public class ScriptSkillToolSet implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(ScriptSkillToolSet.class);

    private final Vertx vertx;
    private final String skillId;
    private final String skillDir;
    private final List<SkillToolDefinition> scriptTools;

    public ScriptSkillToolSet(Vertx vertx, String skillId, String skillDir, List<SkillToolDefinition> scriptTools) {
        this.vertx = vertx;
        this.skillId = skillId;
        this.skillDir = skillDir;
        this.scriptTools = scriptTools;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return scriptTools.stream()
            .map(st -> new ToolDefinition(
                st.name(),
                st.description(),
                st.schema(),
                false 
            ))
            .collect(Collectors.toList());
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        return scriptTools.stream()
            .filter(st -> st.name().equals(call.toolName()))
            .findFirst()
            .map(st -> executeScript(st, call.arguments()))
            .orElseGet(() -> Future.failedFuture("Tool not found: " + call.toolName()));
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        return execute(new ToolCall(UUID.randomUUID().toString(), toolName, args), context);
    }

    private Future<ToolInvokeResult> executeScript(SkillToolDefinition def, Map<String, Object> args) {
        if (def.script() == null) {
            return Future.failedFuture("Tool definition missing script info: " + def.name());
        }
        String command = substituteVariables(def.script().command(), args);
        logger.debug("Executing script tool {} for skill {}: {}", def.name(), skillId, command);

        return vertx.executeBlocking(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                Process process = pb.start();
                ProcessTracker.track(process);
                
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                StringBuilder error = new StringBuilder();
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return ToolInvokeResult.error("Script execution timed out.");
                }

                int exitCode = process.exitValue();
                String result = output.toString().trim();
                String errOutput = error.toString().trim();

                if (exitCode == 0) {
                    return ToolInvokeResult.success(result);
                } else {
                    return ToolInvokeResult.error("Script failed with exit code " + exitCode + ". Error: " + errOutput);
                }
            } catch (Exception e) {
                logger.error("Failed to execute script tool: {}", def.name(), e);
                return ToolInvokeResult.error("Exception during script execution: " + e.getMessage());
            }
        });
    }

    private String substituteVariables(String command, Map<String, Object> args) {
        String result = command;
        if (skillDir != null) {
            result = result.replace("${skillDir}", skillDir);
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
