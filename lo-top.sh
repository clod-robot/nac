#!/bin/bash
exec > /tmp/lo-top.txt 2>&1
APID=$(pgrep -f "app/nac-auth" | head -1)
echo "auth=$APID"
for i in 1 2 3; do
  echo "===== sample $i ====="
  date +%H:%M:%S
  top -b -n 1 | head -12 | tail -6
  top -b -n 1 -H -p "$APID" | awk 'NR>=8 && $9>10 {print $1,$9}' | head -10
  sleep 4
done
