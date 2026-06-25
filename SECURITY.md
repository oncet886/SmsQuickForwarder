# Security Policy

## Reporting Security Issues

Do not publicly post real phone numbers, complete SMS bodies, private logs, keystore files, signing passwords, or other sensitive data in GitHub Issues.

When reporting a bug, prefer:

- masked Debug JSON
- screenshots with numbers and message content redacted
- device model, Android version, app version, and permission state

## Signing Certificate

Official Release APKs for this project should be signed with this certificate SHA-256:

```text
BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17
```

Verify APK signatures before installing builds from outside GitHub Releases.

## Keystore Safety

Never upload or commit:

- `.jks` or `.keystore` files
- `keystore/keystore.properties`
- signing passwords
- private keys

The public `keystore/keystore.properties.example` file must contain placeholders only.
