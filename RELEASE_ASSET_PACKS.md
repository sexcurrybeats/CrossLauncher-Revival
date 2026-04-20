# Release asset policy

CrossLauncher should ship with a clean default identity.

That means:

- the default APK should use assets that are safe to redistribute
- optional customization should happen through XTF
- the public repo should stay focused on the shipped product, not backup packs or local test material

## Default shipped experience

The public build is intended to ship with:

- the default launcher font used by the current public build
- the current default shell icon set
- the current default coldboot/gameboot media used by the public build
- the current default battery glyph and wave presentation used by the public build

## What customization is for

XTF exists so users can move shell customization between installs without changing the shipped default build.

Typical XTF uses:

- icon overrides
- menu sounds
- coldboot/gameboot media
- battery glyph overrides
- launcher preference state

## Public repo rule

The public repo should contain:

- the source and assets needed for the shipped default build
- documentation for supported customization and release behavior

The public repo should not contain:

- private backup packs
- personal theme packs
- one-off release backups
- local QA or test artifacts

## Current status

The current public repo is intended to represent the shipped default product state, while XTF remains the customization path on top of it.
