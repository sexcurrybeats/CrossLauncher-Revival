from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path


TARGET_BYTES = 4 * 1024 * 1024
SUPPORTED_EXTS = {".gif", ".webp", ".apng", ".mp4"}
KEEPABLE_EXTS = {".gif", ".webp", ".apng"}


@dataclass
class MediaInfo:
    width: int
    height: int
    fps: float
    duration: float
    codec: str
    file_size: int


@dataclass
class ConversionResult:
    zip_entry: str
    action: str
    source_size: int
    output_entry: str | None = None
    output_size: int | None = None
    fps: float | None = None
    width: int | None = None
    height: int | None = None
    quality: int | None = None
    note: str | None = None


def run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True, check=False)


def ffprobe_json(ffprobe: str, media_path: Path) -> dict:
    result = run(
        [
            ffprobe,
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_streams",
            "-show_format",
            str(media_path),
        ]
    )
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe failed for {media_path}: {result.stderr.strip()}")
    return json.loads(result.stdout)


def parse_rate(rate: str | None) -> float:
    if not rate or rate == "0/0":
        return 0.0
    try:
        return float(Fraction(rate))
    except Exception:
        return 0.0


def inspect_media(ffprobe: str, media_path: Path) -> MediaInfo:
    info = ffprobe_json(ffprobe, media_path)
    stream = (info.get("streams") or [{}])[0]
    fmt = info.get("format") or {}
    return MediaInfo(
        width=int(stream.get("width") or 0),
        height=int(stream.get("height") or 0),
        fps=parse_rate(stream.get("avg_frame_rate") or stream.get("r_frame_rate")),
        duration=float(stream.get("duration") or fmt.get("duration") or 0.0),
        codec=str(stream.get("codec_name") or ""),
        file_size=media_path.stat().st_size,
    )


def candidate_attempts(source_info: MediaInfo) -> list[tuple[int, int]]:
    size_mb = source_info.file_size / (1024 * 1024)
    if size_mb >= 20:
        base = [(220, 26), (200, 22), (180, 20)]
    elif size_mb >= 14:
        base = [(240, 28), (220, 24), (200, 22)]
    elif size_mb >= 10:
        base = [(260, 30), (240, 26), (220, 22)]
    elif size_mb >= 8:
        base = [(280, 34), (240, 28), (220, 24)]
    elif size_mb >= 6:
        base = [(300, 38), (260, 32), (220, 26)]
    else:
        base = [(320, 42), (280, 34), (240, 28)]
    result: list[tuple[int, int]] = []
    for width, quality in base:
        clamped = min(source_info.width, width)
        pair = (clamped, quality)
        if pair not in result:
            result.append(pair)
    return result


def target_fps(src_fps: float) -> float:
    if src_fps <= 0:
        return 15.0
    return min(src_fps, 30.0)


def webp_output_name(zip_entry: str) -> str:
    path = Path(zip_entry)
    return str(path.with_name("ICON1.webp")).replace("\\", "/")


def convert_to_webp(
    ffmpeg: str,
    source_path: Path,
    source_info: MediaInfo,
    out_path: Path,
) -> tuple[bool, dict[str, float | int | str]]:
    fps = target_fps(source_info.fps)
    best_note = "no viable conversion candidate found"

    for width, quality in candidate_attempts(source_info):
        scale_expr = f"scale={width}:-2:flags=lanczos"
        vf = f"fps={fps:.3f},{scale_expr}"
        if out_path.exists():
            out_path.unlink()
        cmd = [
            ffmpeg,
            "-y",
            "-i",
            str(source_path),
            "-an",
            "-vf",
            vf,
            "-loop",
            "0",
            "-pix_fmt",
            "yuva420p",
            "-vcodec",
            "libwebp_anim",
            "-lossless",
            "0",
            "-quality",
            str(quality),
            "-compression_level",
            "6",
            str(out_path),
        ]
        result = run(cmd)
        if result.returncode != 0 or not out_path.exists():
            best_note = result.stderr.strip() or "ffmpeg conversion failed"
            continue
        output_size = out_path.stat().st_size
        if output_size <= TARGET_BYTES:
            converted_info = inspect_media(
                ffprobe=os.environ["FFPROBE_PATH"],
                media_path=out_path,
            )
            return True, {
                "width": converted_info.width,
                "height": converted_info.height,
                "fps": fps,
                "quality": quality,
                "size": output_size,
            }
        best_note = f"best attempt still {output_size} bytes"
    return False, {"note": best_note}


def format_mb(value: int | None) -> str:
    if value is None:
        return "-"
    return f"{value / (1024 * 1024):.2f} MB"


def write_report(report_path: Path, results: list[ConversionResult]) -> None:
    lines = [
        "# Animated Icon Conversion Report",
        "",
        f"- Total animated entries inspected: {len(results)}",
        f"- Converted to WebP: {sum(1 for r in results if r.action == 'converted')}",
        f"- Kept as-is: {sum(1 for r in results if r.action == 'kept')}",
        f"- Failed to fit under 4 MB: {sum(1 for r in results if r.action == 'failed')}",
        "",
        "| Entry | Action | Source Size | Output Size | Output Entry | Notes |",
        "| --- | --- | ---: | ---: | --- | --- |",
    ]
    for item in results:
        note_parts = []
        if item.width and item.height:
            note_parts.append(f"{item.width}x{item.height}")
        if item.fps:
            note_parts.append(f"{item.fps:.2f} fps")
        if item.quality is not None:
            note_parts.append(f"q={item.quality}")
        if item.note:
            note_parts.append(item.note)
        lines.append(
            "| "
            + " | ".join(
                [
                    item.zip_entry,
                    item.action,
                    format_mb(item.source_size),
                    format_mb(item.output_size),
                    item.output_entry or "-",
                    ", ".join(note_parts) or "-",
                ]
            )
            + " |"
        )
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def should_keep(zip_entry: str, source_size: int) -> bool:
    ext = Path(zip_entry).suffix.lower()
    return ext in KEEPABLE_EXTS and source_size <= TARGET_BYTES


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-xtf", required=True)
    parser.add_argument("--output-xtf", required=True)
    parser.add_argument("--backup-dir", required=True)
    parser.add_argument("--report-path", required=True)
    parser.add_argument("--ffmpeg", required=True)
    parser.add_argument("--ffprobe", required=True)
    args = parser.parse_args()

    os.environ["FFPROBE_PATH"] = args.ffprobe

    source_xtf = Path(args.source_xtf)
    output_xtf = Path(args.output_xtf)
    backup_dir = Path(args.backup_dir)
    report_path = Path(args.report_path)

    backup_dir.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="xtf_anim_convert_") as tmp_dir_str:
        tmp_dir = Path(tmp_dir_str)
        extracted_root = tmp_dir / "extract"
        converted_root = tmp_dir / "converted"
        extracted_root.mkdir(parents=True, exist_ok=True)
        converted_root.mkdir(parents=True, exist_ok=True)

        results: list[ConversionResult] = []
        replacements: dict[str, Path] = {}
        animated_entries: set[str] = set()

        with zipfile.ZipFile(source_xtf, "r") as source_zip:
            names = source_zip.namelist()
            for entry in names:
                ext = Path(entry).suffix.lower()
                base = Path(entry).name.upper()
                if base not in {"ICON1.GIF", "ICON1.WEBP", "ICON1.APNG", "ICON1.MP4"}:
                    continue
                if ext not in SUPPORTED_EXTS:
                    continue
                animated_entries.add(entry)
                extracted_path = extracted_root / entry
                extracted_path.parent.mkdir(parents=True, exist_ok=True)
                with source_zip.open(entry) as source_file, open(extracted_path, "wb") as out_file:
                    shutil.copyfileobj(source_file, out_file)

                backup_path = backup_dir / entry
                backup_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(extracted_path, backup_path)

                source_size = extracted_path.stat().st_size
                if should_keep(entry, source_size):
                    results.append(
                        ConversionResult(
                            zip_entry=entry,
                            action="kept",
                            source_size=source_size,
                            output_entry=entry,
                            output_size=source_size,
                            note="already under 4 MB",
                        )
                    )
                    replacements[entry] = extracted_path
                    continue

                converted_entry = webp_output_name(entry)
                converted_path = converted_root / converted_entry
                converted_path.parent.mkdir(parents=True, exist_ok=True)
                source_info = inspect_media(args.ffprobe, extracted_path)
                ok, info = convert_to_webp(args.ffmpeg, extracted_path, source_info, converted_path)
                if ok:
                    results.append(
                        ConversionResult(
                            zip_entry=entry,
                            action="converted",
                            source_size=source_size,
                            output_entry=converted_entry,
                            output_size=int(info["size"]),
                            fps=float(info["fps"]),
                            width=int(info["width"]),
                            height=int(info["height"]),
                            quality=int(info["quality"]),
                            note=f"converted from {Path(entry).suffix.lower()}",
                        )
                    )
                    replacements[converted_entry] = converted_path
                else:
                    results.append(
                        ConversionResult(
                            zip_entry=entry,
                            action="failed",
                            source_size=source_size,
                            note=str(info["note"]),
                        )
                    )

            failed = [item for item in results if item.action == "failed"]
            if failed:
                write_report(report_path, results)
                print(f"Failed to convert {len(failed)} entries under 4 MB.")
                return 2

            with zipfile.ZipFile(source_xtf, "r") as source_zip, zipfile.ZipFile(
                output_xtf, "w", compression=zipfile.ZIP_DEFLATED
            ) as output_zip:
                for entry in source_zip.infolist():
                    if entry.filename in animated_entries:
                        continue
                    output_zip.writestr(entry, source_zip.read(entry.filename))
                for entry_name, file_path in sorted(replacements.items()):
                    output_zip.write(file_path, arcname=entry_name)

        write_report(report_path, results)
        print(f"Converted XTF written to {output_xtf}")
        print(f"Report written to {report_path}")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
