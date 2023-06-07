import csv
import os
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


def get_time_mem_cpu(stmt):
    stmt = "/usr/bin/time -v mprof run --multiprocess --output memdat " + stmt
    print(stmt)
    diff, t, maxmem, avgmem, cpuget = -1, -1, -1, -1, -1
    status, output = subprocess.getstatusoutput(stmt)
    if status != 0 or not (os.path.exists("diff")):
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
        diff = os.path.getsize("diff")
    if os.path.exists("memdat"):
        os.remove("memdat")
    return diff, maxmem, avgmem, cpuget, t

def get_time_mem_cpu_patch(stmt):
    stmt = "/usr/bin/time -v mprof run --multiprocess --output memdat " + stmt
    print(stmt)
    t, maxmem, avgmem, cpuget = -1, -1, -1, -1
    status, output = subprocess.getstatusoutput(stmt)
    if status != 0 or not (os.path.exists("patch")):
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
    if os.path.exists("memdat"):
        os.remove("memdat")
    if os.path.exists("diff"):
        os.remove("diff")
    if os.path.exists("patch"):
        os.remove("patch")
    return maxmem, avgmem, cpuget, t

def do_x3(old, new, binpath):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    size = os.path.getsize(new)
    diffsize = str(size) if size <= 2 ** 31 else "2147483648"
    if binpath == "":
        diff_cmd = 'xdelta3 -B {} -e -s {} {} diff'.format(diffsize,old,new)
    else:
        diff_cmd = '{} -B {} -e -s {} {} diff'.format(binpath,diffsize,old,new)
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd)
    patch_cmd = "{} -d -s {} diff patch".format(binpath if binpath!="" else "xdelta3",old)
    maxmem_p, avgmem_p, cpuget_p, t_p = get_time_mem_cpu_patch(patch_cmd) 
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t, maxmem_p, avgmem_p, cpuget_p, t_p


def do_bs(old, new, binpath):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    diff_cmd = '{}/bsdiff {} {} diff'.format(binpath,old,new)
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd)
    patch_cmd = "{}/bspatch {} patch diff".format(binpath,old)
    maxmem_p, avgmem_p, cpuget_p, t_p = get_time_mem_cpu_patch(patch_cmd) 
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t, maxmem_p, avgmem_p, cpuget_p, t_p


def do_hd(old, new, binpath):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    diff_cmd = '{}/hdiffz {} {} diff'.format(binpath,old,new)
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd)
    patch_cmd = "{}/hpatchz {} diff patch".format(binpath,old)
    maxmem_p, avgmem_p, cpuget_p, t_p = get_time_mem_cpu_patch(patch_cmd) 
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t, maxmem_p, avgmem_p, cpuget_p, t_p



# ./com
def do_ap(old, new):
    if not (os.path.exists(old) and os.path.exists(new)):
        print("missing apk")
        return -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    diff_cmd = 'java com/google/archivepatcher/sample/SamplePatchGenerator {} {} diff'.format(old,new)
    diff, maxmem, avgmem, cpuget, t = get_time_mem_cpu(diff_cmd)
    patch_cmd = "java com/google/archivepatcher/sample/SamplePatchApplier {} diff patch".format(old)
    maxmem_p, avgmem_p, cpuget_p, t_p = get_time_mem_cpu_patch(patch_cmd) 
    return diff, diff / os.path.getsize(new) if diff != -1 else -1, maxmem, avgmem, cpuget, t, maxmem_p, avgmem_p, cpuget_p, t_p

def clearFile():
    if os.path.exists('newdata'):
        os.remove('newdata')
    if os.path.exists('tmpdiff'):
        os.remove('tmpdiff')

def ver(ov, nv):
    return str(ov) + '_' + str(nv)


def run_bm(excel_path, datapath, start_no, end_no):
    apks = pd.read_excel(excel_path)
    filename = "result_" + str(start_no) + '_' + str(end_no) + ".csv"
    f = open(filename, 'a', encoding='utf-8', newline="")
    csv_write = csv.writer(f)
    if os.path.getsize(filename) == 0:
        csv_write.writerow(
            ['appid', 'sv', 'pv',
             'x3_sz', 'x3%', 'x3_diff_maxmem', 'x3_diff_avgmem', 'x3_diff_cpu', 'x3_diff_t','x3_patch_maxmem', 'x3_patch_avgmem', 'x3_patch_cpu', 'x3_patch_t',
             'bs_sz', 'bs%', 'bs_diff_maxmem', 'bs_diff_avgmem', 'bs_diff_cpu', 'bs_diff_t','bs_patch_maxmem', 'bs_patch_avgmem', 'bs_patch_cpu', 'bs_patch_t',
             'ap_sz', 'ap%', 'ap_diff_maxmem', 'ap_diff_avgmem', 'ap_diff_cpu', 'ap_diff_t','ap_patch_maxmem', 'ap_patch_avgmem', 'ap_patch_cpu', 'ap_patch_t',
             'hd_sz', 'hd%', 'hd_diff_maxmem', 'hd_diff_avgmem', 'hd_diff_cpu', 'hd_diff_t','hd_patch_maxmem', 'hd_patch_avgmem', 'hd_patch_cpu', 'hd_patch_t',
             ])
    app_no, i = 1, start_no
    x3_binPath = "../xdelta-gpl/xdelta3/xdelta3 "
    bs_binPath = "../bsdiff"
    hd_binPath = "../HDiffPatch" 
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
            x3_sz, x3, x3_diff_maxmem, x3_diff_avgmem, x3_diff_cpu, x3_diff_t,x3_patch_maxmem, x3_patch_avgmem, x3_patch_cpu, x3_patch_t = do_x3(old_path, new_path, x3_binPath)
            bs_sz, bs, bs_diff_maxmem, bs_diff_avgmem, bs_diff_cpu, bs_diff_t,bs_patch_maxmem, bs_patch_avgmem, bs_patch_cpu, bs_patch_t = do_bs(old_path, new_path, bs_binPath)
            ap_sz, ap, ap_diff_maxmem, ap_diff_avgmem, ap_diff_cpu, ap_diff_t,ap_patch_maxmem, ap_patch_avgmem, ap_patch_cpu, ap_patch_t = do_ap(old_path, new_path)
            hd_sz, hd, hd_diff_maxmem, hd_diff_avgmem, hd_diff_cpu, hd_diff_t,hd_patch_maxmem, hd_patch_avgmem, hd_patch_cpu, hd_patch_t = do_hd(old_path, new_path, hd_binPath)
            csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),                    
                                x3_sz, x3, x3_diff_maxmem, x3_diff_avgmem, x3_diff_cpu, x3_diff_t,x3_patch_maxmem, x3_patch_avgmem, x3_patch_cpu, x3_patch_t,
                                bs_sz, bs, bs_diff_maxmem, bs_diff_avgmem, bs_diff_cpu, bs_diff_t,bs_patch_maxmem, bs_patch_avgmem, bs_patch_cpu, bs_patch_t,
                                ap_sz, ap, ap_diff_maxmem, ap_diff_avgmem, ap_diff_cpu, ap_diff_t,ap_patch_maxmem, ap_patch_avgmem, ap_patch_cpu, ap_patch_t,
                                hd_sz, hd, hd_diff_maxmem, hd_diff_avgmem, hd_diff_cpu, hd_diff_t,hd_patch_maxmem, hd_patch_avgmem, hd_patch_cpu, hd_patch_t
                                ])

        # big version
        sversion_new = apks.iloc[i].at["sversion"]
        sversion_old = apks.iloc[i + 4].at["sversion"]
        pversion_new = apks.iloc[i].at["pversion"]
        pversion_old = apks.iloc[i + 4].at["pversion"]
        name_new = apks.iloc[i].at["url"].split('/')[-1]
        name_old = apks.iloc[i + 4].at["url"].split('/')[-1]
        old_path = datapath + '/' + appid + '/' + str(sversion_old) + '/' + name_old
        new_path = datapath + '/' + appid + '/' + str(sversion_new) + '/' + name_new
        x3_sz, x3, x3_diff_maxmem, x3_diff_avgmem, x3_diff_cpu, x3_diff_t,x3_patch_maxmem, x3_patch_avgmem, x3_patch_cpu, x3_patch_t = do_x3(old_path, new_path, x3_binPath)
        bs_sz, bs, bs_diff_maxmem, bs_diff_avgmem, bs_diff_cpu, bs_diff_t,bs_patch_maxmem, bs_patch_avgmem, bs_patch_cpu, bs_patch_t = do_bs(old_path, new_path, bs_binPath)
        ap_sz, ap, ap_diff_maxmem, ap_diff_avgmem, ap_diff_cpu, ap_diff_t,ap_patch_maxmem, ap_patch_avgmem, ap_patch_cpu, ap_patch_t = do_ap(old_path, new_path)
        hd_sz, hd, hd_diff_maxmem, hd_diff_avgmem, hd_diff_cpu, hd_diff_t,hd_patch_maxmem, hd_patch_avgmem, hd_patch_cpu, hd_patch_t = do_hd(old_path, new_path, hd_binPath)
        csv_write.writerow([first, ver(sversion_old, sversion_new), ver(pversion_old, pversion_new),                    
                            x3_sz, x3, x3_diff_maxmem, x3_diff_avgmem, x3_diff_cpu, x3_diff_t,x3_patch_maxmem, x3_patch_avgmem, x3_patch_cpu, x3_patch_t,
                            bs_sz, bs, bs_diff_maxmem, bs_diff_avgmem, bs_diff_cpu, bs_diff_t,bs_patch_maxmem, bs_patch_avgmem, bs_patch_cpu, bs_patch_t,
                            ap_sz, ap, ap_diff_maxmem, ap_diff_avgmem, ap_diff_cpu, ap_diff_t,ap_patch_maxmem, ap_patch_avgmem, ap_patch_cpu, ap_patch_t,
                            hd_sz, hd, hd_diff_maxmem, hd_diff_avgmem, hd_diff_cpu, hd_diff_t,hd_patch_maxmem, hd_patch_avgmem, hd_patch_cpu, hd_patch_t
                            ])
        i += 5


if __name__ == '__main__':
    excel_path = "../new_vf.xlsx"
    datapath = "../data"
    run_bm(excel_path, datapath, 0, 10)
    clearFile()
