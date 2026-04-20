# CrossLauncher 0.12.0-alpha2 prerelease notes

This prerelease expands the handheld shell line with custom top-level categories and tighter theme-sharing behavior.

## Highlights

- custom top-level categories can now be created from Manage Custom Items
- file and ROM launch items can now target a selected category
- the triangle Options hint is centered more cleanly, stays visible longer, and also appears on custom file and ROM launch items
- XTF exports now exclude custom file and ROM launch items while still carrying shell-level theme data, custom nodes, and web links

## Included in this prerelease

- signed release build
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
- custom top-level categories

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
