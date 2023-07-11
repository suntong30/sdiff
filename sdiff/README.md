sdiff is desgined for archive formats. Now it supports APK and zip files.
# Usage

## Prerequisite
1. Obtain `zstd` ELF by compiling [zstd](../zstd-dev/) using `make` command.
2. Obtain `hdiffz` and `hpatchz` from  [other algorithms/HDiffPatch](../other%20algorithms/) by `git submodule` and `make`.

## diff

- diff_program_path:The path to the program that actually executes the difference calculation 
- diff_program_parameter:parameters passed to the program
- secondCompress_program_path:Path to a program that performs secondary compression on diff file (We can use the ELF from [other algorithms](../other%20algorithms/), i.e., `hdiffz` for HDiffPatch and `bsdiff` for BSDIFF )
- secondCompress_program_parameter:parameters passed to the program

```shell
java -classpath .:jna-5.12.1.jar com.google.archivepatcher.sample.SamplePatchGenerator old.apk new.apk diff_file diff_program_path diff_program_parameter secondCompress_program_path secondCompress_program_parameter
//example
//java -classpath .:jna-5.12.1.jar com.google.archivepatcher.sample.SamplePatchGenerator old.apk new.apk diff_file ./hdiffz " -f -d " ./zstd " --ultra - 21 "
```

## patch

- patch_program_path:The path to the program that actually performs the patch restore
- patch_program_parameter:parameters passed to the program
- deCompress_program_path:Path to the program to perform decompression on diff file (We can use the ELF from [other algorithms](../other%20algorithms/), i.e., `hpatchz` for HDiffPatch and `bspatch` for BSDIFF )
- deCompress_program_parameter:parameters passed to the program
- thead_num:The number of threads used for the restore process

```
java -classpath .:jna-5.12.1.jar com.goole.archivepatcher.sample.SamplePatchGenerator old.apk diff_file_compressed patch.apk patch_program_path patch_program_parameter deCompress_program_path deCompress_program_parameter thead_num
//example
//java -classpath .:jna-5.12.1.jar com.goole.archivepatcher.sample.SamplePatchGenerator old.apk diff_file_compressed patch.apk ./hpatchz " -f " ./zstd " -k -d " 1 
```

## run script

```shell
python3 ap-x-diff_stack_bar.py excel_path data_path csv_file_name start_no end_no 
```

```
/data_path
----/C10000
	----1.APK
	----2.APK
	----3.APK
----/C10002
	----1.APK
	----2.APK
	----3.APK
```

