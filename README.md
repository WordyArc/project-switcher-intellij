# Project Switcher

Project Switcher is an IntelliJ IDEA plugin for quickly switching between open and recent projects.

Press <kbd>Alt</kbd>+<kbd>F2</kbd> and type to filter projects by name or path. Search tolerates common typos and supports keyboard-layout correction. Open projects are focused immediately; recent projects follow the IDE's **Open project in** setting.

## Screenshots

> Screenshot placeholder: project switcher popup

> Screenshot placeholder: shortcut settings

## Shortcuts

| Key | Action |
|---|---|
| <kbd>Alt</kbd>+<kbd>F2</kbd> | Open or close Project Switcher |
| Type | Filter projects by name or path |
| <kbd>Up</kbd> / <kbd>Down</kbd> | Move the selection |
| <kbd>Enter</kbd> | Open the selected project using the IDE setting |
| <kbd>Shift</kbd>+<kbd>Enter</kbd> | Open a recent project in the current window |
| <kbd>Ctrl</kbd>+<kbd>Enter</kbd> | Open a recent project in a new window |
| <kbd>Delete</kbd> / <kbd>Backspace</kbd> | Close an open project or remove a recent project when the search field is empty |
| <kbd>Esc</kbd> | Clear the search, then close the popup |

The open-in-current-window and open-in-new-window modifiers apply only to recent projects. Selecting an already open project focuses its window.

To prevent accidentally leaving no project to switch to, the current project can be closed from the popup only when it is the only open project.

## Change the Shortcut

Open **Settings | Keymap**, search for **Switch Project**, and assign the shortcut you prefer. The action is also available from **Tools | Switch Project**.

## Requirements

- IntelliJ IDEA 2026.2 or newer

## License

[Apache License 2.0](LICENSE)
