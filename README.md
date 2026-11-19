# Dot Widgets

21 Android home-screen widgets in an Apple + Nothing style: rounded cards, bold numbers, dot-matrix details and a red accent.
Built for the OnePlus Nord 4 (Android 14+, minSdk 31).

Clock and date · Battery rings · Weather · Calendar · Activity rings · Quick toggles · Now playing · World clock ·
Focus timer · Sunrise and sunset · Data and storage · Step counter · Flashlight · Rahu kalam · Notification dots ·
Dot pet · Screen time · Charging meter · Network speed · Dot compass · Year in weeks

## Get the APK (no Android Studio needed)

1. Create a new **private** repo on github.com (for example `dot-widgets`) and upload everything in this folder,
   including the hidden `.github` folder. You can also push it with Git:
   `git init && git add -A && git commit -m init && git branch -M main && git remote add origin <repo-url> && git push -u origin main`
2. Open the repo's **Actions** tab. The *Build APK* run starts by itself and takes about 4 minutes.
3. Open the finished run and download **DotWidgets-apk** from *Artifacts*. Unzip it to get `DotWidgets.apk`.
4. Copy it to the phone, open it and allow installing from that app.

The app is signed with the fixed key `app/dotwidgets.jks`, so a newer APK installs over the old one without
losing settings. The key is only for this personal app; don't reuse it for anything published.

## First run on the phone

1. Open **Dot Widgets** and tap **Allow** on each permission you want (each one says which widget needs it).
2. Set **Battery: unrestricted**. Without it OxygenOS freezes the widgets after a while.
3. Tap **Use my current location** (or type your city, latitude and longitude) and **Save**.
4. Long-press the home screen → **Widgets** → **Dot Widgets** and drag out the ones you want. All of them resize.

## How it works

- Every widget is drawn as an image (`Renderers.kt`) in a 160-unit virtual box, so it scales to any size.
- A once-a-minute alarm (`Ticker`) redraws widgets. It uses a non-waking alarm, so it waits while the screen is off
  and doesn't drain the battery. WorkManager runs every 15 minutes as a backup and refreshes the weather.
- Steps come from the phone's hardware step sensor. Weather comes from Open-Meteo (free, no key).
- Rahu kalam, Yamagandam, Gulikai and Abhijit are worked out from local sunrise and sunset, so they work offline.
- The widget list lives in `tools/widgets.py`. After changing it, run `python3 tools/widgets.py` to regenerate the manifest,
  widget info XML and `Kinds.kt`.

## Taps

| Widget | Tap does |
|---|---|
| Quick toggles | Torch, Wi-Fi panel, Do not disturb, camera (one per corner) |
| Now playing | Top half opens the music app; bottom: previous, play/pause, next |
| Focus timer | Tap to start or pause; bottom-right corner resets |
| Flashlight | Torch on/off |
| Compass, Network speed | Takes a fresh reading |
| Weather, Steps, Activity, Pet | Refreshes now |
| Clock, World clock | Opens alarms |
