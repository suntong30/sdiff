package com.google.archivepatcher.applier;

import  java.io.*;

import com.google.archivepatcher.generator.TempFileHolder;

public class Apply { 
        public void deltaApply(File oldBlob, InputStream deltaIn, PartiallyCompressingOutputStream newBlobOut, String applyPath, String parameters) throws IOException{
          String oldBlobPath = oldBlob.getAbsolutePath();

          TempFileHolder newdata = new TempFileHolder();
          TempFileHolder tmpdiff = new TempFileHolder();
          String newPath = newdata.file.getAbsolutePath();
          String tmpdiffPath = tmpdiff.file.getAbsolutePath();
          /* 读取diffPatch文件 */
          FileOutputStream fos = new FileOutputStream(tmpdiffPath); // 去除ap文件头的差分包，fos即
          int len;
          byte[] tmpdata = new byte[1024];
          while((len = deltaIn.read(tmpdata)) > 0 ) {
              fos.write(tmpdata, 0, len);
          }
          
          long start, end;
          start = System.currentTimeMillis();
          String cmd = applyPath + " " + parameters + " " + oldBlobPath + " " + tmpdiffPath + " " + newPath;
          System.out.println(cmd);
          Process process = Runtime.getRuntime().exec(cmd);      //执行一个系统命令
          InputStream fis = process.getErrorStream(); 
          BufferedReader br =  new BufferedReader( new InputStreamReader(fis)); 
          String line = null;
          while ((line = br.readLine()) !=  null) { 
            System.out.println(line);
          }
          end = System.currentTimeMillis();
          System.out.println("generate new delta-friendly file time : " + (end - start));
          // 通过差分算法，生成了新文件的差分友好文件。进行新文件的差分友好文件重压缩
          start = System.currentTimeMillis();
          FileInputStream fio = new FileInputStream(newPath); // fio为新版本差分友好文件输入流
          tmpdata = new byte[1024];
          len = 0 ;
          while((len = fio.read(tmpdata)) > 0) {
              newBlobOut.write(tmpdata,0,len);
          }
          fio.close();
          fos.close();
          newdata.close();
          tmpdiff.close();
          end = System.currentTimeMillis();
          System.out.println("zip time : " + newBlobOut.get_zip_time());
          System.out.println("z7 compress time : " + newBlobOut.get_z7_compress_time());
          System.out.println("z7 io time : " + newBlobOut.get_z7_io_time());
          System.out.println("z7 extra io time : " + newBlobOut.get_z7_extra_io_time());
          System.out.println("recompress new delta-friendly file time : " + (end - start));
          return ;
        }
}