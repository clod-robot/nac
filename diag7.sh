#!/bin/bash
echo "=== peap5.err ==="
cat /tmp/peap5.err 2>/dev/null | tail -5
echo "=== datasource config ==="
grep -rE 'datasource|jdbc|username|password' /home/mao/nac/app/config/*.yml 2>/dev/null | grep -vi redis | head -10
grep -rE 'url:|username:|password:' /home/mao/nac/app/*.yml /home/mao/nac/app/config/*.yml 2>/dev/null | head -10
echo "=== sudo test ==="
echo MAO5201314sm | sudo -S -v 2>&1 | tail -2 && echo SUDO-OK
