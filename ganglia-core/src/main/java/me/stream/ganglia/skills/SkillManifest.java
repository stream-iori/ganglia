package me.stream.ganglia.skills;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public record SkillManifest(
    String id,
    String version,
    String name,
    String description,
    String author,
    List<PromptDefinition> prompts,
    List<String> tools,
    SkillTrigger activationTriggers
) {
    public static SkillManifest fromJson(JsonObject json) {
        return new SkillManifest(
            json.getString("id"),
            json.getString("version"),
            json.getString("name"),
            json.getString("description"),
            json.getString("author"),
            json.getJsonArray("prompts").stream()
                .map(o -> PromptDefinition.fromJson((JsonObject) o))
                .collect(Collectors.toList()),
            json.getJsonArray("tools").stream()
                .map(Object::toString)
                .collect(Collectors.toList()),
            SkillTrigger.fromJson(json.getJsonObject("activationTriggers"))
        );
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
