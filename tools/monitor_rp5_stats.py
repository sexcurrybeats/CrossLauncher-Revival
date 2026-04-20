#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import subprocess
import time


PACKAGE_NAME = "id.psw.vshlauncher"


def run(adb_base: list[str], *args: str) -> str:
    return subprocess.check_output(adb_base + list(args), text=True, stderr=subprocess.DEVNULL)


def shell(adb_base: list[str], command: str) -> str:
    return run(adb_base, "shell", command)


def parse_battery(adb_base: list[str]) -> tuple[str, str]:
    out = shell(adb_base, "dumpsys battery")
    level = re.search(r"level:\s+(\d+)", out)
    temp = re.search(r"temperature:\s+(\d+)", out)
    level_text = f"{level.group(1)}%" if level else "?"
    temp_text = f"{int(temp.group(1)) / 10.0:.1f}C" if temp else "?"
    return level_text, temp_text


def parse_thermal_status(adb_base: list[str]) -> str:
    out = shell(adb_base, "dumpsys thermalservice")
    match = re.search(r"Thermal Status:\s+(-?\d+)", out)
    return match.group(1) if match else "?"


def parse_gpu(adb_base: list[str]) -> tuple[str, str, str]:
    busy = shell(adb_base, "cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").strip()
    freq = shell(adb_base, "cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq").strip()
    temp = shell(adb_base, "cat /sys/class/kgsl/kgsl-3d0/temp").strip()
    freq_mhz = f"{int(freq) / 1_000_000:.0f}MHz" if freq.isdigit() else freq
    temp_c = f"{int(temp) / 1000.0:.1f}C" if temp.isdigit() else temp
    return busy, freq_mhz, temp_c


def parse_top(adb_base: list[str]) -> dict[str, str]:
    out = shell(adb_base, "top -b -n 1")
    lines = out.splitlines()
    result = {
        "mem_free": "?",
        "mem_used": "?",
        "swap_used": "?",
        "cpu_user": "?",
        "cpu_sys": "?",
        "cpu_idle": "?",
        "app_cpu": "0.0",
        "app_res": "?",
    }

    for line in lines:
        if line.strip().startswith("Mem:"):
            mem_match = re.search(r"Mem:\s+(\d+)K total,\s+(\d+)K used,\s+(\d+)K free", line)
            if mem_match:
                result["mem_used"] = f"{int(mem_match.group(2)) // 1024}MB"
                result["mem_free"] = f"{int(mem_match.group(3)) // 1024}MB"
        elif line.strip().startswith("Swap:"):
            swap_match = re.search(r"Swap:\s+\d+K total,\s+(\d+)K used", line)
            if swap_match:
                result["swap_used"] = f"{int(swap_match.group(1)) // 1024}MB"
        elif "%cpu" in line and "user" in line:
            cpu_match = re.search(r"(\d+)%user.*?(\d+)%sys.*?(\d+)%idle", line)
            if cpu_match:
                result["cpu_user"] = cpu_match.group(1)
                result["cpu_sys"] = cpu_match.group(2)
                result["cpu_idle"] = cpu_match.group(3)
        elif PACKAGE_NAME in line:
            cols = line.split()
            if len(cols) >= 12:
                result["app_cpu"] = cols[8]
                result["app_res"] = cols[5]
                break
    return result


def format_row(ts: str, top: dict[str, str], battery_level: str, battery_temp: str, thermal_status: str, gpu_busy: str, gpu_freq: str, gpu_temp: str) -> str:
    return (
        f"{ts}  "
        f"app_cpu={top['app_cpu']:>5}%  "
        f"app_res={top['app_res']:>6}  "
        f"mem_used={top['mem_used']:>6}  "
        f"mem_free={top['mem_free']:>6}  "
        f"swap={top['swap_used']:>5}  "
        f"cpu(u/s/i)={top['cpu_user']:>3}/{top['cpu_sys']:>3}/{top['cpu_idle']:>3}  "
        f"gpu={gpu_busy:>4} @ {gpu_freq:>6}  "
        f"gpu_temp={gpu_temp:>5}  "
        f"batt={battery_level:>4} {battery_temp:>5}  "
        f"thermal={thermal_status}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Live launcher/device monitor over ADB")
    parser.add_argument("--serial", required=True, help="ADB device serial")
    parser.add_argument("--interval", type=float, default=2.0, help="Sampling interval in seconds")
    args = parser.parse_args()

    adb_base = ["adb", "-s", args.serial]

    print("time       app_cpu  app_res  mem_used  mem_free  swap   cpu(u/s/i)  gpu       gpu_temp  batt        thermal")
    try:
        while True:
            ts = time.strftime("%H:%M:%S")
            top = parse_top(adb_base)
            battery_level, battery_temp = parse_battery(adb_base)
            thermal_status = parse_thermal_status(adb_base)
            gpu_busy, gpu_freq, gpu_temp = parse_gpu(adb_base)
            print(format_row(ts, top, battery_level, battery_temp, thermal_status, gpu_busy, gpu_freq, gpu_temp), flush=True)
            time.sleep(args.interval)
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
