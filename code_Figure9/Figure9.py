import csv
import os
import subprocess
import time

import pandas as pd



def an_x3add(csv_path):
    # csvname=‘ins_info.csv’
    if (not os.path.exists(csv_path)) :
        return 0
    if os.path.getsize(csv_path)==0:
        os.remove(csv_path)
        return 0
    df = pd.read_csv(csv_path)
    add_bytes = 0
    if len(df) == 0:
        return 0
    for i in range(len(df)):
        add_byte = df.loc[i].values[2]
        add_byte = int(add_byte)
        add_bytes += add_byte
    os.remove(csv_path)
    return add_bytes

def an_bsAddCopy(csv_path):
    if (not os.path.exists(csv_path)) :
        return 0,0,0,0,0
    if os.path.getsize(csv_path)==0:
        os.remove(csv_path)
        return 0,0,0,0,0
    df = pd.read_csv(csv_path)
    add_bytes ,cpy_bytes = 0,0
    ctrl_size,diff_size = 0,0
    for i in range(len(df)):
        if i == 0 :
            ctrl_size = df.loc[i].values[0]
            diff_size = df.loc[i].values[1]
            ctrl_size = int(ctrl_size)
            diff_size = int(diff_size)
        else:
            add_byte = df.loc[i].values[1]
            cpy_byte = df.loc[i].values[0]
            add_byte = int(add_byte)
            cpy_byte = int(cpy_byte)
            add_bytes += add_byte
            cpy_bytes += cpy_byte
    os.remove(csv_path)
    return add_bytes,cpy_bytes,len(df)-1,ctrl_size,diff_size



def normalRun(stmt):
    status, _ = subprocess.getstatusoutput(stmt)
    if status != 0 :
        print("run error")
        print(stmt)


def do_bs(old,new,binpath):
    diff = 0
    if not (os.path.exists(old) and os.path.exists(new)):
        print("{} or {} missing".format(old, new))
        return 0
    normalRun("{}/bsdiff {} {} diff".format(binpath, old, new))
    if not os.path.exists("diff"):
        return 0
    diff =os.path.getsize("diff")
    normalRun("{}/bspatch {} patch diff".format(binpath,old))
    if os.path.exists("diff"):
        os.remove("diff")
    if os.path.exists("patch"):
        os.remove("patch")
    return diff

def do_x3(old,new,binpath):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("{} or {} missing".format(old, new))
        return 0
    normalRun("{} -e -s {} {} diff".format(binpath, old, new))
    normalRun("{} -d -s {} diff patch".format(binpath, old))
    os.system('rm -rf diff patch')
    


def run_bm(excel_path, datapath, start_no, end_no):
    apks = pd.read_excel(excel_path)
    i, app_no = start_no, 1
    csvname = 'x3_bs_bytesInfo.csv'
    f = open(csvname, 'a', encoding='utf-8', newline='')
    csv_write = csv.writer(f)
    if os.path.getsize(csvname)==0:
        csv_write.writerow(["appid","version","x3_add","x3_copy&run","bs_add","bs_copy","bs_insNum","bs_ctrlSize","bs_diffSize","bs_extraSize"])
    f_hd = open ('hd_bytesInfo.csv','a',encoding='utf-8', newline='')
    csv_write_hd = csv.writer(f_hd)
    if os.path.getsize('hd_bytesInfo.csv')==0:
        csv_write_hd.writerow(["copy_bytes,add_bytes,ins_num,ctrlData_size,subData_size,compressNewdata_size"])
    
    
    x3_path = "./xdelta3/xdelta3 "
    bs_path = "./bsdiff"
    while i < end_no:
        appid = apks.iloc[i].at["appid"]
        print("===============No {}  appid:{}  excel_no:{}===============".format(app_no, appid, i))
        app_no += 1
        for j in range(1):
            sversion_old, sversion_new = apks.iloc[i + j + 1].at["sversion"], apks.iloc[i + j].at["sversion"]
            pversion_old, pversion_new = apks.iloc[i + j + 1].at["pversion"], apks.iloc[i + j].at["pversion"]
            name_old, name_new = apks.iloc[i + j + 1].at["url"].split('/')[-1], apks.iloc[i + j].at["url"].split('/')[-1]
            old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
            new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new
            name_new = apks.iloc[i + j].at["url"].split('/')[-1]
            name_old = apks.iloc[i + j + 1].at["url"].split('/')[-1]
            version_info = str(pversion_old) + '-' + str(pversion_new)

            newFileSize = os.path.getsize(new_path)
            do_x3(old_path, new_path, x3_path)
            x3_addBytes = an_x3add('ins_info.csv')
            bs_diff = do_bs(old_path, new_path, bs_path)
            bs_add,bs_cpy ,bs_insNum,ctrl_size,diff_size= an_bsAddCopy("bs_ins.csv")
            if os.path.exists("bs_ins.csv"):
                os.remove("bs_ins.csv")
            normalRun("./HDiffPatch/hdiffz -f -d -c-bzip2 {} {} diff".format(old_path, new_path))
            os.remove("diff")
            csv_write.writerow([appid, version_info, x3_addBytes, newFileSize - x3_addBytes,
                                bs_add,bs_cpy,bs_insNum,ctrl_size,diff_size,bs_diff-32-ctrl_size-diff_size])
        sversion_old, sversion_new = apks.iloc[i + 4].at["sversion"], apks.iloc[i].at["sversion"]
        pversion_old, pversion_new = apks.iloc[i + 4].at["pversion"], apks.iloc[i].at["pversion"]
        name_old, name_new = apks.iloc[i + 4].at["url"].split('/')[-1], apks.iloc[i].at["url"].split('/')[-1]
        old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
        new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new
        version_info = str(pversion_old) + '-' + str(pversion_new)
        
        newFileSize = os.path.getsize(new_path)
        do_x3(old_path, new_path, x3_path)
        x3_addBytes = an_x3add('ins_info.csv')
        bs_diff = do_bs(old_path, new_path,bs_path)
        bs_add,bs_cpy ,bs_insNum,ctrl_size,diff_size= an_bsAddCopy("bs_ins.csv")
        if os.path.exists("bs_ins.csv"):
            os.remove("bs_ins.csv")
        normalRun("./HDiffPatch/hdiffz -f -d -c-bzip2 {} {} diff".format(old_path, new_path))
        os.remove("diff")
        csv_write.writerow([appid, version_info, x3_addBytes, newFileSize - x3_addBytes,
                            bs_add,bs_cpy,bs_insNum,ctrl_size,diff_size,bs_diff-32-ctrl_size-diff_size])
        i += 5


if __name__ == '__main__':
    excel_path = "../new_vf.xlsx"
    datapath = "../data"
    run_bm(excel_path, datapath, 0  ,5)
