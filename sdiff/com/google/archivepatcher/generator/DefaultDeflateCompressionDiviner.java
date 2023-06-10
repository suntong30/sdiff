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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.shared.ByteArrayInputStreamFactory;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.MultiViewInputStreamFactory;
import com.google.archivepatcher.shared.RandomAccessFileInputStreamFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

// 7z猜参数
import java.lang.Runtime;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;



/**
 * Divines information about the compression used for a resource that has been compressed with a
 * deflate-compatible algorithm. This implementation produces results that are valid within the
 * {@link DefaultDeflateCompatibilityWindow}.
 */
public class DefaultDeflateCompressionDiviner {

  /** The levels to try for each strategy, in the order to attempt them. */
  private static final Map<Integer, List<Integer>> LEVELS_BY_STRATEGY = getLevelsByStrategy();

  public  int num7z = 0;

  /**
   * A simple struct that contains a {@link MinimalZipEntry} describing a specific entry from a zip
   * archive along with an optional accompanying {@link JreDeflateParameters} describing the
   * original compression settings that were used to generate the compressed delivery in that entry.
   */
  public static class DivinationResult {
    /**
     * The {@link MinimalZipEntry} for the result; never null.
     */
    public final MinimalZipEntry minimalZipEntry;

    /**
     * The {@link JreDeflateParameters} for the result, possibly null. This value is only set if
     * {@link MinimalZipEntry#isDeflateCompressed()} is true <em>and</em> the compression settings
     * were successfully divined.
     */
    public final JreDeflateParameters divinedParameters;

    /**
     * Creates a new result with the specified fields.
     * @param minimalZipEntry the zip entry
     * @param divinedParameters the parameters
     */
    public DivinationResult(
        MinimalZipEntry minimalZipEntry, JreDeflateParameters divinedParameters) {
      if (minimalZipEntry == null) {
        throw new IllegalArgumentException("minimalZipEntry cannot be null");
      }
      this.minimalZipEntry = minimalZipEntry;
      this.divinedParameters = divinedParameters;
    }
  }

  /**
   * Load the specified archive and attempt to divine deflate parameters for all entries within.
   * @param archiveFile the archive file to work on
   * @return a list of results for each entry in the archive, in file order (not central directory
   * order). There is exactly one result per entry, regardless of whether or not that entry is
   * compressed. Callers can filter results by checking
   * {@link MinimalZipEntry#getCompressionMethod()} to see if the result is or is not compressed,
   * and by checking whether a non-null {@link JreDeflateParameters} was obtained.
   * @throws IOException if unable to read or parse the file
   * @see DivinationResult 
   */
  public List<DivinationResult> divineDeflateParameters(File archiveFile) throws IOException {
    List<DivinationResult> results = new ArrayList<>();
    for (MinimalZipEntry minimalZipEntry : MinimalZipArchive.listEntries(archiveFile)) {
      JreDeflateParameters divinedParameters = null;
      if (minimalZipEntry.isDeflateCompressed()) {
        MultiViewInputStreamFactory isFactory =
            new RandomAccessFileInputStreamFactory(
                archiveFile,
                minimalZipEntry.getFileOffsetOfCompressedData(),
                minimalZipEntry.getCompressedSize());

          if (minimalZipEntry.getCompressedSize() < (100 * 1024)) {
            try (InputStream is = isFactory.newStream()) {
              byte[] compressedBytes = new byte[(int) minimalZipEntry.getCompressedSize()];
              is.read(compressedBytes);
              divinedParameters =
                  divineDeflateParameters(new ByteArrayInputStreamFactory(compressedBytes));
            } catch (Exception ignore) {
              divinedParameters = null;
            }
          }else {
            divinedParameters = divineDeflateParameters(isFactory);
          }
          // if (divinedParameters == null) {
          //   divinedParameters = divineDeflateParameters_7z(isFactory, minimalZipEntry.getCompressedSize());
          // }
      }
      results.add(new DivinationResult(minimalZipEntry, divinedParameters));
    }



    return results;
  }

  /**
   * Returns an unmodifiable map whose keys are deflate strategies and whose values are the levels
   * that make sense to try with the corresponding strategy, in the recommended testing order.
   *
   * <ul>
   *   <li>For strategy 0, levels 1 through 9 (inclusive) are included.
   *   <li>For strategy 1, levels 4 through 9 (inclusive) are included. Levels 1, 2 and 3 are
   *       excluded because they behave the same under strategy 0.
   *   <li>For strategy 2, only level 1 is included because the level is ignored under strategy 2.
   * </ul>
   *
   * @return such a mapping
   */
  private static Map<Integer, List<Integer>> getLevelsByStrategy() {
    final Map<Integer, List<Integer>> levelsByStrategy = new HashMap<>();
    // The best order for the levels is simply the order of popularity in the world, which is
    // expected to be default (6), maximum compression (9), and fastest (1).
    // The rest of the levels are rarely encountered and their order is mostly irrelevant.
    levelsByStrategy.put(0, Collections.unmodifiableList(Arrays.asList(6, 9, 1, 4, 2, 3, 5, 7, 8)));
    levelsByStrategy.put(1, Collections.unmodifiableList(Arrays.asList(6, 9, 4, 5, 7, 8)));
    // Strategy 2 does not have the concept of levels, so vacuously call it 1.
    levelsByStrategy.put(2, Collections.singletonList(1));
    return Collections.unmodifiableMap(levelsByStrategy);
  }

  /**
   * Determines the original {@link JreDeflateParameters} that were used to compress a given piece
   * of deflated delivery.
   *
   * @param compressedDataInputStreamFactory a {@link MultiViewInputStreamFactory} that can provide
   *     multiple independent {@link InputStream} instances for the compressed delivery.
   * @return the parameters that can be used to replicate the compressed delivery in the {@link
   *     DefaultDeflateCompatibilityWindow}, if any; otherwise <code>null</code>. Note that <code>
   *     null</code> is also returned in the case of <em>corrupt</em> zip delivery since, by definition,
   *     it cannot be replicated via any combination of normal deflate parameters.
   * @throws IOException if there is a problem reading the delivery, i.e. if the file contents are
   *     changed while reading
   */
  public JreDeflateParameters divineDeflateParameters(
      MultiViewInputStreamFactory compressedDataInputStreamFactory) throws IOException {
    byte[] copyBuffer = new byte[32 * 1024];
    // Iterate over all relevant combinations of nowrap, strategy and level.
    for (boolean nowrap : new boolean[] {true, false}) {
      Inflater inflater = new Inflater(nowrap);
      Deflater deflater = new Deflater(0, nowrap);

      strategy_loop:
      for (int strategy : new int[] {0, 1, 2}) {
        deflater.setStrategy(strategy);
        for (int level : LEVELS_BY_STRATEGY.get(strategy)) {
          deflater.setLevel(level);
          inflater.reset();
          deflater.reset();
          try {
            if (matches(inflater, deflater, compressedDataInputStreamFactory, copyBuffer)) {
              end(inflater, deflater);
              return JreDeflateParameters.of(level, strategy, nowrap);
            }
          } catch (ZipException e) {
            // Parse error in input. The only possibilities are corruption or the wrong nowrap.
            // Skip all remaining levels and strategies.
            break strategy_loop;
          }
        }
      }
      end(inflater, deflater);
    }
    return null;
  }
  // 添加7z猜参数
  public JreDeflateParameters divineDeflateParameters_7z(
      MultiViewInputStreamFactory compressedDataInputStreamFactory, long compressed_size) throws IOException {

    // 添加7z猜参数
    byte[] copyBuffer_7z = new byte[(int)compressed_size];
    for (boolean nowrap : new boolean[] {true, false}) {
      Inflater inflater = new Inflater(nowrap);
      level_loop:
      for (int level = 9; level >= 1; level -= 2) {
        inflater.reset();
        try {
          if (matches_7z(inflater, compressedDataInputStreamFactory, copyBuffer_7z, level)) {
            inflater.end();
            // System.out.println("7z shoot");
            return JreDeflateParameters.of(level, 7, nowrap);
          }
        } catch (ZipException e) {
          // e.printStackTrace();
          break level_loop;
        }
      }
      inflater.end();
    }
    return null;
  }

  /**
   * Closes the (de)compressor and discards any unprocessed input. This method should be called when
   * the (de)compressor is no longer being used. Once this method is called, the behavior
   * De/Inflater is undefined.
   *
   * @see Inflater
   * @see Deflater
   */
  private static void end(Inflater inflater, Deflater deflater) {
    inflater.end();
    deflater.end();
  }

  /**
   * Checks whether the specified deflater will produce the same compressed delivery as the byte
   * stream.
   *
   * @param inflater the inflater for uncompressing the stream
   * @param deflater the deflater for recompressing the output of the inflater
   * @param copyBuffer buffer to use for copying bytes between the inflater and the deflater
   * @return true if the specified deflater reproduces the bytes in compressedDataIn, otherwise
   *     false
   * @throws IOException if anything goes wrong; in particular, {@link ZipException} is thrown if
   *     there is a problem parsing compressedDataIn
   */
  private boolean matches(
      Inflater inflater,
      Deflater deflater,
      MultiViewInputStreamFactory compressedDataInputStreamFactory,
      byte[] copyBuffer)
      throws IOException {

    try (MatchingOutputStream matcher =
            new MatchingOutputStream(
                compressedDataInputStreamFactory.newStream(), copyBuffer.length);
        InflaterInputStream inflaterIn =
            new InflaterInputStream(
                compressedDataInputStreamFactory.newStream(), inflater, copyBuffer.length);
        DeflaterOutputStream out = new DeflaterOutputStream(matcher, deflater, copyBuffer.length)) {
      int numRead;
      while ((numRead = inflaterIn.read(copyBuffer)) >= 0) {
        out.write(copyBuffer, 0, numRead);
      }
      // When done, all bytes have been successfully recompressed. For sanity, check that
      // the matcher has consumed the same number of bytes and arrived at EOF as well.
      out.finish();
      out.flush();
      matcher.expectEof();
      // At this point the delivery in the compressed output stream was a perfect match for the
      // delivery in the compressed input stream; the answer has been found.
      return true;
    } catch (MismatchException e) {
      // Fast-fail case when the compressed output stream doesn't match the compressed input
      // stream. These are not the parameters you're looking for!
      return false;
    }
  }

  // 获取压缩区大小
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

  // 获取压缩区内容
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
      // e.printStackTrace();
      return null;
    }
  }

  private boolean matches_7z(
      Inflater inflater,
      MultiViewInputStreamFactory compressedDataInputStreamFactory,
      byte[] copyBuffer,
      int level)
      throws IOException {

    try (MatchingOutputStream matcher =
            new MatchingOutputStream(
                compressedDataInputStreamFactory.newStream(), copyBuffer.length);
          InflaterInputStream inflaterIn =
            new InflaterInputStream(
                compressedDataInputStreamFactory.newStream(), inflater, copyBuffer.length)) {
      int numRead;

      // 一次性解压出来
      String input_file = "./temp";
      try {
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(input_file));
        while ((numRead = inflaterIn.read(copyBuffer)) >= 0) {
          // System.out.println(numRead);
          outputStream.write(copyBuffer, 0, numRead);
        }
        outputStream.close();
      } catch (Exception e) {
        // e.printStackTrace();
      }

        String temp_zip_file = "./temp.zip";
        // 调用7zz命令行压缩
        try {
          String homePath = System.getProperty("user.dir");
          String zipPath = homePath + "/archive-patcher-7z/7zip/7zz a ";
          String cmd_7z = zipPath + temp_zip_file + " " + input_file + " -mx" + Integer.toString(level);
          // String cmd_7z = "/home/jbw/ota/basic_code/7z2201-linux-x64/7zz a " + temp_zip_file + " " + input_file + " -mx9";
          Runtime run = Runtime.getRuntime();
          Process ps = run.exec(cmd_7z);
          ps.waitFor();
        } catch (Exception e) {
          e.printStackTrace();
        }
        // 读取 temp.zip，比对压缩数据区
        // long fileSize = new File(temp_zip_file).length();
        // byte[] allBytes = new byte[(int) fileSize];
        int compressed_7z_size = get7zCompressedSize(temp_zip_file);
        if (compressed_7z_size == 0) {
          File inputfile = new File(input_file); 
          inputfile.delete();
          File zipfile = new File(temp_zip_file); 
          zipfile.delete();
          // throw new MismatchException("Data does not match");
          return false;
        }
        byte [] compress_7z_data = get7zCompressedData(temp_zip_file, compressed_7z_size);

        int maxToRead = Math.min(copyBuffer.length, compressed_7z_size);
        byte [] match_buffer;
        match_buffer = matcher.getBuffer(maxToRead);
        for (int i = 0; i < maxToRead; i++) {
          if (match_buffer[i] != compress_7z_data[i]) {
            // System.out.println(i);
            File zipfile = new File(input_file); 
            zipfile.delete();
            // throw new MismatchException("Data does not match");
            return false;
          }
        }
        File zipfile = new File(input_file); 
        zipfile.delete();
        num7z += 1;
        return true;
    } catch (MismatchException e) {
    // Fast-fail case when the compressed output stream doesn't match the compressed input
    // stream. These are not the parameters you're looking for!
      return false;
    }
  }


}