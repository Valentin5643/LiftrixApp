#!/bin/bash
MINUTES=${1:-3}
NOTE=${2:-"Continue debugging"}
TARGET=${3:-"liftrix:0"}
echo "⏰ Scheduling: $NOTE (in $MINUTES min)"
SECONDS=$((MINUTES * 60))
(sleep $SECONDS && {
    tmux send-keys -t "$TARGET" "/debug Continue Kotlin compiler debugging. After this, schedule next check: ./schedule_with_note.sh 3 \"Keep debugging Kotlin compiler until build succeeds\""
    sleep 1
    tmux send-keys -t "$TARGET" Enter
}) &
echo "✅ Scheduled for $TARGET in $MINUTES minutes"
