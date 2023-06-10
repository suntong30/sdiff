package com.google.archivepatcher.applier;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

import java.util.List;

public class MTlistSplit {
    List<TypedRange<JreDeflateParameters>> CompressionRanges;
    int compress7zScore;
    int listLength;
    int threadNum;
    int haveSplitPos = 0;
    boolean isAverage;
    int averageSubLen;


    MTlistSplit(List<TypedRange<JreDeflateParameters>> CompressionRanges, int compress7zScore, int threadNum) {
        this.CompressionRanges = CompressionRanges;
        this.compress7zScore = compress7zScore;
        this.listLength = CompressionRanges.size();
        this.threadNum = threadNum;
        this.isAverage = compress7zScore == 0;
        this.averageSubLen = isAverage ? this.listLength / this.threadNum + 1 : 0;
        System.out.println("7z Num : " + compress7zScore + "\n Use " + (isAverage ? "average " : "7zNum ") + " Split");

    }

    List<TypedRange<JreDeflateParameters>> getSubLis(int no) {
        if (isAverage) {//平均划分
            if (no == threadNum - 1) {
                return CompressionRanges.subList(no * averageSubLen, listLength);
            } else {
                return CompressionRanges.subList(no * averageSubLen, (no + 1) * averageSubLen);
            }
        } else {//根据7z数量进行分配
            if (no == threadNum - 1) {
                return CompressionRanges.subList(haveSplitPos, listLength);
            } else {
                int need7zSCore = compress7zScore / (threadNum - no);
                int cur7zScore = 0;
                int endPos = 0;
                for (int i = haveSplitPos; i < listLength; i++) {
                    cur7zScore += CompressionRanges.get(i).getMetadata().strategy == 7 ? CompressionRanges.get(i).getLength() : 0;
                    if (cur7zScore >= need7zSCore) {
                        compress7zScore -= cur7zScore;
                        endPos = i + 1;
                        break;
                    }
                }
                if (endPos == haveSplitPos + 1) {//线程数量多于7zcompress
                    endPos++;
                }
                int splitPosBackup = haveSplitPos;
                haveSplitPos = endPos;
                return CompressionRanges.subList(splitPosBackup, endPos);
            }
        }
    }

}
