# Changelog

The topmost section is published as the plugin's change notes.

## 1.*.*

- The plugin installs on 2026.3 EAP, where reopening a recent project used to be rejected as incompatible.
- Typed text goes into a search field at the top of the popup, and <kbd>Enter</kbd> opens the project matching everything typed so far, even when the keys arrive faster than the list redraws.
- Open projects are listed from the most recently used, and the previous project is selected when the popup opens, so <kbd>Alt</kbd>+<kbd>F2</kbd> followed by <kbd>Enter</kbd> switches back to it.
- <kbd>Cmd</kbd>+<kbd>W</kbd> on macOS and <kbd>Ctrl</kbd>+<kbd>W</kbd> elsewhere closes the current project even while other projects are open, and hands the focus to the one used before it.
- Moving the selection scrolls the list only when the selected row would leave the view, and <kbd>Page Up</kbd>, <kbd>Page Down</kbd>, <kbd>Home</kbd> and <kbd>End</kbd> jump through a long list, which now has a scrollbar.
- Closing or removing a project from the popup no longer flashes the loading message, drops the icons or scrolls the list back to the top.
- Search no longer matches the user home directory in project paths, ranks matches in the project name above matches in its location, and shows the location of a project that matched only there.
- A footer shows the location of the selected project and the keys that act on it.
- <kbd>Shift</kbd>+click and <kbd>Ctrl</kbd>+click open a recent project in the current or a new window, the same as <kbd>Shift</kbd>+<kbd>Enter</kbd> and <kbd>Ctrl</kbd>+<kbd>Enter</kbd>.
- Pressing the Switch Project shortcut again closes the popup whatever shortcut the keymap assigns to it, not only <kbd>Alt</kbd>+<kbd>F2</kbd>.
- The popup opens with the last known project list and sharp icons in its first frame and refreshes them in the background.
- Project icons load all at once instead of four at a time, and in Power Save Mode the popup no longer waits for icons that the IDE does not compute.
- Recent projects whose directory no longer exists are dimmed.
- Recent projects on WSL and other non-local file systems open through the IDE's own reopen action, which prepares their environment first.
- When the IDE cannot build its recent project list, the popup still lists the open projects instead of showing an error.
- The plugin no longer loads on a remote development host, where its popup cannot be shown, and hides its action in JetBrains Client.

## 1.1.2

- The characters that matched the search are highlighted in project names, and in paths where those are shown.
- The popup responds to the keyboard again on 2026.2.3, where <kbd>Up</kbd>, <kbd>Down</kbd>, typing and the <kbd>Alt</kbd>+<kbd>F2</kbd> toggle had stopped working.

## 1.1.1

- Fix IntelliJ Platform binary compatibility regression.

## 1.1.0

- <kbd>Up</kbd> and <kbd>Down</kbd> now wrap around the list: moving up from the first row selects the last one, and moving down from the last row returns to the first.
- Rows that no longer fit are shortened with an ellipsis instead of being cut off: paths lose their middle, branches lose their prefix, and a long name can no longer crowd out the path and branch beside it.
- The first popup of an IDE session now opens with its project icons already in place, instead of having them fade in over the list. The lookup they need is done in the background shortly after a project window opens.
- Closing an open project or removing a recent one moved from <kbd>Delete</kbd> to <kbd>Cmd</kbd>+<kbd>W</kbd> on macOS and <kbd>Ctrl</kbd>+<kbd>W</kbd> elsewhere, and now works while a search query is being typed. <kbd>Delete</kbd> and <kbd>Backspace</kbd> only edit the query.
- Branch names no longer go missing when the popup has not been opened for a while. They now appear as soon as the IDE finishes looking them up, instead of only after a few reopens.

## 1.0.1

- The plugin description now lists every shortcut, including <kbd>Shift</kbd>+<kbd>Enter</kbd>, <kbd>Ctrl</kbd>+<kbd>Enter</kbd> and <kbd>Esc</kbd>.
- Fixed the screenshot links in the description, which still pointed at the repository's former name.

## 1.0.0

- Initial release.
- <kbd>Alt</kbd>+<kbd>F2</kbd> opens a popup listing open and recent projects; pressing it again closes the popup.
- Type to filter by project name or path, with typo tolerance and keyboard-layout correction.
- <kbd>Enter</kbd> opens the selection following the IDE's <b>Open project in</b> setting; <kbd>Shift</kbd>+<kbd>Enter</kbd> reuses the current window and <kbd>Ctrl</kbd>+<kbd>Enter</kbd> opens a new one.
- <kbd>Delete</kbd> and <kbd>Backspace</kbd> remove a recent project or close an open one while the search field is empty.
- Project icons are loaded asynchronously and sharpened on HiDPI displays.
- Projects that share a name are disambiguated by their path.
