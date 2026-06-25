# SmsQuickForwarder Release

## Version

- versionName:
- versionCode:

## Changes

- 

## APK

- File:
- SHA-256:
- MD5:

## Signing Certificate

Official certificate SHA-256:

```text
BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17
```

## Install Notes

- Install the APK on an Android phone with SMS capability.
- Grant SMS receive/send permissions and notification permission if required.
- If switching from an old debug-signed APK to this Release APK, uninstall the debug build first.
- Future Release APKs signed with the same certificate can upgrade this build in place.

## Verification

```bash
apksigner verify --verbose --print-certs <apk-file>
```
