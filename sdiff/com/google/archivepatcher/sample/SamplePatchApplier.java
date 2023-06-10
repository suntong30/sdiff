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

package com.google.archivepatcher.sample;

import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import java.io.InputStream; 
import java.io.InputStreamReader;
import java.io.BufferedReader; 

/** Apply a patch; args are old file path, patch file path, and new file path. */
public class SamplePatchApplier {
  public static void main(String... args) throws Exception {
    File oldFile = new File(args[0]); // must be a zip archive
    File patch = new File(args[1]);
    String compressPath = args[5];
    String compressParameters = args[6];
    String patchPath = patch.getAbsolutePath();
    String cmd = compressPath + " " + compressParameters + " " + patchPath;
    System.out.println(cmd);
    int threadNum = Integer.parseInt(args[7]);
    long start, end;
    start = System.currentTimeMillis();
    Process process = Runtime.getRuntime().exec(cmd);      //执行一个系统命令 
    InputStream fis = process.getErrorStream(); 
    BufferedReader br =  new BufferedReader( new InputStreamReader(fis)); 
    String line = null;
    while ((line = br.readLine()) !=  null) {
      System.out.println(line);
    }
    end = System.currentTimeMillis();
    System.out.println("decompress time : " + (end - start));
    
    String uncomp_patchPath = args[1].substring(0,args[1].lastIndexOf("."));
    File uncomp_patch = new File(uncomp_patchPath);
    try (FileInputStream patchIn = new FileInputStream(uncomp_patch); // 被解压的更新包
        FileOutputStream newFileOut = new FileOutputStream(args[2])) {
      new FileByFileV1DeltaApplier().applyDelta(oldFile, patchIn, newFileOut, args[3], args[4], threadNum);
    } finally {
      uncomp_patch.delete();
    }
  }
}
