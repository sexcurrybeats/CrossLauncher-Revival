# CrossLauncher 0.12.0-alpha3 prerelease notes

This prerelease tightens the shell icon customization flow so long target names are readable before you select them.

## Highlights

- the shell icon customization picker now uses a wider side menu layout
- long `Node` and `Settings` icon target paths can be read more cleanly
- the wider menu treatment is limited to the shell icon picker, so regular triangle menus stay compact

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
