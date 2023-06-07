import csv
import os
import subprocess
import pandas as pd



def processCsvFile(appid,sold,snew,id):
    result_file_path = "./matchInfo/{}".format(appid)
    os.system("mkdir -p {}".format(result_file_path))
    newFileName = "{}-{}_{}.csv".format(id,sold,snew)
    os.system("mv matchInfo.csv "+newFileName)
    os.system("mv {} {}".format(newFileName,result_file_path))



def do_hd(old, new, binpath):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("{} or {} missing".format(old, new))
        return -1, -1, -1, -1, -1, -1
    diff_cmd = "{} {} {} diff".format(binpath, old, new)
    status, _ = subprocess.getstatusoutput(diff_cmd)
    if status != 0:
        print('ERROR !!\n {}'.format(diff_cmd))
    if os.path.exists('diff'):
        os.remove('diff')



def run_bm(excel_path, datapath, start_no, end_no):
    apks = pd.read_excel(excel_path)
    i = start_no
    app_no = 1
    while i < end_no:
        appid = apks.iloc[i].at["appid"]
        print("No {}  appid:{}  excel_no:{}".format(app_no, appid, i))
        app_no += 1
        for j in range(4):
            sversion_new = apks.iloc[i + j].at["sversion"]
            sversion_old = apks.iloc[i + j + 1].at["sversion"]
            name_new, name_old = apks.iloc[i + j].at["url"].split('/')[-1], apks.iloc[i + j + 1].at["url"].split('/')[-1]
            old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
            new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new
            do_hd(old_path, new_path, './HDiffPatch/hdiffz -f ')
            processCsvFile(appid,sversion_old,sversion_new,j+1)
            
        sversion_new = apks.iloc[i].at["sversion"]
        sversion_old = apks.iloc[i+4].at["sversion"]
        name_new, name_old = apks.iloc[i].at["url"].split('/')[-1], apks.iloc[i + 4].at["url"].split('/')[-1]
        old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
        new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new
        do_hd(old_path, new_path, './HDiffPatch/hdiffz -f ')
        processCsvFile(appid,sversion_old,sversion_new,5)
        i += 5


if __name__ == '__main__':
    excel_path = "../new_vf.xlsx"
    datapath = "../data"
    run_bm(excel_path, datapath, 0, 5)
