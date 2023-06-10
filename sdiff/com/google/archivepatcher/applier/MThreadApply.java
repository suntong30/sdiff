package com.google.archivepatcher.applier;

import java.io.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.google.archivepatcher.generator.TempFileHolder;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

public class MThreadApply {
    public static final String mtTempFileName = "newPatchFile_sub";

    public void deltaApply(File oldBlob, InputStream deltaIn, String applyPath, String parameters, OutputStream patch_file,
                           int _threadNum, List<TypedRange<JreDeflateParameters>> fullList) throws IOException, InterruptedException {
        String oldBlobPath = oldBlob.getAbsolutePath();
        TempFileHolder newdata = new TempFileHolder();
        TempFileHolder tmpdiff = new TempFileHolder();
        String newPath = newdata.file.getAbsolutePath();
        String tmpdiffPath = tmpdiff.file.getAbsolutePath();
        /* 读取diffPatch文件 */
        FileOutputStream fos = new FileOutputStream(tmpdiffPath); // 去除ap文件头的差分包，fos即
        int len;
        byte[] tmpdata = new byte[1024];
        while ((len = deltaIn.read(tmpdata)) > 0) {
            fos.write(tmpdata, 0, len);
        }
        fos.close();
        long start, end;
        start = System.currentTimeMillis();
        String cmd = applyPath + " " + parameters + " " + oldBlobPath + " " + tmpdiffPath + " " + newPath;
        System.out.println(cmd);
        Process process = Runtime.getRuntime().exec(cmd);      //执行一个系统命令
        InputStream fis = process.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line;
        while ((line = br.readLine()) != null) {
            System.out.println(line);
        }
        end = System.currentTimeMillis();
        System.out.println("generate new delta-friendly file time : " + (end - start));
        // 通过差分算法，生成了新文件的差分友好文件。进行新文件的差分友好文件重压缩
        start = System.currentTimeMillis();
        int compress7zScore = 0;
        for (TypedRange<JreDeflateParameters> jreDeflateParametersTypedRange : fullList) {
            int strategy7z = jreDeflateParametersTypedRange.getMetadata().strategy;
            compress7zScore += strategy7z == 7 ? jreDeflateParametersTypedRange.getLength() : 0;
        }
        // compressRange 和线程数量取一个较小值
        int threadNum = Math.min(_threadNum, fullList.size());
        MTlistSplit mTlistSplit = new MTlistSplit(fullList,compress7zScore,threadNum);
        // lastProcessPos = 上一个分块处理到的pos
        // lastSubPos = 上一次切分list处理到位置
        long lastProcessPos = 0;
        CountDownLatch latch = new CountDownLatch(threadNum);
        for (int i = 1; i <= threadNum; i++) {
            List<TypedRange<JreDeflateParameters>> sublist;
            sublist = mTlistSplit.getSubLis(i-1);
            long currentPartEndPos = sublist.get(sublist.size() - 1).getOffset() + sublist.get(sublist.size() - 1).getLength();
            MTCompressWorker mt = new MTCompressWorker(newPath, lastProcessPos, currentPartEndPos - lastProcessPos, sublist, i, threadNum, latch);
            lastProcessPos = currentPartEndPos;
            mt.start();
        }
        latch.await();
        //System.out.println("All threads finish ,start merge files ");
        BufferedOutputStream bos = new BufferedOutputStream(patch_file);
        int haveWrite = 0;
        for (int i = 1; i <= threadNum; i++) {
            File needMergeFile = new File(mtTempFileName + i);
            haveWrite += readFile(needMergeFile, bos);
            needMergeFile.delete();
        }
        System.out.println("Merge files finish with:  " + haveWrite + " bytes");
        bos.flush();
        bos.close();
        newdata.close();
        tmpdiff.close();
        end = System.currentTimeMillis();
        System.out.println("zip time : " + 0);
        System.out.println("z7 compress time : " + 0);
        System.out.println("z7 io time : " + 0);
        System.out.println("z7 extra io time : " + 0);
        System.out.println("Recompress new delta-friendly file time : " + (end - start));

    }

    public static int readFile(File file, BufferedOutputStream bos) {
        BufferedInputStream bis;
        int allwrite = 0;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            int len;
            byte[] bytes = new byte[1024];
            while ((len = bis.read(bytes)) != -1) {
                allwrite += len;
                bos.write(bytes, 0, len);
            }
            bos.flush();
            bis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return allwrite;
    }
}
