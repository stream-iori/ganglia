package work.ganglia.coding.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UrlValidatorTest {

  @Test
  void testRejectsPrivateIps() {
    assertNotNull(UrlValidator.validate("http://127.0.0.1/secret"));
    assertNotNull(UrlValidator.validate("http://10.0.0.1/internal"));
    assertNotNull(UrlValidator.validate("http://192.168.1.1/admin"));
    assertNotNull(UrlValidator.validate("http://169.254.169.254/latest/meta-data"));
  }

  @Test
  void testRejectsNonHttpSchemes() {
    assertNotNull(UrlValidator.validate("ftp://example.com/file"));
    assertNotNull(UrlValidator.validate("file:///etc/passwd"));
    assertNotNull(UrlValidator.validate("javascript:alert(1)"));
  }

  @Test
  void testAllowsPublicUrls() {
    assertNull(UrlValidator.validate("https://example.com"));
    assertNull(UrlValidator.validate("http://example.com/path?q=1"));
  }

  @Test
  void testRejectsEmptyAndNull() {
    assertNotNull(UrlValidator.validate(null));
    assertNotNull(UrlValidator.validate(""));
    assertNotNull(UrlValidator.validate("   "));
  }

  @Test
  void testRejectsNoHost() {
    assertNotNull(UrlValidator.validate("http://"));
  }

  @Test
  void testRejectsZeroAddress() {
    assertNotNull(UrlValidator.validate("http://0.0.0.0/"));
  }
}
