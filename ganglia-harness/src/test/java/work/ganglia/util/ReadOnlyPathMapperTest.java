package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReadOnlyPathMapperTest {

  private final PathMapper delegate = inputPath -> "/safe/path/" + inputPath;

  @Test
  void map_delegatesToInnerMapper() {
    var readOnly = new ReadOnlyPathMapper(delegate);
    assertEquals("/safe/path/foo.txt", readOnly.map("foo.txt"));
  }

  @Test
  void isReadOnly_returnsTrue() {
    var readOnly = new ReadOnlyPathMapper(delegate);
    assertTrue(readOnly.isReadOnly());
  }

  @Test
  void checkWriteAccess_throwsSecurityException() {
    var readOnly = new ReadOnlyPathMapper(delegate);
    assertThrows(SecurityException.class, () -> readOnly.checkWriteAccess("any-path"));
  }

  @Test
  void wrapsNullDelegate_throwsNPE() {
    assertThrows(NullPointerException.class, () -> new ReadOnlyPathMapper(null));
  }
}
