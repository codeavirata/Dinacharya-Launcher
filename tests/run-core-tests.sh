#!/bin/bash
# Compiles the app's pure-Java core (the parts that use no Android classes: schedule engine, alarm timeline, reports,
# backup, home-screen rules, pause challenges, locked groups, snooze cost) and runs the tests. Needs only a JDK (17 or newer).
# Usage: ./tests/run-core-tests.sh
set -e
cd "$(dirname "$0")"
SRC=../app/src/main/java/app/dinacharya/wallpaper
OUT="$(mktemp -d)"
CORE="Routine TypeStats Tokens DeviceAuth CloudDrive BackupArchive BackupPolicy CloudBackup SlotOps SwipeTracker TabSwipe DragReorder \
Overrides Plan AlarmState AlarmPlanner Ev UsageDetail DayReport ReportAssembler Analyzer WeekStats Suggestions HistoryStore \
Http MiniJson SubColors Allotment SlotGaps SlotPlay Todos PosterExtras WakeQuotes PauseChallenge SnoozeCost LauncherConfig AppKey AppEntry AppSearch FocusRules LockRules Limits DayPass PassLedger FirstRun PassReset HomeWatch GuardRules FrontTracker AlarmSurface FeaturePlan GuardLoop AdLayers Translator Tr DayOff LibraryMode LivePlan EarlyFinish ShakeCounter LockMode SlotUse SlotReport OneOpen SlotTodo VideoProgress GoalSlots PlanTable AlarmWatch WallpaperWatch QuietRules NoResponse StaleTimers SlotAdjust FinishChoices MissedSlots ReplaceFromNow NoticeButtons RatePrompt InviteClient InviteDays InviteActive RoutineTemplates RoutineShare DayCardLayout RoutineLock RoutineDiff FirstDay ReferrerCode"
FILES=""
for f in $CORE; do FILES="$FILES $SRC/$f.java"; done
javac -encoding UTF-8 -nowarn -d "$OUT" $FILES
javac -encoding UTF-8 -nowarn -cp "$OUT" -d "$OUT" app/dinacharya/wallpaper/*.java
FAILED=0
for t in TranslatorTest LangFilesTest LauncherTest PassLedgerTest PassResetTest GuardTest AdLayersTest AlarmPlanTest CloudTest GestureTest PlanTest ReportTest SlotOpsTest TypeStatsTest WeekTest TempRoutineTest AlertModeTest TodoGoalTest LockTypeTest TodoRepeatTest LiveDayTest WakeLockTest LockModeTest SlotUseTest SlotReportTest HabitsTest SlotGoalTest PlanScreenTest AlarmWatchTest AlarmChainTest EarlySkipTest AnySlotTest StrictCapTest WakeLineTest WaitChoicesTest QuietRulesTest NoResponseTest StaleTimerTest SlotCascadeTest ChangePlanTest SlotRedesignTest MissedSlotsTest AlarmSurfaceTest FeaturePlanTest ReplaceFromNowTest BreakEndTest NoticeButtonsTest CardAtOnceTest ChangedSlotCardTest PartsTest RateTest InviteTest ShareTest RoutineLockTest RoutineDiffTest FirstDayTest ReferrerCodeTest; do
  echo "== $t"
  if ! java -Dfile.encoding=UTF-8 -cp "$OUT" app.dinacharya.wallpaper.$t | grep -E "FAIL|ALL PASS|FAILS|Exception"; then FAILED=1; fi
done
rm -rf "$OUT"
exit $FAILED
