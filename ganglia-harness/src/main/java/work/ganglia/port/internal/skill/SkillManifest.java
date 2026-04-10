package work.ganglia.port.internal.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record SkillManifest(
    String id,
    String version,
    String name,
    String description,
    String author,
    List<PromptDefinition> prompts,
    List<String> tools,
    List<SkillToolDefinition> skillTools,
    SkillTrigger activationTriggers,
    String instructions,
    String skillDir,
    String jarPath) {
  private static final Logger logger = LoggerFactory.getLogger(SkillManifest.class);
  private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  public SkillManifest {
    if (prompts == null) {
      prompts = Collections.emptyList();
    }
    if (tools == null) {
      tools = Collections.emptyList();
    }
    if (skillTools == null) {
      skillTools = Collections.emptyList();
    }
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
        null,
        null);
  }

  public static SkillManifest fromMarkdown(String folderId, String content) {
    return fromMarkdown(folderId, content, null, null);
  }

  public static SkillManifest fromMarkdown(
      String folderId, String content, String skillDir, String jarPath) {
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

    String skillId = getString(metadata, "id", folderId);

    List<SkillToolDefinition> skillTools = new ArrayList<>();
    Object toolsObj = metadata.get("tools");
    if (toolsObj instanceof List<?> toolsList) {
      for (Object item : toolsList) {
        if (item instanceof Map<?, ?> toolMap) {
          String type = getString(toolMap, "type", "SCRIPT");
          SkillToolDefinition.ScriptInfo scriptInfo = null;
          SkillToolDefinition.JavaInfo javaInfo = null;

          if ("SCRIPT".equals(type)) {
            String command = getString(toolMap, "command", null);
            if (command == null && toolMap.get("script") instanceof Map<?, ?> sm) {
              command = getString(sm, "command", null);
            }
            scriptInfo = new SkillToolDefinition.ScriptInfo(command);
          } else if ("JAVA".equals(type)) {
            String className = getString(toolMap, "className", null);
            if (className == null && toolMap.get("java") instanceof Map<?, ?> jm) {
              className = getString(jm, "className", null);
            }
            javaInfo = new SkillToolDefinition.JavaInfo(className);
          }

          skillTools.add(
              new SkillToolDefinition(
                  getString(toolMap, "name", null),
                  getString(toolMap, "description", null),
                  type,
                  scriptInfo,
                  javaInfo,
                  getString(toolMap, "schema", null)));
        }
      }
    }

    List<String> filePatterns = Collections.emptyList();
    List<String> keywords = Collections.emptyList();
    Object triggerObj = metadata.get("activationTriggers");
    if (triggerObj instanceof Map<?, ?> tm) {
      filePatterns = parseList(tm.get("filePatterns"));
      keywords = parseList(tm.get("keywords"));
    } else {
      filePatterns = parseList(metadata.get("filePatterns"));
      keywords = parseList(metadata.get("keywords"));
    }
    SkillTrigger trigger = new SkillTrigger(filePatterns, keywords);

    return new SkillManifest(
        skillId,
        getString(metadata, "version", "1.0.0"),
        getString(metadata, "name", skillId),
        getString(metadata, "description", ""),
        getString(metadata, "author", "Unknown"),
        new ArrayList<>(),
        new ArrayList<>(),
        skillTools,
        trigger,
        body,
        skillDir,
        jarPath);
  }

  private static String getString(Map<?, ?> map, String key, String defaultValue) {
    Object val = map.get(key);
    return val != null ? val.toString() : defaultValue;
  }

  private static Map<String, Object> parseFrontmatter(String frontmatter) {
    if (frontmatter.isEmpty()) {
      return new HashMap<>();
    }
    try {
      return yamlMapper.readValue(frontmatter, Map.class);
    } catch (Exception e) {
      logger.warn(
          "Failed to parse YAML frontmatter: {}. Falling back to empty map.", e.getMessage());
      return new HashMap<>();
    }
  }

  private static List<String> parseList(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
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
          json.getString("id"), json.getString("path"), json.getInteger("priority", 0));
    }
  }

  public record SkillTrigger(List<String> filePatterns, List<String> keywords) {
    public static SkillTrigger fromJson(JsonObject json) {
      if (json == null) {
        return new SkillTrigger(List.of(), List.of());
      }
      return new SkillTrigger(
          json.getJsonArray("filePatterns", new JsonArray()).stream()
              .map(Object::toString)
              .toList(),
          json.getJsonArray("keywords", new JsonArray()).stream().map(Object::toString).toList());
    }
  }
}
