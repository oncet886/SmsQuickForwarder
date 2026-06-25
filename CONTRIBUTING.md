# Contributing

Contributions are welcome.

## Workflow

1. Fork the repository.
2. Create a topic branch.
3. Make focused changes.
4. Run unit tests:

```bash
./gradlew testDebugUnitTest
```

If there is no Gradle wrapper in your checkout:

```bash
gradle --no-daemon testDebugUnitTest
```

5. Open a Pull Request and describe the change and test results.

## Rules

- Do not commit keystores, signing properties, passwords, real SMS content, or real phone numbers.
- Do not change `applicationId` without prior discussion.
- Do not add unnecessary Android permissions.
- Do not add network access unless the project direction changes explicitly.
- Keep changes small and testable.
- Mention any manual SMS testing performed on a real device.
