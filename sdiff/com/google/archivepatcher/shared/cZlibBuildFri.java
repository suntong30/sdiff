package com.google.archivepatcher.shared;
import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

public class cZlibBuildFri {
    public interface LibraryDiff extends Library {
        LibraryDiff LIBRARY_diff = Native.load("./czlib/czlib.so", LibraryDiff.class);
        /**
         * 接口中只需要定义你要用到的函数或者公共变量，不需要的可以不定义
         * 映射libadd.so里面的函数，注意类型要匹配
         */
        boolean _Z7processPKcS0_PlS1_ib(String blobName,String friBlobName,long pos[],long length[],int count,boolean reverse);
        //_Z7processPKcS0_PlS1_ib
    }
    public static <T> boolean  buildFriBlob
            (List<TypedRange<T>> rangesToUncompress, // 对差分友好文件的解压计划，即待解压文件的偏移和长度
             File file, // 待解压的文件
             File friFile){
        int count = rangesToUncompress.size();
        long pos [] = new long[count];
        long length [] = new long[count];
        for(int i = 0 ; i< count ;i++){
            pos[i]= rangesToUncompress.get(i).getOffset();
            length[i]= rangesToUncompress.get(i).getLength();
        }
        return LibraryDiff.LIBRARY_diff._Z7processPKcS0_PlS1_ib(file.getAbsolutePath(),friFile.getAbsolutePath(),pos,length,count,false);
    }
    public static <T> boolean  buildFriBlobGenerate
            (long pos [],
             long length [],
             int count ,// 对差分友好文件的解压计划，即待解压文件的偏移和长度
             File file, // 待解压的文件
             File friFile){
        System.out.println("use c++ zlib generate friBlob");
        return LibraryDiff.LIBRARY_diff._Z7processPKcS0_PlS1_ib(file.getAbsolutePath(),friFile.getAbsolutePath(),pos,length,count,true);
    }

}
