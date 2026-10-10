# Burndown

Android app with home screen widgets that show the burndown and burnup of the current sprint on a GitHub Projects board.

- Burndown and burnup charts with an ideal line
- Measure by issue count or a number field like story points
- Per day or per hour data points
- Syncs every 30 minutes in the background

## Setup

1. Create a GitHub token with the `read:project` scope (and `repo` if the repos are private)
2. Open the app, paste the token, enter the project owner and number and hit **Load project**
3. Pick the iteration field and measure, then **Save and sync**
4. Add the widgets from your launcher

GitHub doesn't keep history for project fields, so done work comes from `closedAt` / `mergedAt` and the total scope is recorded locally on each sync.

## Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Releases

Every push to `main` builds an APK and publishes it as a GitHub release. To sign it with your own key add these repo secrets, otherwise it gets signed with a debug key:

- `KEYSTORE_BASE64` (`base64 -w0 release.jks`)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
