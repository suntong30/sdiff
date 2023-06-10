#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "zlib.h"
#include <iostream>
#include <fstream>
#define CHUNK 32768 
using namespace std;


void copyBytes(FILE* input, FILE* output, int length) {
    char buffer[CHUNK]; // 创建缓冲区
    while (length > 0) {
        int bytesToRead = min(length, CHUNK); // 计算本次要读取的字节数
        fread(buffer, sizeof(char), bytesToRead, input); // 从输入文件中读取字节到缓冲区
        fwrite(buffer, sizeof(char), bytesToRead, output); // 将缓冲区中的字节写入到输出文件中
        length -= bytesToRead; // 更新剩余要输出的字节数
    }
}


bool  decompressBytes(FILE* input, FILE* output, int compressedStart, int compressedLength ,long & allUncom) {

    unsigned char inBuffer[CHUNK];
    unsigned char outBuffer[CHUNK];

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.opaque = Z_NULL;
    stream.avail_in = 0;
    stream.next_in = Z_NULL;
    inflateInit2(&stream,-MAX_WBITS);
    fseek(input, compressedStart, SEEK_SET);
    while (compressedLength > 0) {
        int bytesToRead = min(compressedLength, CHUNK);
        stream.avail_in = fread(inBuffer, 1, bytesToRead, input);
        stream.next_in = inBuffer;
        do {
            stream.avail_out = CHUNK;
            stream.next_out = outBuffer;
            int ret = inflate(&stream, Z_NO_FLUSH);
            switch (ret) {
            case Z_NEED_DICT:
                ret = Z_DATA_ERROR;     
            case Z_STREAM_ERROR:
            case Z_DATA_ERROR:
            case Z_MEM_ERROR:
                (void)inflateEnd(&stream);
                return false;
            }
            int have = CHUNK - stream.avail_out;
            allUncom += have;
            fwrite(outBuffer, 1, have, output);
        } while (stream.avail_out == 0);
        compressedLength -= bytesToRead;
    }
    inflateEnd(&stream);
    return true;
}

bool process(const char * blobName,const char * friBlobName,long pos[],long length[],int count,bool reverse){
    //printf("old apk name: %s \n temp fripath : %s\n",blobName,friBlobName);
    FILE * blob = fopen(blobName, "rb");
    FILE * friBlob = fopen(friBlobName, "ab");
    int curPos = 0;
    bool flag = true;
    for(int i = 0;i<count;i++){
        if(pos[i] > curPos){
            int len = pos[i] - curPos;
            //从blob输出len个字节到friBlob
            copyBytes(blob,friBlob,len);
            curPos = pos[i];
        }
        //解压缩length[i]个字节到friBlob
        long friBlobPos = ftell(friBlob);
        long curUncom = 0 ;
        if(!decompressBytes(blob,friBlob,pos[i],length[i],curUncom)){
            flag  = false;
            break;
        }
        curPos = curPos + length[i];
        if(reverse){
            pos[i] = friBlobPos;
            length [i] = curUncom;
        }
    }
    //找到文件结尾，copy剩余的字符
    fseek(blob,0,SEEK_END);
    long  fileSize = ftell(blob);
    if(fileSize > curPos&& flag){
        int len = fileSize - curPos;
        copyBytes(blob,friBlob,len);
    }
    int firSize = ftell(friBlob);
    fclose(blob);
    fclose(friBlob);
    //printf("run %s c++  zlib finish with %d bytes\n",flag?"success":"wrong",firSize);
    return flag;
}