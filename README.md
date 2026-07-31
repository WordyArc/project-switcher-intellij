# Project Switcher

An IntelliJ IDEA plugin for fast, keyboard-driven switching between recent projects.

Press <kbd>Alt</kbd>+<kbd>F2</kbd> to open a searchable popup listing your recent projects with their
icons and current Git branch. Filter as you type, then hit <kbd>Enter</kbd> to switch.

## Shortcuts

| Key                                        | Action                                          |
|--------------------------------------------|-------------------------------------------------|
| <kbd>Alt</kbd>+<kbd>F2</kbd>               | Open the popup — press again to close it        |
| Any printable character                    | Filter the list                                 |
| <kbd>↑</kbd> / <kbd>↓</kbd>                | Move the selection                              |
| <kbd>Enter</kbd>                           | Open the selection, letting the IDE ask where   |
| <kbd>Shift</kbd>+<kbd>Enter</kbd>          | Open in the **current** window, without asking  |
| <kbd>Ctrl</kbd>+<kbd>Enter</kbd>           | Open in a **new** window                        |
| <kbd>Esc</kbd>                             | Clear the filter, or close the popup            |

Plain <kbd>Enter</kbd> follows <kbd>Settings</kbd> → <kbd>Appearance & Behavior</kbd> →
<kbd>System Settings</kbd> → <kbd>Open project in</kbd>, so it shows the "New Window / This Window"
question only while that is set to ask. The modifiers override it either way.

Already-open projects are listed first and are always just focused; the modifiers apply to the
**Recent** section.

## Requirements

- IntelliJ IDEA **2026.2** or newer (build `262+`)
- JDK **25** to build

## Building

```bash
./gradlew build          # compile and verify
./gradlew runIde         # launch a sandbox IDE with the plugin installed
./gradlew buildPlugin    # produce build/distributions/project-switcher-<version>.zip
./gradlew verifyPlugin   # run the JetBrains Plugin Verifier
```

Plugin version and target platform live in [`gradle.properties`](gradle.properties);
plugin/dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml);
plugin metadata lives in [`plugin.xml`](src/main/resources/META-INF/plugin.xml).

## Installation

<kbd>Settings</kbd> → <kbd>Plugins</kbd> → <kbd>⚙️</kbd> → <kbd>Install Plugin from Disk…</kbd>, then
pick the zip produced by `./gradlew buildPlugin`.

## Publishing

The [Release](.github/workflows/release.yml) workflow signs and publishes to JetBrains Marketplace
when a GitHub release is published. It needs these repository secrets:

| Secret                 | Purpose                                       |
|------------------------|-----------------------------------------------|
| `PUBLISH_TOKEN`        | JetBrains Marketplace token                   |
| `CERTIFICATE_CHAIN`    | Signing certificate chain                     |
| `PRIVATE_KEY`          | Signing certificate private key               |
| `PRIVATE_KEY_PASSWORD` | Password for the private key                  |

See [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) for how to
generate them.

## License

[Apache License 2.0](LICENSE)
