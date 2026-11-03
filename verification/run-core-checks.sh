#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
check_output="$(mktemp -d)"
trap 'rm -rf "$check_output"' EXIT
java com.sun.tools.javac.Main -d "$check_output" \
 "$project_root/app/src/main/java/gr/radiorise/player/ReminderTiming.java" \
 "$project_root/app/src/main/java/gr/radiorise/player/LogRedactor.java" \
 "$project_root/verification/ReminderTimingCheck.java" \
 "$project_root/app/src/main/java/gr/radiorise/player/Tr.java" \
 "$project_root/app/src/main/java/gr/radiorise/player/IcsCalendar.java" \
 "$project_root/verification/IcsCalendarCheck.java"
java -cp "$check_output" ReminderTimingCheck

java -cp "$check_output" IcsCalendarCheck
