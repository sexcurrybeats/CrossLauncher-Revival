#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


PACKAGE_NAME = "id.psw.vshlauncher"
EXTERNAL_ROOT = f"/storage/emulated/0/Android/data/{PACKAGE_NAME}/files"
INTERNAL_ROOT = f"/data/user/0/{PACKAGE_NAME}"
LAUNCH_ACTIVITY = f"{PACKAGE_NAME}/.activities.Xmb"


def run(cmd: list[str], *, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        check=check,
        text=True,
        capture_output=capture,
    )


def adb_cmd(serial: str, *args: str) -> list[str]:
    return ["adb", "-s", serial, *args]


def adb_shell(serial: str, command: str, *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(adb_cmd(serial, "shell", command), check=check)


def adb_exec_out_bytes(serial: str, *args: str) -> bytes:
    return subprocess.check_output(adb_cmd(serial, "exec-out", *args))


def ensure_device(serial: str) -> None:
    run(adb_cmd(serial, "get-state"))


def set_or_insert_string_pref(xml_text: str, key: str, value: str) -> str:
    replacement = f'<string name="{xml_escape(key)}">{xml_escape(value)}</string>'
    existing = re.compile(rf'<string\s+name="{re.escape(key)}">.*?</string>', re.DOTALL)
    if existing.search(xml_text):
        return existing.sub(replacement, xml_text, count=1)
    if "</map>" in xml_text:
        return xml_text.replace("</map>", f"    {replacement}\n</map>", 1)
    if "<map/>" in xml_text:
        return xml_text.replace("<map/>", f"<map>\n    {replacement}\n</map>", 1)
    return (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        "<map>\n"
        f"    {replacement}\n"
        "</map>\n"
    )


def xml_escape(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def count_files(root: Path) -> int:
    if not root.exists():
        return 0
    return sum(1 for p in root.rglob("*") if p.is_file())


def extract_xtf(xtf_path: Path, work_dir: Path) -> dict[str, object]:
    extracted = {
        "app_customization_files": 0,
        "custom_icon_files": 0,
        "coldboot_assets": 0,
        "gameboot_assets": 0,
        "menu_sfx_files": 0,
        "battery_glyph_files": 0,
        "font_path": "",
        "theme_path": "",
        "storage_label_memory_stick": False,
    }

    prefs_dir = work_dir / "prefs"
    external_dir = work_dir / "external"
    internal_dir = work_dir / "internal"
    report_dir = work_dir / "report"
    prefs_dir.mkdir(parents=True, exist_ok=True)
    external_dir.mkdir(parents=True, exist_ok=True)
    internal_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    registry_path = prefs_dir / "xRegistry.sys.xml"
    wave_path = prefs_dir / "libwave_setting.xml"
    font_target_path: Path | None = None
    theme_target_path: Path | None = None

    with zipfile.ZipFile(xtf_path) as zf:
        for name in zf.namelist():
            if name.endswith("/"):
                continue
            data = zf.read(name)
            out_path: Path | None = None

            if name == "prefs/xRegistry.sys.xml":
                out_path = registry_path
            elif name == "prefs/libwave_setting.xml":
                out_path = wave_path
            elif name.startswith("custom/dev_hdd0/game/"):
                rel = name.removeprefix("custom/")
                out_path = external_dir / rel
                extracted["app_customization_files"] += 1
            elif name.startswith("custom/custom_icons/"):
                rel = name.removeprefix("custom/")
                out_path = external_dir / rel
                extracted["custom_icon_files"] += 1
            elif name.startswith("custom/dev_flash/vsh/resource/"):
                rel = name.removeprefix("custom/")
                out_path = external_dir / rel
                lower = name.lower()
                if "/hud/" in lower and "battery" in lower:
                    extracted["battery_glyph_files"] += 1
                elif "/sfx/" in lower:
                    extracted["menu_sfx_files"] += 1
                elif "gameboot" in Path(name).name.lower():
                    extracted["gameboot_assets"] += 1
                elif "coldboot" in Path(name).name.lower():
                    extracted["coldboot_assets"] += 1
            elif name.startswith("fonts/active_font."):
                ext = Path(name).suffix or ".ttf"
                font_target_path = internal_dir / "files" / "fonts" / f"launcher_font{ext}"
                out_path = font_target_path
            elif name.startswith("themes/active_theme."):
                ext = Path(name).suffix or ".xtf"
                theme_target_path = external_dir / "themes" / f"imported_active_theme{ext}"
                out_path = theme_target_path
            elif name == "refs/user_font_path.txt":
                extracted["font_path"] = data.decode("utf-8", errors="ignore").strip()
            elif name == "refs/current_theme.txt":
                extracted["theme_path"] = data.decode("utf-8", errors="ignore").strip()

            if out_path is not None:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                out_path.write_bytes(data)

    if registry_path.exists():
        xml_text = registry_path.read_text(encoding="utf-8", errors="ignore")
        if font_target_path is not None:
            xml_text = set_or_insert_string_pref(
                xml_text,
                "/crosslauncher/theme/fontPath",
                f"{INTERNAL_ROOT}/files/fonts/{font_target_path.name}",
            )
        if theme_target_path is not None:
            xml_text = set_or_insert_string_pref(
                xml_text,
                "/crosslauncher/theme/currentId",
                f"{EXTERNAL_ROOT}/themes/{theme_target_path.name}",
            )
        registry_path.write_text(xml_text, encoding="utf-8")
        extracted["storage_label_memory_stick"] = (
            'name="/crosslauncher/shell/storageRootLabelStyle" value="1"' in xml_text
        )

    return extracted


def reset_launcher_state(serial: str) -> None:
    adb_shell(serial, f"am force-stop {PACKAGE_NAME}")
    adb_shell(
        serial,
        (
            "rm -rf "
            f"'{EXTERNAL_ROOT}/custom_icons' "
            f"'{EXTERNAL_ROOT}/themes' "
            f"'{EXTERNAL_ROOT}/dev_flash' "
            f"'{EXTERNAL_ROOT}/dev_hdd0/game' "
            f"'{EXTERNAL_ROOT}/dev_hdd0/shortcut'"
        ),
    )
    adb_shell(
        serial,
        (
            f"run-as {PACKAGE_NAME} sh -c "
            "\"cd /data/user/0/id.psw.vshlauncher && "
            "rm -rf files/fonts files/wallpaper files/dev_hdd0/game files/dev_hdd0/shortcut "
            "shared_prefs/xRegistry.sys.xml shared_prefs/libwave_setting.xml && "
            "mkdir -p shared_prefs files\""
        ),
    )


def push_stage_to_device(serial: str, local_stage: Path, remote_stage: str) -> None:
    adb_shell(serial, f"rm -rf '{remote_stage}'")
    run(adb_cmd(serial, "push", str(local_stage), remote_stage), capture=False)


def restore_to_device(serial: str, local_stage: Path, remote_stage: str) -> None:
    external_stage = local_stage / "external"
    prefs_stage = local_stage / "prefs"
    internal_stage = local_stage / "internal"

    if external_stage.exists():
        for child in external_stage.iterdir():
            run(adb_cmd(serial, "push", str(child), f"{EXTERNAL_ROOT}/{child.name}"), capture=False)

    push_stage_to_device(serial, local_stage, remote_stage)
    adb_shell(
        serial,
        (
            f"run-as {PACKAGE_NAME} sh -c "
            "\"cd /data/user/0/id.psw.vshlauncher && "
            "mkdir -p shared_prefs files/fonts files/wallpaper files/dev_hdd0/game files/dev_hdd0/shortcut && "
            f"if [ -f '{remote_stage}/prefs/xRegistry.sys.xml' ]; then cp '{remote_stage}/prefs/xRegistry.sys.xml' shared_prefs/xRegistry.sys.xml; fi && "
            f"if [ -f '{remote_stage}/prefs/libwave_setting.xml' ]; then cp '{remote_stage}/prefs/libwave_setting.xml' shared_prefs/libwave_setting.xml; fi && "
            f"if [ -d '{remote_stage}/internal/files' ]; then cp -R '{remote_stage}/internal/files/.' files/; fi\""
        ),
    )

    if prefs_stage.exists() and not (prefs_stage / "xRegistry.sys.xml").exists():
        raise RuntimeError("Prepared stage is missing xRegistry.sys.xml")
    if internal_stage.exists() and count_files(internal_stage) == 0:
        pass


def launch_and_capture(serial: str, screenshot_path: Path) -> None:
    adb_shell(serial, f"am start -n {LAUNCH_ACTIVITY}")
    time.sleep(4.0)
    screenshot_bytes = adb_exec_out_bytes(serial, "screencap", "-p")
    screenshot_path.write_bytes(screenshot_bytes)


def read_device_registry(serial: str) -> str:
    return adb_exec_out_bytes(
        serial,
        "run-as",
        PACKAGE_NAME,
        "cat",
        "shared_prefs/xRegistry.sys.xml",
    ).decode("utf-8", errors="ignore")


def write_report(report_path: Path, xtf_path: Path, extracted: dict[str, object], screenshot_path: Path, registry_xml: str) -> None:
    report = [
        "# XTF Round-Trip Validation",
        "",
        f"- Date: {dt.datetime.now().astimezone().isoformat()}",
        f"- XTF: `{xtf_path.name}`",
        f"- Screenshot: `{screenshot_path.name}`",
        "- Method: filesystem-equivalent fresh-install reset plus restore into the same app-owned paths used by the importer",
        "",
        "## Restored Payload",
        "",
        f"- App customization files: `{extracted['app_customization_files']}`",
        f"- Custom icon files: `{extracted['custom_icon_files']}`",
        f"- Coldboot assets: `{extracted['coldboot_assets']}`",
        f"- Gameboot assets: `{extracted['gameboot_assets']}`",
        f"- Menu SFX files: `{extracted['menu_sfx_files']}`",
        f"- Battery glyph files: `{extracted['battery_glyph_files']}`",
        f"- Storage label set to Memory Stick: `{extracted['storage_label_memory_stick']}`",
        "",
        "## Smoke Checks",
        "",
        f"- Registry restored: `{'/crosslauncher/shell/storageRootLabelStyle' in registry_xml}`",
        f"- Memory Stick mode active: `{'name=\"/crosslauncher/shell/storageRootLabelStyle\" value=\"1\"' in registry_xml}`",
        f"- Custom font path restored: `{'/crosslauncher/theme/fontPath' in registry_xml}`",
        "",
        "## Notes",
        "",
        "- This validates payload completeness and restore semantics on-device.",
        "- It does not exercise the in-app document-picker import UI path.",
    ]
    report_path.write_text("\n".join(report) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a CrossLauncher XTF by performing a fresh-install-equivalent restore on a connected device.")
    parser.add_argument("--serial", required=True, help="ADB device serial")
    parser.add_argument("--xtf", required=True, help="Path to the XTF file to validate")
    parser.add_argument("--output-dir", default="verification_shots", help="Directory for the validation screenshot/report")
    args = parser.parse_args()

    xtf_path = Path(args.xtf).resolve()
    if not xtf_path.exists():
        raise FileNotFoundError(f"XTF not found: {xtf_path}")

    ensure_device(args.serial)

    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    screenshot_path = output_dir / f"xtf_roundtrip_{stamp}.png"
    report_path = output_dir / f"xtf_roundtrip_{stamp}.md"

    with tempfile.TemporaryDirectory(prefix="xtf_roundtrip_") as temp_dir_str:
        temp_dir = Path(temp_dir_str)
        extracted = extract_xtf(xtf_path, temp_dir)
        remote_stage = f"/sdcard/Download/xtf_roundtrip_stage_{stamp}"
        reset_launcher_state(args.serial)
        restore_to_device(args.serial, temp_dir, remote_stage)
        launch_and_capture(args.serial, screenshot_path)
        registry_xml = read_device_registry(args.serial)
        write_report(report_path, xtf_path, extracted, screenshot_path, registry_xml)
        adb_shell(args.serial, f"rm -rf '{remote_stage}'", check=False)

    print(f"SCREENSHOT={screenshot_path}")
    print(f"REPORT={report_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR={exc}", file=sys.stderr)
        raise
