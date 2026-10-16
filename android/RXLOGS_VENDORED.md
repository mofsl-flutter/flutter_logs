# Bundled RxLogs (PLog)

The Android logging engine, PLog, is bundled in this plugin as source instead of being pulled in as
the `com.github.umair13adil:RxLogs:v1.0.27` dependency. It is upstream RxLogs `v1.0.27` plus the
null-config guards described below.

It is bundled because the guards are changes to RxLogs itself, and this plugin is the one place the
team controls. Licence and copyright: see `THIRD_PARTY_NOTICES.md` (MIT).

## Where it came from

| | |
|---|---|
| Upstream | https://github.com/umair13adil/RxLogs |
| Version | tag `v1.0.27`, commit `35d9e68` |
| Copied | `plog/src/main/java/com/blackbox/plog/**` (67 Kotlin and 2 Java files), `plog/src/main/res/values/strings.xml` |
| Location here | `android/src/main/java/com/blackbox/plog/**`, `android/src/main/res/values/strings.xml` |
| Not copied | the `app` module, build files, upstream's tests (they do not compile upstream either) |

## What differs from upstream v1.0.27

Only two files, `utils/FileUtils.kt` and `pLogs/impl/LogWriter.kt`. Everything else is byte-identical.

**The problem.** A write that reaches PLog while `PLogImpl.getConfig()` is null used to execute
`getConfig()?.x!!`. That throws a `NullPointerException` on PLog's background thread and kills the
process. This was the launch crash in Rise Crashlytics `d6a94161` (`AsyncTask #2`, `NullPointerException`
at `FileUtilsKt.setupFilePaths`). `getConfig()` is null once `PLogPreferences` is initialised
(`applyConfigurations` does that first) and before the config has been saved.

**The guards.** Ported from the team fork `abdulkhira1/RxLogs` (commits `cf58616`, `b0184f0`, `d588a4c`,
`e2bd72a`, `b66e99a`, `938c618`, `bcab46e`), which is based on the older `v1.0.23` and lacks later
upstream work, so the changes were ported by hand, not merged:

- `setupFilePaths`: wrapped in `try/catch`. A failure is logged and the file name is returned. Upstream's
  later `folderDate` parameter is kept.
- `checkFileExists`: failed `mkdirs` / `createNewFile` are logged instead of thrown, and the debug flag is
  read as `== true`, so a null config is not an NPE (upstream also dereferenced it inside its own catch).
- `writeToFile`, `appendToFile`: separate handlers for `NullPointerException`, `RuntimeException`, `Exception`.
  `writeToFile`'s debug check is also `== true` (the fork left a `!!` there, which its catch then swallowed, so a
  new file was silently not created without a config).
- `LogWriter`: `shouldWriteLog`, `createPartFile` and the debug logging read the config only when it is set.
  The single-file size limit defaults to 2 MB without one.

**Not changed.** `getLogsSavedPaths` still has `getConfig()?.directoryStructure!!`. It is only called as
`getConfig()?.let { getLogsSavedPaths(it...) }` from export and print paths, so it is not reachable with a null config.

## What stays the same for the app

- **Dependencies**: the plugin declares the same libraries the RxLogs artifact declared (appcompat, gson,
  localbroadcastmanager, rxandroid, rxkotlin, paho client and Android service, legacy-support-v4,
  work-runtime-ktx, work-rxjava2, kotlin-test).
- **Manifest**: the same four permissions (`INTERNET`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`,
  `READ_PHONE_STATE`) and `org.eclipse.paho.android.service.MqttService`. The app's merged manifest is
  identical to the one built with the RxLogs artifact.
- **ProGuard**: RxLogs shipped no consumer rules, so none are added.
- **Generated classes**: `com.blackbox.plog.R` and `BuildConfig` are no longer generated. Nothing references them.

## Tests

`android/src/test/java/com/blackbox/plog/utils/NullConfigGuardTest.kt` (Robolectric) puts PLog in the state
above and calls the file helpers. On unmodified `v1.0.27`, 4 of its 6 tests fail with
`NullPointerException` (including `setupFilePaths` at the production crash line); with the guards all 6 pass.

To run it, use an app that depends on this plugin:

```
cd <app>/android
./gradlew :flutter_logs:testDebugUnitTest --tests 'com.blackbox.plog.utils.NullConfigGuardTest'
```

## Updating to a newer upstream version

1. Check out the new upstream tag and diff `plog/src/main/java/com/blackbox/plog` against this directory.
2. Replace this directory with the new upstream source, then re-apply the guards above to `FileUtils.kt`
   and `LogWriter.kt` (or drop them if upstream has fixed the same problem).
3. Update the dependency versions in `android/build.gradle` to match upstream's `plog/build.gradle`,
   and the version and commit in this file and in `THIRD_PARTY_NOTICES.md`.
4. Run the test above, and check that the app's merged manifest still has the same permissions and services.
