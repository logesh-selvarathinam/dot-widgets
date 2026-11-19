# Single source of truth for the widget list.
# Run: python3 tools/widgets.py  -> regenerates manifest receivers, widget-info XML and preview layouts.
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# kind, provider class, label, layout, cols, rows, description
W = [
    ("CLOCK", "ClockWidget", "Clock and date", "single", 4, 2, "Big time, day, date and next alarm"),
    ("BATTERY", "BatteryWidget", "Battery rings", "single", 2, 2, "Battery level dot ring and temperature"),
    ("WEATHER", "WeatherWidget", "Weather", "single", 2, 2, "Temperature, condition and hourly dots"),
    ("CALENDAR", "CalendarWidget", "Calendar", "single", 2, 2, "Today and your next event"),
    ("ACTIVITY", "ActivityWidget", "Activity rings", "single", 2, 2, "Steps, distance and calories rings"),
    ("TOGGLES", "TogglesWidget", "Quick toggles", "grid4", 2, 2, "Torch, Wi-Fi, Do not disturb, camera"),
    ("NOW_PLAYING", "NowPlayingWidget", "Now playing", "media", 4, 2, "Music from any app with controls"),
    ("WORLD_CLOCK", "WorldClockWidget", "World clock", "single", 2, 2, "Your time and a second city"),
    ("FOCUS", "FocusWidget", "Focus timer", "focus", 2, 2, "Tap to start or pause a focus session"),
    ("SUN", "SunWidget", "Sunrise and sunset", "single", 2, 2, "Daylight arc for your city"),
    ("DATA", "DataWidget", "Data and storage", "single", 2, 2, "Mobile data today and phone storage"),
    ("STEPS", "StepsWidget", "Step counter", "single", 2, 2, "Dot-matrix steps and goal progress"),
    ("TORCH", "TorchWidget", "Flashlight", "single", 2, 2, "One tap flashlight"),
    ("RAHU", "RahuWidget", "Rahu kalam", "single", 4, 2, "Rahu kalam, Yamagandam, Gulikai and Abhijit"),
    ("NOTIFS", "NotifDotsWidget", "Notification dots", "single", 2, 2, "Unread notifications as dots"),
    ("PET", "PetWidget", "Dot pet", "single", 2, 2, "A pixel pet that grows when you walk"),
    ("SCREEN", "ScreenTimeWidget", "Screen time", "single", 2, 2, "Screen-on time and unlocks today"),
    ("CHARGING", "ChargingWidget", "Charging meter", "single", 2, 2, "Charge level, time to full, temperature"),
    ("NETSPEED", "NetSpeedWidget", "Network speed", "single", 2, 2, "Download and upload speed"),
    ("COMPASS", "CompassWidget", "Dot compass", "single", 2, 2, "Tap to read your heading"),
    ("WEEKS", "WeeksWidget", "Year in weeks", "single", 2, 2, "This year as 52 dots"),
]

def dp(cells):
    return {1: 40, 2: 110, 3: 180, 4: 250}[cells]

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)

receivers = []
for kind, cls, label, layout, cols, rows, desc in W:
    key = kind.lower()
    write(os.path.join(RES, f"xml/w_{key}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="{dp(cols)}dp"
    android:minHeight="{dp(rows)}dp"
    android:minResizeWidth="{dp(2) if cols >= 2 else 40}dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="{cols}"
    android:targetCellHeight="{rows}"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/w_loading"
    android:previewLayout="@layout/p_{key}"
    android:description="@string/d_{key}" />
''')
    dark = kind not in ("BATTERY", "CALENDAR", "WORLD_CLOCK", "STEPS", "SCREEN", "NETSPEED", "WEEKS_")
    bg = "#26262A" if dark else "#FAFAFA"
    fg = "#F5F5F7" if dark else "#111111"
    write(os.path.join(RES, f"layout/p_{key}.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/preview_{'dark' if dark else 'light'}"
    android:orientation="vertical"
    android:gravity="bottom"
    android:padding="14dp">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="●"
        android:textColor="#FF453A"
        android:textSize="12sp" />
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="{label}"
        android:textColor="{fg}"
        android:textSize="16sp"
        android:fontFamily="sans-serif-medium" />
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="{desc}"
        android:textColor="{fg}"
        android:alpha="0.6"
        android:textSize="11sp" />
</LinearLayout>
''')
    receivers.append(f'''        <receiver
            android:name=".{cls}"
            android:exported="true"
            android:label="{label}">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/w_{key}" />
        </receiver>''')

strings = "\n".join(f'    <string name="d_{k.lower()}">{d}</string>' for k, _, _, _, _, _, d in W)
write(os.path.join(RES, "values/strings.xml"), f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Dot Widgets</string>
{strings}
</resources>
''')

man = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")
text = open(man).read()
text = re.sub(r"<!-- WIDGETS -->.*<!-- /WIDGETS -->",
              "<!-- WIDGETS -->\n" + "\n".join(receivers) + "\n        <!-- /WIDGETS -->",
              text, flags=re.S)
write(man, text)

kt = "\n".join(f"class {cls} : BaseWidget(Kind.{kind})" for kind, cls, *_ in W)
enum = ",\n".join(f'    {kind}({cls}::class.java, "{label}", Layout.{layout.upper()})' for kind, cls, label, layout, *_ in W)
write(os.path.join(ROOT, "app/src/main/java/com/nandhini/dotwidgets/Kinds.kt"), f'''// Generated by tools/widgets.py. Edit the list there.
package com.nandhini.dotwidgets

enum class Layout {{ SINGLE, GRID4, MEDIA, FOCUS }}

enum class Kind(val provider: Class<out BaseWidget>, val label: String, val layout: Layout) {{
{enum};
}}

{kt}
''')
print("generated", len(W), "widgets")
