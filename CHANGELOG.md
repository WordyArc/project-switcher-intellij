# Changelog

The topmost section is published as the plugin's change notes.

## 1.1.0

- <kbd>Up</kbd> and <kbd>Down</kbd> now wrap around the list: moving up from the first row selects the last one, and moving down from the last row returns to the first.
- Rows that no longer fit are shortened with an ellipsis instead of being cut off: paths lose their middle, branches lose their prefix, and a long name can no longer crowd out the path and branch beside it.
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
