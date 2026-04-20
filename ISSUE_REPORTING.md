# Issue reporting

For prerelease builds, bug reports should be concrete and reproducible.

Minimum useful report:

- device model
- Android version
- build used
  - branch/tag if built locally, or APK filename/checksum if installed from a packaged prerelease
- whether the launcher was in fresh-install state, reset state, or after XTF import
- exact category/node/screen involved
- exact input sequence
  - for example: `Settings -> Theme Settings -> Wave Wallpaper Settings -> Wave Theme -> press Right once`
- expected result
- actual result
- whether the problem reproduces after `Reset All Customizations`
- screenshot or short video if the issue is visual
- `adb logcat` excerpt if the issue is a crash

Preferred report buckets:

- regression
- shell behavior/authenticity mismatch
- customization/XTF issue
- performance hitch/stutter
- platform integration issue
- install/signing/update problem

If the report involves XTF, include:

- the XTF filename
- whether it was imported through the in-app picker
- whether the same issue reproduces on the public-default state without the XTF
