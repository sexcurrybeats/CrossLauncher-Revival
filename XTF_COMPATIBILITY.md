# XTF compatibility notes

XTF is CrossLauncher’s portable shell-state package format.

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

## What new XTF exports do not carry

New exports no longer include per-app or per-game icon/backdrop media.

That means exported XTF packages are now focused on the main shell identity and launcher state rather than acting like a full per-app media backup.

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
