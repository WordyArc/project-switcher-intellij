<p align="center">
  <img src="docs/images/logo.svg" width="112" height="112" alt="Project Switcher logo">
</p>

<h1 align="center">Project Switcher</h1>

<p align="center">
  Switch between open and recent IntelliJ Platform projects without leaving the keyboard.
</p>

---

Project Switcher is an IntelliJ Platform plugin for quickly switching between open and recent projects.

Press <kbd>Alt</kbd>+<kbd>F2</kbd> and type to filter projects by name or location. Search tolerates common typos and
supports keyboard-layout correction. Open projects are listed from the most recently used, and the previous project is
selected, so <kbd>Alt</kbd>+<kbd>F2</kbd> then <kbd>Enter</kbd> switches back to it. Open projects are focused
immediately; recent projects follow the IDE's **Open project in** setting. The footer shows the location of the selected
project and the keys that act on it.

## Screenshots

![The popup listing open projects above the recent ones](docs/images/popup.png)

![Typing narrows the list to the matching projects](docs/images/speed-search.png)

## Shortcuts

| Key                                                     | Action                                                  |
|---------------------------------------------------------|---------------------------------------------------------|
| <kbd>Alt</kbd>+<kbd>F2</kbd>                            | Open or close Project Switcher                          |
| Type                                                    | Filter projects by name or location                     |
| <kbd>Up</kbd> / <kbd>Down</kbd>                         | Move the selection                                      |
| <kbd>Page Up</kbd> / <kbd>Page Down</kbd>               | Move the selection a page at a time                     |
| <kbd>Home</kbd> / <kbd>End</kbd>                        | Select the first or the last project                    |
| <kbd>Enter</kbd> or click                               | Open the selected project using the IDE setting         |
| <kbd>Shift</kbd>+<kbd>Enter</kbd> or <kbd>Shift</kbd>+click | Open a recent project in the current window         |
| <kbd>Ctrl</kbd>+<kbd>Enter</kbd> or <kbd>Ctrl</kbd>+click   | Open a recent project in a new window               |
| <kbd>Cmd</kbd>/<kbd>Ctrl</kbd>+<kbd>W</kbd>             | Close an open project or remove a recent project        |
| <kbd>Esc</kbd>                                          | Clear the search, then close the popup                  |

The open-in-current-window and open-in-new-window modifiers apply only to recent projects. Selecting an already open
project focuses its window. Closing the current project hands the focus to the project used before it.

## Change the Shortcut

Open **Settings | Keymap**, search for **Switch Project**, and assign the shortcut you prefer. The action is also
available from **Tools | Switch Project**.

## Requirements

- Any IntelliJ Platform IDE from the 2026.2 branch — IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider and the rest. The
  plugin depends only on platform-level modules.

## License

[Apache License 2.0](LICENSE)
