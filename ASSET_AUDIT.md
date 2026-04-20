# Asset audit

This file describes the current release-facing asset policy for the public repo and APK.

## Goal

The default shipped build should use assets that are appropriate for public redistribution.

## Current release-facing policy

Asset lanes fall into four practical buckets:

- `SHIP_OK`
- `REVIEW`
- `REPLACE`
- `CUSTOMIZATION_ONLY`

## Current summary

### SHIP_OK

- core project code and bundled license files
- current shipped launcher drawables and XML/vector resources used by the public build
- current shipped public wave assets used by the public build
- the default shipped coldboot/gameboot assets currently used in the public build

### REVIEW

- remaining default font/icon/SFX choices that should get one more explicit pass before a wider public beta

### REPLACE

- anything with unclear redistribution status
- anything that creates an official-console impression in the shipped default build

### CUSTOMIZATION_ONLY

- optional user-imported theme/customization content that is not part of the shipped default experience

## Practical rule

If an asset is not needed for the shipped default build, it should not be in the public repo unless it exists as documentation or example material with clear rights and purpose.
