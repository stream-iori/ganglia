package work.ganglia.infrastructure.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MockMcpStdioServer {
  public static void main(String[] args) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("\"method\":\"initialize\"")) {
          String id = extractId(line);
          String response =
              "{\"jsonrpc\":\"2.0\",\"id\":"
                  + id
                  + ",\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"MockStdio\",\"version\":\"1.0\"}}}";
          System.out.println(response);
          System.out.flush();
        } else if (line.contains("\"method\":\"tools/list\"")) {
          String id = extractId(line);
          String response =
              "{\"jsonrpc\":\"2.0\",\"id\":"
                  + id
                  + ",\"result\":{\"tools\":[{\"name\":\"mockTool\",\"description\":\"A mock tool\",\"inputSchema\":{}}]}}";
          System.out.println(response);
          System.out.flush();
        } else if (line.contains("\"method\":\"ping\"")) {
          String id = extractId(line);
          String response = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}";
          System.out.println(response);
          System.out.flush();
        } else if (line.contains("\"method\":\"notifications/initialized\"")) {
          // Do nothing
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String extractId(String line) {
    int idx = line.indexOf("\"id\":");
    if (idx != -1) {
      int start = idx + 5;
      int end = line.indexOf(",", start);
      if (end == -1) end = line.indexOf("}", start);
      return line.substring(start, end).trim();
    }
    return "1";
  }
}
