# SMS Quick Forwarder

SmsQuickForwarder is a lightweight, open-source Android app for forwarding ordinary SMS messages. It can forward matching SMS messages to a user-configured phone number based on sender or message-body rules, and it uses network access only to check GitHub Releases for new versions.

短信快转发是一个轻量、开源的 Android 普通短信自动转发工具。它可以按照发送号码或短信正文规则，将符合条件的 SMS 转发到指定号码，网络权限仅用于检查 GitHub Releases 新版本。

## Features

- Forward ordinary SMS to a configured target phone number.
- Supports forwarding all SMS or forwarding matched SMS only.
- Local INCLUDE and EXCLUDE rules.
- Match by sender, body, or both.
- Match modes: contains, equals, starts with, and regular expression.
- Rule testing tool that never sends a real SMS.
- Long SMS forwarding with multipart sending.
- Sent-result tracking and limited retry.
- Loop protection for messages from the target number and messages containing `[SMS Forward]`.
- Foreground service and boot restore when forwarding was enabled.
- Debug logs and privacy-safe Debug JSON export.
- Phone numbers and message content are masked by default in diagnostics.
- Automatic new-version checks through public GitHub Releases.
- First-run setup guide for target number, permissions, background behavior, and test sending.
- Local configuration backup and restore for settings and rules.
- Local health checks for forwarding readiness and common failure causes.
- Forwarding failure notifications with masked details.
- Local log search, filtering, statistics, and retention controls.
- No ads and no analytics SDK.

## 功能

- 将普通 SMS 自动转发到用户设置的目标手机号。
- 支持转发全部短信，也支持仅转发符合规则的短信。
- 本地 INCLUDE / EXCLUDE 规则。
- 支持按发送号码、短信正文或任意字段匹配。
- 支持包含、完全相同、以此开头、正则表达式匹配。
- 本地规则测试工具，不会真正发送短信。
- 长短信 multipart 转发。
- 发送回执记录和有限重试。
- 防循环：跳过目标号码发来的短信，跳过包含 `[SMS Forward]` 的短信。
- 前台服务和开机恢复。
- 调试日志和隐私安全的 Debug JSON 导出。
- 诊断信息默认遮罩手机号和短信内容。
- 通过公开 GitHub Releases 自动提醒新版本。
- 首次使用配置向导，覆盖目标号码、权限、后台运行和测试发送。
- 本地配置备份与恢复，备份设置和规则。
- 本地运行健康检查，帮助定位常见转发问题。
- 转发失败通知，默认脱敏显示。
- 本地日志搜索、筛选、统计和保留时间控制。
- 无广告、无统计 SDK。

## SMS And RCS

This app listens for standard Android SMS broadcasts. It does not read or forward RCS messages.

本 App 只支持 Android 普通 SMS 广播，不读取、不转发 RCS 消息。

## Permissions

Actual permissions declared by the app:

| Permission | Purpose |
| --- | --- |
| `RECEIVE_SMS` | Receive ordinary incoming SMS broadcasts. |
| `SEND_SMS` | Forward SMS to the configured target number. |
| `RECEIVE_BOOT_COMPLETED` | Restore the foreground service after reboot when forwarding was enabled. |
| `FOREGROUND_SERVICE` | Keep the forwarding service visible and running. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service type on recent Android versions. |
| `POST_NOTIFICATIONS` | Show the required foreground-service notification on Android 13+. |
| `INTERNET` | Check public GitHub Releases for new versions. |

The app does not request:

- contacts permissions
- location permissions
- call-log permissions
- storage permissions
- notification-listener permissions

## Privacy

- No SMS content is uploaded.
- No phone numbers, rules, logs, or device data are sent to any server.
- Network access is used only to read public GitHub Release metadata for update checks.
- Configuration backups are generated only when the user explicitly exports them.
- Backups do not include logs, SMS bodies, sender history, Debug JSON, keystores, tokens, or passwords.
- Health checks and log search run locally on the device.
- Forwarding failure notifications mask sensitive details by default.
- Target number, rules, and logs are stored only on the device.
- Debug JSON leaves the app only when the user explicitly shares or copies it.
- Debug exports mask phone numbers and message previews by default.
- Backup exports contain settings and rules only; they do not include SMS history.

See [PRIVACY.md](PRIVACY.md) for details.

## Installation

1. Download a Release APK signed by the published certificate.
2. Install it on an Android phone with SMS capability.
3. Grant SMS receive/send permissions and notification permission if required.
4. Set the forwarding target number.
5. Enable SMS forwarding.
6. Send a test SMS to the phone and confirm the target number receives it.

Automatic SMS forwarding may incur carrier SMS charges.

## Build

Use Android Studio or command line. If the project has a Gradle wrapper, prefer `./gradlew`; otherwise use the installed `gradle` command.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Without a wrapper:

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

CI builds only the debug APK and runs unit tests. CI does not use the Release keystore.

## Maintainer Release Workflow

Feature, UI, rule, permission, Manifest, resource, or runtime changes should be released with:

```bash
./scripts/release.sh 0.1.7 8 "SmsQuickForwarder v0.1.7" "Describe the user-facing change."
```

Documentation-only or comment-only changes that do not affect the APK may be pushed with:

```bash
./scripts/push_changes.sh "docs: update README"
```

Release signing is performed locally on the maintainer's Mac with the fixed keystore. GitHub Actions only runs tests and builds Debug APKs. It does not hold the Release private key and does not publish signed Release APKs.

## Release Signing

Release builds require a local keystore and `keystore/keystore.properties`. Do not commit either file.

1. Copy the example:

```bash
cp keystore/keystore.properties.example keystore/keystore.properties
```

2. Generate or provide a private keystore:

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

3. Fill `keystore/keystore.properties` with local private values:

```properties
storeFile=smsquickforwarder-release.jks
storePassword=your-store-password
keyAlias=smsquickforwarder
keyPassword=your-key-password
```

4. Build Release locally:

```bash
gradle --no-daemon clean testDebugUnitTest assembleRelease
```

Official project Release APKs are signed with this certificate SHA-256:

```text
BC:29:EC:4B:7D:30:CC:1B:48:33:0E:C7:07:CC:E8:0D:57:C2:52:31:3F:F1:9C:A4:B7:CE:00:D8:14:57:27:17
```

Verify a Release APK:

```bash
apksigner verify --verbose --print-certs SmsQuickForwarder-v0.1.6-7-release.apk
```

## Screenshots

Screenshots will be added under [screenshots/](screenshots/) in a future update.

## Known Limitations

- Ordinary SMS only; no RCS support.
- Background reliability depends on the device vendor and battery settings.
- Dual-SIM details may require additional user-granted phone-state permission, which this app does not request by default.
- Some carriers may charge for forwarded SMS.
- The app does not automatically install updates; update notifications open GitHub Releases.
- This app is intended for devices you own or are authorized to manage.

## Safety And Legal Notice

Use this app only on devices you own or are authorized to administer. Make sure all forwarding complies with local law, carrier terms, workplace policy, and the privacy expectations of message senders. The app does not hide its launcher icon, does not delete SMS, and does not upload SMS.

## License

MIT. See [LICENSE](LICENSE).

---

# 中文说明

## 项目简介

短信快转发是一个轻量、开源的 Android 普通短信自动转发工具。它可以按照发送号码或短信正文规则，将符合条件的 SMS 转发到指定号码。网络权限仅用于读取公开 GitHub Releases 以检查新版本。

## 使用提醒

- 不支持 RCS。
- 不上传短信。
- 只连接 GitHub Releases 检查新版本。
- 配置备份仅由用户主动导出，默认不包含日志、短信正文或历史号码。
- 健康检查和日志搜索仅在本机执行。
- 转发失败通知默认脱敏。
- 不包含广告和统计 SDK。
- 数据仅保存于本机。
- 自动转发短信可能产生运营商短信费用。
- 请只在你拥有或被授权管理的设备上使用。

## 构建和签名

调试构建使用：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

如果没有 Gradle wrapper，可使用：

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

正式 Release 构建必须配置本地 `keystore/keystore.properties`，并使用同一个签名证书持续升级安装。不要提交 keystore、密码或 `keystore.properties`。
