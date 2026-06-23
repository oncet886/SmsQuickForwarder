# Release Build

## 1. Generate a keystore

```bash
mkdir -p keystore
keytool -genkeypair \
  -v \
  -keystore keystore/smsquickforwarder-release.jks \
  -alias smsquickforwarder \
  -keyalg RSA \
  -keysize 2048 \
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
storeFile=keystore/smsquickforwarder-release.jks
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
app/build/outputs/apk/release/SmsQuickForwarder-0.1.3-4-release.apk
```

## 5. Upgrade installs

Android only allows an installed app to be upgraded when the package name and signing certificate match.
Always keep using the same keystore and alias for future release builds.
