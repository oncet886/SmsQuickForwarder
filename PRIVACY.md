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

## Carrier Charges

Forwarded SMS messages are sent through the device carrier and may incur SMS charges.
