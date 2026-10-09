#!/bin/bash
echo '=== packet timing ==='
echo MAO5201314sm | sudo -S tcpdump -nn -tttt -r /tmp/rad2.pcap 2>/dev/null
echo '=== server log 10:03-10:05 ==='
grep -E '10:0[3-5]' /home/mao/nac/logs/nac-auth.out | tail -40
ls -la /tmp/radparse.py 2>/dev/null || echo 'no radparse on server'
