# Contributing and testing

Thanks for helping out! This is the workflow every change goes through before it reaches players.

## One-time setup

- **JDK 21 and JDK 25** (Temurin or Microsoft). 1.21.11 builds on 21, 26.x on 25. Gradle downloads
  a missing one by itself, but having both installed is faster.
- **Git** (on Windows: Git for Windows, which includes Git Bash for the scripts in `tools/`).
- Fork the repo on GitHub, then clone your fork:
  ```
  git clone https://github.com/<you>/minerefine-mod.git
  cd minerefine-mod
  git remote add upstream https://github.com/81hp/minerefine-mod.git
  ```

## Making a change

1. Start every feature on a fresh branch from the latest `main`:
   ```
   git fetch upstream
   git switch -c my-feature upstream/main
   ```
   Use one branch (and one pull request) per feature. Don't build a new branch on top of an
   unmerged one unless the new feature really needs it.
2. Edit the files where they already are. Avoid the GitHub web "Upload files" button: it puts
   files at the repo root instead of replacing the ones in `src/`, and it records a commit called
   "Add files via upload" with no description.
3. Keep Minecraft out of the logic. Parsers, timers and calculators go in a pure package
   (`beacon/`, `outpost/`, `job/`, `mine/` and so on) with **no** `net.minecraft` imports, so they
   can be tested without starting the game. Only `client/` and `mixin/` touch Minecraft.
4. Don't change `mod_version` in `gradle.properties`. The maintainer bumps it when releasing.

## Testing: three levels, run all three before opening a PR

### 1. Logic tests (about 2 seconds)
```
bash tools/run-core-tests.sh
```
This compiles the pure logic with plain `javac` and runs every test class. It must finish with
`failed: 0`.

When you add logic, add tests to the matching class in `src/test/java/minerefinehud/`
(`ExtrasTests` for panels and timers, `CoreTests` for parsing and so on). Tests are plain
`main()` methods with `eq("name", expected, actual)`, no JUnit. For anything that reads server
text (chat, action bar, item lore, tab list), test with **real messages copied from the server**,
colour codes included, plus a few lines it must ignore (for example a player typing the same
words in chat).

For a brand-new test class, add it to `tools/run-core-tests.sh` **and** to the `logicTests`
list in `build.gradle.kts`.

### 2. Build every Minecraft version (a few minutes)
```
./gradlew buildAll          # Windows PowerShell: .\gradlew.bat buildAll
```
This compiles the mod for 1.21.11, 26.1.2, 26.2 and 26.3 and runs the logic tests again. The jars
land in `build/libs/` as `minerefine-hud-<version>+<mc>.jar`.

The source is written for one version, and code that differs per version sits inside
Stonecutter comments:
```java
//? if >=26.1 {
newApiCall();
//?} else {
/*oldApiCall();
*///?}
```
If something compiles on one version and not another, that's where the guard goes.

### 3. Game tests (several minutes per version)
```
./gradlew testAll                          # all versions
./gradlew :26.2:runClientGameTest          # just one
```
This starts the real game, joins a local test server and opens the mod's screens. Screenshots
are in `versions/<mc>/build/run/clientGameTest/screenshots/`. Check that your panel or screen
looks right there. If you added a new screen or HUD panel, extend
`src/gametest/java/minerefinehud/test/MinerefineClientGameTest.java` to open it.

### 4. Try it in the real game
```
./gradlew :26.2:runClient
```
This starts a dev client with the mod loaded. Join MineRefine and use the feature the way a
player would. Or copy the jar for your version from `build/libs/` into your launcher's mods
folder (next to Fabric API).

Check these:
- It works right after joining, without opening a menu first.
- It still works after a relog and after switching servers or worlds.
- It stays quiet on other servers.
- With the feature turned **off** (new features default to off), nothing looks or behaves
  differently from before.
- An old `config/minerefine-hud/config.json` from before your change still loads.

## Opening the pull request

- Push your branch to your fork and open a PR against `main`.
- In the description: what it does, how you tested it (which of the levels above, and in-game
  on which version), and any server text you **guessed** instead of copying. Mark guesses in
  the code with `// UNVERIFIED:`.
- CI runs the build and the game tests for every version on your PR automatically. Wait for
  them to go green. If one fails, open the run in the **Actions** tab, read the red step and
  push a fix to the same branch.
- If `main` moved on while you were working: `git fetch upstream && git rebase upstream/main`,
  then `git push --force-with-lease`.

After review the maintainer merges your PR, bumps the version, and the next push to `main`
publishes the release.
