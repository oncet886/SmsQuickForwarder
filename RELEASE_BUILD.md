# Release Build

## 1. Generate a keystore

```bash
mkdir -p keystore
keytool -genkeypair \
  -v \
  -storetype JKS \
  -keystore keystore/smsquickforwarder-release.jks \
  -alias smsquickforwarder \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep this keystore private. Future upgrades must be signed with the same keystore.

## 2. Create `keystore/keystore.properties`

Copy the example:

```bash
cp keystore/keystore.properties.example keystore/keystore.properties
```

Edit it:

```properties
storeFile=smsquickforwarder-release.jks
storePassword=your-store-password
keyAlias=smsquickforwarder
keyPassword=your-key-password
```

Do not commit `keystore/keystore.properties` or the `.jks` file.

## 3. Build release APK

```bash
gradle --no-daemon assembleRelease
```

If `keystore/keystore.properties` is missing, release builds fail with a clear error. Debug builds still work.

## 4. APK output

Release APK:

```text
app/build/outputs/apk/release/SmsQuickForwarder-<versionName>-<versionCode>-release.apk
```

## 5. Upgrade installs

Android only allows an installed app to be upgraded when the package name and signing certificate match.
Always keep using the same keystore and alias for future release builds.

Official project Release builds use this certificate SHA-256:

```text
BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17
```

## 6. Maintainer release workflow

Feature, UI, rule, permission, Manifest, resource, or runtime changes should be released with:

```bash
./scripts/release.sh 0.1.7 8 "SmsQuickForwarder v0.1.7" "Describe the user-facing change."
```

Documentation-only or comment-only changes that do not affect the APK may be pushed with:

```bash
./scripts/push_changes.sh "docs: update README"
```

Release signing is done locally on the maintainer's Mac with the fixed keystore. GitHub Actions only runs unit tests and unsigned debug CI builds. GitHub Actions does not hold the Release private key, and the project should not store the keystore in GitHub Secrets unless the signing strategy is explicitly changed in the future.
