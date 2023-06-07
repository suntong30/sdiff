package com.google.archivepatcher.applier;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.google.archivepatcher.shared.DeltaFriendlyFile.DEFAULT_COPY_BUFFER_SIZE;

public class MTCompressWorker extends Thread {
    RandomAccessFile randomFile;
    List<TypedRange<JreDeflateParameters>> subCompressionRanges;
    int processNum;
    String subFileName;
    CountDownLatch latch;

    long chunkOffset = 0;

    MTCompressWorker(String newBlob, long startIndex, long length,
                     List<TypedRange<JreDeflateParameters>> subCompressionRanges, int no, int threadNum, CountDownLatch latch) {
        try {
            //System.out.println(this.getName() + " init at:" + new Date());
            this.subFileName = MThreadApply.mtTempFileName + no;
            randomFile = new RandomAccessFile(newBlob, "r");
            randomFile.seek(startIndex);
            this.processNum = no == threadNum ? (int) (randomFile.length() - startIndex) : (int) length;
            this.subCompressionRanges = subCompressionRanges;
            this.chunkOffset = startIndex;
            this.latch = latch;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            FileOutputStream subPatchFile = new FileOutputStream(subFileName);
            PartiallyCompressingOutputStream recompressingNewBlobOut =
                    new PartiallyCompressingOutputStream(
                            subCompressionRanges,
                            subPatchFile,
                            DEFAULT_COPY_BUFFER_SIZE,
                            this.getName(),
                            chunkOffset);
            byte[] tmpdata = new byte[1024];
            int len;
            int haveProcess = 0;
            while (haveProcess < processNum && (len = randomFile.read(tmpdata)) > 0) {
                len = Math.min(len, processNum - haveProcess);
                haveProcess += len;
                recompressingNewBlobOut.write(tmpdata, 0, len);
            }
            recompressingNewBlobOut.flush();
            //System.out.println(this.getName() + " finish at " + new Date());
            latch.countDown();
        } catch (IOException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }


    }
}
