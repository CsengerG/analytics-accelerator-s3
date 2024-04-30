package com.amazon.connector.s3.blockmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazon.connector.s3.object.ObjectContent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class IOBlockTest {

  @Test
  void testConstructor() {
    CompletableFuture<ObjectContent> mockContent =
        CompletableFuture.completedFuture(mock(ObjectContent.class));

    assertNotNull(new IOBlock(0, 0, mockContent));
    assertNotNull(new IOBlock(0, Long.MAX_VALUE, mockContent));
    assertNotNull(new IOBlock(10, 20, mockContent));
  }

  @Test
  void testConstructorThrows() {
    CompletableFuture<ObjectContent> mockContent =
        CompletableFuture.completedFuture(mock(ObjectContent.class));

    assertThrows(Exception.class, () -> new IOBlock(-1, 100, mockContent));
    assertThrows(Exception.class, () -> new IOBlock(100, -200, mockContent));
    assertThrows(Exception.class, () -> new IOBlock(200, 100, mockContent));
    assertThrows(Exception.class, () -> new IOBlock(100, 200, null));
  }

  @Test
  void testClose() throws IOException {
    // Given
    InputStream mockStream = mock(InputStream.class);
    IOBlock ioBlock =
        new IOBlock(
            0,
            100,
            CompletableFuture.completedFuture(ObjectContent.builder().stream(mockStream).build()));

    // When: ioBlock closed
    ioBlock.close();

    // Then: stream is closed
    verify(mockStream, times(1)).close();
  }

  @Test
  void testContains() {
    // Given
    CompletableFuture<ObjectContent> mockContent =
        CompletableFuture.completedFuture(mock(ObjectContent.class));
    IOBlock ioBlock = new IOBlock(1, 3, mockContent);

    // Then
    assertFalse(ioBlock.contains(Long.MIN_VALUE));
    assertFalse(ioBlock.contains(0));
    assertTrue(ioBlock.contains(1));
    assertTrue(ioBlock.contains(2));
    assertTrue(ioBlock.contains(3));
    assertFalse(ioBlock.contains(4));
    assertFalse(ioBlock.contains(Long.MAX_VALUE));
  }

  @Test
  void testPrematureClose() {
    // Given: stream not spanning the whole range the IOBlock represents
    int streamLength = 10;
    ObjectContent content =
        ObjectContent.builder().stream(new ByteArrayInputStream(new byte[streamLength])).build();
    int ioBlockLength = 2 * streamLength;
    IOBlock ioBlock = new IOBlock(0, ioBlockLength, CompletableFuture.completedFuture(content));

    // When: we read the block --> Then: IOException is thrown
    Exception e =
        assertThrows(
            IOException.class,
            () -> {
              for (int i = 0; i < 2 * ioBlockLength; ++i) {
                ioBlock.getByte((long) i);
              }
            });
    assertTrue(e.getMessage().contains("Premature end of file"));
  }
}
