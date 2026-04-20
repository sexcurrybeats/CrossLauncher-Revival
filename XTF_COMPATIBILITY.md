# XTF compatibility notes

XTF is CrossLauncher's portable shell-state package format.

It is meant for moving launcher customization between installs or devices that are running compatible builds of CrossLauncher.

## Package basics

- file extension: `.xtf`
- container: zip
- package type: `crosslauncher.shell-state`
- current format version: `1`

## What XTF currently carries

- launcher preferences
- wave preferences
- shell icon overrides
- coldboot overrides
- gameboot overrides
- core menu sound overrides
- HUD battery glyph overrides
- theme/font references
- app-owned active theme/font assets when available

## Supported shell asset formats

The launcher currently expects these shell-level asset lanes:

- `ICON0`: static icon
  - formats: `png`, `webp`, `jpg`, `jpeg`
- `ICON1`: animated icon
  - formats: `gif`, `webp`, `apng`
  - practical limit: `4 MB` max
- `PIC1`: backdrop
  - formats: standard image formats
- `PIC0`: overlay
  - formats: standard image formats
- `PIC1_P`: portrait backdrop
  - formats: standard image formats
- `PIC0_P`: portrait overlay
  - formats: standard image formats
- `SND0`: per-item back sound
  - formats: supported sound formats such as `aac`, `ogg`, `mp3`, `wav`, `mid`, `midi`

Shell resource override lanes exposed through Settings currently use:

- shell icon overrides: `png`, `webp`, `jpg`, `jpeg`
- menu sound overrides: `ogg`, `wav`, `mp3`
- coldboot image: standard image formats
- coldboot audio: standard audio import, with a short-duration expectation
- gameboot image/animation: static image or animated `gif` / `webp` / `apng`
- gameboot audio: standard audio import
- battery glyph: standard image formats

Animated icon note:

- `mp4` is not part of the current animated icon import/export path
- if an animated icon is too large, keep the same motion but reduce size or convert it to a lighter `webp`

## What new XTF exports do not carry

New exports no longer include per-app or per-game icon/backdrop media.

New exports also do not include custom file or ROM launch items.

That means exported XTF packages are now focused on the main shell identity and launcher state rather than acting like a full per-app media or launcher-shortcut backup.

## Making and sharing a theme

The normal theme-sharing flow is:

- configure your shell the way you want in Settings
- set up your shell-level icons, menu sounds, wave settings, coldboot, gameboot, and related customization
- export an XTF package from the launcher
- share that `.xtf` file with other users

On the receiving side, another user can import that XTF package through the launcher to apply the shared shell setup on a compatible build.

## Compatibility boundaries

- XTF is intended for compatible CrossLauncher builds, not as a general Android backup format
- imported app/package references may point to software that does not exist on another device
- imported file-based items may still depend on file access that does not exist on another device
- older builds should reject newer unsupported package versions once that validation is fully hardened

## Import behavior

- import restores through the existing launcher settings and resource override systems
- successful import restarts the launcher so the restored state is applied cleanly

## What still needs broader hardening

- unsupported/newer package version rejection
- corrupt package rejection
- more cross-device edge-case testing
