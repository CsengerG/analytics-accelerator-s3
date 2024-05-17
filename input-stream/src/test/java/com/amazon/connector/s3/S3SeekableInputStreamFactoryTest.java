package com.amazon.connector.s3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.amazon.connector.s3.util.S3URI;
import org.junit.jupiter.api.Test;

public class S3SeekableInputStreamFactoryTest {

  @Test
  void testConstructor() {
    ObjectClient objectClient = mock(ObjectClient.class);
    S3SeekableInputStreamFactory s3SeekableInputStreamFactory =
        new S3SeekableInputStreamFactory(objectClient, S3SeekableInputStreamConfiguration.DEFAULT);
    assertEquals(
        S3SeekableInputStreamConfiguration.DEFAULT,
        s3SeekableInputStreamFactory.getConfiguration());
    assertEquals(objectClient, s3SeekableInputStreamFactory.getObjectClient());
  }

  @Test
  void testConstructorThrowsOnNullArgument() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new S3SeekableInputStreamFactory(null, S3SeekableInputStreamConfiguration.DEFAULT);
        });

    assertThrows(
        NullPointerException.class,
        () -> {
          new S3SeekableInputStreamFactory(mock(ObjectClient.class), null);
        });
  }

  @Test
  void testCreateStream() {
    S3SeekableInputStreamFactory s3SeekableInputStreamFactory =
        new S3SeekableInputStreamFactory(
            mock(ObjectClient.class), S3SeekableInputStreamConfiguration.DEFAULT);
    S3SeekableInputStream inputStream =
        s3SeekableInputStreamFactory.createStream(S3URI.of("bucket", "key"));
    assertNotNull(inputStream);
  }

  @Test
  void testCreateStreamThrowsOnNullArgument() {
    S3SeekableInputStreamFactory s3SeekableInputStreamFactory =
        new S3SeekableInputStreamFactory(
            mock(ObjectClient.class), S3SeekableInputStreamConfiguration.DEFAULT);
    assertThrows(
        NullPointerException.class,
        () -> {
          s3SeekableInputStreamFactory.createStream(null);
        });
  }
}