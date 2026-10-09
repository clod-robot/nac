#!/bin/bash
LOG=~/nac/logs/nac-auth.out
echo "===== warn count by minute (last 15 active) ====="
grep "降级容忍" "$LOG" | awk '{print substr($1,1,16)}' | sort | uniq -c | tail -15
echo "===== warn total / first / last ====="
grep -c "降级容忍" "$LOG"
grep "降级容忍" "$LOG" | head -1 | cut -c1-80
grep "降级容忍" "$LOG" | tail -1 | cut -c1-80
echo "===== redis config ====="
redis-cli -a MAO5201314sm --no-auth-warning CONFIG GET timeout
redis-cli -a MAO5201314sm --no-auth-warning CONFIG GET maxclients
redis-cli -a MAO5201314sm --no-auth-warning CONFIG GET tcp-backlog
redis-cli -a MAO5201314sm --no-auth-warning CONFIG GET tcp-keepalive
echo "===== listen overflow / resets ====="
netstat -s | grep -iE "overflow|dropped|reset|failed connection" | head -10
echo "===== redis connected clients now ====="
redis-cli -a MAO5201314sm --no-auth-warning INFO clients
echo "===== somaxconn ====="
cat /proc/sys/net/core/somaxconn
