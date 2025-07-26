#!/bin/bash
MINUTES=${1:-3}
NOTE=${2:-"Continue debugging"}
TARGET=${3:-"liftrix:0"}
SECONDS=$((MINUTES * 60))
(sleep $SECONDS && {
    tmux send-keys -t "$TARGET" "/debug Continue debugging Kotlin compiler. Schedule next: ./schedule_with_note.sh 3"
    sleep 1
    tmux send-keys -t "$TARGET" Enter
    sleep 1
    tmux send-keys -t "$TARGET" Enter  # Double Enter for safety
}) &
echo "⏰ Scheduled: $NOTE in $MINUTES min"
