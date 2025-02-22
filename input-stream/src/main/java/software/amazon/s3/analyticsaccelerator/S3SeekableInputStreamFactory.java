/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.amazon.s3.analyticsaccelerator;

import java.io.IOException;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.s3.analyticsaccelerator.common.Preconditions;
import software.amazon.s3.analyticsaccelerator.common.telemetry.Telemetry;
import software.amazon.s3.analyticsaccelerator.io.logical.LogicalIO;
import software.amazon.s3.analyticsaccelerator.io.logical.impl.DefaultLogicalIOImpl;
import software.amazon.s3.analyticsaccelerator.io.logical.impl.ParquetColumnPrefetchStore;
import software.amazon.s3.analyticsaccelerator.io.logical.impl.ParquetLogicalIOImpl;
import software.amazon.s3.analyticsaccelerator.io.physical.data.BlobStore;
import software.amazon.s3.analyticsaccelerator.io.physical.data.MetadataStore;
import software.amazon.s3.analyticsaccelerator.io.physical.impl.PhysicalIOImpl;
import software.amazon.s3.analyticsaccelerator.request.ObjectClient;
import software.amazon.s3.analyticsaccelerator.request.ObjectMetadata;
import software.amazon.s3.analyticsaccelerator.request.StreamContext;
import software.amazon.s3.analyticsaccelerator.util.ObjectFormatSelector;
import software.amazon.s3.analyticsaccelerator.util.S3URI;

/**
 * Initialises resources to prepare for reading from S3. Resources initialised in this class are
 * shared across instances of {@link S3SeekableInputStream}. For example, this allows for the same
 * S3 client to be used across multiple input streams for more efficient connection management etc.
 *
 * <p>This factory does NOT assume ownership of the passed {@link ObjectClient}. It is the
 * responsibility of the caller to close the client and to make sure that it remains active for
 * {@link S3SeekableInputStreamFactory#createStream(S3URI)} to vend correct {@link
 * SeekableInputStream}.
 */
@Getter
public class S3SeekableInputStreamFactory implements AutoCloseable {
  private final ObjectClient objectClient;
  private final S3SeekableInputStreamConfiguration configuration;
  private final ParquetColumnPrefetchStore parquetColumnPrefetchStore;

  private final MetadataStore objectMetadataStore;
  private final BlobStore objectBlobStore;
  private final Telemetry telemetry;
  private final ObjectFormatSelector objectFormatSelector;

  /**
   * Creates a new instance of {@link S3SeekableInputStreamFactory}. This factory should be used to
   * create instances of the input stream to allow for sharing resources such as the object client
   * between streams.
   *
   * @param objectClient Object client
   * @param configuration {@link S3SeekableInputStream} configuration
   */
  public S3SeekableInputStreamFactory(
      @NonNull ObjectClient objectClient,
      @NonNull S3SeekableInputStreamConfiguration configuration) {
    this.objectClient = objectClient;
    this.configuration = configuration;
    this.telemetry = Telemetry.createTelemetry(configuration.getTelemetryConfiguration());
    this.parquetColumnPrefetchStore =
        new ParquetColumnPrefetchStore(configuration.getLogicalIOConfiguration());
    this.objectMetadataStore =
        new MetadataStore(objectClient, telemetry, configuration.getPhysicalIOConfiguration());
    this.objectFormatSelector = new ObjectFormatSelector(configuration.getLogicalIOConfiguration());
    this.objectBlobStore =
        new BlobStore(
            objectMetadataStore,
            objectClient,
            telemetry,
            configuration.getPhysicalIOConfiguration());
  }

  /**
   * Create an instance of S3SeekableInputStream.
   *
   * @param s3URI the object's S3 URI
   * @return An instance of the input stream.
   */
  public S3SeekableInputStream createStream(@NonNull S3URI s3URI) {
    return new S3SeekableInputStream(s3URI, createLogicalIO(s3URI), telemetry);
  }

  /**
   * Create an instance of S3SeekableInputStream with provided metadata.
   *
   * @param s3URI the object's S3 URI
   * @param contentLength content length
   * @return An instance of the input stream.
   */
  public S3SeekableInputStream createStream(@NonNull S3URI s3URI, long contentLength) {
    Preconditions.checkArgument(contentLength >= 0, "`len` must be non-negative");

    objectMetadataStore.storeObjectMetadata(
        s3URI, ObjectMetadata.builder().contentLength(contentLength).build());

    return new S3SeekableInputStream(s3URI, createLogicalIO(s3URI), telemetry);
  }

  /**
   * Create an instance of S3SeekableInputStream with streamContext.
   *
   * @param s3URI the object's S3 URI
   * @param streamContext contains audit headers to be attached in request header
   * @return An instance of the input stream.
   */
  public S3SeekableInputStream createStream(@NonNull S3URI s3URI, StreamContext streamContext) {
    return new S3SeekableInputStream(s3URI, createLogicalIO(s3URI, streamContext), telemetry);
  }

  LogicalIO createLogicalIO(S3URI s3URI) {
    return createLogicalIO(s3URI, null);
  }

  LogicalIO createLogicalIO(S3URI s3URI, StreamContext streamContext) {
    switch (objectFormatSelector.getObjectFormat(s3URI)) {
      case PARQUET:
        return new ParquetLogicalIOImpl(
            s3URI,
            new PhysicalIOImpl(
                s3URI, objectMetadataStore, objectBlobStore, telemetry, streamContext),
            telemetry,
            configuration.getLogicalIOConfiguration(),
            parquetColumnPrefetchStore);

      default:
        return new DefaultLogicalIOImpl(
            s3URI,
            new PhysicalIOImpl(
                s3URI, objectMetadataStore, objectBlobStore, telemetry, streamContext),
            telemetry);
    }
  }

  /**
   * Closes the factory and underlying resources.
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    this.objectMetadataStore.close();
    this.objectBlobStore.close();
    this.telemetry.close();
  }
}
