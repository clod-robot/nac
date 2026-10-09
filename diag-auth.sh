#!/bin/bash
echo "===== nac-auth processes ====="
pgrep -af "app/nac-auth" || echo "(none)"
echo "===== 1812 / 9001 ====="
ss -tulnp | grep -E ":1812|:9001" || echo "(no ports)"
echo "===== quiet log tail ====="
tail -30 ~/nac/logs/nac-auth-quiet.log 2>/dev/null
echo "===== grep bind/error ====="
grep -iE "bind|already in use|error|exception|failed" ~/nac/logs/nac-auth-quiet.log | tail -15
