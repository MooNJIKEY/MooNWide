# MooNWide

Prebuilt Haven & Hearth client. This repository is a ready-to-run game package, not a source tree.

## Quick start

1. Download the repository as ZIP or clone it.
2. Extract it anywhere on disk.
3. Run `Run-MooNWide.bat`.

For Steam login and integration, use `Run-MooNWide-Steam.bat`.

## Auto update

- `Run-MooNWide.bat` checks GitHub before every launch.
- `Run-MooNWide-Steam.bat` does the same.
- If a newer package exists, it is downloaded and installed automatically, then the launcher restarts itself.
- For a manual check, run `Update-MooNWide.bat`.
- If GitHub is unavailable, the client still starts with the files already on disk.

## What is included

- ready client jars
- trimmed bundled Java runtime in `jre\`
- Windows launchers
- built-in GitHub updater
- required native libraries and game resources

You do not need to build anything before playing.

## Notes

- The login screen shows a `Build ...` label in the lower-left corner. Use it to confirm you and the tester are on the same client build.
- Crash log: `%USERPROFILE%\.haven\moonwide-crash.log`
- If you move or delete `jre\`, the launchers fall back to `JAVA_HOME` or `PATH`
- Legal files are included in `COPYING`, `doc\LGPL-3`, `NOTICE-MooNWide.txt`, and `THIRD_PARTY_NOTICES.txt`
