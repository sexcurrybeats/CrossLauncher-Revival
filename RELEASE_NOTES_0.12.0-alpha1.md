# CrossLauncher 0.12.0-alpha1 prerelease notes

This is the first signed prerelease for CrossLauncher Revival.

## Highlights

- cleaner first-run shell experience
- handheld layout defaults tuned for controller-first use
- debug/internal settings hidden by default
- optional service nodes stay out of the way until configured
- XTF customization support for shell-level settings and assets
- improved wave startup behavior on fresh launch and reset

## Included in this prerelease

- signed release build
- reset-to-defaults flow
- XTF import/export support
- browser node integration
- shell customization support for:
  - icons
  - menu sounds
  - coldboot
  - gameboot
  - battery glyph

## Known limitations

- on a fresh install, Android may ask which launcher should handle the Home action until CrossLauncher is set as the default launcher
- some optional service nodes still need user-assigned targets before they become useful
- Music and Video are not as polished as Photo yet
- broader TV-focused shell work is planned, but this prerelease is still primarily focused on the handheld side

## XTF note

XTF is currently intended for shell-level customization and restore.

New XTF exports do **not** include per-app or per-game icon/backdrop media anymore.

## Reporting issues

Use the guidance in `ISSUE_REPORTING.md` so reports are reproducible and actionable.
