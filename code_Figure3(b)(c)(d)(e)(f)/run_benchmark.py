import os
import csv
import subprocess
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


def get_time_mem_cpu(stmt, delta_path):
    stmt = "/usr/bin/time -v mprof run --multiprocess --output memdat " + stmt
    print(stmt)
    diff, t, maxmem, avgmem, cpuget = -1, -1, -1, -1, -1
    status, output = subprocess.getstatusoutput(stmt)
    if status != 0:
        print("run error")
    else:
        print("run success")
        outputs = output.split('\n')
        f1 = float(outputs[-21].split(' ')[-1])
        f2 = float(outputs[-22].split(' ')[-1])
        t = f1 + f2
        maxmem = float(outputs[-14].split(' ')[-1])
        cpuget = int(outputs[-20].split(' ')[-1][:-1])
        avgmem = analyze_dat("memdat")
        diff = os.path.getsize(delta_path)
    # if os.path.exists(delta_path):
    #     os.remove("diff")
    if os.path.exists("memdat"):
        os.remove("memdat")
    return diff, maxmem, avgmem, cpuget, t


# Xdelta3
def do_x3(old, new, binpath, x3_delta_path):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1
    
    if binpath == "":
        diff_cmd = "xdelta3 -f -e -s " + old + ' ' + new + " " + x3_delta_path
    else:
        diff_cmd = binpath + ' -f -e -s ' + old + ' ' + new + " " + x3_delta_path
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd, x3_delta_path)
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t


# bsdiff
def do_bs(old, new, binpath, bs_delta_path):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1
    diff_cmd = binpath + ' ' + old + ' ' + new + " " + bs_delta_path
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd, bs_delta_path)
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t


# HDiffPatch
def do_hd(old, new, binpath, hd_delta_path):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1
    diff_cmd = binpath + ' ' + old + ' ' + new + " " + hd_delta_path
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd, hd_delta_path)
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t


# archive-patcher
def do_ap(old, new, ap_delta_path):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1
    diff_cmd = 'java -cp /home/emnets/workspace/OTA/algorithms/archive-patcher/sample/src/main/java/:/home/emnets/workspace/OTA/algorithms/archive-patcher/generator/build/libs/generator.jar:/home/emnets/workspace/OTA/algorithms/archive-patcher/shared/build/libs/shared.jar com.google.archivepatcher.sample.SamplePatchGenerator '  + old + ' ' + new + " " + ap_delta_path
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd, ap_delta_path)
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t


def ver(ov, nv):
    return str(ov) + '->' + str(nv)


def run_bm(excel_path, datapath, start_no, end_no):
    apks = pd.read_excel(excel_path)
    f = open('hw_0_99.csv', 'a', encoding='utf-8', newline="")
    csv_write = csv.writer(f)
    if os.path.getsize("hw_0_99.csv") == 0:
        csv_write.writerow(
            ['appid', 'sv', 'pv',
             'x3_sz', 'x3%', 'x3_maxmem', 'x3_avgmem', 'x3_cpu', 'x3_t',
             'bs_sz', 'bs%', 'bs_maxmem', 'bs_avgmem', 'bs_cpu', 'bs_t',
             'ap_sz', 'ap%', 'ap_maxmem', 'ap_avgmem', 'ap_cpu', 'ap_t',
             'hd_sz', 'hd%', 'hd_maxmem', 'hd_avgmem', 'hd_cpu', 'hd_t'])
    i = start_no
    app_no = 1
    while i < end_no:
        appid = apks.iloc[i].at["appid"]
        print("No {}  appid:{}  excel_no:{}".format(app_no, appid, i))
        app_no += 1
        for j in range(4):
            first = appid if j == 0 else ""
            sversion_new = apks.iloc[i + j].at["sversion"]
            sversion_old = apks.iloc[i + j + 1].at["sversion"]
            pversion_new = apks.iloc[i + j].at["pversion"]
            pversion_old = apks.iloc[i + j + 1].at["pversion"]
            name_new = apks.iloc[i + j].at["url"].split('/')[-1]
            name_old = apks.iloc[i + j + 1].at["url"].split('/')[-1]
            old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
            new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new

            # 添加delta文件目录
            x3_delta_dir = '/data/diff_0_99/x3/' + appid
            x3_delta_path = x3_delta_dir + '/' + name_old + '_' + name_new
            os.system('mkdir -p ' + x3_delta_dir)
            x3_sz, x3, x3_maxmem, x3_avgmem, x3_cpu, x3_t = do_x3(old_path, new_path, "", x3_delta_path)

            bs_delta_dir = '/data/diff_0_99/bs/' + appid
            bs_delta_path = bs_delta_dir + '/' + name_old + '_' + name_new
            os.system('mkdir -p ' + bs_delta_dir)
            bs_sz, bs, bs_maxmem, bs_avgmem, bs_cpu, bs_t = do_bs(old_path, new_path,
                                                                  "/home/emnets/workspace/OTA/algorithms/bsdiff/bsdiff", bs_delta_path)

            ap_delta_dir = '/data/diff_0_99/ap/' + appid
            ap_delta_path = ap_delta_dir + '/' + name_old + '_' + name_new
            os.system('mkdir -p ' + ap_delta_dir)
            ap_sz, ap, ap_maxmem, ap_avgmem, ap_cpu, ap_t = do_ap(old_path, new_path, ap_delta_path)
            
            hd_delta_dir = '/data/diff_0_99/hd/' + appid
            hd_delta_path = hd_delta_dir + '/' + name_old + '_' + name_new
            os.system('mkdir -p ' + hd_delta_dir)
            hd_sz, hd, hd_maxmem, hd_avgmem, hd_cpu, hd_t = do_hd(old_path, new_path,
                                                                  "/home/emnets/workspace/OTA/algorithms/HDiffPatch/hdiffz", hd_delta_path)

            csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),
                                x3_sz, x3, x3_maxmem, x3_avgmem, x3_cpu, x3_t,
                                bs_sz, bs, bs_maxmem, bs_avgmem, bs_cpu, bs_t,
                                ap_sz, ap, ap_maxmem, ap_avgmem, ap_cpu, ap_t,
                                hd_sz, hd, hd_maxmem, hd_avgmem, hd_cpu, hd_t,
                                ]) 
        # 最新和最旧做差分
        sversion_new = apks.iloc[i].at["sversion"]
        sversion_old = apks.iloc[i + 4].at["sversion"]
        pversion_new = apks.iloc[i].at["pversion"]
        pversion_old = apks.iloc[i + 4].at["pversion"]
        name_new = apks.iloc[i].at["url"].split('/')[-1]
        name_old = apks.iloc[i + 4].at["url"].split('/')[-1]
        old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
        new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new

        # 添加delta文件目录
        x3_delta_dir = '/data/diff_0_99/x3/' + appid
        x3_delta_path = x3_delta_dir + '/' + name_old + '_' + name_new
        os.system('mkdir -p ' + x3_delta_dir)
        x3_sz, x3, x3_maxmem, x3_avgmem, x3_cpu, x3_t = do_x3(old_path, new_path, "", x3_delta_path)

        bs_delta_dir = '/data/diff_0_99/bs/' + appid
        bs_delta_path = bs_delta_dir + '/' + name_old + '_' + name_new
        os.system('mkdir -p ' + bs_delta_dir)
        bs_sz, bs, bs_maxmem, bs_avgmem, bs_cpu, bs_t = do_bs(old_path, new_path,
                                                                "/home/emnets/workspace/OTA/algorithms/bsdiff/bsdiff", bs_delta_path)

        ap_delta_dir = '/data/diff_0_99/ap/' + appid
        ap_delta_path = ap_delta_dir + '/' + name_old + '_' + name_new
        os.system('mkdir -p ' + ap_delta_dir)
        ap_sz, ap, ap_maxmem, ap_avgmem, ap_cpu, ap_t = do_ap(old_path, new_path, ap_delta_path)
        
        hd_delta_dir = '/data/diff_0_99/hd/' + appid
        hd_delta_path = hd_delta_dir + '/' + name_old + '_' + name_new
        os.system('mkdir -p ' + hd_delta_dir)
        hd_sz, hd, hd_maxmem, hd_avgmem, hd_cpu, hd_t = do_hd(old_path, new_path,
                                                                "/home/emnets/workspace/OTA/algorithms/HDiffPatch/hdiffz", hd_delta_path)

        x3_sz, x3, x3_maxmem, x3_avgmem, x3_cpu, x3_t = do_x3(old_path, new_path, "")
        bs_sz, bs, bs_maxmem, bs_avgmem, bs_cpu, bs_t = do_bs(old_path, new_path,
                                                              "/home/emnets/workspace/OTA/algorithms/bsdiff")
        ap_sz, ap, ap_maxmem, ap_avgmem, ap_cpu, ap_t = do_ap(old_path, new_path)
        hd_sz, hd, hd_maxmem, hd_avgmem, hd_cpu, hd_t = do_hd(old_path, new_path,
                                                              "/home/emnets/workspace/OTA/algorithms/HDiffPatch-master/hdiffz")

        csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),
                            x3_sz, x3, x3_maxmem, x3_avgmem, x3_cpu, x3_t,
                            bs_sz, bs, bs_maxmem, bs_avgmem, bs_cpu, bs_t,
                            ap_sz, ap, ap_maxmem, ap_avgmem, ap_cpu, ap_t,
                            hd_sz, hd, hd_maxmem, hd_avgmem, hd_cpu, hd_t,
                            ]) 
        i += 5


if __name__ == '__main__':
    excel_path = "/home/emnets/workspace/OTA/v2.xlsx" # This is the benchmark excel, please modify 
    datapath = "/home/emnets/workspace/OTA/xxxxxx" # please modify your datapath
    run_bm(excel_path, datapath, 0, 1000)
