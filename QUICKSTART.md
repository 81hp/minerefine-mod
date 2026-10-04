> **Outdated.** This was the first-time setup guide for the single-version build. The build now
> covers several Minecraft versions and CI is in `.github/workflows/build.yml`; see the README's
> "Build and install".

# Quickstart

Two routes. Pick the first one.

---

# Route A: let GitHub build it (recommended, nothing to install)

You never install a JDK, never touch Gradle, never open a terminal. GitHub's build machines do
the work and hand you a finished jar.

## 1. Make a repository

Go to <https://github.com/new>. Name it `minerefine-hud`. Private is fine. Create it.

## 2. Upload this folder

On the new repo's page click **uploading an existing file**, then drag the contents of
`minerefine-hud` into the browser. Click **Commit changes**.

Do not worry about the `.github` folder here, browsers skip hidden folders. The next step adds it
properly.

## 2b. Add the build workflow

This is the part that does the building, and it has to be created in the browser because hidden
folders do not survive a drag and drop upload.

On the repo page: **Add file** → **Create new file**. In the filename box type exactly:

```
.github/workflows/build.yml
```

GitHub turns that path into folders as you type the slashes. Then open
`github-workflow-build.yml` from this project, copy everything in it, paste it into the editor,
and click **Commit changes**.

You can delete `github-workflow-build.yml` afterwards, it is only a carrier copy.

## 3. Wait about four minutes

Open the **Actions** tab. A run starts by itself. Two jobs:

- **Core logic tests** should go green quickly. It checks the message parsing, timer logic and
  armour maths, and validates against the live price data.
- **Build jar** resolves the Fabric versions automatically, then compiles.

## 4. Download the jar

Click the finished run. At the bottom under **Artifacts** there is `minerefine-mod-jar`. Download
it and unzip. That is your mod.

The run summary also prints the four Fabric version numbers it resolved, in case you ever want to
build locally later.

## 5. Install into Lunar

Lunar launcher → version selector on the left → button at the bottom right → **Mods** → drag the
jar into the window.

## Changing something later

Edit the file on GitHub, commit, and a new jar builds by itself. No local setup, ever.

**If the build job fails:** click into it, copy the red error, and paste it to me. That is a much
faster loop than debugging a local install, because the logs are precise about what broke.

---

# Route B: build on your own machine

Only worth it if you want `runClient`, which launches a test Minecraft with the mod already
loaded. That is a genuinely nicer loop than restarting a server to test a plugin, but it needs
real setup first.

## What carries over from your plugin work

Java 21, the `src/main/java` layout, a manifest describing your mod, event listeners, packaging a
jar. All familiar.

## What is different

| Paper plugin | Fabric mod |
|---|---|
| Maven, `pom.xml` | Gradle, `build.gradle` |
| `mvn package` | `.\gradlew.bat build` |
| `plugin.yml` | `fabric.mod.json` |
| Runs on the server | Runs on your client |
| Spigot API, stable for years | Yarn mappings, renamed every version |
| Restart a server to test | `.\gradlew.bat runClient` |

## 1. Get a JDK 21

You have JDK 25 at `C:\Program Files\Microsoft\jdk-25.0.2.10-hotspot`. Minecraft 1.21.11 runs on
21 and Gradle 8.14 does not reliably support 25, so install **Microsoft Build of OpenJDK 21**
alongside it. Nothing is overwritten, several JDKs side by side is normal.

## 2. Gradle: nothing to install

`gradlew.bat` is included and fetches the right Gradle itself, the same idea as the Maven
wrapper.

## 3. Fill in four version numbers

Open <https://fabricmc.net/develop>, pick **1.21.11**, copy the values into `gradle.properties`
where it says `FILL_ME_IN`. Or run Route A once and copy them from the run summary.

## 4. Build

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.x-hotspot'
.\gradlew.bat build
```

First run downloads Gradle, Minecraft and the mappings, so it takes a few minutes. After that it
is seconds. Jar lands in `build\libs\`.

## 5. Test without Lunar

```powershell
.\gradlew.bat runClient
```

Real Minecraft, mod already loaded, connects to any server. Use this for everything except the
final Lunar check, since Lunar ships Sodium and Iris and those are worth ruling out separately.

## VS Code tasks

`vscode-tasks.json` is at the project root. Make a `.vscode` folder, move it in, rename it to
`tasks.json`, and fix the JDK 21 path inside. (I could not write it there directly, the remote
tools are blocked from touching `.vscode`.)

---

# The fast test loop, either route

```powershell
.\tools\run-core-tests.sh
```

Two seconds, no Gradle, no Minecraft, no downloads. Checks message parsing, timer logic, armour
maths and mine name matching. The GitHub workflow runs this too.

# When something breaks

| Symptom | Cause |
|---|---|
| `FILL_ME_IN` errors | Route B step 3 not done |
| "Unsupported class file major version" | `JAVA_HOME` is on JDK 25, point it at 21 |
| `HudLayerRegistrationCallback` not resolved | Fabric API changed it, README has the two-line swap |
| Builds, but nothing on screen | Check `.minecraft/config/minerefine-hud/config.json` exists |
| Mine shows `unknown` | Sidebar does not contain the name, tell me what it does contain |

Anything else, paste me the error. Gradle output is long but usually has exactly one useful line.
