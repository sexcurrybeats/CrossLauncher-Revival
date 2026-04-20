#!/usr/bin/env python3
"""Compare a CrossLauncher XTF package against bundled launcher defaults.

This is an audit tool, not an importer. It never modifies the XTF or project
assets. The comparison is intentionally conservative: if the app resolves a
default through code/resource IDs rather than a direct bundled file path, the
report marks that item for manual review instead of guessing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree


PROJECT_DEFAULT_DIRECT_FILES = {
    "font": Path("launcher_app/src/main/assets/fonts/newrodin.otf"),
    "coldboot_image": Path("launcher_app/src/main/res/drawable-nodpi/coldboot_internal.png"),
    "gameboot_image": Path("launcher_app/src/main/res/drawable-nodpi/gameboot_internal.png"),
}

UPSTREAM_CATEGORY_DRAWABLES = {
    "vsh_home": "category_home.xml",
    "vsh_apps": "category_apps.xml",
    "vsh_game": "category_games.xml",
    "vsh_video": "category_video.xml",
    "vsh_shortcut": "category_shortcut.xml",
    "vsh_photos": "category_photo.xml",
    "vsh_music": "category_music.xml",
    "vsh_settings": "category_setting.xml",
}

UPSTREAM_ICON_DRAWABLE_HINTS = {
    "settings_system_language": "icon_language.xml",
    "settings_category_debug": "category_debug.xml",
}

ICON_MANAGER_PATH = Path("launcher_app/src/main/java/id/psw/vshlauncher/submodules/IconManager.kt")
OG_ICON_ROOT = Path("launcher_app/src/main/assets/og icons")
DEFAULT_ICON_ROOT = Path("launcher_app/src/main/assets/default_psp_icons")
CATEGORY_ICON_ROOT = DEFAULT_ICON_ROOT / "Catergory Icons"
NODE_ICON_ROOT = DEFAULT_ICON_ROOT / "First Level Icons"
UTILITY_ICON_ROOT = DEFAULT_ICON_ROOT / "Supplemental/Utility Icons"
SETTINGS_ICON_ROOT = DEFAULT_ICON_ROOT / "Supplemental/Settings second Level Icons"

IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}
ANIMATED_IMAGE_EXTENSIONS = {"gif", "apng", "webp"}
SOUND_EXTENSIONS = {"aac", "ogg", "mp3", "wav", "mid", "midi"}
ICON_EXTENSIONS = IMAGE_EXTENSIONS | {"gif"}


@dataclass(frozen=True)
class ZipAsset:
    name: str
    size: int
    sha256: str


@dataclass(frozen=True)
class DefaultCandidate:
    path: Path
    exists: bool
    sha256: str | None
    note: str


@dataclass(frozen=True)
class GitBlob:
    ref: str
    path: str
    exists: bool
    size: int | None
    sha256: str | None


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str | None:
    if not path.exists() or not path.is_file():
        return None
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def git_blob(project_root: Path, ref: str, rel_path: str | Path) -> GitBlob:
    path = str(rel_path).replace("\\", "/")
    spec = f"{ref}:{path}"
    result = subprocess.run(
        ["git", "show", spec],
        cwd=project_root,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        return GitBlob(ref, path, False, None, None)
    data = result.stdout
    return GitBlob(ref, path, True, len(data), sha256_bytes(data))


def git_ls_tree(project_root: Path, ref: str, paths: list[str]) -> list[str]:
    result = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", ref, "--", *paths],
        cwd=project_root,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return []
    return sorted(line.strip() for line in result.stdout.splitlines() if line.strip())


def human_size(size: int) -> str:
    units = ["B", "KB", "MB", "GB"]
    value = float(size)
    for unit in units:
        if value < 1024.0 or unit == units[-1]:
            if unit == "B":
                return f"{int(value)} {unit}"
            return f"{value:.1f} {unit}"
        value /= 1024.0
    return f"{size} B"


def read_zip_assets(xtf_path: Path) -> tuple[list[ZipAsset], dict[str, bytes]]:
    assets: list[ZipAsset] = []
    data_by_name: dict[str, bytes] = {}
    with zipfile.ZipFile(xtf_path) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            data = z.read(info.filename)
            data_by_name[info.filename] = data
            assets.append(ZipAsset(info.filename, info.file_size, sha256_bytes(data)))
    assets.sort(key=lambda item: item.name.lower())
    return assets, data_by_name


def parse_kotlin_map(text: str, map_name: str) -> dict[str, str]:
    match = re.search(
        rf"private\s+val\s+{re.escape(map_name)}\s*=\s*mapOf\((.*?)\n\s*\)",
        text,
        flags=re.DOTALL,
    )
    if not match:
        return {}
    body = match.group(1)
    return dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', body))


def load_icon_mappings(project_root: Path) -> dict[str, dict[str, str]]:
    icon_manager = project_root / ICON_MANAGER_PATH
    if not icon_manager.exists():
        return {}
    text = icon_manager.read_text(encoding="utf-8", errors="replace")
    return {
        "category": parse_kotlin_map(text, "categoryMappings"),
        "og_category": parse_kotlin_map(text, "ogCategoryMappings"),
        "node": parse_kotlin_map(text, "nodeMappings"),
        "og_node": parse_kotlin_map(text, "ogNodeMappings"),
        "settings": parse_kotlin_map(text, "settingsMappings"),
    }


def default_candidate(project_root: Path, rel_path: Path, note: str) -> DefaultCandidate:
    path = project_root / rel_path
    return DefaultCandidate(
        path=rel_path,
        exists=path.exists() and path.is_file(),
        sha256=sha256_file(path),
        note=note,
    )


def icon_default_candidates(
    project_root: Path,
    mappings: dict[str, dict[str, str]],
    icon_type_dir: str,
    icon_id: str,
) -> list[DefaultCandidate]:
    candidates: list[DefaultCandidate] = []
    if icon_type_dir == "categories":
        og = mappings.get("og_category", {}).get(icon_id)
        primary = mappings.get("category", {}).get(icon_id)
        if og:
            candidates.append(default_candidate(project_root, OG_ICON_ROOT / og, "bundled OG-priority category icon"))
        if primary:
            candidates.append(default_candidate(project_root, CATEGORY_ICON_ROOT / primary, "bundled default category icon"))
    elif icon_type_dir == "nodes":
        og = mappings.get("og_node", {}).get(icon_id)
        primary = mappings.get("node", {}).get(icon_id)
        if og:
            candidates.append(default_candidate(project_root, OG_ICON_ROOT / og, "bundled OG-priority node icon"))
        if primary:
            candidates.append(default_candidate(project_root, NODE_ICON_ROOT / primary, "bundled default node icon"))
            candidates.append(default_candidate(project_root, UTILITY_ICON_ROOT / primary, "bundled utility node icon fallback"))
    elif icon_type_dir == "settings":
        primary = mappings.get("settings", {}).get(icon_id)
        if primary:
            candidates.append(
                default_candidate(
                    project_root,
                    OG_ICON_ROOT / primary.replace(".32bit", ""),
                    "bundled OG-priority settings icon",
                )
            )
            candidates.append(default_candidate(project_root, SETTINGS_ICON_ROOT / primary, "bundled default settings icon"))
    return candidates


def classify_entry(name: str) -> str:
    lower = name.lower()
    if lower in {"param.ini", "manifest.json"}:
        return "package_metadata"
    if lower.startswith("prefs/"):
        return "preferences"
    if lower.startswith("refs/"):
        return "portable_refs"
    if lower.startswith("fonts/active_font."):
        return "font_override"
    if lower.startswith("themes/active_theme."):
        return "embedded_theme"
    if lower.startswith("custom/custom_icons/"):
        return "icon_override"
    if lower.startswith("custom/dev_hdd0/game/"):
        return "app_customization"
    if lower.startswith("custom/dev_flash/vsh/resource/sfx/"):
        return "menu_sfx_override"
    if lower.startswith("custom/dev_flash/vsh/resource/"):
        filename = name.rsplit("/", 1)[-1]
        stem = filename.rsplit(".", 1)[0].lower()
        ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
        if stem == "coldboot":
            return "coldboot_override" if ext in IMAGE_EXTENSIONS | SOUND_EXTENSIONS else "custom_resource"
        if stem == "gameboot":
            return "gameboot_override" if ext in IMAGE_EXTENSIONS | ANIMATED_IMAGE_EXTENSIONS | SOUND_EXTENSIONS else "custom_resource"
        return "custom_resource"
    return "other"


def parse_pref_keys(xml_data: bytes) -> list[str]:
    try:
        root = ElementTree.fromstring(xml_data)
    except ElementTree.ParseError:
        return []
    keys: list[str] = []
    for child in root:
        key = child.attrib.get("name")
        if key:
            keys.append(key)
    return sorted(keys)


def entry_ext(name: str) -> str:
    return name.rsplit(".", 1)[-1].lower() if "." in name else ""


def direct_default_key_for_entry(name: str) -> str | None:
    lower = name.lower()
    ext = entry_ext(lower)
    if lower.startswith("fonts/active_font."):
        return "font"
    if lower.startswith("custom/dev_flash/vsh/resource/coldboot.") and ext in IMAGE_EXTENSIONS:
        return "coldboot_image"
    if lower.startswith("custom/dev_flash/vsh/resource/gameboot.") and ext in IMAGE_EXTENSIONS | ANIMATED_IMAGE_EXTENSIONS:
        return "gameboot_image"
    return None


def markdown_table(headers: Iterable[str], rows: Iterable[Iterable[str]]) -> list[str]:
    header_list = list(headers)
    lines = [
        "| " + " | ".join(header_list) + " |",
        "| " + " | ".join("---" for _ in header_list) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(str(cell).replace("\n", "<br>") for cell in row) + " |")
    return lines


def app_customization_summary(assets: list[ZipAsset]) -> list[tuple[str, int, str]]:
    by_app: dict[str, list[str]] = defaultdict(list)
    prefix = "custom/dev_hdd0/game/"
    for asset in assets:
        if not asset.name.startswith(prefix):
            continue
        rel = asset.name.removeprefix(prefix)
        if "/" not in rel:
            continue
        app_id, file_name = rel.split("/", 1)
        by_app[app_id].append(file_name)
    rows: list[tuple[str, int, str]] = []
    for app_id, files in sorted(by_app.items()):
        visible = ", ".join(sorted(files)[:8])
        if len(files) > 8:
            visible += f", +{len(files) - 8} more"
        rows.append((app_id, len(files), visible))
    return rows


def build_report(project_root: Path, xtf_path: Path, out_path: Path | None, upstream_ref: str | None) -> str:
    assets, data_by_name = read_zip_assets(xtf_path)
    categories = Counter(classify_entry(asset.name) for asset in assets)
    mappings = load_icon_mappings(project_root)

    lines: list[str] = []
    lines.append("# XTF vs Bundled Defaults")
    lines.append("")
    lines.append(f"- XTF: `{xtf_path}`")
    lines.append(f"- Project root: `{project_root}`")
    if upstream_ref:
        lines.append(f"- Upstream/original Git baseline: `{upstream_ref}`")
    lines.append(f"- Files in XTF: {len(assets)}")
    lines.append(f"- Total payload size: {human_size(sum(asset.size for asset in assets))}")
    lines.append("")

    if upstream_ref:
        asset_paths = git_ls_tree(
            project_root,
            upstream_ref,
            [
                "launcher_app/src/main/assets",
                "launcher_app/src/main/res/drawable",
                "launcher_app/src/main/res/drawable-nodpi",
            ],
        )
        key_rows = []
        key_checks = [
            ("Bundled drawable icon set", "launcher_app/src/main/res/drawable/icon_language.xml"),
            ("Bundled category icon set", "launcher_app/src/main/res/drawable/category_apps.xml"),
            ("Bundled coldboot default", "launcher_app/src/main/res/drawable-nodpi/coldboot_internal.png"),
            ("Bundled gameboot default", "launcher_app/src/main/res/drawable-nodpi/gameboot_internal.png"),
            ("Bundled button glyph font", "launcher_app/src/main/assets/vshbtn.ttf"),
            ("Local default icon asset folder", "launcher_app/src/main/assets/default_psp_icons/Catergory Icons/game.png"),
            ("Local legacy icon asset folder", "launcher_app/src/main/assets/og icons/tex_game.png"),
            ("Local bundled default font", "launcher_app/src/main/assets/fonts/newrodin.otf"),
        ]
        for label, rel in key_checks:
            blob = git_blob(project_root, upstream_ref, rel)
            key_rows.append([label, rel, "Present" if blob.exists else "Not present"])
        lines.append("## Upstream GitHub Baseline")
        lines.append("")
        lines.append(
            "This section checks the original Git-tracked baseline configured for comparison in the current repo."
        )
        lines.append("")
        lines.append(f"- Tracked asset/resource files under baseline: {len(asset_paths)}")
        lines.append("")
        lines.extend(markdown_table(["Asset group", "Path checked", "Baseline result"], key_rows))
        lines.append("")

    manifest_data = data_by_name.get("manifest.json")
    if manifest_data:
        try:
            manifest = json.loads(manifest_data.decode("utf-8"))
            sections = ", ".join(manifest.get("includedSections", [])) or "(none listed)"
            lines.append("## Package Metadata")
            lines.append("")
            lines.append(f"- Title: `{manifest.get('title', '(missing)')}`")
            lines.append(f"- App version in package: `{manifest.get('appVersion', '(missing)')}`")
            lines.append(f"- Included sections: {sections}")
            lines.append("")
        except json.JSONDecodeError:
            lines.append("## Package Metadata")
            lines.append("")
            lines.append("- `manifest.json` exists but could not be parsed as JSON.")
            lines.append("")

    lines.append("## Summary")
    lines.append("")
    summary_rows = ((name, count) for name, count in sorted(categories.items()))
    lines.extend(markdown_table(["Section", "Count"], summary_rows))
    lines.append("")

    lines.append("## Direct Default Overrides")
    lines.append("")
    direct_rows: list[list[str]] = []
    direct_seen = set()
    for asset in assets:
        key = direct_default_key_for_entry(asset.name)
        if key is None:
            continue
        direct_seen.add(key)
        default_rel = PROJECT_DEFAULT_DIRECT_FILES[key]
        default_abs = project_root / default_rel
        default_sha = sha256_file(default_abs)
        upstream_blob = git_blob(project_root, upstream_ref, default_rel) if upstream_ref else None
        if default_sha is None:
            status = "Default missing in repo"
        elif asset.sha256 == default_sha:
            status = "Matches bundled default bytes"
        else:
            status = "Differs from bundled default"
        if upstream_blob is not None:
            if upstream_blob.exists and upstream_blob.sha256 == asset.sha256:
                status += "; matches upstream Git bytes"
            elif upstream_blob.exists:
                status += "; differs from upstream Git bytes"
            else:
                status += "; no upstream Git default at this path"
        direct_rows.append([
            asset.name,
            human_size(asset.size),
            str(default_rel),
            status,
        ])

    for key, default_rel in PROJECT_DEFAULT_DIRECT_FILES.items():
        if key not in direct_seen:
            default_abs = project_root / default_rel
            status = "No XTF override; app uses this default if no runtime override exists"
            if not default_abs.exists():
                status = "No XTF override; expected bundled default is missing"
            if upstream_ref:
                upstream_blob = git_blob(project_root, upstream_ref, default_rel)
                if upstream_blob.exists:
                    status += "; this file exists in upstream Git"
                else:
                    status += "; this file does not exist in upstream Git"
            direct_rows.append(["(not present)", "-", str(default_rel), status])

    lines.extend(markdown_table(["XTF entry", "Size", "Bundled default", "Result"], direct_rows))
    lines.append("")

    icon_assets = [asset for asset in assets if classify_entry(asset.name) == "icon_override"]
    lines.append("## Icon Overrides")
    lines.append("")
    if icon_assets:
        icon_rows: list[list[str]] = []
        icon_pattern = re.compile(r"^custom/custom_icons/(categories|nodes|settings)/([^/]+)\.([^.\\/]+)$")
        for asset in icon_assets:
            match = icon_pattern.match(asset.name)
            if not match:
                icon_rows.append([asset.name, human_size(asset.size), "Unknown custom icon path", "Manual review"])
                continue
            icon_type, icon_id, ext = match.groups()
            candidates = icon_default_candidates(project_root, mappings, icon_type, icon_id)
            existing_candidates = [c for c in candidates if c.exists]
            matching = [c for c in existing_candidates if c.sha256 == asset.sha256]
            if matching:
                result = "Matches bundled candidate bytes"
                default_text = "<br>".join(f"`{c.path}` ({c.note})" for c in matching)
            elif existing_candidates:
                result = "Differs from bundled candidate"
                default_text = "<br>".join(f"`{c.path}` ({c.note})" for c in existing_candidates)
            elif candidates:
                result = "Mapped, but bundled candidate file is missing"
                default_text = "<br>".join(f"`{c.path}` ({c.note})" for c in candidates)
            else:
                result = "No direct bundled-file mapping found; app may fall back to drawable/resource ID"
                default_text = "-"
            if upstream_ref:
                upstream_hint = None
                if icon_type == "categories":
                    drawable_name = UPSTREAM_CATEGORY_DRAWABLES.get(icon_id)
                    if drawable_name:
                        upstream_hint = f"launcher_app/src/main/res/drawable/{drawable_name}"
                elif icon_type == "settings":
                    drawable_name = UPSTREAM_ICON_DRAWABLE_HINTS.get(icon_id)
                    if drawable_name:
                        upstream_hint = f"launcher_app/src/main/res/drawable/{drawable_name}"
                if upstream_hint:
                    blob = git_blob(project_root, upstream_ref, upstream_hint)
                    hint_text = f"`{upstream_hint}` ({'present' if blob.exists else 'not present'} in `{upstream_ref}`)"
                    default_text = f"{default_text}<br>{hint_text}" if default_text != "-" else hint_text
                    result += "; upstream drawable fallback identified"
                else:
                    result += "; no upstream drawable hint known"
            icon_rows.append([f"{icon_type}/{icon_id}.{ext}", human_size(asset.size), default_text, result])
        lines.extend(markdown_table(["Override", "Size", "Default candidate", "Result"], icon_rows))
    else:
        lines.append("No `custom/custom_icons/...` entries found.")
    lines.append("")

    resource_assets = [
        asset
        for asset in assets
        if classify_entry(asset.name)
        in {"coldboot_override", "gameboot_override", "menu_sfx_override", "custom_resource", "embedded_theme", "font_override"}
    ]
    lines.append("## Theme And Resource Entries")
    lines.append("")
    if resource_assets:
        resource_rows = (
            [asset.name, classify_entry(asset.name), human_size(asset.size), asset.sha256[:12]]
            for asset in resource_assets
        )
        lines.extend(markdown_table(["Entry", "Type", "Size", "SHA-256 prefix"], resource_rows))
    else:
        lines.append("No font, theme, coldboot, gameboot, menu SFX, or raw `dev_flash` resource entries found.")
    lines.append("")

    app_rows = app_customization_summary(assets)
    lines.append("## App Customization Entries")
    lines.append("")
    if app_rows:
        lines.append(
            "These are per-app launcher records/assets. They do not replace bundled launcher defaults, "
            "but importing the XTF restores this app list/customization state."
        )
        lines.append("")
        lines.extend(markdown_table(["App/custom item id", "Files", "Entries"], app_rows))
    else:
        lines.append("No `custom/dev_hdd0/game/...` app customization entries found.")
    lines.append("")

    pref_assets = [asset for asset in assets if classify_entry(asset.name) == "preferences"]
    lines.append("## Preference Files")
    lines.append("")
    if pref_assets:
        pref_rows: list[list[str]] = []
        for asset in pref_assets:
            keys = parse_pref_keys(data_by_name[asset.name])
            key_text = ", ".join(keys[:20])
            if len(keys) > 20:
                key_text += f", +{len(keys) - 20} more"
            pref_rows.append([asset.name, human_size(asset.size), str(len(keys)), key_text or "(no keys parsed)"])
        lines.extend(markdown_table(["Entry", "Size", "Keys", "Parsed keys"], pref_rows))
        lines.append("")
        lines.append(
            "Preference XML is listed but not compared against a clean install baseline, because bundled "
            "defaults are mostly code defaults and device-dependent runtime state."
        )
    else:
        lines.append("No preference XML entries found.")
    lines.append("")

    lines.append("## Read This Result")
    lines.append("")
    lines.append(
        "- `Differs from bundled default` means the XTF contains a real replacement for that bundled asset."
    )
    lines.append(
        "- `No XTF override` means this package does not carry that asset, so the app falls back to its bundled default unless another runtime override exists on device."
    )
    lines.append(
        "- Icon comparison is best-effort against direct asset files; drawable-resource fallbacks are marked for manual review."
    )
    lines.append(
        "- This report does not decide whether an asset is public-release-safe. Use `ASSET_AUDIT.md` for licensing/public shipping status."
    )
    if upstream_ref:
        lines.append(
            "- Upstream Git defaults are useful for provenance, but anything visually platform-identifying still needs asset-policy review before public release."
        )
    lines.append("")

    report = "\n".join(lines)
    if out_path:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(report, encoding="utf-8")
    return report


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xtf", required=True, type=Path, help="Path to the .xtf package to inspect.")
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path.cwd(),
        help="CrossLauncher project root. Defaults to the current working directory.",
    )
    parser.add_argument(
        "--upstream-ref",
        help="Optional Git ref to treat as the original/upstream asset baseline, for example origin/master.",
    )
    parser.add_argument("--out", type=Path, help="Optional Markdown report output path.")
    args = parser.parse_args(argv)

    project_root = args.project_root.resolve()
    xtf_path = args.xtf
    if not xtf_path.is_absolute():
        xtf_path = project_root / xtf_path
    xtf_path = xtf_path.resolve()
    out_path = args.out
    if out_path is not None and not out_path.is_absolute():
        out_path = project_root / out_path

    if not xtf_path.exists():
        print(f"XTF not found: {xtf_path}", file=sys.stderr)
        return 2
    if not zipfile.is_zipfile(xtf_path):
        print(f"Not a valid ZIP/XTF package: {xtf_path}", file=sys.stderr)
        return 2

    report = build_report(project_root, xtf_path, out_path, args.upstream_ref)
    if out_path:
        print(f"Wrote {out_path}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
