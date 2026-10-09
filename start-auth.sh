#!/bin/bash
cd /home/mao/nac
bash nac.sh start nac-auth
sleep 10
echo '=== process ==='
ps -eo pid,lstart,cmd | grep 'app/nac-auth' | grep -v grep
echo '=== listen 1812/808x ==='
ss -tulnp 2>/dev/null | grep -E ':1812|:1813|:808' | head
echo '=== last 40 log lines ==='
tail -40 logs/nac-auth.out
