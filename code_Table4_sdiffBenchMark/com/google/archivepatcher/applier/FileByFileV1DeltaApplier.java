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

import com.google.archivepatcher.shared.DeltaFriendlyFile;
import com.google.archivepatcher.shared.RandomAccessFileOutputStream;
import com.google.archivepatcher.shared.cZlibBuildFri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Applies V1 patches.
 */
public class FileByFileV1DeltaApplier implements DeltaApplier {

    /**
     * Default size of the buffer to use for copying bytes in the recompression stream.
     */
    private static final int DEFAULT_COPY_BUFFER_SIZE = 32768;

    /**
     * The temp directory to use.
     */
    private final File tempDir;

    /**
     * Creates a new delta applier that will use the default temp directory for working files. This is
     * equivalent to calling {@link #FileByFileV1DeltaApplier(File)} with a <code>null</code> file
     * argument.
     */
    public FileByFileV1DeltaApplier() {
        this(null);
    }

    /**
     * Creates a new delta applier that will use the specified temp directory.
     *
     * @param tempDir a temp directory where the delta-friendly old blob can be written during the
     *                patch application process; if null, the system's default temporary directory is used
     */
    public FileByFileV1DeltaApplier(File tempDir) {
        if (tempDir == null) {
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        this.tempDir = tempDir;
    }

    @Override
    public void applyDelta(File oldBlob, InputStream deltaIn, OutputStream newBlobOut, String applyPath, String parameters)
            throws IOException, InterruptedException {
        if (!tempDir.exists()) {
            // Be nice, try to create the temp directory. Don't bother to check return value as the code
            // will fail when it tries to create the file in a few more lines anyways.
            tempDir.mkdirs();
        }
        File tempFile = File.createTempFile("gfbfv1", "old", tempDir);
        try {
            applyDeltaInternal(oldBlob, tempFile, deltaIn, newBlobOut, applyPath, parameters, 1);
        } finally {
            tempFile.delete();
        }
    }

    public void applyDelta(File oldBlob, InputStream deltaIn, OutputStream newBlobOut, String applyPath, String parameters, int threadNum)
            throws IOException, InterruptedException {
        //多线程调用
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempFile = File.createTempFile("gfbfv1", "old", tempDir);
        try {
            applyDeltaInternal(oldBlob, tempFile, deltaIn, newBlobOut, applyPath, parameters, threadNum);
        } finally {
            tempFile.delete();
        }
    }

    /**
     * Does the work for applying a delta.
     *
     * @param oldBlob              the old blob
     * @param deltaFriendlyOldBlob the location in which to store the delta-friendly old blob
     * @param deltaIn              the patch stream
     * @param newBlobOut           the stream to write the new blob to after applying the delta
     * @throws IOException if anything goes wrong
     */
    private void applyDeltaInternal(
            File oldBlob, File deltaFriendlyOldBlob, InputStream deltaIn, OutputStream newBlobOut, String applyPath, String parameters, int threadNum)
            throws IOException, InterruptedException {
        // First, read the patch plan from the patch stream.
        PatchReader patchReader = new PatchReader();
        // 解析差分包：plan = oldFileUncompressionPlan, deltaFriendlyOldFileSize, deltaFriendlyNewFileRecompressionPlan, deltaDescriptors
        PatchApplyPlan plan = patchReader.readPatchApplyPlan(deltaIn); // 读取了除差分包以外的所有信息
        long start, end;
        start = System.currentTimeMillis();
        writeDeltaFriendlyOldBlob(plan, oldBlob, deltaFriendlyOldBlob); // 生成旧版本的差分友好文件
        System.out.println("oldFriBlob size : "+deltaFriendlyOldBlob.length());
        end = System.currentTimeMillis();
        System.out.println("generate old delta-friendly file : " + (end - start));
        // Apply the delta. In v1 there is always exactly one delta descriptor, it is bsdiff, and it
        // takes up the rest of the patch stream - so there is no need to examine the list of
        // DeltaDescriptors in the patch at all.
        long deltaLength = plan.getDeltaDescriptors().get(0).getDeltaLength(); // 差分包长度
        // Don't close this stream, as it is just a limiting wrapper.
        @SuppressWarnings("resource")
        LimitedInputStream limitedDeltaIn = new LimitedInputStream(deltaIn, deltaLength);
        // Don't close this stream, as it would close the underlying OutputStream (that we don't own).
        if (threadNum > 1) {
            //使用多线程
            System.out.println("Using thread num : " + threadNum);
            MThreadApply mThreadApply = new MThreadApply();
            mThreadApply.deltaApply(deltaFriendlyOldBlob, limitedDeltaIn, applyPath, parameters, newBlobOut, threadNum, plan.getDeltaFriendlyNewFileRecompressionPlan());
        } else {
            //使用原始版本
            PartiallyCompressingOutputStream recompressingNewBlobOut = // 此处，设置新版本的压缩流
                    new PartiallyCompressingOutputStream(
                            plan.getDeltaFriendlyNewFileRecompressionPlan(),
                            newBlobOut,
                            DEFAULT_COPY_BUFFER_SIZE);
            Apply apply = new Apply();
            apply.deltaApply(deltaFriendlyOldBlob, limitedDeltaIn, recompressingNewBlobOut, applyPath, parameters); // 在差分友好文件层面，应用差分包，生成新文件的差分友好文件

            /* 如果使用c++将新文件的差分友好文件重压缩，需要输出 差分友好文件 和 重压缩计划 */
            recompressingNewBlobOut.flush(); // 将新文件的差分友好文件重压缩
        }
    }

    /**
     * Writes the delta-friendly old blob to temporary storage.
     *
     * @param plan                 the plan to use for uncompressing
     * @param oldBlob              the blob to turn into a delta-friendly blob
     * @param deltaFriendlyOldBlob where to write the blob
     * @throws IOException if anything goes wrong
     */
    private void writeDeltaFriendlyOldBlob(
            PatchApplyPlan plan, File oldBlob, File deltaFriendlyOldBlob) throws IOException {
//        RandomAccessFileOutputStream deltaFriendlyOldFileOut = null;
//        try {
//            deltaFriendlyOldFileOut =
//                    new RandomAccessFileOutputStream(
//                            deltaFriendlyOldBlob, plan.getDeltaFriendlyOldFileSize());
//            DeltaFriendlyFile.generateDeltaFriendlyFile(
//                    plan.getOldFileUncompressionPlan(),
//                    oldBlob,
//                    deltaFriendlyOldFileOut,
//                    false,
//                    DEFAULT_COPY_BUFFER_SIZE);
//        } finally {
//            try {
//                deltaFriendlyOldFileOut.close();
//            } catch (Exception ignored) {
//                // Nothing
//            }
//        }
        if( cZlibBuildFri.buildFriBlob(plan.getOldFileUncompressionPlan(),oldBlob,deltaFriendlyOldBlob)){
            System.out.println("c++ success");
        }else{
            System.out.println("c++ fail");
        }
    }

    /**
     * Return an instance of a {@link DeltaApplier} suitable for applying the deltas within the patch
     * stream.
     * @return the applier
     */
    // Visible for testing only
}
