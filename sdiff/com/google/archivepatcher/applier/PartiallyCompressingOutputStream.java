// Copyright 2016 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.applier;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * An {@link OutputStream} that is pre-configured to compress some of the bytes that are written to
 * it according to the specified parameters.
 */
public class PartiallyCompressingOutputStream extends FilterOutputStream {

  /**
   * The underlying stream.
   */
  private final OutputStream normalOut;

  /**
   * The deflater, non-null only during compression.
   */
  private Deflater deflater = null;

  /**
   * The deflater stream, non-null only during compression.
   */
  private DeflaterOutputStream deflaterOut = null;

  /**
   * Used when writing one byte at a time.
   */
  private final byte[] internalCopyBuffer = new byte[1];

  /**
   * The size of the buffer to use when compressing bytes.
   */
  private final int compressionBufferSize;

  /**
   * The number of bytes written so far.
   */
  private long numBytesWritten;

  /**
   * The iterator that is used to iterate over the compression ranges.
   */
  private final Iterator<TypedRange<JreDeflateParameters>> rangeIterator;

  /**
   * The compress range that is either being worked on or that is coming up next.
   */
  private TypedRange<JreDeflateParameters> nextCompressedRange = null;

  /**
   * The last {@link JreDeflateParameters} that were used.
   */
  private JreDeflateParameters lastDeflateParameters = null;


  private boolean is7zcompressing = false;

  private String tmpFile7z = "7ztmpfile";

  private String tmpFile7zCompressed = "7ztmpfilecompressed.zip";

  private int compressionLevel = 9;

  private boolean nowrap = false;

  private int num7z = 0;

  private  int total = 0;
  private long z7_io_time = 0;
  private long z7_extra_io_time = 0;
  private long zip_time = 0;
  private long z7_compress_time = 0;
  public long get_z7_compress_time(){
    return this.z7_compress_time;
  }
  public long get_z7_extra_io_time(){
    return this.z7_extra_io_time;
  }
  public long get_z7_io_time(){
    return this.z7_io_time;
  }
  public long get_zip_time(){
    return this.zip_time;
  }

  public  long chunkOffset = 0;
  /**
   * Creates a new stream that wraps the specified other stream, compressing the specified ranges
   * with the specified parameters. All unspecified ranges are implicitly copied without
   * modification.
   * @param compressionRanges ranges to be compressed, with accompanying parameters
   * @param out the stream to write to
   * @param compressionBufferSize the size of the buffer to use when compressing data
   */
  public PartiallyCompressingOutputStream(
      List<TypedRange<JreDeflateParameters>> compressionRanges,
      OutputStream out,
      int compressionBufferSize) {
    super(out);
    this.normalOut = out;
    this.compressionBufferSize = compressionBufferSize;
    rangeIterator = compressionRanges.iterator();
    if (rangeIterator.hasNext()) {
      nextCompressedRange = rangeIterator.next();

    } else {
      // Degenerate case, no compression at all/
      nextCompressedRange = null;
    }
  }
  public PartiallyCompressingOutputStream(
          List<TypedRange<JreDeflateParameters>> compressionRanges,
          OutputStream out,
          int compressionBufferSize,
          String mtName,
          long offSet) {
    super(out);
    this.normalOut = out;
    this.compressionBufferSize = compressionBufferSize;
    this.tmpFile7zCompressed = mtName + tmpFile7zCompressed;
    this.tmpFile7z = mtName + tmpFile7z;
    this.chunkOffset = offSet ;
    rangeIterator = compressionRanges.iterator();
    if (rangeIterator.hasNext()) {
      nextCompressedRange = rangeIterator.next();
//      System.out.println(mtName + "till end : "+bytesTillCompressionEnds());
//      System.out.println(mtName +"offset :"+offSet+ " till start : "+bytesTillCompressionStarts());
    } else {
      // Degenerate case, no compression at all/
      nextCompressedRange = null;
    }
  }

  @Override
  public void write(int b) throws IOException {
    internalCopyBuffer[0] = (byte) b;
    write(internalCopyBuffer, 0, 1);
  }

  @Override
  public void write(byte[] buffer) throws IOException {
    write(buffer, 0, buffer.length);
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws IOException { // 一般调用该重载函数
    int writtenSoFar = 0;
    while (writtenSoFar < length) {
      writtenSoFar += writeChunk(buffer, offset + writtenSoFar, length - writtenSoFar); // 数据写入压缩流
    }
  }
  private int get7zCompressedSize(String temp_zip_file) {
    try {
      byte[] compress_data_size_temp = new byte[4]; // 压缩区大小
      InputStream inputStream = new BufferedInputStream(new FileInputStream(temp_zip_file));
      inputStream.skip(18);
      inputStream.read(compress_data_size_temp); // 读取压缩区大小
      int compress_data_size = ((compress_data_size_temp[3] & 0xff) << 24) | ((compress_data_size_temp[2] & 0xff) << 16) |
              ((compress_data_size_temp[1] & 0xff) << 8)  | (compress_data_size_temp[0] & 0xff);
      inputStream.close();
      return compress_data_size;
    } catch (Exception e) {
      // e.printStackTrace();
      return -1;
    }
  }
  private byte[] get7zCompressedData(String temp_zip_file, int compressed_size) {
    try {
      byte[] file_name_len_temp = new byte[2]; // 文件名长度
      InputStream inputStream = new BufferedInputStream(new FileInputStream(temp_zip_file));
      inputStream.skip(26);
      inputStream.read(file_name_len_temp); // 读取文件名长度
      int file_name_len = ((file_name_len_temp[1] & 0xff) << 8) | (file_name_len_temp[0] & 0xff);
      inputStream.skip(file_name_len + 2); // 跳过文件名，后面紧跟着是数据段
      byte[] compressed_data = new byte[compressed_size];
      inputStream.read(compressed_data); // 读取压缩数据段
      inputStream.close();
      File zipfile = new File(temp_zip_file);
      zipfile.delete();
      return compressed_data;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Write up to <em>length</em> bytes from the specified buffer to the underlying stream. For
   * simplicity, this method stops at the edges of ranges; it is always either copying OR
   * compressing bytes, but never both. All manipulation of the compression state machinery is done
   * within this method. When the end of a compression range is reached it is completely flushed to
   * the output stream, to keep things as straightforward as possible.
   * @param buffer the buffer to copy/compress bytes from
   * @param offset the offset at which to start copying/compressing
   * @param length the maximum number of bytes to copy or compress
   * @return the number of bytes of the buffer that have been consumed
   */

  private int writeChunk(byte[] buffer, int offset, int length) throws IOException {
    if (bytesTillCompressionStarts() == 0 && !currentlyCompressing()) { // 开始一次新的压缩
      // Compression will begin immediately.
      // 写入的数据距离下一个压缩数据开始如果是0，且当前并不在压缩
      // 进行zlib压缩 或者7z 的初始化
      JreDeflateParameters parameters = nextCompressedRange.getMetadata();
      if (parameters.strategy == 7){
        //7z压缩先把数据写入到磁盘，设置相应的参数
        is7zcompressing = true;
      }
      else//原版zlib压缩
      {
          if (deflater == null) {
            deflater = new Deflater(parameters.level, parameters.nowrap);
          }else if (lastDeflateParameters.nowrap != parameters.nowrap) {
            // Last deflater must be destroyed because nowrap settings do not match.
            deflater.end();
            deflater = new Deflater(parameters.level, parameters.nowrap);
          }
          // Deflater will already have been reset at the end of this method, no need to do it again.
          // Just set up the right parameters.
          deflater.setLevel(parameters.level);
          deflater.setStrategy(parameters.strategy);
          deflaterOut = new DeflaterOutputStream(normalOut, deflater, compressionBufferSize);
      }
    }
    int numBytesToWrite;
    OutputStream writeTarget;
    if (currentlyCompressing()) {// 如果当前正在zip压缩 或者 7z压缩
      if(is7zcompressing){// 7z压缩
        numBytesToWrite = (int) Math.min(length, bytesTillCompressionEnds()); // 写入的数据如果小于压缩数据的长度 说明这次写入的数据是压缩数据的一部分
        // 把待压缩数据写入一个缓存文件，额外的io时间
        long start = System.currentTimeMillis();
        writeTarget = new FileOutputStream(tmpFile7z,true);
        writeTarget.write(buffer, offset, numBytesToWrite);
        writeTarget.close();
        long end = System.currentTimeMillis();
        z7_extra_io_time += end - start;
      }
      else{// 原版的zlib压缩
        long start = System.currentTimeMillis();
        numBytesToWrite = (int) Math.min(length, bytesTillCompressionEnds());
        writeTarget = deflaterOut;
        writeTarget.write(buffer, offset, numBytesToWrite);
        long end = System.currentTimeMillis();
        zip_time += end - start;
      }
    } else {// 当前没有进行压缩，直接写入新文件
      writeTarget = normalOut;
      if (nextCompressedRange == null) {
        // 所有的压缩都已经完成
        numBytesToWrite = length;
      } else {
        // 正常写入数据直到下一次压缩开始
        numBytesToWrite = (int) Math.min(length, bytesTillCompressionStarts());
      }
      writeTarget.write(buffer, offset, numBytesToWrite);
    }

    numBytesWritten += numBytesToWrite;

    if (currentlyCompressing() && bytesTillCompressionEnds() == 0) { // 一个待压缩文件的所有的数据已经写入。
      long start, end;
      if(is7zcompressing){ // 进行 7z 压缩
        try{
          compressionLevel = nextCompressedRange.getMetadata().level;
          String homePath = System.getProperty("user.dir");
          String zipPath = homePath + "/archive-patcher-7z/7zip/7zz a ";
          String cmd_7z = zipPath + tmpFile7zCompressed+ " " + tmpFile7z + " -mx" + Integer.toString(compressionLevel);
          start = System.currentTimeMillis();
          Runtime run = Runtime.getRuntime();
          Process ps = run.exec(cmd_7z);
          ps.waitFor();
          end = System.currentTimeMillis();
          z7_compress_time += end - start;
        }catch(Exception e){
          e.printStackTrace();
        }
        //7z压缩完成
        //把压缩后的数据写入到normalOut
        int compressed_7z_size = get7zCompressedSize(tmpFile7zCompressed);
        byte [] compress_7z_data = get7zCompressedData(tmpFile7zCompressed, compressed_7z_size);
        start = System.currentTimeMillis();
        if(compress_7z_data == null) {
          System.out.println("7z is null");
        }
        normalOut.write(compress_7z_data, 0 ,compressed_7z_size);
        end = System.currentTimeMillis();
        z7_io_time += end - start;
        //删除临时文件
        File deleteFile = new File(tmpFile7z);
        deleteFile.delete();
        File deleteFile_com = new File(tmpFile7zCompressed);
        deleteFile_com.delete();
        //7z压缩完成
        is7zcompressing = false;
//        System.out.println(num7z);
      }
      else//原版zlib压缩
      {
        start = System.currentTimeMillis();
        deflaterOut.finish();
        deflaterOut.flush();
        end = System.currentTimeMillis();
        zip_time += end - start;
        deflaterOut = null;
        deflater.reset();
        lastDeflateParameters = nextCompressedRange.getMetadata();
      }
      // Compression range complete. Finish the output and set up for the next run.
      
      if (rangeIterator.hasNext()) {
        nextCompressedRange = rangeIterator.next();
      } else {
        nextCompressedRange = null;
        if(deflater != null){
          deflater.end();
        }
        deflater = null;
      }
    }
//    total += numBytesToWrite;
//    System.out.println(total);
    return numBytesToWrite;
  }

  private boolean currentlyCompressing() {
    return deflaterOut != null || is7zcompressing == true;
  }

  private long bytesTillCompressionStarts() {
    if (nextCompressedRange == null) {
      // All compression ranges have been consumed
      return -1L;
    }
    return nextCompressedRange.getOffset() - numBytesWritten - chunkOffset;
  }

  private long bytesTillCompressionEnds() {
    if (nextCompressedRange == null) {
      // All compression ranges have been consumed
      return -1L;
    }
    return (nextCompressedRange.getOffset() + nextCompressedRange.getLength()) - numBytesWritten - chunkOffset;
  }
}
