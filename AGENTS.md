# Codex Project Rules

These rules apply to `/Users/oncet/Documents/Codex/SmsQuickForwarder`.

## Default Delivery Workflow

1. After completing a user-requested feature or bug fix, run the full delivery workflow by default.
2. Do not treat Debug APKs as official delivery artifacts.
3. Official delivery uses a fixed-signature Release APK.
4. Each official release must increment:
   - `versionCode` by at least 1
   - `versionName` according to semantic versioning
5. Never publish two different APKs with the same `versionCode`.
6. Release builds must use the existing fixed keystore. Never generate a new signing key for official releases.
7. Stop immediately if the signing certificate fingerprint does not match:
   `BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17`
8. Automatically run tests and Release build. If either fails, do not commit a release, push a tag, create a GitHub Release, or upload an APK.
9. After feature completion, automatically:
   - commit Git changes
   - push to `origin/main`
   - create a Git tag
   - create a GitHub Release
   - upload the Release APK and SHA-256 checksum file
10. Never commit or upload:
    - `.jks`
    - `keystore.properties`
    - keystore password
    - key password
    - `keystore.zip`
    - private keys
11. Documentation or comment-only changes that do not affect the APK may be committed and pushed without creating a new APK Release.
12. Changes involving functionality, UI, rules, permissions, Manifest, resources, or runtime behavior must publish a new APK.
13. Check GitHub CLI login before every release.
14. If `gh auth status` fails, stop before push/release and tell the user clearly. Do not repeatedly log in or print tokens.
15. Never force push, rewrite public history, delete old tags, or delete old releases.

## Feature Release Steps

For feature changes:

1. Inspect changes.
2. Increment version.
3. Update changelog.
4. Run tests.
5. Build the fixed-signature Release APK.
6. Verify APK metadata and signing certificate.
7. Commit source changes.
8. Push `main`.
9. Create and push tag.
10. Create GitHub Release.
11. Upload APK and SHA-256 checksum file.
12. Report the GitHub Release URL.

Use:

```bash
./scripts/release.sh <versionName> <versionCode> "<release title>" "<release notes>"
```

For documentation-only changes:

```bash
./scripts/push_changes.sh "<commit message>"
```

## Stop Before Publishing

Do not publish if any of these occur:

- tests fail
- Release build fails
- signing fingerprint mismatch
- GitHub CLI is not logged in
- Git branch conflict
- `origin` points to the wrong repository
- sensitive files are detected
- `versionCode` was not incremented
- tag already exists
- the user explicitly says "only modify, do not publish yet"
