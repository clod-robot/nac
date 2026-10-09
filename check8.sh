#!/bin/bash
echo "=== log 13:54:40 - 13:57 ==="
awk '/2026-09-22 13:5[4-7]/' /home/mao/nac/logs/nac-auth.out | grep -avE 'kafka|Kafka|nacos|Nacos|monitor|Monitor' | head -50
echo "=== accounting lines ==="
grep -aE 'Accounting|acct|计费' /home/mao/nac/logs/nac-auth.out | tail -10
echo "=== db creds ==="
ls /home/mao/nac/app/ | head -20
grep -aE 'datasource|jdbc:mysql' /home/mao/nac/nac.sh 2>/dev/null | head -5
find /home/mao/nac -maxdepth 3 -name '*.yml' 2>/dev/null | head -10
