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

import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;
import com.google.archivepatcher.generator.TempFileHolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream; 
import java.io.InputStreamReader;
import java.io.BufferedReader; 

/** Generate a patch; args are old file path, new file path, and patch file path. */
public class SamplePatchGenerator {
  public static void main(String... args) throws Exception {
    File oldFile = new File(args[0]); // must be a zip archive
    File newFile = new File(args[1]); // must be a zip archive
    File uncomp_Patch = new File(args[2]);
    long start, end;
    try (FileOutputStream patchOut = new FileOutputStream(uncomp_Patch);) {
      new FileByFileV1DeltaGenerator().generateDelta(oldFile, newFile, patchOut, args[3], args[4]);
    } finally {
      String compressPath = args[5];
      String compressParameters = args[6];
      String uncomp_PatchPath = uncomp_Patch.getAbsolutePath();
      String cmd = compressPath + " " + compressParameters + " " + uncomp_PatchPath;
      System.out.println(cmd);

      start = System.currentTimeMillis();
      Process process = Runtime.getRuntime().exec(cmd);      //执行一个系统命令
      InputStream fis = process.getErrorStream();
      BufferedReader br =  new BufferedReader( new InputStreamReader(fis));
      String line = null;
      while ((line = br.readLine()) !=  null) {
        System.out.println(line);
      }
      end = System.currentTimeMillis();
      System.out.println("second compression time :" + (end - start));
    }
  }
}
