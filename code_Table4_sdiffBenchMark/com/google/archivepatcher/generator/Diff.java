package com.google.archivepatcher.generator;
import java.io.BufferedReader; 
import java.io.IOException; 
import java.io.InputStream; 
import java.io.InputStreamReader; 
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Diff { 
        public void deltaGenerate(File oldBlob, File newBlob, File deltaOut, String diffPath, String parameters) throws IOException{
          String oldBlobPath = oldBlob.getAbsolutePath();
          String newBlobPath = newBlob.getAbsolutePath();
          String deltaOutPath = deltaOut.getAbsolutePath();
          String cmd = diffPath + " " + parameters + " " + oldBlobPath + " " + newBlobPath + " " + deltaOutPath;
          System.out.println(cmd);
          Process process = Runtime.getRuntime().exec(cmd);      //执行一个系统命令 
          InputStream fis = process.getInputStream(); 
          BufferedReader br =  new BufferedReader( new InputStreamReader(fis)); 
          String line = null;
          while ((line = br.readLine()) !=  null) { 
            System.out.println(line);
          }
          return ;
        }
}