# Changelog

The topmost section is published as the plugin's change notes.

## 1.0.0

- Initial release.
- <kbd>Alt</kbd>+<kbd>F2</kbd> opens a popup listing open and recent projects; pressing it again closes the popup.
- Type to filter by project name or path, with typo tolerance and keyboard-layout correction.
- <kbd>Enter</kbd> opens the selection following the IDE's <b>Open project in</b> setting; <kbd>Shift</kbd>+<kbd>Enter</kbd> reuses the current window and <kbd>Ctrl</kbd>+<kbd>Enter</kbd> opens a new one.
- <kbd>Delete</kbd> and <kbd>Backspace</kbd> remove a recent project or close an open one while the search field is empty.
- Project icons are loaded asynchronously and sharpened on HiDPI displays.
- Projects that share a name are disambiguated by their path.
