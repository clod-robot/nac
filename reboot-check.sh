#!/bin/bash
echo "===== uptime ====="
uptime
echo "===== last reboots/shutdowns ====="
last -x reboot shutdown 2>/dev/null | head -8
echo "===== previous boot shutdown reason ====="
journalctl -b -1 --no-pager 2>/dev/null | tail -25 | grep -iE 'shutdown|reboot|power|thermal|panic|oom|watchdog|systemd-shutdown' | tail -15
echo "===== temperature (if available) ====="
sensors 2>/dev/null | grep -iE 'core|temp' | head -6 || echo "no sensors"
echo "===== middleware now ====="
ss -tln | grep -E '3306|6379|9092|2181|8848' | awk '{print $4}'
echo "===== free ====="
free -m | head -2
