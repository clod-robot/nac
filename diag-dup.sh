#!/bin/bash
echo '===== nac-auth procs ====='
ps -ef | grep nac-auth | grep -v grep
echo '===== UDP listeners 1812/1813/3799 ====='
echo 'MAO5201314sm' | sudo -S -p '' ss -lunp | grep -E '1812|1813|3799'
echo '===== PID files ====='
ls -la /home/mao/nac/app/pids/ 2>/dev/null
echo '===== cmdlines ====='
for p in $(pgrep -f nac-auth); do
  echo "PID=$p"
  tr '\0' ' ' < /proc/$p/cmdline
  echo
done
echo '===== recent nac-auth log (tail 60) ====='
ls -t /home/mao/nac/app/logs/nac-auth*.log 2>/dev/null | head -1
tail -n 60 "$(ls -t /home/mao/nac/app/logs/nac-auth*.log 2>/dev/null | head -1)" 2>/dev/null
