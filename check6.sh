#!/bin/bash
echo "=== NOW: $(date '+%F %T') ==="
ls -la /tmp/peap5.pcap /tmp/cap5.out 2>/dev/null
echo "=== cap5.out ==="
cat /tmp/cap5.out 2>/dev/null | tail -3
echo "=== auth log tail 25 ==="
tail -25 /home/mao/nac/logs/nac-auth.out
echo "=== session cols ==="
mysql -uroot -pNceRadius@2026 -D nac -e "SHOW COLUMNS FROM sys_radius_session" 2>&1 | awk '{print $1}' | tr '\n' ' '
echo
echo "=== latest sessions ==="
mysql -uroot -pNceRadius@2026 -D nac -e "SELECT * FROM sys_radius_session ORDER BY id DESC LIMIT 2\G" 2>&1 | head -40
