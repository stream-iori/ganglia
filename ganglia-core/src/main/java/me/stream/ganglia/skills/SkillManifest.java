package me.stream.ganglia.skills;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
    SkillTrigger activationTriggers,
    String instructions
) {

   public SkillManifest {
       if (prompts == null) prompts = Collections.emptyList();
       if (tools == null) tools = Collections.emptyList();
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
            SkillTrigger.fromJson(json.getJsonObject("activationTriggers")),
            ""
        );
    }

    public static SkillManifest fromMarkdown(String folderId, String content) {
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

        Map<String, String> metadata = parseFrontmatter(frontmatter);

        String skillId = metadata.getOrDefault("id", folderId);
        List<String> filePatterns = parseList(metadata.getOrDefault("filePatterns", ""));
        List<String> keywords = parseList(metadata.getOrDefault("keywords", ""));

        return new SkillManifest(
            skillId,
            metadata.getOrDefault("version", "1.0.0"),
            metadata.getOrDefault("name", skillId),
            metadata.getOrDefault("description", ""),
            metadata.getOrDefault("author", "Unknown"),
            new ArrayList<>(),
            parseList(metadata.getOrDefault("tools", "")),
            new SkillTrigger(filePatterns, keywords),
            body
        );
    }

    private static Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> metadata = new HashMap<>();
        if (frontmatter.isEmpty()) return metadata;

        String[] lines = frontmatter.split("\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                metadata.put(key, value);
            }
        }
        return metadata;
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String s : value.split(",")) {
            String trimmed = s.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
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
