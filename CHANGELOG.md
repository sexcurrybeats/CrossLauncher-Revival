# Changelog

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
