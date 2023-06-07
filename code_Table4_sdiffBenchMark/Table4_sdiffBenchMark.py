import os
import csv
import subprocess
import time
import pandas as pd


def analyze_dat(dat_path):
    df = pd.read_table(dat_path, sep=',', skiprows=1)
    memuse = 0
    if len(df) == 0:
        return 0
    for i in range(len(df)):
        mem = df.loc[i].values[0].split(' ')[1]
        memuse += float(mem)
    return round(memuse / len(df), 6)



def get_time_mem_cpu_ap_generate(stmt, delta_path):
    stmt = "/usr/bin/time -v mprof run --multiprocess --output memdat " + stmt
    print(stmt)
    diff, maxmem ,cpuget,avgmem ,\
    time_guess,time_generate_deltafri_file,\
    time_fast_block,time_build_suf,time_search,time_write,\
    time_second_compress ,diff_time, pyTime, usrBinT = -1, -1, -1, -1, -1, -1, -1,-1,-1,-1,-1,-1,-1,-1
    startTime = time.time()
    status, output = subprocess.getstatusoutput(stmt)
    pyTime = time.time() - startTime
    if status != 0:
        print("run error")
    else:
        print("run success")
        outputs = output.split('\n')
        f1 = float(outputs[-21].split(' ')[-1])
        f2 = float(outputs[-22].split(' ')[-1])
        usrBinT = f1 + f2
        maxmem = float(outputs[-14].split(' ')[-1])
        cpuget = int(outputs[-20].split(' ')[-1][:-1])
        avgmem = analyze_dat("memdat")
    
        time_guess = float(outputs[0].split(' : ')[-1]) 
        time_generate_deltafri_file = float(outputs[2].split(' : ')[-1]) 
        time_fast_block = float(outputs[10].split(' ')[-1])
        time_build_suf = float(outputs[11].split(' ')[-1])
        time_search = float(outputs[12].split(' ')[-1])
        time_write = float(outputs[13].split(' ')[-1])
        diff_time = float(outputs[15].split(' ')[-2])
        time_second_compress = float(outputs[18].split(':')[-1])

        diff = os.path.getsize(delta_path+'.zst')
    if os.path.exists("memdat"):
        os.remove("memdat")
    return diff, maxmem ,cpuget,avgmem,time_guess,time_generate_deltafri_file,time_fast_block,time_build_suf,time_search,time_write, diff_time,time_second_compress,pyTime,usrBinT

def get_time_mem_cpu_ap_apply(stmt, delta_path):
    stmt = "/usr/bin/time -v mprof run --multiprocess --output memdat " + stmt
    print(stmt)
    maxmem,cpuget,avgmem,time_decompression, time_generate_old_deltafri_file, time_patch, time_recompresssion, pyTime, usrBinT = -1, -1, -1, -1, -1, -1, -1, -1, -1
    startTime = time.time()
    status, output = subprocess.getstatusoutput(stmt)
    pyTime = time.time() - startTime
    if status != 0:
        print("run error")
    else:
        print("run success")
        outputs = output.split('\n')
        f1 = float(outputs[-21].split(' ')[-1])
        f2 = float(outputs[-22].split(' ')[-1])
        usrBinT = f1 + f2
        maxmem = float(outputs[-14].split(' ')[-1])
        cpuget = int(outputs[-20].split(' ')[-1][:-1])
        avgmem = analyze_dat("memdat")

        time_decompression = float(outputs[1].split(' : ')[-1]) 
        time_generate_old_deltafri_file = float(outputs[4].split(' : ')[-1]) 
        time_patch = float(outputs[6].split(' : ')[-1]) 
        time_recompresssion = float(outputs[11].split(' : ')[-1]) 
    if os.path.exists("memdat"):
        os.remove("memdat")
    return maxmem,cpuget,avgmem,time_decompression, time_generate_old_deltafri_file, time_patch, time_recompresssion, pyTime, usrBinT


def do_ap(old, new, ap_delta_path):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    diff_cmd = 'java -classpath .:jna-5.12.1.jar com.google.archivepatcher.sample.SamplePatchGenerator  '  + old + ' ' + new + " " + ap_delta_path + ' ../HDiffPatch/hdiffz  \"-f -d \" ../zstd-dev/zstd  \"--ultra -21\"'
    diff, maxmem ,cpuget,avgmem,time_guess,time_generate_deltafri_file,time_fast_block,time_build_suf,time_search,time_write, diff_time,time_second_compress,pyTime,usrBinT = get_time_mem_cpu_ap_generate(diff_cmd, ap_delta_path)
    if os.path.exists('./useless'):
        os.remove('./useless')
    return diff, diff / os.path.getsize(new) if diff != -1 else -1,maxmem ,cpuget,avgmem,time_guess,time_generate_deltafri_file,time_fast_block,time_build_suf,time_search,time_write, diff_time,time_second_compress,pyTime,usrBinT

def do_ap_apply(old, ap_delta_path, new):
    if not (os.path.exists(old) and os.path.exists(ap_delta_path)):
        print("missing apk")
        return -1, -1, -1, -1, -1
    diff_cmd = 'java -classpath .:jna-5.12.1.jar com.google.archivepatcher.sample.SamplePatchApplier '  + old + ' ' + ap_delta_path + " " + './useless_new' + ' ../HDiffPatch/hpatchz \" -f \" ../zstd-dev/zstd \"-k -d\" 1'
    maxmem,cpuget,avgmem,time_decompression, time_generate_old_deltafri_file, time_patch, time_recompresssion, pyTime, usrBinT = get_time_mem_cpu_ap_apply(diff_cmd, ap_delta_path)
    if os.path.exists('useless.zst'):
        os.remove('./useless.zst')
    return maxmem,cpuget,avgmem,time_decompression, time_generate_old_deltafri_file, time_patch, time_recompresssion, pyTime, usrBinT




def ver(ov, nv):
    return str(ov) + '->' + str(nv)


def run_bm(excel_path, datapath, start_no, end_no):
    apks = pd.read_excel(excel_path)
    data_file_name = "sdiffBenchmark.csv"
    f = open(data_file_name, 'a', encoding='utf-8-sig', newline="")
    csv_write = csv.writer(f)
    if os.path.getsize(data_file_name) == 0:
        csv_write.writerow(
            ['appid', 'sv', 'pv',
             'sdiff_sz', 'sdiff%', 'sdiff_maxmem', 'sdiff_cpu', 'sdiff_avgmem','sdiff_t',
             'sdiff_patch_maxmem', 'sdiff_patch_cpu', 'sdiff_patch_avgmem','sdiff_patch_t'
            ])
    i = start_no
    app_no = 1
    while i < end_no:
        appid = apks.iloc[i].at["appid"]
        print("No {}  appid:{}  excel_no:{}".format(app_no, appid, i))
        app_no += 1
        for j in range(1):
            first = appid if j == 0 else ""
            sversion_new = apks.iloc[i + j].at["sversion"]
            sversion_old = apks.iloc[i + j + 1].at["sversion"]
            pversion_new = apks.iloc[i + j].at["pversion"]
            pversion_old = apks.iloc[i + j + 1].at["pversion"]
            name_new = apks.iloc[i + j].at["url"].split('/')[-1]
            name_old = apks.iloc[i + j + 1].at["url"].split('/')[-1]
            old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
            new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new

            sdiff_sz, sdiff, maxmem ,cpuget, avgmem , _, _, _, _, _, _, _, _, _,usrBinT = do_ap(old_path, new_path, './useless')
            p_maxmem, p_cpuget, p_avgmem, _, _, _, _, _, p_usrBinT = do_ap_apply(old_path, './useless.zst', './useless_new')

            csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),
                                sdiff_sz, sdiff, maxmem ,cpuget, avgmem, usrBinT,
                                p_maxmem,p_cpuget,p_avgmem, p_usrBinT])
            if os.path.exists("./useless_new"):
                os.remove("./useless_new")

        sversion_new = apks.iloc[i].at["sversion"]
        sversion_old = apks.iloc[i + 4].at["sversion"]
        pversion_new = apks.iloc[i].at["pversion"]
        pversion_old = apks.iloc[i + 4].at["pversion"]
        name_new = apks.iloc[i].at["url"].split('/')[-1]
        name_old = apks.iloc[i + 4].at["url"].split('/')[-1]
        old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
        new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new

        sdiff_sz, sdiff, maxmem ,cpuget, avgmem ,_, _, _, _, _, _, _, _, _,usrBinT = do_ap(old_path, new_path, './useless')
        p_maxmem, p_cpuget, p_avgmem, _, _, _, _, _, p_usrBinT = do_ap_apply(old_path, './useless.zst', './useless_new')

        csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),
                                sdiff_sz, sdiff, maxmem ,cpuget, avgmem, usrBinT,
                                p_maxmem,p_cpuget,p_avgmem, p_usrBinT])
        if os.path.exists("./useless_new"):
            os.remove("./useless_new")
        i += 5


if __name__ == '__main__':
    excel_path = "../new_vf.xlsx"
    datapath = "../data"
    run_bm(excel_path, datapath, 0,10)

