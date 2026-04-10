package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSanitizerTest {

  @TempDir Path tempDir;

  @Test
  void testSanitizeRelativePath() {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    String result = sanitizer.sanitize("subdir/../.");
    assertNotNull(result);
  }

  @Test
  void testSanitizeAbsolutePathWithinRoot() throws IOException {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    String result = sanitizer.sanitize(tempDir.toString());
    assertNotNull(result);
    // Use toRealPath to handle symlinks (e.g. macOS /var -> /private/var)
    assertTrue(result.startsWith(tempDir.toRealPath().toString()));
  }

  @Test
  void testSanitizeEscapeRootThrows() {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    assertThrows(SecurityException.class, () -> sanitizer.sanitize("/etc/passwd"));
  }

  @Test
  void testSanitizeNullThrows() {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(null));
  }

  @Test
  void testSanitizeEmptyThrows() {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(""));
  }

  @Test
  void testMapDelegatesToSanitize() {
    PathSanitizer sanitizer = new PathSanitizer(tempDir.toString());
    String resultMap = sanitizer.map(tempDir.toString());
    String resultSanitize = sanitizer.sanitize(tempDir.toString());
    assertEquals(resultSanitize, resultMap);
  }

  @Test
  void testEscapeShellArgNormal() {
    assertEquals("'hello world'", PathSanitizer.escapeShellArg("hello world"));
  }

  @Test
  void testEscapeShellArgNull() {
    assertEquals("''", PathSanitizer.escapeShellArg(null));
  }

  @Test
  void testEscapeShellArgEmpty() {
    assertEquals("''", PathSanitizer.escapeShellArg(""));
  }

  @Test
  void testEscapeShellArgWithSingleQuote() {
    String result = PathSanitizer.escapeShellArg("it's");
    assertTrue(result.contains("it"));
    assertTrue(result.contains("s"));
  }

  @Test
  void testDefaultConstructorUsesUserDir() {
    PathSanitizer sanitizer = new PathSanitizer();
    String userDir = System.getProperty("user.dir");
    // Sanitizing the project root itself should work
    String result = sanitizer.sanitize(userDir);
    assertNotNull(result);
  }

  @Test
  void testMappingPathSanitizerVirtualToActual() throws IOException {
    MappingPathSanitizer sanitizer = new MappingPathSanitizer("/workspace", tempDir.toString());
    String result = sanitizer.sanitize("/workspace");
    assertNotNull(result);
    // Use toRealPath to handle symlinks (e.g. macOS /var -> /private/var)
    assertTrue(result.startsWith(tempDir.toRealPath().toString()));
  }

  @Test
  void testMappingPathSanitizerVirtualSubpath() {
    MappingPathSanitizer sanitizer = new MappingPathSanitizer("/workspace", tempDir.toString());
    String result = sanitizer.sanitize("/workspace/src");
    assertNotNull(result);
    assertTrue(result.contains("src"));
  }

  @Test
  void testMappingPathSanitizerEscapeThrows() {
    MappingPathSanitizer sanitizer = new MappingPathSanitizer("/workspace", tempDir.toString());
    assertThrows(Exception.class, () -> sanitizer.sanitize("/etc/passwd"));
  }
}
