# Dinacharya Launcher: a minimal home screen for your daily routine

A home-screen app (a launcher) with **no apps on it**: just your day. Alarms, goals, daily reports
and a Google Drive backup come with it.

- **Home screen (launcher):** no icons and no app list. The whole screen is your day: the time on the
  left, today's date on the right, phone use today and every slot of the day with the current one
  highlighted, in four colour themes (light and night). It is shown by the **Dinacharya Launcher live wallpaper**,
  which comes with the launcher: Dinacharya asks Android to put it in place and you confirm once (Android
  never lets an app do that silently). Being the real wallpaper, it also shows on the lock screen and in the
  recent-apps screen. A small one-line "© 2026 | Developed by Code Avirata" credit sits at the bottom (it can be hidden). Tap
  your day to open the dashboard, swipe up for every app (a plain list with search and an A to Z strip, over a
  blurred view of your day; Dinacharya is in it too and opens the dashboard), swipe down for notifications, left for the camera
  and right for the phone (both can be any app), press and hold for settings. Apps that pull you away can wait a
  few seconds before they open, or (Pro) ask a quick sum, a sentence, slow breaths or why you are opening them;
  you can always open them anyway. **Locked groups** keep a set of apps shut for hours you choose. The first time the
  app opens, a **welcome**, a **setup** that first asks which features you want (and asks the phone for a permission only when a
  feature you chose needs it) and a **tour** lead into the dashboard (the setup and the tour can be replayed from Settings).
- **Routines for each day:** a weekday routine, a weekend routine, an exam-day routine… each used
  on the days you pick, and a **routine for one day** (today, tomorrow or any date). Alarms, the home screen and
  reports follow the right one every day.
- **Editing:** drag to reorder, swipe to delete or add, notes and activity types (Study, Revision…)
  per slot, and Undo. The day always keeps its length.
- **Alarms:** an alarm at the start of each slot; at bedtime it asks when you go to sleep. A test
  alarm and a phone checklist show whether alarms will really ring on your phone.
- **Goals and insights:** daily goals per type (e.g. Study 6 h) with streaks, a score for each day,
  phone use by hour and app, and suggestions. Any day can be saved as a PDF.
- **Google sign-in and backup:** like WhatsApp: sign in with your Google account, back up daily,
  weekly or monthly to a private space in your Drive, and restore on a new phone.
- **Free, with an optional Pro:** the app is free with a small ad on Home and Insights. Free keeps the useful
  core: the home screen, alarms, a to-do list for every slot, **today's report** (with a PDF), one pause rule for one app and one lock on one app.
  **Dinacharya Pro**, Rs 49 a month or Rs 499 once for life (both set in Play Console), removes the ads and unlocks **goals** (a goal for a slot of your
  routine, with its own to-dos), every saved day, the weekly view and the deeper analysis, any number of pauses and locks, locked groups, strict locks,
  extra routines and colour themes. Anyone can open all of it for a day, free, by watching **three short videos**; when the day is over it closes again.
  The **Free and Pro** screen (the last page of the tour, and a button on the Pro screen) shows the two side by side. See "Dinacharya Pro and ads" and "Earn from the app".

The **dashboard** has five tabs at the bottom: **Home** (now and next, today's goals, a preview of the home screen),
**Routine**, **Insights**, **Backup** and **Settings** (Dinacharya Pro, home screen, home screen look, alarms,
permissions, help, about). Tap a tab, or swipe left or right on the page to move to the next or the previous tab (the pages follow your finger and slide back if you let go early; a slider, a switch or a card you swipe to delete keeps its own swipe). You open it from the home screen (tap your day, or pick the dashboard for a swipe in the home settings)
(Dinacharya is also in the list of all apps: opening it there shows the dashboard). The app's one icon opens a welcome the first time and the dashboard after that. The home screen itself has no icon (it is what Android shows when you press Home) and is kept out of the list of recent apps, so Dinacharya shows there once.

## Get the APK

Pick one way. Each gives you `app-debug.apk`, which you can install on your phone.

### A. Build it online with GitHub (nothing to install)

1. Create a free GitHub account and a new empty repository.
2. Upload every file and folder from this project, including the hidden `.github` folder.
   If the upload skips `.github`, open **Add file > Create new file**, type
   `.github/workflows/build-apk.yml` as the name, and paste in the contents of that file.
3. Open the **Actions** tab, choose **Build APK**, then **Run workflow**.
4. When it turns green, open the run and download **Dinacharya-Launcher-apk** at the bottom.
   Unzip it to get `app-debug.apk`.

### B. Build it in Android Studio (on a PC or Mac)

1. Open this folder in Android Studio and wait for Gradle to finish syncing.
2. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

### C. Command line

With JDK 17 and the Android SDK installed (`ANDROID_HOME` set), run `./gradlew assembleDebug`.

## Install on the phone

1. Copy `app-debug.apk` to the phone and tap it.
2. If asked, allow installing from this source ("Install unknown apps").
3. **Updating from version 1:** uninstall the old Dinacharya first. Version 2 is signed with a
   new, fixed key (needed for Google sign-in), and Android will not install it over an app signed
   with a different key.

## Use it

1. Open **Dinacharya**. The first time, a **welcome** screen ("Get started") leads into a **setup** that starts by asking what you want
   Dinacharya to do, so nothing is asked for that you did not choose. Five **features** are offered, each with a small animated
   picture of what it does (still pictures when the phone has animations turned off): **Day alarms**, **Phone time and app limits**,
   **Pause and lock apps**, **Home screen and wallpaper** and **Quiet phone**. Under each one it lists, in plain words, what the
   phone will be asked to allow. You tick the ones you want and tap **Set up N features**; the setup then goes through only what
   those need, one page each, with a picture of what the step is for, why it is needed and what happens without it, and checks
   it as you go (`FeaturePlan` says which things a feature needs and in what order; `Features` holds the words). The things are:
   **notifications**, **exact alarms**, the **lock-screen alarm**, **battery** use without limits, your phone maker's own
   background lists, **usage access**, **display over other apps** (for locks that hold in front of other apps), **Do Not
   Disturb** access, your **home app** and the **wallpaper**. Each page opens the right Android screen and ticks itself when it
   is done; any page can be skipped with **Not now**. A feature you chose and did not finish stays on the dashboard's Home tab
   ("N features still to finish") and in **Settings > Features**, where you can also add more features at any time. Android makes
   you confirm each of these yourself on its own screen: an app cannot switch them on silently. Then a **tour** walks through
   the app, and it ends in the dashboard with a "You're all set" message (a button to go to your home screen, or to
   make Dinacharya your home app if that is still to do). Until the wallpaper is in place the launcher paints the same day itself, so the home screen is never
   empty, but your recent-apps screen and lock screen keep showing the old wallpaper. People who set the app up before this
   setup existed keep what they have: only alarms count as chosen for them, if alarms are on. **If the setup is cut short** (Android
   opens the home screen when its home app page closes, or the phone is switched off) the app picks it up again with the features
   already chosen and goes on from the next thing still to allow (`FirstRun.SETUP`, the `setup_begun` note), instead of showing the
   welcome again. Any one feature can be used on its own: nothing else is asked for. Pause and lock apps also asks for the home app,
   because an ordinary pause or lock holds when an app is opened from Dinacharya's home screen (a strict one holds everywhere).
2. Tap your day to open the dashboard. On **Routine**, pick
   **Wake-up time** or **First task time**, set the time, and edit the slots
   (or tap **Paste from web app** to bring in a routine made in the web planner).
3. Optional: turn on alarms (**Settings > Alarms**), set goals (**Insights**) and sign in to back
   up (**Backup**).

## A calmer layout: parts that open and close

To keep each screen short, details sit behind a header that opens and closes. A header is at least 48 dp tall, shows a short line of
what is inside while it is closed ("3 slots · 4 h", "2 apps have a pause", "Still to allow: Notifications"), has an arrow that turns,
and is read out by a screen reader as "expanded" or "collapsed". The app remembers what you left open, screen by screen.

- **Home:** the home-screen preview is closed to begin with. The Now card keeps the break and to-do buttons, and **Your to-do list** opens under it.
- **Routine:** a slot card shows one line (type · length · alarm); the arrow opens its type, kind, length buttons, alarm and ⋮ menu.
  **Time by type**, **How to edit slots**, **More tools** and **Activity types** are closed to begin with.
- **Insights:** a goal row shows its name and streak, and opens to its plan, its apps and its buttons. **Slots so far**, **Phone today**,
  **Time by type today**, **All your goals** and **Habits** are closed to begin with, each with a one-line summary. **Habits** (Pro) is a short list of cards
  (one thing noticed on each, with a colour stripe, a title and a badge: **Good**, **Watch** or **Set up**); a card opens to **What I noticed** and
  **Try this**, and opening one closes the one that was open.
- **Backup:** **Settings**, **Restore**, **Other ways to sign in** and the long note about the backup are closed to begin with.
- **Settings:** in **Home screen**, **Focus**, **Phone use in slots**, **Locked apps**, **Strong lock**, **Hidden apps**, **Swipes**, **Look** and **Today** each
  show a summary and open on tap, with the long explanations under **How it works**. Alarms has **Slots without a sound**,
  **Start-of-day alarm** and **Is the phone ready?**; Help has one fold-out per topic.
- At an alarm, the choices open under their button, one at a time.

Nothing was removed: every setting is where it was, one tap further.

## The home screen

**Making it your home screen.** Tap **Make this your home app** (on the home screen, on the
dashboard's Home tab, or in the tour). Android shows its own question; say yes. On older phones it opens
the **Home app** setting instead, where you pick Dinacharya. To go back to your old launcher at any
time: **Settings > Home screen > Change home app**, or Android's own **Settings > Apps > Default apps
> Home app**. Nothing is removed from your phone.

**What it shows.** No apps at all: your day. The Dinacharya day poster is the phone's wallpaper (a live
wallpaper that redraws every minute while the screen is on). It shows the time, the date, phone use today and
every slot, with the one you are in highlighted. **Putting it in place:** the first-start setup asks Android to
switch to the Dinacharya Launcher wallpaper. The wallpaper is part of the launcher, not an extra, so the launcher **looks at it every
time the home screen comes up** (and when the dashboard opens, after a restart or an update, and every few hours in the background)
instead of trusting an old note. If it is not in place, the launcher **paints your day itself at once**, so the home screen is never
plain, and shows a small chip, **Bring back your day wallpaper**; the dashboard's Home tab shows an amber card ("Your day wallpaper
is gone", "Only the lock screen has your day" or "Your day wallpaper is not set") with a **Bring it back** button. A wallpaper that
was in place and then lost is asked for again **at once**, and again after 10 minutes if you turned the preview down; one that was
never set is asked for once a day. It can also be done from the dashboard's Home tab, the tour and the home settings.
Android shows its own preview with a **Set wallpaper** button; tap it (on Android 14 and later it may ask where:
choose **Home screen** or **Home and lock screen**; "Lock screen" alone leaves the home screen on the old picture, and
the app says so). Some phones do not report a live wallpaper back to apps, so the app also counts its own wallpaper engine as proof
while it is running and the preview you just confirmed for two minutes; an old note is never proof. **Closing the app from App info
(Force stop) makes Android remove a live wallpaper and cancel every alarm of the app, and Android tells the app nothing about it.**
The app cannot prevent that; it finds out the next time it starts and puts both back (see "If Android closes the app" under Alarms).
This is why the recent-apps screen shows your day instead of another picture. **At midnight** the old schedule scrolls up and out while the next day rises from below, and it has
fully taken its place by your wake-up time, so the day on the screen is never stale. Its colours, clock, phone time and
spacing are in **Settings > Home screen look** in the dashboard (also reached from **Settings > Home screen >
Look > Change what the home screen shows**). **Auto, Dark, Light** in the home settings colour the list of all
apps and the pause screen, not the day.

**Gestures.**

| Gesture | What it does |
| --- | --- |
| Swipe up | All apps, as a plain list of names (no icons; Dinacharya is in the list and opens the dashboard), over a blurred and dimmed view of your day: **the keyboard opens at once with the cursor in the search box** (turn it off in Settings > Home screen > "Open the keyboard in the list of all apps"), type to search (a single match opens at once after two letters, if you keep that on), slide along the A to Z strip to jump, press and hold an app to add a pause, hide it or open its info |
| Swipe down | Notifications (needs a permission the app asks for with its install; if a phone refuses, swipe down from the top edge as usual) |
| Swipe right | The phone (dialer) by default; you can pick any app, the dashboard, or nothing |
| Swipe left | The camera by default; you can pick any app, the dashboard, or nothing |
| Press and hold | The home screen's settings |
| Tap your day | The dashboard |

**Pause before distractions.** In **Settings > Home screen > Focus** (or press and hold an app in the
all-apps list) give an app a pause. When you open it during the kinds of slot you chose (Task by default),
a screen counts down a few seconds with **Go back** and **Open anyway**. It is friction, not a lock: you can
always open the app after the wait. Free: a short pause for **1 app**. **Pro:** strict (longer) pauses,
a daily limit in minutes per app (uses Usage access), any number of apps, and **playful pauses**: pick how the
pause goes for each app (**Settings > Home screen > Focus**, or press and hold the app): a **quick sum** (addition,
subtraction or multiplication; three of them for a strict pause or a used-up limit), a **sentence to type**,
**slow breaths** with a moving circle (two, or four when strict) or **say why** you are opening it (a real need opens
it at once; the other reasons get a kind sentence and a short wait). Go back is always there, and a longer wait
(30 seconds, 60 when strict) brings **Skip the challenge**. Each pause is counted; the day report and the suggestions
use it ("went back 4 times", "opened anyway 6 times").

*Pauses apply when you open an app from this home screen (its all-apps list or a shortcut).* On their own they
cannot stop an app that is opened from the recent-apps screen, a notification or another app: Android does not let a
launcher do that. The **Strong lock** (below) can; it needs two permissions, which the app asks for when you save a lock or a pause.

**Locked groups.** In **Settings > Home screen > Locked groups** make a group (for example *Social*), choose its
apps and how it locks: by **time** (hours and days), by **activity** (while a kind of slot is going on) or both (see
"The group editor" below), or lock it **now** for 30 minutes to 8 hours. While a group is locked, its apps
are greyed with "locked" in the all-apps list, and opening one shows a screen with the group, the time it ends and
the time left, with **Go back**. You can break a lock yourself: tap **Break the lock**, wait 30 seconds, and type a
sentence. When the lock runs out while the screen is showing, the app opens. Free: **one lock on one app** (the first app in it holds; if an older lock has more, its row says "only 1 held, needs Pro"). **Pro:** locked **groups**, with a name for each, any
number of groups (up to 8, with up to 40 apps each) and **strict locks**, which cannot be broken and cannot be
changed or deleted while they hold. A lock that was set up while Pro was on and is left after Pro ends keeps its
group but stops holding, apart from the first group. Opening a locked app and going back counts as a pause in the
day report; a lock is checked before a pause rule, so a locked app is never also paused.

**The group editor: choose how it locks.** Tap **Add a locked group** (or **Edit** on a group). The screen is short on purpose: the
apps, then **Lock by** with a choice of three, and under it only the settings for the choice you made.

- **Time**: locked at the hours you set. Pick the from and to times (a window can run past midnight, and the same hour for both means the
  whole day) and the days (**Every day**, **Weekdays**, **Weekends**, or tap single days). This is the choice a new group starts with.
- **Activity**: locked whenever a slot of the types you pick is going on, at any hour, for example *Social* while a *Study* slot runs. Tap
  **Choose activity types** and tick any of "Any task", "Any rest"… or single types such as Study or Exercise (Other is there too). There are
  no hours to set. Back-to-back slots of the same type are one stretch. A break, a slot taken off, and the gap between slots do not lock, and
  the lock screen says "While you are on: Study".
- **Time + activity**: locked only when **both** hold, for example *Social* on weekdays 09:00 to 17:00 **but only while a Study slot is going
  on in those hours**. You set the hours and days and pick the types.

A line under the settings says in plain words what the choice comes to ("Locked while a Study slot is going on."). Save asks for what is
missing (an activity type for the last two, a day for **Time + activity**). The older way of mixing them, locked in the hours **and also**
whenever the types are going on, is not offered for new locks; a group that was made that way shows a fourth choice, **Time or activity**, so
it keeps working as it did and can be changed.

**More options** is closed unless one of its settings is already on (a tap opens it):

- **Lock now for** 30 min to 8 h, and **Unlock now**: a lock by hand, on top of the choice above. It always holds.
- **Also lock in library mode**: while library mode is on (see "Library mode") the group is locked as well, whatever the choice above says; the
  lock screen says "Locked until library mode is switched off" when it is an open-ended session.
- **Hide the apps while they are locked** and **Strict** (Pro), explained below.

The choice uses the same limits as other groups (free: one lock on one app; groups of apps and strict locks are Pro), and it also holds through the
Strong lock. Groups saved by an earlier build keep their meaning: with no activity types they are a lock by time; with types and no days,
by activity; with types, days and the "only when both hold" switch, **Time + activity**.

**Hide locked apps.** Turn on **Hide the apps while they are locked** in a group and its apps are left out of the list of all apps
and out of search while the lock holds, and come back when it ends. (Locks still hold on any other way of opening an app, such as recents
or notifications, with the Strong lock.)

*Locks hold wherever an app is opened from.* On the home screen itself a lock is checked when you open an app; for an app
already open, or opened from the recent-apps screen, **App info**, a notification, another app or another home app, the
**Strong lock** (below) holds it. It is on from the start and needs two permissions that you give once (you are asked right after saving
a lock); until then a lock holds on the home screen only, and the app says so. A strict lock is
self-control, not a security wall: while it holds, the home settings cannot be reset and a backup restore leaves
the groups alone, but you could still switch the home app, open the app from its Android app info, or uninstall
Dinacharya. Calls still arrive and the emergency call on the lock screen is unaffected.

**Strong lock (on from the start; part of a lock).** Without it, a lock or a pause only works when you open an app from
Dinacharya's home screen, so an app can still be opened from the recent-apps screen, App info, a notification, a widget, another
home app, or kept open when a lock starts, and nothing is held at all while another launcher is the home app. It runs by itself as
soon as something is locked or paused, and does nothing (no notification either) while nothing is. It needs two things that Android makes you
allow yourself: saving a lock or a pause asks for them (a plain message first, then Android's page, and the second after the first is given),
the home screen shows the chip **Make your locks hold everywhere** and the dashboard a card until both are given, and each has a button in
**Settings > Home screen > Strong lock**, which can also switch it off by hand: **Usage access** (the same permission the daily limits use, so it can see
which app is in front) and **Display over other apps** (so the lock or pause screen can appear in front of an app).
How it works: a small foreground service (`GuardService`, with a quiet notification "Strong lock is on") looks at the app in
front about three times a second while the screen is on (`FrontTracker` reads Android's usage events; nothing while the
screen is off or the phone is locked). When that app is in a locked group, or has a pause rule, the same decision the home
screen makes (`GuardRules`, which reuses `LockRules` and `FocusRules`, so free, Pro and video-pass limits apply exactly as
they do there) says what to do: Dinacharya first puts a plain cover over the app, then brings up the real lock or pause
screen (`GuardActivity`, which shows the very same `LockScreen` and `PauseScreen`). **Go back** goes home; a pause is
waited out or answered as usual; a lock can be broken with the 30-second wait and the sentence (not a strict lock). An app
you were let into is not stopped again while you use it (nor for 30 seconds after you leave it), a lock that starts while an
app is open holds it at once, and a pause comes only when an app is opened, not while you use it. If the lock screen cannot
come up (some phones restrict starting screens from the background), the cover keeps holding the app by itself with a
Go back button (and, for a pause, **Open anyway** after the wait). `GuardLoop` holds this one-look logic and is tested.
**Never covered:** Dinacharya itself, the phone and dialer, emergency calls, Settings, the permission screens, the package
installer, system screens, Google Play services, the keyboard and any home app, so you can always call, undo the setting or
uninstall. It stops itself when you switch it off, when nothing is locked or paused any more, or when you take a permission away, starts again after a restart, an update, every time the home screen or dashboard comes up, on the three-hourly job and when its task is swiped away (`StrongMode`, with a beat written at every look so the problem report can say whether it runs), and only the
name of the app in front is compared with your own list (nothing is saved or sent). The settings card, the Pro prompts and the
text next to locks and pauses say whether it is on.

*Honest limits.* It cannot be a security wall (a person can switch it off, use Android's app info or uninstall); a strict lock
is still self-control. It has **never run on a real phone**: phone makers differ in how they treat background services and
screens started from them, so try it on your phone and expect fixes; if the phone kills background apps, allow Dinacharya
to run in the background (the setup's battery step). It needs a notification (Android requires one for a foreground
service). It is a permission Google Play looks at closely: see `store/data-safety-and-declarations.md` for what to declare
and how to remove it if Google asks.

**Hidden apps.** Press and hold an app in the all-apps list and choose Hide. It disappears from the list
and from search; **Settings > Home screen > Hidden apps** brings it back. Uninstalling is done in Android's
own app info (the menu has an App info button); the app does not delete apps itself.

**If something goes wrong.** The home screen has a plain fallback with **Try again**, **Change home
app** and **Open Dinacharya**, so a problem here can never leave the phone without a way out.

**Not in the app, on purpose:** double-tap to lock the screen (it needs an accessibility service, which
Google Play reviews very strictly), an accessibility service of any kind, app icons in the list, and a request for the
"see all apps" permission. The app list comes from Android's launcher interface, which needs no special permission. A lock
that also works from outside this home screen is the Strong lock above.

If a big clock or the status bar covers the top of your day, raise **Space at top** in
**Settings > Home screen look**. Changes made from an alarm are for today only.

## Take a break, and to-do lists

Under the slot that is going on, the **Now** card on **Home** has two buttons.

- **Take a break** opens a small box with **−** and **+** (5 minutes at a time, 10 to begin with) and a switch, **Until this slot ends**.
  The box says when you will be back ("Back at 15:40"). A break never goes past the end of the slot, so it cannot touch the next one;
  when the slot is almost over the switch is on and cannot be turned off. While a break lasts the card says "On a break until…",
  the time is not counted as worked, and **Back to work** ends it early. When it is over a notification asks if you want more time
  (the same − and +, again only up to the end of the slot) or to go on. A break is not available in Sleep, in a slot that was taken
  off, or when the slot is almost over. Pause rules that depend on the slot and locks by activity type (see "Locked groups") are
  lifted during a break.
- **To-do** opens the small tasks of this slot: tick, add and remove them. Under the card, **Your to-do
  list** shows every item with a tick box ("3 of 5 done" when it is closed), so nothing is hidden in the app. The list of a slot has two kinds of
  line: the things you added to the slot, and the **goals of that slot** (see "Goals and streaks"): a goal with no to-dos of its own is one more thing to do (tick it,
  or time ticks it when the target is reached); a goal with to-dos is a heading with its own to-dos under it, struck only when all of them are done. The goal's
  to-dos can be ticked here, on Home, in the to-do box or in Insights, and it is one tick everywhere. To-dos are free; goals are Pro.

**A to-do can repeat.** Next to the box where you type a new item is a **↻** button: **Does not repeat** (the default),
**Every day**, **Every other day** or **On chosen days** (day buttons appear). It goes back to "does not repeat" after each item you add.
A repeating item shows "↻ every day · tap to stop repeating"; tapping it asks if it should stop. Each day gets a fresh, unticked copy.
An item added to a slot's list comes back in the first slot of that day with the same name and kind (so it follows a renamed
or moved slot, but not a slot that was taken off). Stopping a repeat keeps the
copies that already exist. Up to 20 things can repeat. A to-do that does not repeat is for that day only.

**On the live wallpaper** the lists are drawn under the slot while it runs, in the colours of your theme, and the rest of the home
screen is not moved. Up to 8 lines are shown. When there are more, the finished items scroll up out of sight first; if none are
finished, the list pages down through the rest every 8 seconds (a small "7–14 of 14" tells where you are). A short list stays still,
and nothing moves while the screen is off.

## When the day does not go as planned

The schedule on **Home** and on the live wallpaper is a picture of **your day as it really goes**, not only as it was planned. The
saved routine is never changed by any of this (alarms, reports and locks go on using it); only what you see is adjusted, and the
next day starts clean.

**What Home and the wallpaper show.** A slot you **start later** shows "STARTING LATER" with the time it will start, and the slot
before it carries on until then ("Carried on, so it ends at … instead of …"). A slot that begins late shows "STARTED LATE" with the
real time ("Started late, at 9:10 instead of 9:00"), and the same for a slot that **ran longer** because you continued the previous
one, or one that began early or ended early. A **break** shows as its own row, "Break until 4:30 · 12 min left", and then "Back
to <slot>, which ends at …". The row, the time left and the note under the slot's name all follow the clock on their own, so you do not
have to do anything.

**Finish early.** Under the slot that is going on, next to **Take a break** and **To-do**, is **Finish early**. It asks what comes next:

- **Start "next slot" now**: the slot you are in ends now and the next one starts at once. It still ends at its planned time, so it gets
  the time that was left.
- **Take a break first**: a break of 5 minutes at a time (− and +), then the next slot goes on. A break never reaches past the time the next
  slot was to begin.
- **Start "next slot" after a while**: the same − and +; the next slot starts at that time.
- **Take the rest of today off**: see "Taking a day off" below.

The break and "after a while" buttons are not shown when there is no room for at least 5 minutes. There is no "finish early" in a slot
that was taken off, one that was carried on or cut, or when fewer than 3 minutes are left. A slot after the next that was taken off is passed over: the
next slot that is on begins. **Every slot can be finished early**, sleep and the slot before it too, and the button says what it does:

- **Finish early** in an ordinary slot, as above.
- **Early sleep** in the slot just before sleep: "Go to sleep early?" opens the bedtime question ("Early sleep", "Going to sleep early?"), and when you set the wake-up
  alarm sleep begins at that moment instead of at the planned time, the slot before it ends there, and the bedtime question that was to ring at the planned time stays
  quiet. You wake at the normal time (more sleep, nothing else changes). "Not yet" closes it and nothing changes.
- **Early wake** in sleep (all night, from when sleep begins): "Wake up early?" with **Start my day now**. The next day begins now: its first slot keeps its planned end and gets
  the time, the night ends now, the alarm of the first slot is not rung, and every other slot stays where it was. "Changed for today only" on the dashboard lists it ("Tomorrow starts at
  4:00 instead of 5:30 (you woke early)") and **Undo today's changes** takes it back. Reports show the planned sleep and the real bedtime as before.

**Skip a slot when it starts.** On the slot-start question (see "Alarms") **Skip this slot, start "next slot" now** takes the slot off for today and starts the next one at
once; the next slot ends when it was planned to, so it gets the skipped slot's time. The report shows the skipped slot as skipped and counts the next from the moment it began;
Home shows "Started early". A skip is not offered on the bedtime question (sleep has **Early wake** instead).

### Taking a day off

There are two ways in: the **Take the rest of today off** button of **Finish early**, and, on the **start-of-day alarm**, the button
**Take the day off** (it is there when **Offer "Take the day off"** is on in **Settings > Alarms > Start-of-day alarm**, which it is by
default). Either way the app first asks you to confirm. After that:

- The slot going on ends now, and **every alarm of today is silent**: no slot alarm, no bedtime question, no break timer and no
  reminders. Home says "Enjoy your day off", and the wallpaper shows the rest of the day as a day off.
- The app asks: **Plan your day now, later, or leave it?**
  - **Plan now** opens the Routine tab so you can make the schedule for what is left of today. The app knows you have planned the
    day again when the day's slots or its start time are different from what they were when you took the day off (a slot added, removed,
    renamed or resized, or a new start time). Then the alarms come back and the new plan is used for the rest of today. A small
    change for today only (for example a skipped slot) does not count; use **Back to my schedule** for that.
  - **Later** asks when to ask again: in 1 hour, 2 hours, 4 hours, or this evening at 18:00 (that last one only when it is at least 3 hours away).
    At that time a notification brings the question back.
  - **Leave it** keeps the day off. Nothing rings until tomorrow, or until you plan the day yourself.
- On Home, a day off shows **Plan the day** and **Back to my schedule**. The second one ends the day off at once and turns your own schedule and
  alarms back on.
- The day off ends by itself when that schedule day is over (at the next start of day), so tomorrow is never affected. A day off also
  silences the bedtime question that evening; if you want it, tap **Back to my schedule** first.

## Weekday, weekend and other routines

*(Making a second routine is part of Dinacharya Pro; the first routine is free and a routine
that already exists is never taken away.)*

At the top of the **Routine** tab you see your routines as chips. Tap **＋ Weekend routine** (or
**＋ New**) to make another one, starting as a copy of an existing routine. A routine called
"Weekend" is used on Saturday and Sunday straight away. Under the chips, the days in colour use
the selected routine: tap a day to give it to this routine. Long-press a chip (or tap **Rename**)
to rename it; **Delete** sends its days back to the first routine.

**A routine for one day.** For a day that is not like the others (an exam, a trip, a day off) tap **＋ One day only** next to
the chips, choose **Today**, **Tomorrow** or **Another date…**, and which routine to copy. The day follows that routine and
nothing else changes: it is listed under the chips with its date, you edit its slots like any routine, and it disappears
by itself once the date has passed (**Delete** removes it earlier). A date can have only one; making another for the same date
replaces it (with an Undo). Alarms, the home screen, the wallpaper, reports and goals all follow it on that date. Making one is
part of the same Pro item as extra routines.

Each routine has its own start time and slots; the look, activity types and goals are shared.
Under **Start of day**, **Time by type** shows how the day is shared out: one bar with a slice for each type in its colour, then
the hours and the share of the day for each kind, and the hours for each type inside it (Study 6 h, Exercise 1 h…). It follows the
routine you are editing and changes as you change the slots.

When tomorrow follows a different routine, tonight's sleep stretches or shortens so it ends
exactly when tomorrow starts (for example Friday night runs on to Saturday's later wake-up).

## Editing slots

Every slot in the **Routine** list is a card. The day always stays the same length (24 hours for
a full day): minutes only ever move from one slot to another, they are never lost or made up.

- **Move:** hold the **≡** handle on the left of a card and drag it up or down. The list scrolls
  by itself near the top and bottom of the screen. Let go to drop it.
- **Delete:** swipe a card to the **left** (or **⋮ > Delete**). The app asks where its minutes should
  go: to the slot above, to the slot below, or shared out over all the other slots. The last
  remaining slot cannot be deleted.
- **Add below:** swipe a card to the **right** (or **⋮ > Add a slot below**). Give it a name, a kind and a
  length, and choose where its minutes come from: the slot you swiped, the next slot, or all the
  other slots (Sleep is never touched when other slots can lend). No slot is ever made shorter
  than 5 minutes this way, and the app tells you how many minutes can be taken.
- **Add slot** at the top adds one just before Sleep. **Add rests** puts a short Rest between
  back-to-back tasks and takes its minutes from the task before it, so the day stays 24 hours.
- The **−** and **+** buttons change a slot by 5 minutes (hold for 30); tap the length to type
  it. This changes the length of the day, and the note under **Start of day** tells you if you
  are over or under 24 hours.

After deleting, moving or adding, an **Undo** bar appears for a few seconds.

- **Notes:** **⋮ > Add a note** on any slot, for example the chapters to cover. The note shows
  under the slot, on Home while the slot runs, and on its alarm.
- **Duplicate:** **⋮ > Duplicate** splits a slot into two equal halves.

If you delete, add or move slots, today's temporary changes (a rename or a later start chosen at
an alarm) are cleared, because they were tied to the old list. Changing colours or types does not
clear them.

### Activity types

A type is a kind of activity inside a main kind, for example **Study** inside **Task**. Every slot has a
type: there is no "no type" any more. Each of the five kinds has an **Other** type, used when you pick nothing; a slot
that had no type now shows as Other, and **Other** cannot be removed. Start types are Study, Revision, Practice and Work
(Task), Nap (Rest), Exercise, Reading, Family time and Personal (Personal), Meal (Meal), Sleep (Sleep), and Other under
every kind. Routines saved by an earlier version get the new types added; nothing already there is changed. Add your own
from **Activity types** at the bottom of the Routine tab (tap **＋ Add** in a kind's row), or choose **＋ New type…** in a
slot's type list. Tap a type chip to remove it; its slots become **Other** and no slot is deleted.

Every type gets **its own colour automatically**, the same everywhere: the dots in lists, the slices of the ring chart
in Insights and the bar on the Routine tab. The colour follows the type's place in your list of types, so it stays the
same while the list only grows. The Other type of each kind has a muted tint of its own, so the five can be told apart.

The home screen shows the type under the slot name (instead of the kind). Alarms, reports, the score
and suggestions treat the slot as its main kind, so a Study slot counts as a Task.

## Goals and streaks

*(Goals are part of Dinacharya Pro. Goals that already exist keep working in Free, and what a free-day pass added goes back when the day is over.)*

A goal belongs to **one slot of your routine**: "2 hours of Morning study", not "2 hours of Study". On **Insights**, tap **Add a goal**, pick the
**slot** (the list shows the slots of your routine, by name; with no slot yet it asks you to add one in the Routine tab first), a daily amount
(such as 2 h), how it repeats, and, if you like, a **to-do list** for the goal. Through the day the goal fills up with the kept time of that
slot. A 🔥 streak counts the days in a row you reached it. Tap a goal to change or remove it. Today's goals also show on **Home**, and the goal appears under its slot
and on the live wallpaper.

- **A goal without to-dos is a to-do of its slot.** It shows in the slot's to-do list as one line: you can tick it, or it ticks itself when the time
  target is reached (the goal bar, the streak and the reports stay time-based). A goal that time has ticked cannot be unticked.
- **A goal with to-dos is a heading over them.** The to-dos of the goal (up to 12, the same on every day the goal counts, ticked fresh each day) are
  listed under the goal in the slot's list, on Home, in the Insights goal card and on the live wallpaper. The goal itself is struck only when **every one of
  its to-dos is done**; the heading cannot be ticked by hand. A goal's to-dos are not copied anywhere: a tick made on Home, in the to-do box, in Insights or in the
  list under the slot is the same tick everywhere (and the live wallpaper shows it).
- **Two kinds of to-do, one list.** A to-do added to a slot with the **To-do** button belongs to that slot only; a to-do inside a goal belongs to the goal and shows in the slot too.
- **Repeat.** A goal can be for **today only**, **every day**, **every other day** or **on chosen days**, and can be limited to days that also have
  another activity (**Only on days with…**). It counts on a day only when that day's schedule has the slot; on other days it neither counts nor breaks the streak. **All your goals** lists each goal with its rule and whether it counts today.
  A today-only goal is cleared by itself once the day has passed.
- **If a slot is renamed** (in the Routine tab) its goals follow it. A goal's slot cannot be changed in the goal editor: to move a goal to another slot, remove
  it and add it again. If the slot is deleted or its activity type is changed, the goal stays in the list but counts on no day until a slot of that kind and
  name exists again (remove the goal if it is not needed).
- **Older goals** that were made for a whole activity type (such as Study 6 h) keep working as before and are not deleted; new goals always take a slot.
- The time of a goal is measured against its first matching slot of the day.

## Insights: Today so far

**Insights > Today so far** shows the score and the parts it is made of, then:

- **Time by type so far:** a ring chart of the time you have spent, by activity type, in the type colours, with a legend.
  Time counts as the slots of your plan pass; breaks and put-off time are left out.
- **Slots so far:** one row for each slot with the time worked, how much of it had an app in use (**On apps**) and how much
  had none (**Without apps**). Tap a slot to see each app, its breaks, the time its start was put off and time given to or taken
  from the slots next to it.
- **Daily goals:** each goal with its bar, the apps used while it was going on and the time without any app.
- **Phone today:** the hours with an app in use and the top apps.

App times need Usage access (see "Clock and phone time"). Without it the app lists are empty and the slot times still show.

## Colours

*(Teal is free; the other three colour themes are part of Dinacharya Pro, and one already chosen is
kept.)*

In **Settings > Home screen look**, tap **Teal**, **Sunset**, **Ocean** or **Graphite**. With
**Light / Night / Auto** this gives eight looks, used on the home screen, the previews and the
slot colour bars. **Show "Developed by" mark** turns the small mark on the home screen on or off.

## Clock and phone time

Both are on by default and can be switched off in **Settings > Home screen look**.

- **Time:** a large clock on the left, in 12-hour or 24-hour form. It needs no permission.
  If you switch the clock off, the date moves back to the left.
- **Phone today:** the time the screen has been on with an app in front of you, with a bar for
  each hour. Android only shares this with apps you allow, so tap **Allow usage access** in the
  app, find **Dinacharya** in the list and switch it on. Until then there is no phone panel. It is optional.
  **If the switch is greyed out and says "Restricted setting":** Android 13 and later lock some switches for an app
  that was installed from a file instead of Google Play (Android 15 and later include Usage access). Unlock it once:
  open **Settings > Apps > Dinacharya**, tap **⋮** at the top right, choose **Allow restricted settings** and
  confirm with your PIN or fingerprint, then switch Usage access on. (If ⋮ does not show that choice yet, try to
  switch Usage access on once first; Android then shows it.) The setup's **Greyed out?** button and the dialog's
  **Greyed out?** button explain this and open the app info page. An app installed from Google Play is not restricted.

How it is counted: time inside apps. The home screen, the lock screen and the system bar are
not counted, and time with two apps open at once is counted once. Android's Digital Wellbeing
may differ by a few minutes, because each phone maker measures slightly differently.

## Alarms

Alarms are **off until you switch them on**. On **Home** the **Alarms** card says "Alarms are off";
tap **Turn on alarms**. (The same switch is **Settings > Alarms > Remind me with an alarm**.) The app
then walks you through what Android needs: first **notifications**, then the **exact alarms** setting,
where Android opens its own screen: switch Dinacharya on and come back. Each slot has a bell (🔔) on
the Routine tab; tap it to choose what the slot does when it starts (see "What a slot does when it starts"). A slot's note is
shown on its alarm.

**Check that it works.** Open **Settings > Alarms** and tap **Ring a test alarm in 10 seconds**, then
lock the phone and wait. You should hear the alarm and see "Alarms are working on this phone."
Under the button is a **phone checklist**: Alarms switched on, Notifications, Exact alarms, Alarm
screen over the lock screen, No battery limits (Android's general battery list hides Dinacharya until its filter is
switched to **All apps**, so the app sends you to its own **App info > Battery > Unrestricted** instead) and (on phones whose maker has its own list) Allow
background activity. Each line shows a tick or a button that opens the right setting. On **Home**
the Alarms card shows the next alarm, and turns into **Alarms need your attention** with a
**Fix now** button when something is missing, or when an alarm that was due never reached the app.

When a slot starts, the alarm rings (it uses your default alarm sound) and asks:

- **Continue as planned**, or
- **Change**: type a new name for this slot, or **Take it off**. This is for today only. A slot
  that is taken off keeps its time and is shown struck through, so the other slots stay put.

The question is asked one thing at a time. The screen shows the slot, a short line, and one big button, **Start "slot" now**.
Under it are two quiet choices that open when tapped (one open at a time, so the screen never fills up): **Start later** and
**Other options**. The text is the normal size everywhere. In full, when a slot starts you can choose:

- **Start "slot" now**, which is the same as continuing as planned;
- **Start later** > **Start "slot" after** 5, 10, 15… minutes (the − and + buttons move it by 5; the default is 10). The slot's first minutes are
  not counted as worked, and the app asks again when the time is up;
- **Start later** > **Continue "previous slot" until** a time (the current time plus 10 minutes to begin with, moved with − and +). The
  slot before keeps going and this one starts later. The later start is cut so it never reaches into the slot after this one;
- **Skip this slot, start "next slot" now** (under the two quiet choices, and on the start-of-day screen for the first slot of the day): the slot is taken off for
  today and the next slot begins at once. It keeps its planned end, so it gets the time of the skipped slot, and the rest of the day stays where it was. Its own alarm at the
  planned time stays quiet. When the next slot is sleep the button reads **Skip this slot and go to sleep** and opens the bedtime question. On the start-of-day screen, if
  you chose a way to turn that alarm off (sums, a pause, a shake, a sentence), skipping asks for it too;
- **Other options** > **Other plan**, which is the old **Change** (a new name for today);
- **Other options** > **Take it off for today**.

Everything is limited to the slot that is starting: putting a start off, carrying the last slot on, resting or extending can
use the time of this slot but never moves the slot after it. The buttons that do not fit (for example when the slot is only 5
minutes long) are not shown.

If you do not answer, the alarm stops by itself after 5 minutes, is logged as "no answer" and
leaves a notification. Your schedule is not changed.

### If Android closes the app

**Closing the app from App info (Force stop) makes Android cancel every alarm the app had set and remove its live wallpaper, and Android does not tell
the app.** Nothing can ring or draw until the app runs again; that is Android's rule and the app cannot get round it. A crash does **not** clear the alarms, and
the wallpaper is guarded so that one error cannot make it crash over and over (Android drops a wallpaper that does).

What the app does about it: every time it starts (the home screen coming up, the dashboard, a restart or update of the phone, the wallpaper starting, and a background check
every three hours) it **sets the next alarm again, checks that Android still holds it, and checks the wallpaper**. It also asks Android why it was last closed (Android 11
and later). When an alarm that was set never reached the app, you get a **Missed alarm** notification ("This alarm did not ring because Android had closed the app. It is set
again now."), the Alarms card on Home says "Android closed the app and cleared the alarm set for …", and **Settings > Alarms** (the diagnosis) shows when and how Android last
ended the app. Changing the routine, the bell of a slot or the day's schedule saves and sets the alarms at once. The one thing to do yourself: after a Force stop, open the app
once. If you are never sure, **Settings > Alarms > Ring a test alarm in 10 seconds** shows whether alarms work.

### What a slot does when it starts

Tap the bell on a slot to choose one of four things, one for each slot:

1. **Notification with ring** (the alarm, as before),
2. **Notification with vibration** (no sound, the phone vibrates and the question opens),
3. **Silent notification** (no sound and no vibration: the question is on screen with **Start now** and **In 10 min** buttons),
4. **No notification** (nothing at all).

**Slots without a sound.** In **Settings > Alarms** the switch **Ask me about slots set to a silent notification** is on by default.
When it is on, a slot with a silent notification still asks what you are doing, so the app knows your answer; switch it off
and those slots show nothing. A slot you left out before (bell off) is a silent notification now, so what you had keeps working
and the question is there, as you asked for.

### The start-of-day alarm

In a new routine the **first slot is the start-of-day alarm already**. On a phone that had the app before, the first slot is made the start-of-day alarm once, the first time this version opens, if none was chosen (the bell on that slot takes it off again). To choose another slot, open its bell: **Make this the start-of-day alarm**. There can be only one, it has to
ring or vibrate, and it has to be the first alarm of the day, so the slots before it must be set to **No notification** first (the app tells
you if it cannot). It then works like this:

- It **keeps ringing until you start the day or put it off**, not for 5 minutes: up to half an hour, then it stops and leaves a
  notification (your schedule is not changed). The other alarms behave as before.
- **Start the day** begins the first slot. **Start with a delay of** 5 minutes (− and + change it by 5) puts the start off and asks again.
- **Before it can be put off, a line is shown**: a line that comes with the app, in your language (a different one each day), or
  your own line from **Settings > Alarms > Your own line to read**.
- **Challenge before putting it off** (Settings > Alarms): none, three quick sums, or a sentence to type. The challenge only
  guards the delay; starting the day is always one tap. (This one is for people who tend to press delay in their sleep.)
- **To turn the alarm off** (Settings > Alarms > Start-of-day alarm): you choose how the alarm is switched off. **Just tap Start my
  day** (the default), **three quick sums**, **Take a short pause (four slow breaths)**, **Shake the phone**, or **type a short
  sentence**. The alarm keeps ringing until the task is done, and the screen tells you what to do:
  - *Sums*: three small sums, each answered right before the next.
  - *Pause*: a circle that grows and shrinks; you breathe in and out four times. It is timed by the clock, and if you leave the screen the
    breaths start again.
  - *Shake*: shake the phone firmly (the count is on screen). A phone that has no motion sensor, or does not report shakes, asks for the
    sums instead, so the alarm can never be stuck.
  - *Type*: a short sentence in your language, for example "Today I do what I planned" (capitals, spaces and the full stop do not matter).
- **A motivating quote** is shown on the alarm screen, under the time (switch it off with **Show a motivating quote**). There are 49
  lines: 15 sayings and 34 well-known lines by named people. A different one comes each day, **Another quote** shows the next, and your own
  line from **Your own line to read** is used instead when you wrote one. The lines are translated into the six languages; the names stay as
  they are.
- **Take the day off** is a button on this alarm (see "Taking a day off").

**Bedtime.** When the Sleep slot starts it asks **Going to sleep** or **More time**.
More time asks after how many minutes to ask again (10, 15, 30, 60 or your own number; the one
you use most is offered first) and then repeats. When you choose Going to sleep and it is later
than planned, you get up to three ways to wake up, with a preview of each:

1. **Normal schedule**: nothing changes.
2. **Shift everything later**: you wake after your planned sleep length, and bedtime stays
   fixed. The lost time comes out of Rest slots first, then Personal slots (never below half,
   at least 5 minutes), then the last Rest and Personal slots are dropped. Tasks, meals and
   sleep are never touched.
3. **Skip overlapped slots**: same wake-up time; slots covered by the extra sleep are dropped
   and a partly covered one starts at the alarm. Later slots keep their normal times.

If you are not late, only option 1 is offered. An option that does not fit is greyed out with
the reason. These changes are always worked out from your saved routine for that one date, so
they never pile up, and tomorrow starts clean. **Changed for today only** (on Home) lists them
with an **Undo** button. Editing the saved routine in the app clears them.

**If an alarm is late, silent or missing.**

1. Do the checklist above until every line has a tick, then ring the test alarm again.
2. **Exact alarms.** Since Android 14 a freshly installed app is not allowed to ring on the exact
   minute until you allow it. Without it alarms can be several minutes late.
3. **Alarm screen over the lock screen.** On Android 14 and newer this can also be switched off
   by default; the checklist has a button for it. Without it the alarm still rings and shows a
   notification, but the question does not open by itself. **One window, not two.** When an alarm rings, the app decides
   once, from the state of the phone at that moment (`AlarmSurface`), where the question appears: with the phone locked or the
   screen off, the **full screen** (and the notification is the loud one that carries it); with the phone in use, a **drop-down
   banner** only (with *Start now* and *In 10 min* buttons; build 78: it carries the full-screen setting so Android keeps it up until it is answered, and still opens no window on top of it); and when Dinacharya itself is in
   front, the question simply opens **in the app** and the notification goes quiet, so nothing appears twice. The checklist's
   **Alarm pop-up allowed** line catches the case where the alarm notification was turned down to a quiet one in Android's
   settings, and the moment you come back to the home screen or the dashboard while an alarm still rings, the question is brought up.
4. **Phone makers.** Xiaomi, Oppo, Realme, Vivo, OnePlus, Huawei, Samsung and others stop apps in
   the background with their own settings (often called Auto-start, App launch, Battery saver or
   Protected apps). The **Allow background activity** button opens the right page on the common
   ones, or the app's settings if it cannot. Allow auto-start and "no restrictions" for
   Dinacharya there. Android itself cannot make an app ring when the maker's software has
   force-stopped it.
   The one-tap "allow background" pop-up is deliberately not used: Google Play only accepts it for a few kinds
   of app (chat, safety, automation), not alarms.
5. **Sound.** An alarm tries the phone's alarm tone, then its notification tone, then its ringtone, then a built-in beep, so a missing or unreadable tone can never leave it silent; the vibration is tagged as an alarm so the phone does not mute it. If Android refuses to run the ringing service, the backup notification repeats its sound and vibration until you answer it, or until 5 minutes have passed (30 for the start-of-day alarm), and then stops by itself. The checklist's **Alarm volume** line catches the alarm volume being turned right down and opens the phone's Sound settings.
6. If the phone never delivered an alarm, the Home card says so, and **Settings > Help > Copy problem
   report** includes when the last alarm was set, fired and rang, which tells us where it stopped.

### Library mode

For the library, a class or an exam. While **library mode** is on, **every alarm of the app only vibrates, with no sound**: the
start-of-day alarm, the slot alarms, the bedtime question and the break and later-start timers. The questions are still asked (on
the screen and in the notification), and a slot set to a silent notification or to no notification stays that way. It is looked at at
the moment an alarm rings, so switching library mode on or off between an alarm being set and ringing is always right.

- **On Home** there is a **Library mode** card with a **Library mode** switch (on until you switch it off, no dialog), and **Settings**. While it is on the card says "On until 16:30" (or "On until you switch it off").
- **Focus and locks > Library and Quiet mode > Library mode** has the same, plus:
  - **Turn it on by itself at fixed hours**: choose the days, and **From** and **Until** (an end at or before the start runs into the next
    morning, and the settings say so).
  library mode lasts, and a library lock never ends before library mode does.
- A restart of the phone or an update of the app puts the ringer and the timers back in order.

## Phone use in slots

Optional (part of Pro), and **off** until you switch it on. It shows, on the live wallpaper and in the reports, when you picked up the phone during a slot
and how much of the slot you really gave to the activity.

**Switch it on:** Settings > **Phone use in slots** > **Show phone use during slots**. It needs **Usage access** (the same permission as the
phone time on Home); the card shows whether it is allowed and takes you to the place to allow it. With the switch off the wallpaper shows nothing
about phone use and nothing in the app behaves differently from before.

**Choose the apps that belong to an activity.** Under **Apps that are part of an activity** every activity type of your routine is listed
(Task, Rest, Meal, Personal), each with a **Choose** button: up to 40 apps for one activity type, and up to 40 activity types. The choice is
per activity type, not per slot: a notes app chosen for Study is part of every Study slot. **Dinacharya Launcher itself is always part of
every slot** while the switch is on (looking at your day is not a distraction).

- **Other phone use** is time in any app that is not chosen for that slot's activity, while the slot is going on. Breaks and a put-off start are
  not part of the slot. Use less than 15 seconds apart (switching from one app to another) is one stretch. At most the newest 40 stretches
  are kept for a slot.
- **Focus** is the share of the slot's time with no other phone use. **Time devoted** is the slot's time without other phone use.
  **Longest focus** is the longest run inside the slot with none.
- **On the live wallpaper**, the card of the slot going on gets red marks on its bar at the times other phone use happened, and lines under the
  name: **NO PHONE USE · focused 25 min** while there has been none; otherwise **PHONE IN USE NOW · 4 min so far** (or **PHONE USE · 6 min**),
  then the latest two stretches with their exact times and the app used longest in each (**10:12 – 10:16  Instagram**, or **10:40 →** while it goes
  on), and **Focused for 12 min since** once the phone is put down. A slot that is over shows **· phone 6 min** after its activity when the
  phone was used for a minute or more. Nothing shows in a slot's first minute, and a crowded routine gets fewer lines.
- **In the reports** (see "Slot analysis" below) every stretch is saved with the day, so an older day can show the exact times too. Days saved
  before build 23 have no exact times; their analysis says so.
- **The Focus part of the score** and the suggestions use the same other phone use. With the switch off, or with no apps chosen, they count all
  phone use in a slot, as before.
- **Reset these settings** (at the end of Settings) also clears these choices. Your routine and reports are never touched.

## Performance, reports and PDF

Open **Insights > Performance and reports**.

- **Today:** the report so far. This is the only report in Free (with its PDF).
- **Week** (Pro): score trend, phone time per day, best and weakest slots and suggestions.
- **Days** (Pro): every saved day. Tap one to read the full report.

In Free, **Days** and **Week** show a short note and a "Pro" button (or the free-day offer) instead of the list; the **How each slot went** analysis, the habit cards
in Insights and the **Phone use in slots** choices show as locked lines the same way. Anything already chosen under Phone use in slots is kept and works again with Pro.

When a schedule day ends (from one wake-up to the next), its report is saved on the phone. It
shows each slot as kept, changed, taken off, no answer or not tracked; phone use by hour laid
over your plan; the top 5 apps; phone use inside task slots and in the hour before bed; sleep
planned against actual; planned against kept time; and suggestions.

**Score (0 to 100)** = Plan kept 40% + Focus 35% + Sleep 25%.

- Plan kept: kept counts 1, changed counts half, taken off or no answer count 0.
- Focus: how little of your task time went on the phone (35% of task time on the phone scores 0).
  Needs at least 10 minutes of task time. When **Phone use in slots** is on, time in the apps chosen for
  the activity does not count against it.
- Sleep: half for going to bed near the planned time, half for how much you slept.

A part that cannot be measured (alarms off, no usage access, bedtime not answered) is left out
and the other weights are scaled up, so it is never counted as a zero.

**Slot analysis.** On the report of today and of every saved day, between the sleep part and the slots table, **How each slot went** shows
four tiles (**Focus**, **Time devoted**, **Other phone use**, **Longest focus**) and a switch between **By slot** and **By activity**.
It is always shown; the apps you chose for an activity (see "Phone use in slots") only count when that switch is on.

- **By slot:** one line for every slot that ran, with its time, name, a short line ("Devoted 45 min · Other phone use 6 min") and a
  badge with its focus (75% and over is teal, 50 to 74 amber, below 50 red). A line opens to: the figures, a **Timeline** bar with red
  marks where other phone use happened, **Other phone use, with the exact times** (time range, app, length; the latest 40), and the apps used
  in the slot. Taken-off and not-yet-started slots are not listed; a footer says how many.
- **By activity:** the same for each activity type, adding the number of slots and the interruptions, and the apps used across its slots.
- Only one line is open at a time: opening another closes the one before.
- Without Usage access it says so instead of showing zeros. The PDF has the same analysis as **Focus, slot by slot** and a **By activity**
  list with bars (the dropdowns are for the screen; the PDF shows the figures).

**Suggestions and habits** come from plain rules on your own days (for example: a slot you
take off 3 times, a task slot where the phone eats a quarter of the time, late nights, phone in
the hour before bed). There is no AI service and no internet involved. A real language model
could be added later as an option.

**PDF.** Open a day, then **Save PDF** or **Share PDF** (mail, chat, Drive...). It is an A4 file
with the charts included.

## Google sign-in and Drive backup

**What it does (like WhatsApp's chat backup).** On the **Backup** tab you see the last backup
and its size, a **Back up** button, your Google account, **Back up to Google Drive**
(Never, Daily, Weekly, Monthly, or Only when I tap Back up) and **Back up using** (Wi-Fi only,
or Wi-Fi or mobile data). The backup holds everything: all routines and their days, activity
types, goals, notes, settings, today's changes, alarm answers and every day report. It is one
file in your Drive's private app space: it does not appear among your Drive files and only this
app can read it; each backup replaces the previous one. When you sign in on a new phone (or after
reinstalling) and a backup exists, the app offers to restore it. You can also tap **Restore from
Google Drive** at any time. Before restoring, what was on the phone is kept aside inside the app.
Automatic backups run in the background when the phone is online and the battery is not low.

**One-time setup in Google Cloud (about 10 minutes).** Google only lets an app sign you in
after its developer registers it. Do this once with your own Google account:

1. Open **console.cloud.google.com** and create a project (any name, e.g. "Dinacharya").
2. **APIs & Services > Library:** search **Google Drive API** and press **Enable**.
3. **Google Auth Platform** (older screens: **OAuth consent screen**):
   - **Branding:** app name "Dinacharya Launcher", your email as support and developer contact.
   - **Audience:** user type **External**. Then press **Publish app** (to "In production").
     If you leave it in "Testing", add your Gmail under **Test users**, and Google ends the
     sign-in every 7 days. The app asks only for non-sensitive permissions, so publishing does
     not need a Google review.
   - **Data access:** add the scopes `.../auth/drive.appdata`, `.../auth/userinfo.email` and
     `.../auth/userinfo.profile`.
4. **Clients** (or **Credentials > Create credentials > OAuth client ID**): choose **Android**.
   - Package name: `app.dinacharya.launcher`
   - SHA-1 certificate fingerprint: `48:94:D3:1F:BE:2F:4A:60:ED:4A:B2:1F:E9:00:61:FE:3D:8C:E8:B8`

   That fingerprint belongs to the signing key included in this project
   (`app/signing/dinacharya.jks`), which every build uses, so it never changes. Nothing needs
   to be pasted into the app. Keep your copy of this project private, because anyone with that
   key file can sign apps as you.
5. In the app open **Backup > Sign in with Google**, pick your account and allow access.

**Sign in with a code (fallback).** If the account list does not open on a phone (for example a
phone without Google Play services), use **Backup > Sign in with a code**. It needs one more
client in the same project: **Create OAuth client ID > TVs and Limited Input devices**. Copy its
Client ID and secret into **Backup > Code sign-in setup (client ID)** (or into
`app/src/main/res/values/google.xml` before building). The app then shows a short code; type
it at google.com/device on any phone or computer.

**If sign-in fails** ("developer error 10", "UNREGISTERED_ON_API_CONSOLE" (code 8), or "the
sign-in screen closed without an account"), Google does not accept the app yet. The first two
mean the Android client from step 4 is missing, has a different package name or SHA-1, or was
created in a different project than the one where the Drive API is enabled. Open **Backup > Check sign-in setup**: it shows the package
name and the SHA-1 of the app actually installed, says whether that SHA-1 matches the one above,
and lists what Google Cloud must contain. The usual causes are: the Android client is missing, in
another project, or has a typo in the SHA-1; the consent screen is in "Testing" without your
Gmail under Test users; or the change was saved less than 5 to 10 minutes ago. The error code
Google gives is shown in the message and kept in **Settings > Help > Copy problem report**.

**Other copies.** Any day can still be saved or shared as a PDF from its report. Android's own
device backup also covers the app's data if it is on for your phone (the Google sign-in keys are
left out of it on purpose).

## Languages

**Settings > Language** (or the line on the welcome screen) lets the person pick **English, हिन्दी, मराठी, ગુજરાતી, বাংলা, தமிழ் or తెలుగు**;
the default, **Automatic**, follows the phone's language when the app has it and is English otherwise. The change shows at once.
Numbers, times and dates stay in 0-9 so a schedule reads the same everywhere. Names (Dinacharya, Pro, Google, Drive), the app names
on the phone and anything the person typed (slot titles, notes, group names) are never translated. "© 2026 | Developed by Code Avirata" stays in English.

How it works (so it can be corrected): the program's text is English. `Translator` (plain Java, tested) looks each text up in a
word list, `app/src/main/assets/lang/<code>.tsv` (`English TAB translation`; `{1}`, `{2}` mark a changing part such as a number or a
time and may move in the translation; `\n` is a new line). A long text with no entry is cut at line ends, sentence ends, colons,
bullets, commas and brackets and each piece is looked up. A pattern like `{1} apps` applies only to a number ("3 apps"), never to
somebody's words ("Social apps"). `Lang` (Android) applies it to every screen, dialog, toast and notification as they appear.
Sentences are written whole in the program (never glued from pieces) because word order differs between languages.

**The translations were written by Claude, not by native speakers. Expect some awkward or wrong wording and have a native speaker
read them before a Play release.** To correct a word, edit the `.tsv` (or the matching `tools/i18n/work/<code>.txt` line and run
`python3 tools/i18n/build.py`, which rebuilds the six files; building replaces hand edits in the `.tsv`). `python3 tools/i18n/extract.py
app/src/main/java/app/dinacharya/wallpaper units.json` followed by `python3 tools/i18n/build.py --check units.json` lists English text in
the program that has no entry (identifiers and the Google sign-in error details are expected in that list). `LangFilesTest` loads the
six files and checks that marks match, digits are 0-9, the picker offers exactly the languages that have a file, and that about fifty
real put-together sentences (the video buttons, reports, the Pro page) come out with no English left. A new language is a new
`.tsv` plus a line in `Lang.LANGS`.

## Dinacharya Pro and ads

**Free:** the home screen, the tour, one routine with every slot feature, alarms, bedtime flow, a **to-do list for every slot** (also repeating
to-dos), **today's report** (score, phone time by hour and app, suggestions, and a PDF of it), backup and restore, the default Teal colours,
a short pause for **one app** and one lock on **one app**. The free version shows one small banner ad at the bottom of the dashboard's
**Home** and **Insights** tabs and of the report screen. There are never ads on the home screen, the all-apps list,
an alarm, the pause and lock screens or in a PDF.

**Pro (through Google Play: Rs 49 a month, or Rs 499 once for life, as set in Play Console):** removes the ads, and adds

- **Goals:** a goal for a slot of your routine, with its own to-do list (see "Goals and streaks"). Goals that already exist keep working in Free.
- **Reports:** every saved day (with its PDF), the **Week** view and its suggestions, and the **deeper analysis**: the slot-by-slot analysis
  (**How each slot went**), the habit cards and phone use in slots. Free keeps today's report only; past days, the week and the analysis show a
  "Pro" mark and a short note, never an empty screen.
- **Pauses and locks:** pauses for any number of apps, strict pauses, daily limits and playful pauses; any number of locks, locked **groups** of apps and strict locks.
- **Routines and looks:** weekday, weekend and one-day routines, and the Sunset, Ocean and Graphite colour looks.

If someone had a second routine, goals, several paused or locked apps or a colour before, they keep them: nothing already made is taken away or
deleted. What Free cannot use is kept and works again with Pro (the pause and lock rows say "needs Pro").

**Try everything in Pro for a day, for three short videos.** Anyone who has not bought Pro can choose to watch **3 rewarded videos** (Google AdMob). The
**third** opens **everything in Pro for 24 hours**: there is one offer, with one button, and no choosing of items. Each video watched to the end
counts at once, so stopping after one or two keeps them for the rest of that day (a message between videos says how many are left and offers
**Stop here**); the next morning the count starts again. A video that cannot be found never costs the ones already counted. The offer is made in
the "Part of Pro" message that appears wherever a Pro item is tapped (under the Days and Week tabs and the analysis in Performance, in goals, and in the pause and lock sections
of the home settings), on the **Pro screen** ("Try Pro free for a day") and on the last page of the tour. While the day runs, the Pro screen says
"Everything in Pro: open until ..." and the offer is not shown again; when it is over, it is offered again. At most 12 videos a day are offered
(`AdLayers.DAILY_CAP`). The rule classes take a mask with one bit per item (`DayPass.has(mask, item)`; the pass opens all 15 items: saved
days, week view, deeper analysis, pauses for more apps, strict pauses, daily limits, playful pauses, more locks, more apps in a lock, strict
locks, routines, goals and the three colour looks), so each feature is still gated on its own. Having Pro (the purchase) opens all of them for good.
The offer disappears when Pro is bought or ads are off in the build (`ads_enabled`). Counting is in `VideoProgress.java` and the passes are kept in `PassLedger.java` (both tested).

**Ad layers (more earnings from the same video, without more videos).** `VideoPass` asks for the video from up to three AdMob
**Rewarded** ad units in turn, **the best paying first** (`admob_rewarded_id`, then `_2`, then `_3`; set a high eCPM floor on
the first, a lower one on the second and none on the third in AdMob, see "Earn from the app"). When nobody pays the first
price, the next layer is asked, so each video earns as much as it can and one is still found when the best price is not on
offer. A layer that had nothing is left alone for a while (90 seconds after "no fill", longer if it keeps failing, up to 8
minutes; 30 minutes for a wrong id; the last layer is always tried) so the next video does not wait for it again, and a layer
that does not answer in 10 seconds is skipped. **One video is loaded ahead of time** (while an offer is on screen, and the next
one while a video plays) and used for up to 50 minutes, so the video usually starts at once. Nothing plays by itself and there
are no ads on the home screen, the all-apps list, alarms, pause or lock screens. The rules are in `AdLayers.java` (tested);
the asking is in `VideoPass.java`. Layers with the test id or a repeated id are skipped, so the shipped build with the test
ids has one layer.

**When the day is up, what the pass opened closes and what was set up with it goes back to the free version.** The pass is a trial, so what was made
with it does not stay. When a pass starts, the phone takes a note of what existed for each feature (`PassReset.capture`);
when the pass has ended, whatever was added or switched on with it is removed or set back, and everything that was there
before stays as it was. Each item puts back only its own part: *more paused apps*: rules beyond the free one app are
removed; *strict pauses*: a pause that was made strict goes back to the short pause; *daily limits*: a limit set goes back
to none (or to the older limit); *playful pauses*: the kind goes back to the plain wait; *more locks*: extra locks are
removed; *more apps in a lock*: a lock holds one app again (or the number it held before); *strict locks*: a lock that
was made strict stops being strict; *Routines*: the weekday, weekend and other routines made are removed (those days use
the main routine again); *Goals*: the goals added (with their own to-dos) are removed; *a colour look*: that look goes back to the earlier one;
*saved days, week view, deeper analysis*: nothing is stored, so nothing needs to go back. Choices the free version allows are never touched, the main
routine is never touched, and having Pro (bought before the day ends, too) keeps everything. A message on the dashboard
says what went back ("Your video pass has ended ..."), and the Pro prompt, the Pro screen, the tour and the home settings say
beforehand that this will happen. The reset runs at the moment the day ends (a system alarm, `PassSettle`), after a restart,
and whenever the dashboard, the home screen, its settings or the wallpaper starts, so it does not depend on the app being
open; restoring a backup during a pass makes the restored settings the "before", so a restore is never undone by a pass. The
rules are in `PassReset.java` and `DayPass.java` (tested); the record and the note are kept on the phone next to the Pro
record, outside any backup, and a pass that is longer than a day or has not started yet counts for nothing, so setting
the clock back does not give a longer pass. (Passes kept by an earlier build, which could be one item for a day or three, are still understood.) The video needs a network connection and the consent form answered (a "No video right now"
message says so otherwise, and **Settings > Copy problem report** shows the last video result). Change
`DayPass.LENGTH_MS` (a day) for a different length.

- **See Pro** (Settings > Dinacharya Pro, and every "See Pro" button in the app: the pause and lock screens in
  the home settings, the report's older days and Week tab, the Pro prompts) opens one screen of its own with the offer of a free day, a **Compare Free and Pro** button, the two plans (**Subscribe monthly** and **Get lifetime Pro**, each with its price) and **Restore my purchase** (for a new phone, a reinstall, or a
  purchase made on another phone with the same Google account). The prices shown are whatever Google Play reports
  for `pro_monthly` and `pro_unlock`; the app does not hard-code them, so set them to **50** and **499** in Play Console (see "Pro purchase: monthly and lifetime").
- **The Free and Pro screen** (`CompareActivity`; the last page of the tour, and **Compare Free and Pro** on the Pro screen) puts everything the
  app does on one page, **Free** on the left and **Pro** on the right, in groups that open one at a time (Home screen and alarms, To-dos,
  Reports, Goals, Pauses, Locks, Routines and looks). A group shows what Free gives and what Pro adds, with a tick or a "Pro" mark on each line. The lines come
  from one table (`PlanTable.java`, tested), the same limits the app enforces, so the screen cannot say something the app does not do. Anyone who
  has Pro sees "You have Pro"; anyone else sees the free-day offer and **See Pro plans**.
- Ads: the banner shows a status line ("Loading an ad…", or "No ad right now (code N)") instead of an empty gap,
  retries every 45 seconds up to 6 times, and **Settings > Copy problem report** includes the ad and consent
  status. A banner also sits at the bottom of the report screen (free only). Ads need a network connection and, where
  the law asks, the consent form answered; with the test IDs in `monetization.xml` Google serves test ads.
- The app checks Google Play for the purchase each time it starts. If Google Play cannot be reached,
  the last known answer is kept, so a bad connection never takes Pro away. A refund removes it.
- In Europe, the UK and some other places a Google consent form appears first, and **Settings > Ad
  privacy choices** lets the person change their answer.
- A **debug** build installed straight from GitHub (not from Google Play) has a **Pretend I have Pro**
  switch in the Pro page so the unlocked screens can be tried. The Play build does not have it.
- The money settings are in `app/src/main/res/values/monetization.xml`. `ads_enabled` = `false`
  builds a version with no ads at all.

## Publish on Google Play

Everything below is something only the owner of the Google accounts can do. The code, the
build workflow, the privacy policy, the listing text and the graphics are ready (`docs/`, `store/`).

**Plan for the time it takes:** a Google Play developer account is quick, but a *personal* account
created after November 2023 must first run a **closed test with at least 12 testers for 14 days
in a row** before Google lets you publish publicly. Start that as early as you can.

1. **Create a Google Play developer account** at play.google.com/console (one-time US$25 fee, ID
   check, and a payments profile if you will sell Pro).
2. **Make your own upload key (once).** The test key in this project is public to anyone who sees
   the project, so it must never be used for Google Play.
   - Put this project in a **private** GitHub repository (**Settings > General > Change visibility**).
   - Open **Actions > Make my Google Play upload key > Run workflow**. When it finishes, download
     **my-upload-key** (kept for 2 days) and save `upload.jks` and `upload-key-secrets.txt` somewhere
     safe, such as a password manager. **If you lose this key you will need Google's help to replace
     it.** Delete the download from your computer afterwards.
   - In the repository open **Settings > Secrets and variables > Actions > New repository secret**
     and add the four secrets named in `upload-key-secrets.txt`: `UPLOAD_KEY_ALIAS`,
     `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_PASSWORD` and `UPLOAD_KEYSTORE_BASE64`.
3. **Put in your real ad and product settings** (see "Earn from the app" below). At least replace the
   test AdMob IDs in `monetization.xml`.
4. **Build the bundle.** **Actions > Build for Google Play > Run workflow**, then download
   **Dinacharya-play-bundle**, which contains `app-release.aab`. For every later update raise
   `versionCode` (and `versionName`) in `app/build.gradle`: Google refuses a number it has seen.
5. **Create the app in Play Console** (**Create app**: name, default language, App, Free). Then fill
   **Dashboard > Set up your app**: use the answers in `store/data-safety-and-declarations.md`
   (privacy policy, ads, content rating, target audience, data safety, foreground service,
   exact alarms), and `store/listing.md` plus the pictures in `store/graphics/` for the store listing.
6. **Turn on GitHub Pages for the privacy policy.** In the repository: **Settings > Pages > Source:
   Deploy from a branch > main > /docs**. A **private** repository can only do this on a paid
   GitHub plan; on the free plan make a second, public repository that contains only the `docs`
   folder. The policy will be at `https://YOURNAME.github.io/REPOSITORY/privacy.html`. The
   contact email in `docs/privacy.html` is `codeavirata.support@gmail.com`; change it there if it ever moves.
7. **Upload and test.** **Testing > Closed testing > Create track/release**, upload the `.aab`, accept
   **Play App Signing**, add your testers' Gmail addresses (or a Google Group), and send them the
   opt-in link. They must stay opted in for 14 days. The same `.aab` can go to **Internal testing**
   first (up to 100 testers, no wait) to try Pro, ads and sign-in on a phone.
8. **Make Google sign-in work for the Play build.** Google re-signs the app, so the Play version has a
   **different fingerprint** from the test APK. In Play Console open **Test and release > App
   integrity > App signing** and copy the **SHA-1 of the app signing key certificate** (also
   the SHA-1 of the upload key, for builds you install yourself from the bundle). In Google Cloud,
   open **Google Auth Platform > Clients > Create client > Android**, with package
   `app.dinacharya.launcher` and that SHA-1. Make one client for each SHA-1; keep the one for
   the test key too. Wait about 10 minutes. The consent screen must be **In production**.
9. **Apply for production** once the closed test has run its 14 days (Play Console shows a
   countdown and an **Apply for production** button), then choose countries and roll out.
   Google reviews the app; the first review can take several days.

**Things that will confuse you**

- The Play version and the test APK have the same package name but different signatures. Android will
  not install one over the other: uninstall one before installing the other (backup first).
- Google may ask for a short screen recording of the alarm ringing (for the foreground-service and
  full-screen declarations). `store/data-safety-and-declarations.md` says what to show.
- A launcher is the phone's home screen, so test it carefully on a real phone before a public release:
  several phone makers change how home apps are chosen, how the notification shade can be opened, and how
  apps behave in the background. The fallback screen and the **Change home app** button are there for that.
- Google Play requires apps to target a recent Android version. This project targets Android 16
  (API 36). Raise `targetSdk` and `compileSdk` (and the Android Gradle Plugin) when Google's yearly
  deadline moves on; the rules and dates are on developer.android.com.
- The Billing, Ads and consent libraries could not be downloaded where this project was written
  (see "Limits"), so the first online build is the first time they compile for real. If the build
  prints an error in `Pro.java`, `Ads.java` or `AdSlot.java`, copy it and send it to me.

## Earn from the app

Nothing here changes how the app works; it is the account setup that only you can do.


### Quiet mode

In **Focus and locks > Library and Quiet mode > Quiet mode**: rules that set the phone by themselves, made the way locks are made.

- A **rule** has a name, what the phone does (**Ring**, **Vibrate**, **Silent** or **DND**) and when:
  - **Time**: hours on chosen days (the same days and hours as a lock; an end at or before the start runs into the next morning),
  - **Activity**: while a slot of the chosen activity types is going on (a type such as Study, or "Any rest"), at any hour,
  - **Time + activity**: only when a slot of the chosen types is going on inside the hours,
  - and **Also in library mode**, so the rule holds whenever library mode is on.
- **Ring** turns the ringer up. **Vibrate** and **Silent** only turn it down (a ringer that is quieter already is left alone). **DND** switches
  Do Not Disturb on (priority only): notifications of other apps are held back and calls from people the phone allows still ring.
- When rules overlap the quietest wins (Ring, Vibrate, Silent, DND). A break, a slot taken off and the gaps between slots do not count.
- When no rule holds any more, the phone goes back to how it was, unless you changed the ringer yourself meanwhile. Do Not Disturb that you had
  on already is never touched. Up to 10 rules.
- **Silent** and **DND** need **Do Not Disturb access**; it is asked when you save such a rule, and without it the phone only vibrates. Nothing else
  is read.
- The older "put the phone on vibrate in library mode" choice became a rule for library mode by itself.

### Pro purchase: monthly and lifetime

1. Upload your first build (step 7 above), at least to **Internal testing**. Google only lets you
   create products for an app that already has a build with the Billing permission in it.
2. **The lifetime plan: Monetize with Play > Products > In-app products > Create product.** Product ID exactly
   `pro_unlock`. Name "Dinacharya Pro (lifetime)", a description, the price **Rs 499** (India; Play Console converts it
   for other countries, and you can edit each country's price), status **Active**. If you made it earlier at Rs 99,
   change the price to 499; people who already bought it keep Pro.
3. **The monthly plan: Monetize with Play > Products > Subscriptions > Create subscription.** Product ID exactly
   `pro_monthly`. Name "Dinacharya Pro (monthly)", then **Add base plan**: auto-renewing, billing period **Monthly**, price
   **Rs 49** (India), then **Activate** it. One base plan only. (Details in `store/data-safety-and-declarations.md`, section 8.)
   The app shows both plans on its Pro screen. Pro is on while either the lifetime purchase is owned or the monthly plan is
   running; if a monthly plan lapses, Pro turns off the next time the app starts online.
4. Finish the **payments profile** (Play Console > Setup > Payments profile) and your **tax**
   information, or the purchase will not work in production.
5. **Test both without paying:** **Settings > License testing**, add your own Gmail and your testers'.
   They get test purchases that charge nothing (test subscriptions renew every few minutes and stop after a few
   cycles, so you can see a plan lapse). Install the app from the testing track (a purchase only works for an app
   installed from Google Play).

### Ads (AdMob)

1. Make an account at admob.google.com and **Add app**: Android, say it is **not yet published** (or
   link it once it is listed), name Dinacharya.
2. Create the ad units: a **Banner** (the small ad on the dashboard) and **up to three Rewarded** ones (the "watch videos to
   open Pro features" choices; see "Ad layers" under Dinacharya Pro and ads). AdMob gives you an **App ID** (it looks like
   `ca-app-pub-1234567890123456~1234567890`) and an **Ad unit ID** for each (`ca-app-pub-1234567890123456/1234567890`).
   For the three Rewarded units set **eCPM floors** that fall: for example the first unit a high floor, the second a lower one
   and the third **no floor**. (AdMob: Ad units > the unit > eCPM floors; use the floors AdMob suggests for your countries,
   and look at the results after a few weeks, then move the floors. One rewarded unit with no floor also works, it just
   earns the average.) Do not put the same unit id twice.
3. Paste them into `app/src/main/res/values/monetization.xml` as `admob_app_id`, `admob_banner_id`, `admob_rewarded_id`
   (the top layer), `admob_rewarded_id_2` and `admob_rewarded_id_3`, then build again. Until you do, all are Google's test
   ones.
4. In AdMob open **Privacy & messaging** and create the **European regulations** message (the
   consent form the app already requests) and, if you want, the US states one.
5. In AdMob link your **payments** details and complete your address verification: AdMob pays out
   only when the balance passes its payout threshold.
6. **Never tap the real ads in your own app, and never ask friends to.** Google treats that as invalid
   traffic and can close the account. Until step 3 the app shows Google's **test ads**, which are safe
   to tap and pay nothing. **If you publish without doing step 3, the ads you ship are test ads and
   earn nothing.**
7. Tell Play about the ads (content: "Contains ads", Advertising ID, Data safety). The answers are in
   `store/data-safety-and-declarations.md`.

### Honest expectations

A small banner on three screens, and the optional video, earn very little until many people use the app, and only a small
share of free users ever buy Pro. Both pay better the more people install, so the closed test, a
good listing and real screenshots matter more than any setting here. Google takes a share of Pro
sales (and its rate has changed recently: see Play Console's **Fees** page for what applies to you),
and AdMob pays out only after your balance passes its threshold.

## Limits and what has been tested

The schedule engine (including weekday/weekend routines), alarm timeline, bedtime options, slot
editing, goals, streaks, type totals, reports, score, suggestions, usage counting, the Drive
backup and restore (against a pretend Google: sign-in, upload, replace, restore byte for byte,
damaged or foreign files refused) and the backup schedule are covered by automated tests. The
day poster that is the home screen, in all eight colour looks with the developer mark, the report pages, the PDF layout,
and the footer were rendered and inspected as images.

Also covered by tests: the alarm timeline (`AlarmPlanTest`), including the test alarm. The tests are in `tests/`; run
`./tests/run-core-tests.sh` (needs only a JDK) to compile the pure-Java core and run all of them. They cover the swipe
defaults, the pause rules and the pause challenges (sums, sentence matching, rounds, the way past), the snooze cost,
the midnight-to-wake-up scroll timing and the locked-group rules (daily windows, overnight windows, whole-day windows,
locks started by hand, Free against Pro, saving and loading groups), the video pass rules (one item for a day, the same
item for three days, a whole group for a day, stretching a pass, one at a time, a pass that is too long or not started yet
counts for nothing; every one of the 14 items opens exactly that item and no other, in the pause, lock and report rules),
what goes back to the free version when a pass ends (`PassResetTest`: each pause, lock, routine, goal and look item puts
back only its own part, a whole group puts back every part, an older one-item note is still understood, what was there
before stays, a second run changes nothing), the ad layers (`AdLayersTest`: the order of the ad units, leaving a unit alone
after it failed, never refusing a video because of earlier failures, how long a loaded ad lasts, the day's cap), the Strong
lock (`GuardTest`: which app is in front from Android's usage events, what is held and what never is, passes and strict
locks, and each single "look" of the loop: a cover is not put up twice, comes down when the lock screen or another app is in
front, Settings and Dinacharya itself are never held), the "Dinacharya is not your home app any more" rule (`HomeWatch`) and the first-run order
(welcome, setup, tour), the translation engine (`TranslatorTest`) and the six shipped language files (`LangFilesTest`), and, since 3.0,
the repeating to-dos and the way the wallpaper pages through a long list (`TodoRepeatTest`: every day, every other day, chosen days,
one copy per day, a renamed or removed slot, stopping a repeat, the 20-item limit, old saved lists still loading, finished items
scrolling up first, paging through the rest). The screens of 3.0 (the parts that open and close, the break box, the slot-start
screen) compile against the real Android library, but their layout, sizes and animations have not been seen on a screen.

Build 21 added tests for the day as it really goes (`LiveDayTest`, 116 checks): the schedule that Home and the wallpaper show when a start is put
off, a break is taken, the slot before is carried on, a slot starts or ends early, or the day is taken off (and that the saved plan is never changed); the
alarms that must not ring on a day off, in a break or before a later start; when "Finish early" is offered and what each answer does; the reports for early
starts and early finishes; and library mode (when it is on, when it ends, fixed hours that run past midnight). `WakeLockTest` (113 checks) covers the locks by time, by activity type and by both together, and in library mode, the quotes (a different one
each day, "another quote", your own line), the shake counter (the count, a sensor that never reports), the start-of-day settings and how a day off is shown in
reports. `LangFilesTest` also checks that the new sentences (breaks, late and early starts, lock lines, library mode, day off), all 49 quotes and the sentences to type come out with no English left in all six languages, and that the sentences to start the day have no comma or full stop to mistype.
These are logic tests of the pure-Java part; the screens, the alarm ringing in vibrate-only, the phone's sensor and ringer, and the lock-screen display have **not**
run on a phone.

Build 23 added `SlotUseTest` (other phone use against the apps of an activity, joined stretches, focus, longest focus, a focus that is running, the 40-stretch limit,
a slot with a break, no permission), `SlotReportTest` (the slot and activity lines of the analysis, the timeline marks, the one-line-open rule
with thousands of random taps, saving and loading a report with its stretches, a 4000-round random check) and `HabitsTest` (the habit cards agree with the
old suggestion rules in 6000 random days, tones, heads and the "not enough days" case). `SlotUseTest` also covers saving and loading the per-activity apps. Tests and
language checks cover the new sentences in all six languages. The wallpaper marks and the PDF analysis were drawn to images and looked at. The settings card, the
dropdowns of the analysis (and the switch between By slot and By activity), the habit cards and the arrows that turn **have not been seen on a phone**: they compile
against the Android library, but their layout, sizes and animation are untested.

Build 24 added `SlotGoalTest` (a goal for one slot: which days and slots it counts, a slot renamed or taken off, a goal with no to-dos ticked by hand or by time and
not un-tickable once time did it, a goal with to-dos struck only when all are done, the to-do list of a slot as Home, the to-do box and the wallpaper build it, one tick
everywhere, older goals for a whole type, saving, loading and damaged lines) and `PlanScreenTest` (the Free and Pro table agrees with the limits the app enforces, and the
slot choices of the goal editor). `PassLedgerTest`, `PassResetTest`, `LauncherTest`, `GuardTest` and `LockTypeTest` were brought to the new rules: three videos open everything for a day and then it all goes back,
one paused app and one locked app in Free, today's report only. `LangFilesTest` checks the new sentences in all six languages. The wallpaper poster with goal headings and
their to-dos and the PDF were drawn to images and looked at. The new screens (the Free and Pro page, the goal editor with its to-do list, the locked notes in Performance,
the to-do rows with headings) compile against the Android library but **have not been seen on a phone**.

Build 26 extended `GuardTest`: the lock follows the settings (nothing runs while nothing is locked, a lock or a pause starts it, switching it off by hand stops it, an old saved config now loads with it on),
which permission is asked first and when the next one is, and the ways round the home screen replayed through the watcher's own pieces (Recents, App info > Open, a notification, a watcher that starts while the app is
already open, another launcher, an app whose group hides it from the list). `LangFilesTest` checks the new sentences in the six languages.

Build 25 added `AlarmWatchTest` (when an alarm counts as missed or lost, which ways of closing the app clear the alarms, when the wallpaper counts as in place or lost and when it is asked
for again), `AlarmChainTest` (the alarm planner followed day after day through eleven routine options, with slots taken off and a day off: the alarms come forward in time, once each,
and the start-of-day alarm is there every morning; the marks of slots started early are dropped when the schedule changes) and `EarlySkipTest` (a day that starts early: the plan of both
days, the alarms and the bedtime choices; Finish early, Early sleep and Early wake offered in every slot; skipping a slot, also over slots that are off, the first slot and the one before
sleep; the report and the history). A fuzz run of random routines through the alarm planner found nothing wrong. The new screens and flows (the skip button, Early sleep, Early wake, the
wallpaper chip and card, the Missed alarm notification) compile against the Android library but **have not been seen on a phone**, and Force stop could not be tried.

The home screen's logic (search, the pause rules, the settings text, the pause counts in reports) is covered by
automated tests (`LauncherTest`). Its screens, the tour and its pictures are compiled against the real
Android 14 library; the tour's pictures were also drawn to images and checked. The home screen itself, the
live wallpaper, the first-start setup, the all-apps list (its slide-in and blur), the pause and lock screens with their
challenges, the group editor, the swipes and the alarm screen **have never run on a phone**: launchers differ between phone
makers, so expect a round of fixes there. The same goes for the **Strong lock** (the service, the cover and the lock screen it
brings up, on different phone makers' Android), and for the **rewarded video layers** (they were compiled against stand-ins for
Google's ads library and the sequences were run against pretend storage, never against real ads).

The whole app is compiled against the real Android 14 (API 34) library with no errors (the
project itself targets Android 16, API 36, whose library was not available there). The Google
sign-in, Google Play Billing, AdMob and consent libraries could not be downloaded where this was
written, so those parts were compiled against stand-ins that copy their published interfaces. The
three GitHub workflows (debug APK, Play bundle, upload key) have only been checked for correct
format, never run. A full APK could not be built there and the app has **not** been run on a real
phone, so expect a round of small fixes for: the new screens' look and feel on a real display,
swiping and dragging, the alarm sound and lock-screen screen, notifications and permissions
(**the alarms in particular must be tried on your own phone with the test alarm**), Usage access,
the real PDF writer, the first Google sign-in and backup against Google's live service, and
the Pro purchase and the ads, which only work for an app installed from Google Play.

### If the app closes unexpectedly

The app writes down what went wrong. Next time you open it, a yellow card on Home says so; tap
**Copy details** and send the text. You can also copy a report any time from **Settings > Help
and problem report**. Problems in background work (reports, backup, phone-time reading) do not
close the app: they are written down and that one job is skipped.

### Version notes

**1.0, build 85: rate on Google Play, and Light, Dark or System default for everyone**
- **App colours for everyone.** Settings > App colours now offers three looks, all free: **Dark** (Graphite & Amber), **Light** (White & Black, until now part of Pro) and **System default**, which follows the phone's own dark mode and changes with it. The choice is kept across restarts, as before, and each tile is a small preview of the look. The wallpaper keeps its own Light, Night and Auto look (`Ui.COLOURS_SYSTEM`, `Ui.phoneNight`).
- **Rate on Google Play.** Settings > Support has **Rate Dinacharya**, and About has a button, both opening the app's page in the Play Store app (or on the web when it is not there). Home shows a small card offering the same, only after the app has been used on 5 different days and a week has passed since the first; **Not now** puts it away for two weeks, a second **Not now** or a rating puts it away for good. The card asks nothing about whether the person likes the app, as Google Play's rules require (`RatePrompt`, `RateApp`).
- **Send feedback.** Settings > Support has **Send feedback**, and About has the button and the address: the phone's email app opens with a message to codeavirata.support@gmail.com, with the app version, Android version, phone model and language at the end. Help > Problem report has **Email problem report**, which puts the whole report in the message (Copy problem report is still there). The app sends nothing itself; with no email app on the phone, the address and the message are copied instead (`SettingsPage.mail`, `SUPPORT_EMAIL`).
- Translated into Hindi, Marathi, Gujarati, Bengali, Tamil and Telugu.
- Tests: `RateTest` (51 suites in all).

**1.0, build 84: a slot you changed in the middle, answered on the right part**
- **The cause.** A slot you change while it runs ("Change plan") is several parts with one number: the time before, the new plan, and what is left. Several answers looked up "the slot" by its number alone, so they landed on the first part (long over) or on the whole slot, and a second change wiped out the first.
- **The alarm at the rest of a changed slot** (the 11:30 question of a slot changed at 10:00, say). **Continue** now carries on the replaced task, not the slot before. **Rest first** shows the wait on the rest, not as a late start of the earlier part. **Skip** takes off only the rest, and the next slot begins. **Change plan** again keeps the first change. **Swap** keeps what was done before. An alarm for a later part that nobody answers shows **No response** (`LivePlan`, `NoResponse.untilPart`, `Ev` answers now name the part: `src@plannedStart`; older ones read as before).
- **On the Home card.** **Finish** on the replaced task or on the rest of a changed slot works (finish, rest, start next). A second **Change plan** keeps the first. After **Adjust time**, **Change plan** begins when you pressed it, not later by the minutes that were added (changes are now applied in the order they were made: `Overrides` keeps an order number, older files read as before). **Continue** while waiting and then **Finish** no longer leaves "Delayed start" on until the Continue time ends. **Finish** on a break at the end of a carried slot hands over to the next slot, and a break that is going on when you Finish ends then, so it cannot come back if the slot is carried on later. **Finish** in the first minute of a slot was ignored (the slot went on, and the next slot's alarm was silenced): it now shows that slot for one minute and hands over (`LivePlan`, found on the emulator).
- **Not offered, on purpose** (the saved plan cannot show them, and they did nothing or did the wrong thing): **New activity** under **Finish** on a slot that began early; **Continue** under **Change plan** on a slot that began early, on a slot that is itself carried on, and on a part that never ran (its time was a wait).
- **Left as it was:** **Change plan** on a slot that began early renames the whole slot, including the minutes already done (the saved plan has it starting later). Two parts next to each other with exactly the same name become one part, so a carry-on written for the first one then ends with the joined part. Swap on a part that is being carried on can show the old name until the carry ends. Day off and the break-over notification buttons were not tried again.
- Tests: `PartsTest` (50 suites in all). Plan changes checked in combination (every answer at the alarm of every kind of part, and every action on the Home card, two and three in a row) and on an Android 14 emulator.

**1.0, build 83: the slot card and the buttons on an alarm notification**
- **Break and Finish did nothing on the Home card after "Change plan".** A slot changed while it runs is several parts with one number (the time before, the new plan, what is left). The card put a break on the first part (long over) and cut the first part when you finished or carried on, so after a change of plan the break never showed, and a finished slot kept running on the card while the app already counted the next one as begun. They now go to the part that is going on (`LivePlan.partAt`, `LivePlan.lastPartBefore`).
- **The card was up to half a minute late.** The card draws whole minutes, to the nearest one, so a Break, Finish, Back to work, Start or Day off pressed in the second half of a minute was drawn from the next minute. Something that has already happened is never drawn later than the minute the card is in.
- **The buttons were missing from the alarm notification on a new install.** Android 14 and later do not let the ringing service run until "Alarms & reminders" is allowed, so the alarm is shown as the backup notification. That one had no buttons. It now has the same ones as the ringing notification (`NoticeButtons`): **Start now** and **In 10 min** for a slot; **Back to work**, **+5 min** and **Stop** for a break that is over; **Stop** for the rest. Pressing one also removes the backup notification and its repeating sound.
- **Left as it was:** alarms still come late (up to a few minutes) until "Alarms & reminders" is allowed, which Android decides, not the app. After "Adjust time" in a slot, a later "Change plan" in the same slot begins the new plan late by the minutes that were added.
- Tests: `CardAtOnceTest`, `ChangedSlotCardTest`, `BreakEndTest`, `NoticeButtonsTest` (49 suites in all). Checked on an Android 14 emulator with exact alarms off and on.

**1.0, build 82: Home always shows your day, also right after a Force stop**
- **The cause.** From build 80 (and, after twenty seconds, before it) the home screen opened Android's own *Set wallpaper* preview half a second after it started whenever the live wallpaper was gone. After a Force stop the wallpaper is always gone, so pressing Home showed a system screen over the launcher, with the phone's default wallpaper behind it, and it looked as if the launcher had not come back.
- **The fix.** The home screen never opens Android's preview by itself any more. It starts at once, paints your day itself while the wallpaper is gone (`WallpaperWatch`, `Keeper.lastEndWasForceStop`), and shows the button **Bring back your day wallpaper**.
- **The wallpaper is still required.** The first-start setup asks for it, and the dashboard (opened from the app icon) opens Android's preview when the wallpaper is gone: at once, and again after ten minutes if you turned it down (once a day when it was never set), after the home-app question if that is also open (`MainActivity.askForWallpaper`). The Home tab keeps its card and the home screen its button until it is back.

**1.0, build 81: build 78's fixes and build 80's force-stop help, together**
- Back from build 78 (see its notes below): alarms that stay on screen, a changed plan that begins when you change it, the Home card showing the new plan at once, and the first-timer changes. Together with build 80's help after a Force stop.
- **Not back: the optional wallpaper.** The live wallpaper is required again, as in build 77: the setup asks for it, and the home screen asks for it again when it is lost. The "optional wallpaper" paragraph and the "No thanks" texts of build 78 are not part of this build (the work is kept on the branch `preview/first-timer-and-slot-fix`, commit `1920cf3`).
- Made as the build-78 commits (without `1920cf3`) put back on top of build 80, so the history shows each change.

**1.0, build 80: after a Force stop, the app helps you get your home screen back**
- On some phones, closing the app from App info (**Force stop**) also switches the Home button back to the phone's own launcher with its own wallpaper. Android decides that, not the app, and the app cannot start itself again afterwards. What the app can do now:
  - **A card that stays.** While Dinacharya used to be your home app and is not now, the Home tab shows *Dinacharya is not your home app* with *Make it my home app* and *Stop asking*. It does not go away for a day after *Not now* like the pop-up does.
  - **Plain words.** The pop-up and Settings > Home app now say that some phones switch back to their own launcher after Force stop, and what to tap.
  - **A report that tells.** The problem report has a *Home app* part: which launcher Home opens, whether Dinacharya was the home app before, whether Android's home role is held by it, and how the app last ended.
  - **No plain wallpaper for 20 seconds.** After a Force stop the launcher used to wait 20 s for Android to put the live wallpaper back (it does so after a crash or a memory clean-up, never after Force stop). It now knows the last end was a Force stop (`Keeper.lastEndWasForceStop`, Android 11 and later) and paints its own day poster and asks for the wallpaper at once (`WallpaperWatch.status` with `forceStopped`).

**1.0, build 79: build 77 again (build 78 taken back)**
- Build 78 was rolled back: the code is exactly build 77's, with a higher build number (79) so Google Play accepts it as the newer upload. Everything build 78 held (alarms that stay on screen, a changed plan that begins when you change it, the first-timer changes, the optional wallpaper) is kept on the branch `preview/first-timer-and-slot-fix` (commit `2a7e75a`, the old main) and can be brought back with `git revert` of this commit.
**1.0, build 78: alarms that stay on screen**
- **The banner stays until the alarm is answered.** With the phone in use in another app, the alarm showed a banner without Android's full-screen setting (build 77 took it off to avoid two windows). Android hides such a banner after a few seconds, and the alarm went on ringing with nothing on screen to answer it. Now the notification carries the setting in that case too (`AlarmSurface.carriesFullScreen`: not only when locked): Android shows one **sticky banner** instead of a window, and opens the question itself if banners are turned off for the alarm. The app still opens nothing on top of what you are doing.
- **Shown at once.** From Android 12 a ringing service's notification may be held back for up to ten seconds unless it has buttons; a break that is over, the bedtime question and the plan question had none, so the sound came first and the banner late. The notification is now marked `FOREGROUND_SERVICE_IMMEDIATE`.
- **Buttons on every alarm.** A break that is over has **Back to work**, **+5 min** (only while two minutes of the slot are left, `SlotPlay.canExtendBreak`) and **Stop**; the bedtime and plan questions have **Stop**. Stop silences the alarm and leaves a quiet notification with the question one tap away. A break-over alarm nobody answers for five minutes now leaves that quiet note instead of vanishing.
- **Settings > Alarms** says how the last alarm showed (over the lock screen, a banner that stays, or in the app), and the two checklist lines (**Alarm screen over the lock screen**, **Alarm pop-up allowed**) now say what they do for an alarm while you use another app.

**1.0, build 78, slot times: a changed plan begins when you change it**
- **Change plan on a slot that is running starts now.** The minutes you ask for ("Gym for 30 minutes") used to be counted from the slot's planned start, so a late start or an unanswered alarm ate into them (09:30 to 11:00, answered at 09:50, Gym for 30: Gym showed 10 minutes). Now the plan stores a **start offset** (`Overrides.Edit.from`, saved as one extra column only when there is one) and `Plan.plan` splits the slot into **before / new plan / rest** (`ReplaceFromNow` works out the numbers). The example gives *No response* 09:30 to 09:50, **Gym 09:50 to 10:20**, the slot again 10:20 to 11:00; the parts add up to the 90 minutes. A change made before the slot begins is exactly as it was.
- **The time before is named for what it was.** The stretch where the alarm rang and nobody answered stays **No response** after the answer (it used to turn into "Delayed start"), and a start you put off after that is **Delayed start**; the report counts them the same way (`SlotGaps`). If you started the slot on time and worked at it, those minutes stay the slot's own and are not rewritten as the new plan.
- **One path, three places.** The Home sheet, the alarm window and the wait for a put-off start all use `SlotActions.changePart`; ending a wait by changing the plan ends it **now** (it used to be pushed back to the slot's start, which wiped the delay out of the day). The sheet counts from what is left ("Rest of the slot (70 min)", "Starts now, at 9:50 AM. The time before stays as it was."), and the message says when it runs ("Changed for today from 9:50 AM to 10:20 AM: Gym, then back to the plan").
- **The Home card shows the new plan at once.** A slot changed in the middle is three parts with one slot number (the time before, the new plan, the rest), and the Home card found "its" part by the number alone, which is the first part: the time before, with the old name. Right after "Gym" was chosen the card still said *Reading & notes* (or *No response*). The part is now found by the number and the minute (`SlotPlay.indexOfPart`; Home, the "slot now" snapshot and the Change plan sheet use it), and the minutes that went by before the change are counted in whole minutes, not to the nearest, so the new plan never begins after the moment you made it (`ReplaceFromNow`). The test now looks at the day at the moment of the change, not five minutes after it.
- Known limit: changing the plan of the same slot a second time replaces the first change (as before), so the part that already passed is shown as the slot's own again. New test `ReplaceFromNowTest` (the cases above, rounding, the last minute, a slot across midnight, reports, saved data from an older build). Six languages (6 new lines).

**1.0, build 78, first-timers: less to take in, nothing to fear**
- **Welcome says three plain things** (all your apps are still there and *swipe up* lists them; the routine and reports stay on the phone and no sign-up is needed; you can switch back to your old home screen any time).
- **A shorter first tour.** The first run shows five cards (the home screen, every app, the routine, the alarms, changing the plan) and the last check, then goes straight to the dashboard; the **Free / Pro table is no longer put in front of someone who has not used anything yet** (it is in Settings and comes up when a Pro feature is tapped). **Settings > Take the tour** still plays all fourteen cards and ends with that table (`TourActivity.FIRST_RUN`).
- **Permission pages say what will happen.** What each page asks for is named by what it lets the app do ("ring on the minute", "stay awake", "show locks over apps"; `Features.stepName`), and a box **What happens next** says what Android will put on the screen and what to tap (`Features.stepNext`).
- **A calmer dashboard for a start that is not finished.** While alarms have never worked (`alarms_worked_once` is set the first time they are on with everything allowed), the alarm card is a quiet **Next step** card ("Switch on your day alarms", "One more thing before alarms can ring") and only that card is shown; a wallpaper that was never set is a quiet card too. Amber stays for things that broke: a missed alarm, a lost wallpaper, locks that hold only on the home screen, alarms that worked before and stopped.
- **Safety lines:** the lock editor says *Phone, emergency calls, Settings and Dinacharya are never locked* (this is `GuardRules.alwaysSafe`; the line was checked against the list), and Settings > Home app says you can switch back to your old home app any time.
- **One-line help on each tab, once.** After the first run only (`tab_hints_on`, never set for an older install) Home, Routine, Insights, Backup and Settings each show one line the first time they open, closed with **Got it** (`Page.hint`, kept above the page so a page that rebuilds its body does not lose it).
- Not done on purpose: a three-item "first day" checklist (the dashboard already lists the next step, and a second list would be one more thing to read). Six languages (34 new lines).

**1.0, build 77: one alarm window, and a setup that asks which features you want**
- **One window for an alarm.** On a phone in use an alarm showed a drop-down banner and a full-screen window at once. Now the app decides when the alarm rings (`AlarmSurface`, with `AlarmService.surfaceNow`): phone locked or screen off, the full screen (the loud notification carries it); phone in use, a banner only, with *Start now* and *In 10 min* and no full screen over it; Dinacharya in front, the question opens in the app and the notification goes to the quiet channel (and is made quiet again once the question is on screen). The test alarm and the missed-alarm path use the same choice.
- **A setup that starts with the person's choice.** The first-start setup asks which of five features you want (**Day alarms**, **Phone time and app limits**, **Pause and lock apps**, **Home screen and wallpaper**, **Quiet phone**), each with an animated picture (`FeatureScenes`, drawn with `android.graphics` only, no image files; a still frame when the phone has animations off). It asks the phone for a permission only when a feature you chose needs it, one page at a time with a picture, the reason and what happens without it, and **Not now** on every page. What was chosen is kept (`features_chosen`); the dashboard's Home tab and the tour mention only chosen features that are not finished. **Settings > Features** (was Check phone settings) reopens it. Two things are new: **Display over other apps** is asked for with locks, and **Do Not Disturb** access with Quiet phone. People who installed before keep alarms as their chosen feature when alarms are on. A setup cut short (for example by Android opening the home screen after the home app page) is now picked up again where it was, not started over from the welcome. **Pause and lock apps** now also asks for the home app.
- New tests `AlarmSurfaceTest` and `FeaturePlanTest`. `tools/preview/run.sh` draws the animated scenes on a computer into contact sheets of six frames, so they can be looked at without a phone. Six languages updated (71 new lines each).

**1.0, build 26: locks hold wherever an app is opened from**
- **Why locks failed.** A lock (and a pause) held only when an app was opened from this home screen. Opened from **Recents**, from **App info** (Settings > Apps > the app > Open), from a notification
  or from another launcher, it opened as if nothing was locked. The part that holds those ways, the **Strong lock**, was an extra that was **off until you found it in the home settings**,
  needed two permissions that nothing asked for, and nothing started it again if Android stopped it. So a person who made a lock had a lock that looked set and did not hold.
- **Now it is part of a lock.** The Strong lock is **on from the start** and runs by itself as soon as something is locked or paused (and not at all while nothing is, so there is no
  notification and no battery use before that). Saving a lock or a pause **asks for the two permissions at once** (a plain message first, then Android's page; the second after the first is given).
  While one is missing, the home screen shows the chip **Make your locks hold everywhere** and the dashboard a card **Your locks hold only on the home screen** with **Hold them everywhere**; the lock
  and pause notes say the same instead of claiming it holds. Anyone can still switch the Strong lock off by hand (Settings > Home screen > Strong lock); that is kept.
- **Kept running.** The watcher is looked at every time the home screen or dashboard comes up, after a restart or an update, on the three-hourly job, and when its task is swiped away (it asks Android to
  start it again in two seconds). It writes a beat at every look, and **Settings > Help > Copy problem report** now has a **Locks** section: switched on or off, something locked or not, each permission,
  and whether the watcher is running. **A Force stop of the app from App info still ends it** (Android stops everything the app has, alarms and the live wallpaper too) until the app is opened or Home is pressed; that is Android's rule.
- Texts that were never translated are now in all six languages: the Strong lock card, the lock and pause notes, and the first sentences of the group editor's note (they went through a helper the text finder skips).
  About 40 other help sentences in Settings and the Backup page are still English in the six languages (found by checking, not changed in this build).

**1.0, build 25: alarms and the wallpaper checked and put back by the app, skip a slot, Early sleep and Early wake**
- **The wallpaper is part of the launcher.** It is checked every time the home screen comes up, from the dashboard, after a restart and every few hours; when it is gone the
  launcher paints your day itself at once, shows **Bring back your day wallpaper** and asks Android for it again at once (and after 10 minutes if turned down). The old rule that
  trusted a note and stopped asking for 30 days is gone. The wallpaper's callbacks are guarded, so an error in one cannot make Android drop it. See "The home screen".
- **Alarms check themselves.** Each time the app starts it sets the next alarm again, checks that Android still holds it and notes when one that was set never arrived (a **Missed
  alarm** notification and a line on Home). It also asks Android why it was last closed and says so in Settings > Alarms. A background check runs every three hours. **A Force stop
  from App info still clears Android's alarms and the live wallpaper until the app is opened again; that is Android's rule.** All eleven routine options (every day, weekday and
  weekend routines, a routine for today only or one date, taking slots off, a day off) were run through the alarm planner in tests: the alarms come in order, once each, and the
  start-of-day alarm is there every morning. The marks that keep a slot's own alarm quiet are tied to the schedule they were made for. See "If Android closes the app".
- **Skip this slot** on the slot-start question: the slot is taken off and the next one begins now. **Every slot has an early finish**: **Finish early**, **Early sleep** (the slot
  before sleep) and **Early wake** (sleep). See "When the day does not go as planned".
- New texts were translated into the six languages by the developer: please read them once on a phone. Nothing in this build was seen on a phone.

**1.0, build 24: goals for slots with to-dos, one free day for three videos, a Free and Pro page**
- **Goals belong to a slot** of your routine (not to a whole activity type) and can have **their own to-do list**. A goal with no to-dos is one more to-do of its
  slot (tick it, or time ticks it at the target); a goal with to-dos is a heading, struck only when all its to-dos are done. They are one list: a tick anywhere is a tick
  everywhere (Home, the to-do box, Insights, the live wallpaper, the reports). Goals that were made before keep working. See "Goals and streaks".
- **Free keeps** the home screen, alarms, to-dos, **today's report** (with its PDF), one paused app and one locked app. **Pro adds** goals, every saved day, the week view,
  the deeper analysis (slot analysis, habit cards, phone use in slots), more pauses and locks, routines and colour looks, and removes the ads. Nothing already made is deleted.
- **Three short videos open everything in Pro for a day**, then it all goes back (the old per-item videos and 3-day passes are gone from the screens). One button, one offer.
- A new **Free and Pro** page (the last page of the tour and a button on the Pro screen) shows both side by side, in groups that open one at a time.
- Everything else is as in build 23. New texts were translated into the six languages by the developer: please read them once on a phone.

**1.0, build 23: phone use in slots, slot analysis, habit cards**
- **Phone use in slots** (optional, Settings > Phone use in slots, off to begin with): choose the apps that belong to each activity; any other
  phone use during a slot is marked on the live wallpaper with the exact times and in the reports. See "Phone use in slots".
- **Slot analysis** on the report (and in the PDF): for each slot and each activity, the focus, the time devoted, the other phone use with exact times, the
  longest focus and the apps used. See "Performance, reports and PDF".
- **Habits** in Insights is now a short list of cards that open one at a time (what I noticed, try this), not a long block of text.
- The Focus part of the score and the suggestions count only other phone use when apps are chosen and the switch is on; otherwise nothing changed.
- Everything else is as in build 22. New texts were translated into the six languages by the developer: please read them once on a phone.

**1.0, build 22: a simpler lock editor**
- The editor for a locked group is shorter. A **Lock by** choice at the top (**Time**, **Activity** or **Time + activity**) shows only what that
  choice needs; Lock now, library mode, hide and strict moved into a closed **More options**. Locks saved before keep working the same way.
  Nothing else changed. See "The group editor: choose how it locks".

**1.0, build 21: the day as it really goes**
- **Home and the wallpaper follow what you do.** Starting a slot later, carrying the last slot on, taking a break, starting early or finishing early
  now change the schedule you see ("STARTING LATER", "STARTED LATE", "RAN LONGER", "STARTED EARLY", "FINISHED EARLY", a break row). The saved routine and the
  alarms are not changed by it. See "When the day does not go as planned".
- **Finish early** under the slot going on: start the next slot now, take a break first, start it after a while, or take the rest of today off.
- **Take the day off** from Finish early or from the start-of-day alarm: no alarm rings; the app asks "Plan your day now, later, or leave it?"
  and the alarms come back when you plan the day again, or tomorrow.
- **Locks** can now be by time, by activity type, or by both together (a switch on the group), and a group can also be locked in library mode.
- **Start-of-day alarm:** turn it off by a tap, three sums, a short pause (four breaths), a shake of the phone, or a typed sentence;
  a motivating quote (49 lines, a different one each day); and a **Take the day off** button.
- **Library mode** (Home card and Focus and locks > Library and Quiet mode): every alarm only vibrates; optional fixed hours and days; locks in library mode.
- New texts were translated into the six languages by the developer, not by a native speaker: please read them once on a phone.

**1.0 (first release on Google Play)**
- **Pro has two plans:** **Rs 49 a month** (a Google Play subscription, product `pro_monthly`) or **Rs 499 once for life** (the one-time product `pro_unlock`). The Pro screen shows both side by side with the prices Google Play reports; Pro is on while either is active. A monthly subscriber who buys lifetime is told to cancel the monthly plan in Google Play, and "Manage subscription" opens Google Play's page for it. Anyone who bought `pro_unlock` before keeps Pro. In Play Console create the subscription and set `pro_unlock` to Rs 499 (see "Pro purchase: monthly and lifetime").
- The app is named **Dinacharya Launcher** and is credited "© 2026 | Developed by Code Avirata" on one line (home screen mark, Settings > About, store text, privacy page). The version shown in Settings > About is **1.0**. Google Play's internal build number (`versionCode`) is **45** and goes up by one with every build you install or upload, because Google only accepts a number higher than every one it has seen. Settings > About shows it after the version ("1.0 (build 45)"), so you can tell which build is on the phone. The notes below, from 2.2 to 3.0.1, are the private test builds that came before.

**2.2**
- **Dinacharya is now a home screen (launcher), not a wallpaper app.** No apps on it: the whole screen is your
  day (the time, the date and every slot, the current one highlighted), shown by the Dinacharya Launcher live wallpaper that
  comes with the launcher. It asks Android to put the wallpaper in place (you confirm once), so your day is also on
  the lock screen and in the recent-apps screen; until then the launcher paints the same day itself. Tap your day for
  the dashboard, swipe up for all apps, swipe down for notifications, left for the camera and right for the phone
  (both changeable), press and hold for settings.
- **The all-apps list** is a plain list of names, with a see-through blurred background and a slide-in. There are no
  icons. Dinacharya is in the list; opening it shows the dashboard (a pause or lock is never put on it).
- **At midnight** the old schedule scrolls up and the new day rises and fully replaces it by the wake-up time.
- **Focus pauses:** apps can wait a few seconds before they open (Go back or Open anyway). A new rule pauses
  in focus times by default. Strict pauses, daily limits, any number of apps and **playful pauses** (a quick sum, a
  sentence to type, slow breaths, say why) are part of Pro; the day report counts pauses and what you did.
- **Bedtime:** "It will take more time" now shows what that costs: minutes of sleep lost before your alarm, the
  sleep you would still get, the shortfall against your plan and how much you have snoozed today.
- **Alarm screen:** the question is brought up more reliably (see "If an alarm is late"); a new checklist line
  checks the alarm pop-up.
- **A setup on first start** asks for every permission the app needs (home app, wallpaper, notifications, alarms,
  exact alarms, full-screen alarm, battery, phone-maker lists, usage access), one screen each, ticking each as it is
  done. It can be skipped and reopened from **Settings > Check phone settings**. A **tour** follows.
- **The home screen no longer shows a wallpaper chip.** The wallpaper is asked for in the setup, then at most once a day.
- **Locked groups:** lock a set of apps for hours you choose, or for the next few hours. Free: one group. Pro: more
  groups and strict locks.
- The dashboard (routine, insights, backup, settings) is opened from the home screen and had no icon of its own (from 2.4 the app's icon opens it, through the welcome).
- **Fixes after the first phone tests:** the wallpaper is no longer asked for again once it is set (the check now
  uses two sources, handles lock-screen-only, and remembers a confirmed preview); the battery step opens the app's
  own info page (the general battery list hid Dinacharya); and a greyed-out Usage access on a file-installed app
  comes with the one-time "Allow restricted settings" steps.
- **Free and Pro, reshaped:** free users see today and the last 3 saved days of the report (the week tab and older
  days are Pro), pause 2 apps (was 3) and lock 2 apps in one lock; groups, many locks, strict locks and playful
  pauses are Pro. Pro is Rs 49 a month or Rs 499 once for life (both set in Play Console).
- **A See Pro screen** of its own (Free against Pro, the price, Get Pro, Restore). The old "See Pro" button in the
  home settings did nothing because Android did not pass the request on to the dashboard; all of them now open this
  screen directly.
- **Dinacharya is in the all-apps list again** (opening it shows the dashboard).
- **Ads:** the banner is always shown while ads are on (with a loading or "no ad" line), retries, also sits on the
  report screen, and the problem report carries the ad and consent status.
- **The tour** has eleven pictures plus the setup page, including **Lock apps for a while** and **Free, and Pro**.
- Version 9 (2.3).

**2.4**
- **Watch videos, use Pro features for a while** (see "Dinacharya Pro and ads"): rewarded videos open Pro features, one pass at a
  time (from 2.6 one item per video, from 2.7 up to a whole group, see below). AdMob ad units of type Rewarded are needed for real
  ads (`admob_rewarded_id`, and from 2.7 `_2` and `_3`).
- **One entry in recent apps.** Dinacharya used to show twice: the home screen and the dashboard were two separate tasks.
  The home screen no longer has an icon and is excluded from recents, so only the dashboard shows there.
- **A proper first start.** The app's icon now opens a **welcome** screen, then the setup, then the tour, then the dashboard
  with a "You're all set" message, instead of dropping straight onto the bare home screen. After that the icon opens the
  dashboard. Pressing Home before the first run is finished picks it up where it stopped. Updating from an older version
  skips straight to the dashboard (the setup and the tour were already done).
- Version 10 (2.4).

**2.5**
- **A video pass now really ends.** Before, the 24 hours only switched the Pro feature off; whatever had been made with it
  stayed saved. Now, when the day is over, the extra pauses, locks, routines, goals and colour look made with the pass go
  back to the free version, and what you had before stays (see "Dinacharya Pro and ads"). Pro, bought before or after,
  keeps everything. The wording on the Pro screen, the "Part of Pro" message, the tour and the home settings says so before
  you watch.
- Version 11 (2.5).

**2.6**
- **One video opens ONE thing.** Before, a video opened a whole area at once (watching for "Pauses" gave strict pauses,
  daily limits, playful pauses and as many paused apps as you like together). Now there are 14 items (saved days, week
  view, more paused apps, strict pauses, daily limits, playful pauses, more locks, more apps in a lock, strict locks,
  routines, goals, and the Sunset, Ocean and Graphite looks), and a video opens exactly the one asked for. Each one also
  goes back to the free version on its own when the day ends, and only what it made. Pro (the purchase) still opens
  everything. If every Pro feature seems open without any video, the Pro screen shows "Dinacharya Pro" with a tick (Pro
  is owned), or, in the test (debug) build, "Pretend I have Pro" under "For testing" is switched on.
- **A way back when Dinacharya stops being your home app.** Android keeps the home app choice outside the app and can
  drop it (after an update, a reinstall or when another launcher is picked). The app now remembers when it was the home
  app. When it is not any more, the dashboard asks once each time it is opened: "Make it my home app" (Android's own
  dialog), "Not now" (quiet for a day) or "Stop asking". Launcher > Home app in the settings shows the state at any
  time. The home screen's start-up is also guarded against any failure, so it comes up with a plain list of apps instead
  of an error. If the Home button shows another launcher, check Android's Settings > Apps > Default apps > Home app.
- Version 12 (2.6).

**2.7**
- **Strong lock (optional; on from the start since build 26).** Locks and pauses now also hold an app opened from the recent-apps screen, a notification, a widget
  or another launcher, and an app that is open when a lock starts, which they could not before (they worked only from
  Dinacharya's home screen, and not at all while another launcher was the home app). It is off by default; turn it on in
  Settings > Home screen > Strong lock and allow Usage access and Display over other apps (see "The home screen"). The phone,
  Settings and system screens are never covered. Tested in rules and in the one-look logic, never on a real phone.
- **Videos in layers, to earn more without more ads in the way.** (1) Ad layers: the video is asked for from up to three
  AdMob ad units, best paying first, with one video loaded ahead of time. (2) Reward layers: 1 video opens one item for a
  day, 2 videos the same item for 3 days, 3 videos the whole group for a day; "Stop here" between videos keeps what was
  earned, and a day-long pass can be stretched to 3 days with one more video. What a pass made goes back to the free version
  when it ends, for a whole group too. Passes now keep a set of items (the old one-item record is still read).
- You need to create the extra Rewarded ad units in AdMob and put their ids in `monetization.xml` (see "Earn from the app").
- Version 13 (2.7).

**3.0.1**
- **To-dos are slot-only.** The goal to-do lists are gone: the To-do box, the Home card and the wallpaper show the lists of the slot
  only, and goals in Insights no longer have a To-do button. Goal lists made in 3.0 stay saved but are not shown. Version 17 (3.0.1).

**3.0**
- **To-do:** an item can repeat (every day, every other day, on chosen days; up to 20); **Home** shows every item of the slot
  with tick boxes; the live wallpaper shows up to 8 lines and pages through the rest every 8 seconds, finished items first.
- **Take a break** uses − and + and an **Until this slot ends** switch instead of a list; so does **More time** when a break is over.
- **Slot start question:** one big **Start now** button, with **Start later** and **Other options** opening under it, and normal-size text on
  every alarm screen.
- **A calmer layout:** parts that open and close on Home, Routine, Insights, Backup and Settings, with a one-line summary and the open
  or closed state remembered. Nothing was removed.
- Translations for the new text in the six languages. Version 16 (3.0).

**2.9**
- **Home:** **Take a break** on the Now card; to-do lists for a slot and for goals, drawn on the live wallpaper only while that slot runs.
- **Routine:** a **routine for one day** (today, tomorrow or a date); **Time by type** under Start of day; every type has a kind
  ("Other" replaces "no type") and the types Meal, Personal and Sleep were added; each type has its own automatic colour in the charts.
- **Insights:** a fuller **Today so far** (ring chart by type, time on apps and without apps for each slot, goals with their apps and to-dos);
  goals only for activities in that day's schedule, today-only goals only for what is left today, repeat every day, every other
  day, on chosen days or only on days with certain activities, and a to-do list (repeating to-dos came in 3.0).
- **Time recording:** a slot and a goal each show the time in every app and the time without an app; the slot-start question now has
  start now / start after / continue the slot before / other plan / take it off, all limited to the current slot; a break and its extension
  stay inside the slot.
- **Alarms:** four things a slot can do when it starts (ring, vibration, silent, none), the silent ones asking by default; one
  **start-of-day alarm** that rings until the day starts or is put off, with a line to read first and an optional challenge.
- **Lock:** lock a group during an activity type; optionally hide the locked apps.
- Home-screen pause rules now also lift during a break. New texts were translated into the six languages by the developer, not by a
  professional translator.
- Version 15 (2.9). Older saved data is read and kept: slots with no type become Other, a bell that was off becomes a silent notification.
- Tested by compiling every source against the Android API and by the program's own test files and checks; **not yet run on a phone**.

**2.8**
- **Fixed: a video unlocking more than was asked for, and rewards without a video.** Passes are now a list, one entry per
  thing with its own timer (`PassLedger`): a video for a colour look opens that look and nothing else; a video for strict pauses
  opens strict pauses and nothing else; strict pauses on a third app needs its own video(s) and is not covered by a pass for
  "pauses for more than two apps". A reward is given only after an ad was actually shown and its reward fired; a failed or
  closed ad opens nothing and costs nothing, and the messages and toasts say what was really opened.
- **Languages:** English, हिन्दी, मराठी, ગુજરાતી, বাংলা, தமிழ், తెలుగు (see "Languages").
- **The all-apps list opens the keyboard** with the cursor in the search box (can be turned off).
- Translated text must be whole sentences, so a few messages in the program were rewritten (the video dialog, the tour's Pro
  page, a streak tip, "Used on N days").
- Version 14 (2.8).

**2.1**
- **Alarms:** a Home card, guided permission steps, a test alarm, a phone checklist with buttons for
  each setting (including common phone makers' background lists), and a notice when an alarm
  never reached the app. The ringing service uses Android's "system exempted" type for alarm apps.
- **Dinacharya Pro** (Rs 49 a month or Rs 499 once, through Google Play) and small ads with a consent form.
- **Google Play ready:** targets Android 16, edge-to-edge screens, swipe-back support, a separate
  upload key for Google Play, a bundle (`.aab`) workflow, a privacy policy page and store listing text.
- Usage access is now explained in a message before Android's settings open.

**2.0**
- Five tabs with a bottom bar; settings grouped into menus.
- Google sign-in (account picker, with code sign-in as fallback) and WhatsApp-style Drive backup
  with Daily/Weekly/Monthly schedule, Wi-Fi only option and restore. Replaces the old visible
  "Dinacharya" PDF folder upload.
- Weekday/weekend (and any other) routines, used on the days you pick.
- Daily goals per activity type with streaks; time by type in reports and PDFs.
- Notes per slot (shown on the slot, on Home and on the alarm), Duplicate, and Undo.
- Small developer mark on the home screen (can be hidden); developer mark and About page in the app.
- New fixed signing key (uninstall version 1 first).

**1.1**
- Fixed the crash when switching alarms on (missing exact-alarm permission on Android 12+), and
  slot times disappearing. Crash guard and problem reports. New look with cards, switches,
  segmented choices, − / + minute stepper and animations.

## Privacy

The full policy is `docs/privacy.html` (the page you give to Google Play).

Your routine, answers and reports stay on the phone. The home screen reads the list of apps on the phone
only to show it on the phone; the list, your pauses and your choices are never sent anywhere (they are
included in your own Drive backup only if you turn that on). Data leaves the phone in two cases only:
your own backup, sent to your own Google Drive after you sign in; and, in the free version, what
Google AdMob collects to show ads (advertising ID, IP address, ad interactions, diagnostics).
Buying Pro removes the ads. Payments are handled by Google Play; the app only learns whether Pro is
owned. The developer runs no server and receives no data. The app asks for:

| Permission | Why |
| --- | --- |
| Usage access (you grant it in Settings) | Phone time on the home screen and in reports, and the minutes for daily app limits (Pro). Explained in the app before Android's settings open. |
| Open the notification shade | Lets swiping down on the home screen show your notifications. An ordinary permission; nothing is read. |
| Notifications, full-screen notifications | Show the alarm question, also when the phone is locked |
| Alarms and reminders (exact alarms) | Ring each alarm at the exact minute a slot starts |
| Foreground service (system exempted), keep awake, vibrate | Ring the alarm, and keep the phone awake while it rings |
| Display over other apps (you grant it in Settings; only for the Strong lock, which runs while something is locked or paused) | Show your own lock or pause screen in front of an app you locked or paused |
| Foreground service (special use; only while something is locked or paused and the Strong lock is on) | Keep watching which app is in front so a lock or pause holds wherever the app was opened from |
| Run at start-up | Set the alarms and the backup schedule again after a restart |
| Internet, network state | Google sign-in, the Drive backup, ads, and checking Pro with Google Play |
| Google Play billing | The Pro purchase (monthly plan or lifetime) |
| Advertising ID (added by the ads library) | Ads in the free version |
| Google account (when you sign in) | Your name and email, and the app's own private space in your Drive. The app cannot see your other Drive files. |

Android 8.0 or newer.

**1.0, build 27: finish any slot early, a small Day off button, named goals, goals for tomorrow**

- **Finish early works almost everywhere.** It now also shows on a break (finishing ends the break and the next slot starts), on a slot that was carried on past its end (the next slot starts now) and when only one minute is left. It is still not offered on a slot you took off, a waiting row, a cut slot or when no slot follows. The "wait" choice is not offered on a carried slot or on sleep.
- **A small "Day off" chip** sits at the top right of the Home page and of the Routine tab. It asks the same confirmation as before and says so if today is already a day off.
- **Goals can have a name** ("Goal name (optional)", up to 30 letters). The name is only a label: which slot the goal belongs to, and how it is counted, did not change. Old goals load as before.
- **Goals for tomorrow.** "＋  Goal for tomorrow" in the Goals section, and "Tomorrow only" in the way-to-repeat list. It lists the slots of tomorrow's schedule and counts only tomorrow.
- New test `AnySlotTest`; `EarlySkipTest` updated for the shorter room. Six languages updated.

**1.0, build 28:** the small "Day off" chip is now white with a teal outline so it stands out, and on Home it sits on its own line under the greeting (right side) instead of sharing the greeting row. Nothing else changed.

**1.0, build 29:** the small "Day off" chip is removed from the Routine tab (it stays on Home). Nothing else changed.

**1.0, build 36:** a strict lock cannot last for ever. Because there is no way past a strict lock, saving one is refused when it would hold for more than **12 hours in a row**, or would leave less than **2 free hours** in any day of the week, or when it includes **library mode** (which can have no end). The check lays the week out minute by minute: the daily window (back-to-back windows count as one run) and, for locks by activity type, the slots of those types in the routine of each day; with "Time + activity" only the minutes that are both count. A strict lock that was saved before this build is left as it is. The rule is in `LockRules.strictProblem` and has its own test (`StrictCapTest`).

**1.0, build 37:** the start-of-day alarm's own line (Settings > Alarms > Start-of-day alarm). Typing in the box no longer sends the cursor back to the first letter (the saved line has its spaces trimmed, so the box was being rewritten while you typed; it is now left alone while it has focus). A new choice, **How your own line is used**, says what the line is for: shown as the quote (the default, as before), typed to put the day off, typed to start the day, or typed for both. A line meant for typing is not also shown as the quote, is limited to **80 letters** in the box (cut at a word if an older, longer line is used), and is used only where that challenge is set to "Type a sentence"; otherwise the built-in sentences come up as before. The rules are in `Routine.quoteLine`/`typeLine` and have their own test (`WakeLineTest`).

**1.0, build 45:** **No response**. When a slot's alarm or notification goes off (written to the history as `rang`) and you do not answer, the slot counts from the moment you answer, and the time before is marked **No response**. Answering within 3 minutes of the alarm counts as answering in time. Any answer counts (start, start later, take a break, rename, take it off, and so on). Until you answer, the Home card says **NO RESPONSE** with the same choices as a delayed start (Start now, Continue the slot before, Other plan); if you never answer, the whole slot is no response and counts as no active time. In reports and Insights the slot shows a **No response** line with its time; a start you then put off counts from the answer, so the two never count twice. Every slot that rings is covered; sleep keeps its own bedtime question. A slot started before its alarm has no no-response time. Code: `NoResponse`, `LivePlan` (the wait), `SlotGaps.noRespMs`, `DayReport.Row.noRespMs`. Test: `NoResponseTest`.

**1.0, build 44:** fix: after "Early sleep" the bedtime alarm could still ring when the routine was edited afterwards (editing the routine makes slot numbers mean other slots, so the early start was forgotten). The bedtime alarm now also stays quiet when you already went to sleep that night (`AlarmPlanner.sleptAlready`, from the sleep entry in the history, which does not depend on slot numbers). Test: `EarlySkipTest`.

**1.0, build 43:** fix: after "Early sleep" (or a slot started early) the slot's own notification and alarm could still ring at its planned time. The alarm now checks the history when it is due (`AlarmPlanner.begunEarly`) and stays quiet when that slot began early or the slot before it was skipped; going to sleep early also clears a bedtime question that was set to be asked again. Test: `EarlySkipTest`.

**1.0, build 42:** fix: Library mode could not be found or switched on. Focus and locks > **Library and Quiet mode** is now listed in Settings (under Focus and locks), and library mode is a plain **switch** (on until you switch it off; the fixed hours stay as before) on both the Home card and in that card, with no dialog. The Home card's **Settings** button opens that card (it used to open Alarms, where library mode no longer is). If library mode is also on by the clock, switching off the switch says it stays on until those hours end.

**1.0, build 41:** **Quiet mode**, as its own part. Focus and locks has a new card, **Library and Quiet mode**, with two parts: Library mode (moved there from Alarms, unchanged) and Quiet mode. `QuietRules` holds the rules (what the phone does, and when: hours, activity types, both, or library mode; the quietest wins) and the pure steps that set the ringer and Do Not Disturb and put them back (`ringerStep`, `holdStep`); `QuietDialog` edits a rule; `Quiet` applies them and looks again when the app opens, after a restart, when you answer a slot, and at the next window edge, slot start or end, end of a break or library switch (it replaces `LibraryRinger`). The hours code of locks (`LockRules.windowEnd`, `nextStart`, `during`) now works on plain values so rules and locks share it. The goal rings on the wallpaper from build 38 are removed (`GoalGlance` and its test are gone; the goals stay in Insights). Test: `QuietRulesTest`.

**1.0, build 39:** changing the plan while a slot runs or waits. **Change plan** is a small pill on the Home tab's Now card. It renames the running slot for today only (its type stays, so strict locks and alarms keep working); the slots before and after do not move. While a slot's start is put off, the Now card offers three answers, the same as the alarm screen: **Start now** (the wait ends), **Continue the slot before** with a − and + for the time (the wait ends there and the slot before goes on until then, at most an hour and never past the slot's end), and **Other plan** (a new name replaces the slot for today). The last answer about a late start is the one that counts (`LivePlan`, `SlotGaps`), so a wait can be shortened; reports show a replaced slot under its new name. Actions are `SlotActions.startWaitingNow`, `continuePreviousWhileWaiting`, `changePlan`. Test: `WaitChoicesTest`.

**1.0, build 38:** goals and Insights. **Time by type** now counts a slot that is running right now (a slot without an alarm showed 0 until it ended; the report calls it "upcoming", and the part that has passed now counts). **Daily goals** are lighter: a summary ring ("3 of 5 goals", the minutes still to go), and one slim line per goal with a small clock-style ring (`GoalRing`: quarter ticks, a tick mark when met; tap for the details as before). The three big add buttons are one small plus that opens a short sheet (every day, today only, tomorrow only). The summary is `TypeStats.summary` (each goal capped at its target). The wallpaper does not show goal rings (tried, then removed). Tests: `TypeStatsTest` (running slot, summary).

**1.0, build 35:** fixed: an app turned off and then on again in Settings was missing from the list of all apps (the list was only read again when an app changed while the home screen was open; it is now read again every time the home screen comes to the front). New: press and hold an app, then **Uninstall** opens Android's own uninstall screen, which asks to confirm. It is offered for apps you installed in the main profile, not for phone apps, work apps or Dinacharya itself. This adds the `REQUEST_DELETE_PACKAGES` permission.

**1.0, build 35:** the list of all apps is read again when an app is turned off or on, installed, removed or updated (Android's package broadcast, and once more a moment after you come back to Home), and an open list follows what you just changed in the settings (an app shown again in Hidden apps is there at once). Press and hold an app: **Uninstall** now also appears for a phone app that has had updates (it is called "Uninstall updates" there, and Android takes the updates off); a phone app with no updates can only be turned off, in App info.

**1.0, build 34:** in the Focus card the "Pause during" switches are in their own dropdown (it shows the ticked kinds when closed), so the card is shorter. Nothing else changed.

**1.0, build 33:** "Swipes" is now **Shortcut**. The strong lock has no switch of its own any more: the **strict** choice switches it on. A strict lock group (or a strict pause, both Pro) makes the watcher run, so the lock also holds an app opened from Recents, App info, a notification, a widget or another home app, and one that is already open when the lock starts. Ordinary locks and pauses hold when an app is opened from the home screen, as before. The two things the strong lock needs (Usage access, Display over other apps) show in the **Locked apps** card only while a strict lock or pause is set, and Android asks for them when the strict lock is saved. The Focus card now shows only pauses, Locked apps only locks and Phone use in slots only phone use.

**1.0, build 31:** the settings are sorted into categories in Settings, with nothing removed. **General** has Language, Take the tour and Check phone settings. **Home screen** has Home app, Swipes, App list, Hidden apps and Home screen look. **Focus and locks** has Focus (the pauses), Locked apps and Phone use in slots. **Alarms and phone** has Alarms and Permissions. Each line opens its page with its card open, and on every settings page only one card is open at a time: opening one closes the other. The strong lock is now a switch inside the Locked apps card, next to the locks it makes hold everywhere (a group's own *strict* choice is separate: it only decides whether that lock can be broken). Removed as duplicates: the second Language, Take the tour and Check phone settings lines that were inside the old Home screen page, and the button that sent you from the home settings to the look page (Home screen look is in the Settings list).

**1.0, build 30:** the app's Google Play package name (`applicationId`) is now `app.dinacharya.launcher` (the code keeps its folders and `namespace`). The report-sharing provider uses the new name too, so the new app installs beside an older build. Nothing else changed.
