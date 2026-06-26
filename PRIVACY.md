# Privacy Policy

SmsQuickForwarder is designed to run locally on the user's Android device.

## Data Collection

The app does not collect, upload, sell, or share:

- SMS content
- phone numbers
- forwarding rules
- debug logs
- device information

The app requests `INTERNET` only to read public GitHub Releases metadata for new-version checks. It does not upload SMS, phone numbers, rules, logs, target numbers, or device identifiers.

## Local Data

The following data is stored only on the device:

- forwarding target number
- forwarding enabled/paused state
- local matching rules
- recent debug and forwarding logs
- update-check preferences, ignored version, and last-check status
- onboarding completion state
- backup/restore preferences such as log retention and failure notification setting

## Network Access

Network requests are limited to:

- `https://api.github.com/repos/oncet886/SmsQuickForwarder/releases/latest`
- GitHub Release pages opened by the user

The app does not use a GitHub token, analytics SDK, advertising SDK, or any custom update server.

## Debug Export

Debug JSON leaves the app only when the user explicitly copies or shares it through the Android system share sheet.

By default:

- phone numbers are masked
- message previews are limited
- full message bodies are not exported
- rule keywords are masked

Users may explicitly enable additional diagnostic detail before sharing.

## Configuration Backup

Configuration backups are created only when the user explicitly exports them. By default, backups include settings and rules only.

Backups do not include:

- SMS bodies
- debug logs or forwarding history
- sender-number history
- Debug JSON
- keystore files
- GitHub tokens
- passwords

## Health Checks And Logs

Health checks and log search run locally on the device. Search terms are not saved and are not sent over the network.

Forwarding failure notifications use masked details and do not show full message bodies.

Debug JSON may include non-sensitive window layout diagnostics such as status bar height, navigation bar height, display cutout inset, keyboard inset, screen size, and orientation. These values do not include SMS content, phone numbers, rules, logs, or device identifiers.

## Carrier Charges

Forwarded SMS messages are sent through the device carrier and may incur SMS charges.
