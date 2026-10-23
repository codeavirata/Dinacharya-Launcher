# Play Console answers

These are suggested answers, based on what the app does today. You are the one who signs the
declarations, so read each question in Play Console and make sure the answer is still true for
the version you upload. If you add features later (analytics, crash reporting, your own server),
these answers change.

## 1. Data safety form (App content > Data safety)

**Privacy policy URL:** the address of `docs/privacy.html` (see README, "Turn on GitHub Pages").

**Does the app collect or share any of the required user data types?** Yes (because of ads).

**Is all of the user data encrypted in transit?** Yes (Google AdMob, Google sign-in, Google Drive and
Google Play use HTTPS).

**Do you provide a way for users to request that their data is deleted?** Ad data is held by Google
(users can reset the advertising ID in Android settings). Everything else stays on the phone or in
the user's own Drive, and the developer holds none of it. Choose the option that matches how the
form words it for your case; there is no developer-side data to delete.

### Data the Google Mobile Ads (AdMob) SDK handles in the free version

Google lists four kinds of data that the SDK collects and shares for advertising, analytics and fraud
prevention (page checked 19 October 2026, SDK 25.5.0):
https://developers.google.com/admob/android/privacy/play-data-disclosure

Re-open that page when you fill the form, because Google updates it, and copy what it says.
As of this writing:

| Google's data category | Collected | Shared | Purpose to tick | Required or optional |
| --- | --- | --- | --- | --- |
| Device or other IDs (advertising ID, app set ID) | Yes | Yes (Google) | Advertising or marketing; Analytics; Fraud prevention, security and compliance | Required |
| App activity: app interactions (taps, launches, video views) | Yes | Yes (Google) | same | Required |
| App info and performance: diagnostics | Yes | Yes (Google) | same | Required |
| IP address (Google's page lists it separately) | Yes | Yes (Google) | same | Required |

In Play Console's list, IP address is reported under the location or device-ID groups; follow the
instruction on Google's page for where it belongs.

### The Drive backup (optional feature)

The backup file goes to the user's own Google Drive, in the app's hidden space, and the developer
never receives it. Google's Data safety guidance says an app does not have to declare data the user
uploads to the user's own cloud account when the app never collects or accesses it for the developer.
Because the app itself writes and reads this file, there are two defensible answers:

- **Cautious (recommended):** declare **App activity > Other user-generated content** (routine, notes,
  answers) as **collected**, **not shared**, **optional**, purpose **App functionality**, encrypted in transit.
- **Minimal:** declare nothing for it and explain in the privacy policy (already done).

Over-declaring has no penalty; under-declaring can be flagged. If unsure, take the cautious answer.

### The home screen (launcher)

The app is a home screen. To show the list of all apps it asks Android's launcher interface (the one every
home screen uses; it needs no permission) which apps can be opened. That list is used on the phone to draw the
screen and is **never sent to the developer or to anyone else**, so under Data safety's **App activity >
Installed apps** answer **not collected**. The user's choices (hidden apps, shortcuts, pause rules, locked groups) and the count
of pauses stay on the phone and, if the user turns on the Drive backup, go to the user's own Drive with the rest
(the cautious answer above already covers it). The app does **not** ask for `QUERY_ALL_PACKAGES`.
Locked groups need no extra permission: the lock is the launcher's own screen in front of the app. The first-start
setup only guides the user to Android's own screens for permissions the app already declares (notifications, exact
alarms, full-screen alarm, battery, usage access); it adds none.

### Not collected by the developer

Name and email (read by Google sign-in on the phone, never sent to the developer), phone-use time,
routine, purchase details (Google Play handles the payment; the app only learns whether Pro is owned),
location, contacts, photos, files, messages.

## 2. Ads (App content > Ads)

**Does your app contain ads?** Yes.

## 3. Advertising ID (App content > Advertising ID)

**Does your app use an advertising ID?** Yes. Purposes: Advertising or marketing; Analytics; Fraud
prevention. (The Google Mobile Ads library adds the `AD_ID` permission by itself.)
If you build a version without ads (`ads_enabled` = false and the ads library removed), answer No.

## 4. Content rating (App content > Content rating)

Category: **Utility, Productivity, Communication or Other**. Answer **No** to violence, sexual content,
language, controlled substances, gambling, and user-to-user interaction. Answer **Yes** to
"digital purchases" and "ads" if asked. The expected result is Everyone / 3+.

## 5. Target audience and content (App content > Target audience)

Choose **18 and over** unless you decide to serve teenagers. Choosing an age group below 18
triggers extra rules about ads and data, and under 13 triggers Google's Families policy, which this
app has not been built for. If you do want teenagers, say so before publishing so the ads can be set
to suit them (child-directed or under-age-of-consent ad settings).

## 6. Other App content forms

| Form | Suggested answer |
| --- | --- |
| App access | All functions are available without login. Sign-in with Google is optional (only for backup). |
| Government apps | No |
| Financial features | None. (Buying Pro through Google Play is not a financial feature.) |
| Health apps | The app plans sleep and rest times but does not track or give health advice. Read Google's current list of health features and tick **None** only if none apply. |
| News apps / COVID-19 | No |

## 7. Permissions that Play asks you to explain

### Foreground service (App content > Foreground service permissions)

- Type: **System exempted** (`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`).
- Why: "The app rings an alarm at the exact minute a slot of the user's routine starts, using the
  exact-alarm permission. The service plays the alarm sound and vibration until the user answers
  the question on screen, for at most five minutes, then stops."
- Google may ask for a short **screen recording**: schedule a test alarm (Settings > Alarms > Test alarm),
  lock the phone, show it ringing and the question screen, then answer it.

### Exact alarms

The app uses `SCHEDULE_EXACT_ALARM` (the user allows it in Android settings; the app checks it, explains
it and sends the user to the right setting). It does **not** use `USE_EXACT_ALARM`, which Google limits to
apps whose main job is alarms, timers or calendars. If Play Console asks about exact-alarm use, the
answer is: "Alarms at the start of each scheduled slot of the user's daily routine."

### Full-screen intent (`USE_FULL_SCREEN_INTENT`)

Used only to show the alarm question over the lock screen when an alarm rings. On Android 14 and later
the app checks whether it is allowed and shows a button to the setting if not. If Play Console asks
for a declaration, answer that alarms are how the user answers each slot's question, and the full-screen
screen is shown only while an alarm is ringing.

### Usage access (`PACKAGE_USAGE_STATS`)

This is a special permission that the user turns on in Android settings. I could not find a Play form
that is specific to it. Before the permission is requested, the app shows a plain message saying what is read,
that it stays on the phone, and that the user can switch it off. If Play review asks, the reason is:
"Shows the user's own phone-use time on their home screen and in their own reports, and counts the minutes for the
daily app limits the user sets on the home screen."

### Strong lock: Display over other apps (`SYSTEM_ALERT_WINDOW`) and a foreground service of type `specialUse`

Both are used only by the optional **Strong lock**, which is **off until the user turns it on** in Settings > Home screen >
Strong lock. The user gives the two special permissions (Usage access, Display over other apps) in Android's own settings;
the app explains each before sending them there. While it is on, a small foreground service (`GuardService`, type
`specialUse`, with a notification that says "Strong lock is on") looks at which app is in front, using the same Usage access the
daily limits use, and, only when that app is one the user locked or paused, covers it with the same lock or pause screen the
home screen shows. It reads nothing but the name of the app in front, compares it with the user's own list on the phone, keeps
nothing and sends nothing. The phone and dialer, Settings, system screens, the keyboard, home apps and Dinacharya Launcher itself are
never covered, so the user can always call, undo the setting or uninstall.

What Play Console may ask, and a ready answer:

- **Foreground service permissions > Special use:** subtype text (also in the manifest):
  "Holds the apps the person has locked or paused, wherever they are opened from, by showing the lock or pause screen in front of
  them." Why: "The user chooses apps to lock or pause for a time. To hold such an app when it is opened from recent apps, a
  notification or another launcher, the app must keep watching which app is in front while the user's lock is on. The user turns it
  on and off in the app, and a notification is shown the whole time it runs." Google may want a **short screen recording**: turn on
  Strong lock, lock an app, open the app from the recent-apps screen and show the lock screen coming up in front of it.
- **Display over other apps / `SYSTEM_ALERT_WINDOW`:** "Used only to show the user's own lock or pause screen in front of an app
  the user locked or paused, and only when the user has switched the Strong lock on and allowed the permission in Android settings.
  It is not used for ads, for anything the user did not choose, or to hide what the app is doing."
- This is not an accessibility service, and the app still does not use `QUERY_ALL_PACKAGES`. Be honest that this is a
  self-control feature the user sets up for themselves; Google's review may ask for a recording or decide that the permission is not
  acceptable for the app. If that happens, the app works without it (the Strong lock is optional): remove
  `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE` and the `GuardService` entries from the manifest, or switch the
  Strong lock card off in the code, and publish again.

### Battery limits

The app does **not** declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and does not show the one-tap allow pop-up (Play
accepts it only for a few kinds of app). It explains the setting and opens the app's own info page, and Android's
battery list, where the user chooses Unrestricted.

### Home screen (the `HOME` intent filter)

The main activity declares the `HOME` category so the user can choose Dinacharya Launcher as the phone's home app
in Android's own setting (the app asks through Android's role request, which shows the system's question).
No Play Console form is specific to this. If review asks how the "pause" works, the answer is: "The pause is a
screen inside the app's own home screen, shown before the app opens an app the user chose to slow down. The
user can always tap Open anyway after a few seconds. By default the app does not draw over other apps, does not use an
accessibility service, and does not block or close any app. Only if the user switches on the optional Strong lock, the lock or
pause screen can also appear in front of an app they chose, using the permissions explained in the next section." The app does
not use accessibility services, `QUERY_ALL_PACKAGES` or `REQUEST_DELETE_PACKAGES`.

### The live wallpaper (`BIND_WALLPAPER` service)

The app contains a live wallpaper service that draws the user's own routine (a picture of the day). It is
not a separate product: it is how the launcher shows the day, so it also appears on the lock screen and in the
recent-apps screen. The launcher asks Android to switch to it with `ACTION_CHANGE_LIVE_WALLPAPER`; Android shows
its own preview and the user confirms. The wallpaper uses no permission, reads nothing but the user's routine on
the phone and sends nothing anywhere. The manifest marks the live-wallpaper feature as not required, so phones
without it can still install the app.

### The alarm screen (`USE_FULL_SCREEN_INTENT`)

The alarm question for each slot (and bedtime) opens as a full-screen notification over the lock screen, the
purpose Android reserves this permission for in alarm apps. On Android 14 and newer the user can switch it off in
Android's settings; the app's checklist has a button for it and the alarm still rings and notifies if it is off.

### Open the notification shade (`EXPAND_STATUS_BAR`)

An ordinary permission (granted at install, no form) that lets a swipe down on the home screen open the
notification shade. If a phone refuses, nothing happens and the system's own swipe still works.

### Billing

`com.android.vending.BILLING` is added for the one-time Pro purchase.

## 8. In-app product

Monetize > Products > In-app products > Create product:

- Product ID: `pro_unlock` (must match `pro_product_id` in `app/src/main/res/values/monetization.xml`)
- Name: Dinacharya Pro
- Description: Removes ads and unlocks every saved day and the weekly view in reports, locked groups and strict locks, weekday and weekend routines, daily goals, all colour themes and the full focus rules (strict pauses, daily limits, any number of apps, playful pauses). One-time purchase.
- Price: **Rs 99** (India). Set it once, then Google converts it to other countries' prices. The app shows whatever price Google Play reports; it is not written in the code.
- Status: Active.

## 9. Before you send the app for review

- Replace the **test AdMob IDs** in `monetization.xml` with your own: the App ID, the Banner and the Rewarded ad unit (README, "Earn from the app").
- Try the app as your **home app** on a real phone first (README, "The home screen"): the swipes, the all-apps list,
  the pause screen, and the way back with **Change home app**.
- Add testers in a **Closed test** and run it for 14 days with at least 12 testers (personal accounts).
- Add the **Play app signing SHA-1** to your Google Cloud Android client, or Google sign-in will fail
  for people who install from Play (README, "Publish on Google Play", step 8).
