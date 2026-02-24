package me.stream.ganglia.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public record SkillManifest(
    String id,
    String version,
    String name,
    String description,
    String author,
    List<PromptDefinition> prompts,
    List<String> tools,
    List<ScriptToolDefinition> scriptTools,
    SkillTrigger activationTriggers,
    String instructions,
    String skillDir
) {
    private static final Logger logger = LoggerFactory.getLogger(SkillManifest.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SkillManifest {
        if (prompts == null) prompts = Collections.emptyList();
        if (tools == null) tools = Collections.emptyList();
        if (scriptTools == null) scriptTools = Collections.emptyList();
    }

    public static SkillManifest fromJson(JsonObject json) {
        return new SkillManifest(
            json.getString("id"),
            json.getString("version"),
            json.getString("name"),
            json.getString("description"),
            json.getString("author"),
            json.getJsonArray("prompts", new JsonArray()).stream()
                .map(o -> PromptDefinition.fromJson((JsonObject) o))
                .collect(Collectors.toList()),
            json.getJsonArray("tools", new JsonArray()).stream()
                .map(Object::toString)
                .collect(Collectors.toList()),
            Collections.emptyList(),
            SkillTrigger.fromJson(json.getJsonObject("activationTriggers")),
            "",
            null
        );
    }

    public static SkillManifest fromMarkdown(String folderId, String content) {
        return fromMarkdown(folderId, content, null);
    }

    public static SkillManifest fromMarkdown(String folderId, String content, String skillDir) {
        String frontmatter = "";
        String body = "";

        content = content.trim();
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end != -1) {
                frontmatter = content.substring(3, end).trim();
                body = content.substring(end + 3).trim();
            } else {
                body = content;
            }
        } else {
            body = content;
        }

        Map<String, Object> metadata = parseFrontmatter(frontmatter);

        String skillId = metadata.getOrDefault("id", folderId).toString();
        
        List<ScriptToolDefinition> scriptTools = new ArrayList<>();
        if (metadata.get("tools") instanceof List<?> toolsList) {
            for (Object item : toolsList) {
                if (item instanceof Map<?, ?> toolMap) {
                    scriptTools.add(new ScriptToolDefinition(
                        (String) toolMap.get("name"),
                        (String) toolMap.get("description"),
                        (String) toolMap.get("command"),
                        (String) toolMap.get("schema")
                    ));
                }
            }
        }

        SkillTrigger trigger = null;
        Object triggerObj = metadata.get("activationTriggers");
        if (triggerObj instanceof Map<?, ?> tm) {
            List<String> filePatterns = parseList(tm.get("filePatterns"));
            List<String> keywords = parseList(tm.get("keywords"));
            trigger = new SkillTrigger(filePatterns, keywords);
        } else {
            // Legacy/alternative flat format support
            trigger = new SkillTrigger(
                parseList(metadata.get("filePatterns")),
                parseList(metadata.get("keywords"))
            );
        }

        return new SkillManifest(
            skillId,
            metadata.getOrDefault("version", "1.0.0").toString(),
            metadata.getOrDefault("name", skillId).toString(),
            metadata.getOrDefault("description", "").toString(),
            metadata.getOrDefault("author", "Unknown").toString(),
            new ArrayList<>(),
            new ArrayList<>(),
            scriptTools,
            trigger,
            body,
            skillDir
        );
    }

    private static Map<String, Object> parseFrontmatter(String frontmatter) {
        if (frontmatter.isEmpty()) return new HashMap<>();
        try {
            return yamlMapper.readValue(frontmatter, Map.class);
        } catch (Exception e) {
            logger.warn("Failed to parse YAML frontmatter: {}. Falling back to empty map.", e.getMessage());
            return new HashMap<>();
        }
    }

    private static List<String> parseList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        if (value instanceof String s) {
            if (s.startsWith("[") && s.endsWith("]")) {
                s = s.substring(1, s.length() - 1);
            }
            return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public record PromptDefinition(String id, String path, int priority) {
        public static PromptDefinition fromJson(JsonObject json) {
            return new PromptDefinition(
                json.getString("id"),
                json.getString("path"),
                json.getInteger("priority", 0)
            );
        }
    }

    public record SkillTrigger(List<String> filePatterns, List<String> keywords) {
        public static SkillTrigger fromJson(JsonObject json) {
            if (json == null) return new SkillTrigger(List.of(), List.of());
            return new SkillTrigger(
                json.getJsonArray("filePatterns", new JsonArray()).stream().map(Object::toString).toList(),
                json.getJsonArray("keywords", new JsonArray()).stream().map(Object::toString).toList()
            );
        }
    }
}
