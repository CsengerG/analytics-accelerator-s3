package com.amazon.connector.s3.io.logical.parquet;

import static com.amazon.connector.s3.util.Constants.PARQUET_FOOTER_LENGTH_SIZE;
import static com.amazon.connector.s3.util.Constants.PARQUET_MAGIC_STR_LENGTH;

import com.amazon.connector.s3.common.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.apache.parquet.format.FileMetaData;
import org.apache.parquet.format.InterningProtocol;
import shaded.parquet.org.apache.thrift.TException;
import shaded.parquet.org.apache.thrift.protocol.TCompactProtocol;
import shaded.parquet.org.apache.thrift.protocol.TProtocol;
import shaded.parquet.org.apache.thrift.transport.TIOStreamTransport;
import shaded.parquet.org.apache.thrift.transport.TTransportException;

/** Allows for parsing a tail of a parquet file to get its FileMetadata. */
class ParquetParser {

  /**
   * Parses the tail of a parquet file to obtain its FileMetaData.
   *
   * @param fileTail tail bytes of parquet file to be parsed
   * @param contentLen The length of the parquet file tail to be parsed
   * @return FileMetaData
   * @throws IOException
   */
  public FileMetaData parseParquetFooter(ByteBuffer fileTail, int contentLen) throws IOException {

    // TODO: https://app.asana.com/0/1206885953994785/1207471636563541 This is an initial basic
    // implementation. We should look at supporting different parquet versions, encrypted files etc.
    Preconditions.checkArgument(
        contentLen > PARQUET_MAGIC_STR_LENGTH + PARQUET_FOOTER_LENGTH_SIZE,
        "Specified content length is too low");

    int fileMetadataLengthIndex =
        contentLen - PARQUET_MAGIC_STR_LENGTH - PARQUET_FOOTER_LENGTH_SIZE;

    fileTail.position(fileMetadataLengthIndex);

    byte[] buff = new byte[PARQUET_FOOTER_LENGTH_SIZE];
    fileTail.get(buff, 0, PARQUET_FOOTER_LENGTH_SIZE);

    int fileMetadataLength = readIntLittleEndian(new ByteArrayInputStream(buff));
    int fileMetadataIndex = fileMetadataLengthIndex - fileMetadataLength;
    fileTail.position(fileMetadataIndex);
    byte[] footer = new byte[fileMetadataLength];
    fileTail.get(footer, 0, fileMetadataLength);

    try {
      FileMetaData fmd = new FileMetaData();
      fmd.read(protocol(new ByteArrayInputStream(footer)));
      return fmd;
    } catch (TException e) {
      throw new IOException("can not read FileMetaData: " + e.getMessage(), e);
    }
  }

  private static TProtocol protocol(InputStream from) throws TTransportException {
    return protocol(new TIOStreamTransport(from));
  }

  private static InterningProtocol protocol(TIOStreamTransport t) {
    return new InterningProtocol(new TCompactProtocol(t));
  }

  private static int readIntLittleEndian(InputStream in) throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    int ch3 = in.read();
    int ch4 = in.read();

    return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
  }
}