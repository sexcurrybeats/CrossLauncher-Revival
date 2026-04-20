# CrossLauncher 0.12.0-alpha4 prerelease notes

This prerelease is focused on readability and visual polish in the handheld shell.

## Highlights

- added a PSP-style text shadow pass across shell text
- shell icon changes from `Customize Shell Icons` now refresh live without restarting the launcher
- default category, node, and settings fallback icons now use a softer built-in shadow treatment
- handheld battery presentation was tightened for both the default battery and custom battery glyph lanes

## Included in this prerelease

- signed release build
- custom top-level categories
- reset-to-defaults flow
- XTF import/export support
- browser node integration
- shell customization support for:
  - icons
  - menu sounds
  - wave settings
  - coldboot
  - gameboot
  - battery glyph

## Setup notes

- If media-based features are missing files or not behaving correctly, grant access at `Settings -> Media Settings -> Media Access`.
- XTF import itself uses Android's document picker, but media access is still recommended for other media-backed launcher features.
- If you want your own wallpaper or live wallpaper behind the shell, disable `Use as Internal Layer` in `Settings -> Wave Wallpaper Settings`.

## Known limitations

- on a fresh install, Android may ask which launcher should handle the Home action until CrossLauncher is set as the default launcher
- some optional service nodes still need user-assigned targets before they become useful
- Music and Video are not as polished as Photo yet
- broader TV-focused shell work is planned, but this prerelease is still primarily focused on the handheld side
- device testing coverage is still narrow

## XTF note

XTF is intended for shell-level customization and restore.

Current exports keep shell preferences and theme-related state, but they do not include per-app or per-game icon/backdrop media, and they do not include custom file or ROM launcher shortcuts.

## Reporting issues

Use the guidance in `ISSUE_REPORTING.md` so reports are reproducible and actionable.
