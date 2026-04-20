# Changelog

## 0.12.0-alpha3 prerelease

### Highlights

- widened the shell icon customization picker so long node and settings paths can be read before selecting them
- shell icon path browsing now uses a wide menu layout without making normal triangle menus oversized

### Theme customization

- `Customize Shell Icons` now opens in a wider side menu mode
- deep `Node` and `Settings` icon targets are easier to read and browse
- the shell icon picker remains the only menu using the expanded width, so normal app and context menus stay compact

## 0.12.0-alpha2 prerelease

### Highlights

- added custom top-level categories
- improved the on-screen Options hint panel alignment and timing
- custom file and ROM launch items now also show the Options hint
- XTF exports now exclude file and ROM launch items while still carrying shell-level theme data, nodes, and web links

### Custom categories

- users can create custom top-level categories from Manage Custom Items
- file and ROM launch items can now target a selected category instead of always landing in Game
- deleting a custom category safely rehomes its contents back to Apps

### XTF behavior

- XTF is now a cleaner theme-sharing lane
- exported packages keep shell settings and theme-related state
- exported packages no longer carry file and ROM launcher shortcuts between devices

### UX polish

- the triangle Options hint is measured and centered more cleanly
- the hint stays visible longer


## 0.12.0-alpha1 prerelease

### Highlights

- cleaned up the default shell experience so it feels less developer-facing
- tightened settings wording and organization
- reduced context-menu clutter
- improved fresh-install and reset defaults
- improved wave startup behavior on boot and reset
- added signed prerelease build support

### Shell and navigation

- handheld mode now starts with handheld-oriented defaults
- circle/cancel behavior is aligned with the current handheld default setup
- browser node launches the device browser
- placeholder service nodes stay hidden until configured
- Recent Games is now a real launcher-managed recent list for game apps

### Customization

- XTF import/export is available for shell-level launcher state
- shell icon overrides, menu sounds, coldboot, gameboot, and battery glyph overrides are supported through XTF
- XTF export is now focused on shell-level customization and no longer exports per-app or per-game icon/backdrop media

### Visual and UX polish

- top-right HUD behavior and alignment were improved
- inner settings wording was cleaned up
- shell labels were simplified for public-facing use
- animated icon handling is stricter and more performance-friendly

### Known open areas

- some default asset choices still need one more explicit release review
- some service nodes still depend on user-assigned targets
- Music and Video still need deeper polish
- TV-focused shell work is planned later
