# Dinacharya Launcher: a minimal home screen for your daily routine

A home-screen app (a launcher) with **no apps on it**: just your day. Alarms, goals, daily reports
and a Google Drive backup come with it.

- **Home screen (launcher):** no icons and no app list. The whole screen is your day: the time on the
  left, today's date on the right, phone use today and every slot of the day with the current one
  highlighted, in four colour themes (light and night). It is shown by the **Dinacharya live wallpaper**,
  which comes with the launcher: Dinacharya asks Android to put it in place and you confirm once (Android
  never lets an app do that silently). Being the real wallpaper, it also shows on the lock screen and in the
  recent-apps screen. A small "Developed by ASG" mark sits at the bottom (it can be hidden). Tap
  your day to open the dashboard, swipe up for every app (a plain list with search and an A to Z strip, over a
  blurred view of your day; Dinacharya is in it too and opens the dashboard), swipe down for notifications, left for the camera
  and right for the phone (both can be any app), press and hold for settings. Apps that pull you away can wait a
  few seconds before they open, or (Pro) ask a quick sum, a sentence, slow breaths or why you are opening them;
  you can always open them anyway. **Locked groups** keep a set of apps shut for hours you choose. The first time the
  app opens, a **welcome**, a short **setup** (every permission) and a **tour** lead into the dashboard (the setup and
  the tour can be replayed from Settings).
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
  core: the home screen, alarms, today's report with the last 3 saved days, pauses on 2 apps and locks on 2 apps.
  A one-time **Dinacharya Pro** purchase (set to Rs 99 in Play Console) removes the ads and unlocks every saved day
  and the weekly view, any number of pauses and locks, locked groups, strict locks, extra routines, goals and colour
  themes. The **See Pro** screen shows the two side by side. See "Dinacharya Pro and ads" and "Earn from the app".

The **dashboard** has five tabs at the bottom: **Home** (now and next, today's goals, a preview of the home screen),
**Routine**, **Insights**, **Backup** and **Settings** (Dinacharya Pro, home screen, home screen look, alarms,
permissions, help, about). You open it from the home screen (tap your day, or pick the dashboard for a swipe in the home settings)
(Dinacharya is also in the list of all apps: opening it there shows the dashboard). The app's one icon opens a welcome the first time and the dashboard after that. The home screen itself has no icon (it is what Android shows when you press Home) and is kept out of the list of recent apps, so Dinacharya shows there once.

## Get the APK

Pick one way. Each gives you `app-debug.apk`, which you can install on your phone.

### A. Build it online with GitHub (nothing to install)

1. Create a free GitHub account and a new empty repository.
2. Upload every file and folder from this project, including the hidden `.github` folder.
   If the upload skips `.github`, open **Add file > Create new file**, type
   `.github/workflows/build-apk.yml` as the name, and paste in the contents of that file.
3. Open the **Actions** tab, choose **Build APK**, then **Run workflow**.
4. When it turns green, open the run and download **Dinacharya-wallpaper-apk** at the bottom.
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

1. Open **Dinacharya**. The first time, a **welcome** screen ("Get started") leads into a **setup** that goes through everything the app needs, one screen each, and
   checks it as you go: make Dinacharya your **home app**, put its **wallpaper** in place, allow **notifications**,
   switch **alarms** on, allow **exact alarms**, the **full-screen alarm**, **battery** use without limits, your
   phone maker's own background lists, and **usage access** (for phone time). Each step explains why, opens the
   right Android screen and ticks itself when it is done; any step can be skipped, and the setup can be reopened
   from **Settings > Check phone settings** or the card on the dashboard's Home tab. Android makes you confirm
   each of these yourself on its own screen: an app cannot switch them on silently. Then a **tour** walks through
   the app, and it ends in the dashboard with a "You're all set" message (a button to go to your home screen, or to
   make Dinacharya your home app if that is still to do). Until the wallpaper is in place the launcher paints the same day itself, so the home screen is never
   empty, but your recent-apps screen and lock screen keep showing the old wallpaper.
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
  **Time by type today**, **All your goals** and **Habits** are closed to begin with, each with a one-line summary.
- **Backup:** **Settings**, **Restore**, **Other ways to sign in** and the long note about the backup are closed to begin with.
- **Settings:** in **Home screen**, **Focus**, **Locked apps**, **Strong lock**, **Hidden apps**, **Swipes**, **Look** and **Today** each
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
switch to the Dinacharya wallpaper; if you skip it, the launcher asks again at most once a day until it is on (the
home screen has no button for it). It can also be done from the dashboard's Home tab, the tour and the home settings.
Android shows its own preview with a **Set wallpaper** button; tap it (on Android 14 and later it may ask where:
choose **Home screen** or **Home and lock screen**; "Lock screen" alone leaves the home screen on the old picture, and
the app says so). Some phones do not report a live wallpaper back to apps, so the app also notes when its own
wallpaper is really running, and once you have confirmed the preview it stops asking for 30 days. This is why the
recent-apps screen shows your day instead of another picture. **At midnight** the old schedule scrolls up and out while the next day rises from below, and it has
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
always open the app after the wait. Free: a short pause for up to 2 apps. **Pro:** strict (longer) pauses,
a daily limit in minutes per app (uses Usage access), any number of apps, and **playful pauses**: pick how the
pause goes for each app (**Settings > Home screen > Focus**, or press and hold the app): a **quick sum** (addition,
subtraction or multiplication; three of them for a strict pause or a used-up limit), a **sentence to type**,
**slow breaths** with a moving circle (two, or four when strict) or **say why** you are opening it (a real need opens
it at once; the other reasons get a kind sentence and a short wait). Go back is always there, and a longer wait
(30 seconds, 60 when strict) brings **Skip the challenge**. Each pause is counted; the day report and the suggestions
use it ("went back 4 times", "opened anyway 6 times").

*Pauses apply when you open an app from this home screen (its all-apps list or a shortcut).* On their own they
cannot stop an app that is opened from the recent-apps screen, a notification or another app: Android does not let a
launcher do that. The optional **Strong lock** (below) can; it needs two permissions and is off until you turn it on.

**Locked groups.** In **Settings > Home screen > Locked groups** make a group (for example *Social*), choose its
apps and the hours it is locked (from, to, and the days; a window can run past midnight, and the same hour for
start and end means the whole day), or lock it **now** for 30 minutes to 8 hours. While a group is locked, its apps
are greyed with "locked" in the all-apps list, and opening one shows a screen with the group, the time it ends and
the time left, with **Go back**. You can break a lock yourself: tap **Break the lock**, wait 30 seconds, and type a
sentence. When the lock runs out while the screen is showing, the app opens. Free: **one lock of up to 2 apps** (the first two in it hold; if an older lock has more, its row says "only 2 held, needs Pro"). **Pro:** locked **groups**, with a name for each, any
number of groups (up to 8, with up to 40 apps each) and **strict locks**, which cannot be broken and cannot be
changed or deleted while they hold. A lock that was set up while Pro was on and is left after Pro ends keeps its
group but stops holding, apart from the first group. Opening a locked app and going back counts as a pause in the
day report; a lock is checked before a pause rule, so a locked app is never also paused.

**Lock during an activity type.** A group can also be locked **while a kind of activity is going on**, for example *Social* during
Study. In the group's settings, under **Also lock during**, tap **Choose activity types** and tick any of: "Any task", "Any rest"…
or single types such as Study or Exercise (Other is there too). The group is then locked when the slot of your day that is going
on has one of those types, as well as in its hours; with no hours set it is locked by activity alone. Back-to-back slots of the same type are one
stretch. A break, a slot taken off, and the gap between slots lift this lock, and the lock screen says "While you are on: Study".
It uses the same limits as other groups (free: one group of up to 2 apps; strict locks are Pro), and it also holds through the
Strong lock.

**Hide locked apps.** Turn on **Hide the apps while they are locked** in a group and its apps are left out of the list of all apps
and out of search while the lock holds, and come back when it ends. (Locks still hold on any other way of opening an app, such as recents
or notifications, with the Strong lock.)

*Like pauses, locks apply when you open an app from this home screen.* On their own they cannot stop an app that is
already open, or one opened from the recent-apps screen, a notification, another app or another home app: the
optional **Strong lock** (below) does. A strict lock is
self-control, not a security wall: while it holds, the home settings cannot be reset and a backup restore leaves
the groups alone, but you could still switch the home app, open the app from its Android app info, or uninstall
Dinacharya. Calls still arrive and the emergency call on the lock screen is unaffected.

**Strong lock (optional, off by default).** Without it, a lock or a pause only works when you open an app from
Dinacharya's home screen, so an app can still be opened from the recent-apps screen, a notification, a widget, another
home app, or kept open when a lock starts, and nothing is held at all while another launcher is the home app. Turn on
**Settings > Home screen > Strong lock** and Dinacharya also holds those. It needs two things that Android makes you
allow yourself, each with a button in that card: **Usage access** (the same permission the daily limits use, so it can see
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
uninstall. It stops itself when you switch it off or take a permission away, starts again after a restart, and only the
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
that also works from outside this home screen is the optional Strong lock above.

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
- **To-do** opens the small tasks of this slot: tick, add and remove them. A to-do belongs to its slot only. Under the card, **Your to-do
  list** shows every item with a tick box ("3 of 5 done" when it is closed), so nothing is hidden in the app.

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

*(Adding a new goal is part of Dinacharya Pro. Goals that already exist keep working.)*

On **Insights**, tap **Add a goal**: pick an activity type (such as Study) and a daily amount (such as 6 h). Through the day
the goal fills up with kept slots of that type; slots without an alarm count as done because the app cannot ask. A 🔥 streak
counts the days in a row you reached it. Tap a goal to change or remove it. Today's goals also show on **Home**, and every
report and PDF has a **Time by activity type** section.

- **Only for activities that are in the schedule.** The types you can choose are the ones in that day's routine. A goal for
  **today only** offers only the activities still to come today (the slot running now counts; finished and taken-off slots are not offered).
- **Repeat.** A goal can be for **today only**, **every day**, **every other day** or **on chosen days**, and, for the repeating
  ones, **only on days with** certain activities (for example Revision only on days with a Study slot). A today-only goal is
  cleared by itself once the day has passed. A goal counts on a day only when that
  day's schedule has the activity; on other days it neither counts nor breaks the streak. **All your goals** lists each goal
  with its rule and whether it counts today.
- **To-dos belong to slots, not goals.** A goal only counts time. Small tasks are added to a slot with the **To-do** button on Home
  (see "Take a break, and to-do lists").

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
- **Other options** > **Other plan**, which is the old **Change** (a new name for today);
- **Other options** > **Take it off for today**.

Everything is limited to the slot that is starting: putting a start off, carrying the last slot on, resting or extending can
use the time of this slot but never moves the slot after it. The buttons that do not fit (for example when the slot is only 5
minutes long) are not shown.

If you do not answer, the alarm stops by itself after 5 minutes, is logged as "no answer" and
leaves a notification. Your schedule is not changed.

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

Make one slot the **start-of-day alarm** from its bell: **Make this the start-of-day alarm**. There can be only one, it has to
ring or vibrate, and it has to be the first alarm of the day, so the slots before it must be set to **No notification** first (the app tells
you if it cannot). It then works like this:

- It **keeps ringing until you start the day or put it off**, not for 5 minutes: up to half an hour, then it stops and leaves a
  notification (your schedule is not changed). The other alarms behave as before.
- **Start the day** begins the first slot. **Start with a delay of** 5 minutes (− and + change it by 5) puts the start off and asks again.
- **Before it can be put off, a line is shown**: a line that comes with the app, in your language (a different one each day), or
  your own line from **Settings > Alarms > Your own line to read**.
- **Challenge before putting it off** (Settings > Alarms): none, three quick sums, or a sentence to type. The challenge only
  guards the delay; starting the day is always one tap.

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
   notification, but the question does not open by itself. **If the screen was late:** Android only lets
   an alarm open a full screen by itself while the phone is locked or the screen is off; while you are using
   another app it shows a pop-up instead (and some phones hide pop-ups). So the question is brought up several
   ways: the full-screen notification, an immediate try to open it, again each time the screen turns on or the
   phone is unlocked while it rings, and the moment you come back to the home screen or the dashboard. The
   checklist's **Alarm pop-up allowed** line catches the case where the alarm notification was turned down to
   a quiet one in Android's settings.
4. **Phone makers.** Xiaomi, Oppo, Realme, Vivo, OnePlus, Huawei, Samsung and others stop apps in
   the background with their own settings (often called Auto-start, App launch, Battery saver or
   Protected apps). The **Allow background activity** button opens the right page on the common
   ones, or the app's settings if it cannot. Allow auto-start and "no restrictions" for
   Dinacharya there. Android itself cannot make an app ring when the maker's software has
   force-stopped it.
   The one-tap "allow background" pop-up is deliberately not used: Google Play only accepts it for a few kinds
   of app (chat, safety, automation), not alarms.
5. If the phone never delivered an alarm, the Home card says so, and **Settings > Help > Copy problem
   report** includes when the last alarm was set, fired and rang, which tells us where it stopped.

## Performance, reports and PDF

Open **Insights > Performance and reports**.

- **Today:** the report so far.
- **Week:** score trend, phone time per day, best and weakest slots and suggestions.
- **Days:** every saved day. Tap one to read the full report.

When a schedule day ends (from one wake-up to the next), its report is saved on the phone. It
shows each slot as kept, changed, taken off, no answer or not tracked; phone use by hour laid
over your plan; the top 5 apps; phone use inside task slots and in the hour before bed; sleep
planned against actual; planned against kept time; and suggestions.

**Score (0 to 100)** = Plan kept 40% + Focus 35% + Sleep 25%.

- Plan kept: kept counts 1, changed counts half, taken off or no answer count 0.
- Focus: how little of your task time went on the phone (35% of task time on the phone scores 0).
  Needs at least 10 minutes of task time.
- Sleep: half for going to bed near the planned time, half for how much you slept.

A part that cannot be measured (alarms off, no usage access, bedtime not answered) is left out
and the other weights are scaled up, so it is never counted as a zero.

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
   - Package name: `app.dinacharya.wallpaper`
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
on the phone and anything the person typed (slot titles, notes, group names) are never translated. "Developed by ASG" stays in English.

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

**Free:** the home screen, the tour, one routine with every slot feature, alarms,
bedtime flow, today's report and the **last 3 saved days** (with PDF), existing goals, backup and restore, the default
Teal colours, short pauses for up to **2 apps** and one lock for up to **2 apps**. The free version shows one small banner ad at the bottom of the dashboard's
**Home** and **Insights** tabs and of the report screen. There are never ads on the home screen, the all-apps list,
an alarm, the pause and lock screens or in a PDF.

**Pro (one-time purchase through Google Play, no subscription; Rs 99 as set in Play Console):** removes the ads, and unlocks
every saved day in the report and the **week** view (older days and the week tab show a "See Pro" button for free users),
**locked groups** (many groups, many apps each, strict locks), and
creating more routines (weekday and weekend), adding goals and type totals, the other colour
themes, and the full focus rules (strict pauses, daily limits, pauses for any number of apps, playful pauses). If someone had a second routine, goals or a colour before, they keep them: nothing already
made is taken away or locked.

**Try Pro items for a while, for short videos: 1 video = 1 item for a day, 2 videos = the same item for 3 days, 3 videos
= the whole group for a day.** Anyone who has not bought Pro can choose to watch rewarded videos (Google AdMob) and have
Pro items open. There are 14 items in 6 groups, and the person picks how many videos to watch: **1 video** opens exactly
the one item that was asked for, for **24 hours**; **2 videos** open the same item for **3 days**; **3 videos** open **every
item of the group it is in** for 24 hours (all four pauses, all three locks, both report views or all three colour looks;
Routines and Goals have no group, so they offer only 1 and 2). Never the whole app. The items: *Performance* (every saved
day; the week view), *Pauses* (pauses for more than two apps; strict pauses; daily limits; playful pauses), *Locks* (more
than one lock; more than two apps in a lock; strict locks), *Routines*, *Goals*, and each colour look (*Sunset*, *Ocean*,
*Graphite*). The rule classes take a mask with one bit per item (`DayPass.has(mask, item)`), so a strict-pauses video
makes pauses strict and does nothing else. It is offered, never forced, in three places: the **"Watch videos"** choice in
the "Part of Pro" message that appears wherever a Pro item is tapped, under the locked Week tab and older days in
Performance and in the pause and lock sections of the home settings (one button per item), and the **"Try it first"** list
on the Pro screen. Fair play: only a video watched to the end counts; between videos a message says what the next one adds
and offers **Stop here**, which keeps what the videos so far earned (one video = the item for a day, two = three days); a
later video that cannot be found never costs the earlier ones; while a one-item day pass runs, **"Keep it for 3 days: watch 1
more video"** stretches it. At most 12 videos a day are offered (`AdLayers.DAILY_CAP`). Every pass is its own entry with its own timer (`PassLedger`): a video for strict pauses opens strict pauses and nothing else, and
needing two things (strict pauses on a third app, say) means one video for each; neither opens the other, and a reward is
counted only when an ad was really shown and finished. The offer disappears when Pro is bought or ads are off in the build (`ads_enabled`).
Having Pro (the purchase) opens all 14.

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

**When the time is up (a day, or three), the pass's changes go back to the free version.** The pass is a trial, so what was made
with it does not stay. When a pass starts, the phone takes a note of what existed for that feature (`PassReset.capture`);
when the pass has ended, whatever was added or switched on with it is removed or set back, and everything that was there
before stays as it was. A pass for a whole group is put back item by item, in the same order, with the same rules as a pass for the single item. Each item puts back only its own part: *more paused apps*: rules beyond the free two apps are
removed; *strict pauses*: a pause that was made strict goes back to the short pause; *daily limits*: a limit set goes back
to none (or to the older limit); *playful pauses*: the kind goes back to the plain wait; *more locks*: extra locks are
removed; *more apps in a lock*: a lock holds two apps again (or the number it held before); *strict locks*: a lock that
was made strict stops being strict; *Routines*: the weekday, weekend and other routines made are removed (those days use
the main routine again); *Goals*: the goals added are removed; *a colour look*: that look goes back to the earlier one;
*saved days, week view*: nothing is stored, so nothing needs to go back. Choices the free version allows are never touched, the main
routine is never touched, and having Pro (bought before the day ends, too) keeps everything. A message on the dashboard
says what went back ("Your video pass has ended ..."), and the Pro prompt, the Pro screen, the tour and the home settings say
beforehand that this will happen. The reset runs at the moment the day ends (a system alarm, `PassSettle`), after a restart,
and whenever the dashboard, the home screen, its settings or the wallpaper starts, so it does not depend on the app being
open; restoring a backup during a pass makes the restored settings the "before", so a restore is never undone by a pass. The
rules are in `PassReset.java` and `DayPass.java` (tested); the record and the note are kept on the phone next to the Pro
record, outside any backup, and a pass that is longer than three days or has not started yet counts for nothing, so setting
the clock back does not give a longer pass. (A pass that was running when the app was updated from 2.6 is still understood.) The video needs a network connection and the consent form answered (a "No video right now"
message says so otherwise, and **Settings > Copy problem report** shows the last video result). Change
`DayPass.LENGTH_MS` (a day) and `DayPass.MAX_MS` (three days) for different lengths.

- **See Pro** (Settings > Dinacharya Pro, and every "See Pro" button in the app: the pause and lock screens in
  the home settings, the report's older days and Week tab, the Pro prompts) opens one screen of its own with a
  Free against Pro table, the price, **Get Pro** and **Restore my purchase** (for a new phone, a reinstall, or a
  purchase made on another phone with the same Google account). The price shown is whatever Google Play reports
  for `pro_unlock`; the app does not hard-code it, so set it to **99** in Play Console (see "One-time Pro purchase").
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
   folder. The policy will be at `https://YOURNAME.github.io/REPOSITORY/privacy.html`. Open
   `docs/privacy.html` first and put your real contact email in place of `your-email@example.com`.
7. **Upload and test.** **Testing > Closed testing > Create track/release**, upload the `.aab`, accept
   **Play App Signing**, add your testers' Gmail addresses (or a Google Group), and send them the
   opt-in link. They must stay opted in for 14 days. The same `.aab` can go to **Internal testing**
   first (up to 100 testers, no wait) to try Pro, ads and sign-in on a phone.
8. **Make Google sign-in work for the Play build.** Google re-signs the app, so the Play version has a
   **different fingerprint** from the test APK. In Play Console open **Test and release > App
   integrity > App signing** and copy the **SHA-1 of the app signing key certificate** (also
   the SHA-1 of the upload key, for builds you install yourself from the bundle). In Google Cloud,
   open **Google Auth Platform > Clients > Create client > Android**, with package
   `app.dinacharya.wallpaper` and that SHA-1. Make one client for each SHA-1; keep the one for
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

### One-time Pro purchase

1. Upload your first build (step 7 above), at least to **Internal testing**. Google only lets you
   create products for an app that already has a build with the Billing permission in it.
2. **Monetize with Play > Products > In-app products > Create product.** Product ID exactly
   `pro_unlock`. Name "Dinacharya Pro", a description, the price **Rs 99** (India; Play Console converts it
   for other countries, and you can edit each country's price), status **Active**. (Details in
   `store/data-safety-and-declarations.md`, section 8.)
3. Finish the **payments profile** (Play Console > Setup > Payments profile) and your **tax**
   information, or the purchase will not work in production.
4. **Test it without paying:** **Settings > License testing**, add your own Gmail and your testers'.
   They get test purchases that charge nothing. Install the app from the testing track (the
   purchase only works for an app installed from Google Play).

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

**1.0 (first release on Google Play)**
- The app is named **Dinacharya Launcher** and is credited "Developed by ASG" (home screen mark, Settings > About, store text, privacy page). The version shown in Settings > About is **1.0**. Google Play's internal build number (`versionCode`) stays at 17 because Google only accepts a number higher than every one it has seen; users never see it. The notes below, from 2.2 to 3.0.1, are the private test builds that came before.

**2.2**
- **Dinacharya is now a home screen (launcher), not a wallpaper app.** No apps on it: the whole screen is your
  day (the time, the date and every slot, the current one highlighted), shown by the Dinacharya live wallpaper that
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
  pauses are Pro. Pro is a one-time Rs 99 (set in Play Console).
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
- **Strong lock (optional).** Locks and pauses now also hold an app opened from the recent-apps screen, a notification, a widget
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
- **Dinacharya Pro** (one-time purchase through Google Play) and small ads with a consent form.
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
| Display over other apps (you grant it in Settings; only for the optional Strong lock) | Show your own lock or pause screen in front of an app you locked or paused |
| Foreground service (special use; only while the Strong lock is on) | Keep watching which app is in front so a lock or pause holds wherever the app was opened from |
| Run at start-up | Set the alarms and the backup schedule again after a restart |
| Internet, network state | Google sign-in, the Drive backup, ads, and checking Pro with Google Play |
| Google Play billing | The one-time Pro purchase |
| Advertising ID (added by the ads library) | Ads in the free version |
| Google account (when you sign in) | Your name and email, and the app's own private space in your Drive. The app cannot see your other Drive files. |

Android 8.0 or newer.
