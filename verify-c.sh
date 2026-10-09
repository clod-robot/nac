#!/bin/bash
echo '=== capture status ==='
cat /tmp/cap3b.out 2>/dev/null
ls -la /tmp/rad3.pcap 2>/dev/null
echo MAO5201314sm | sudo -S tcpdump -nn -tttt -r /tmp/rad3.pcap 2>/dev/null | tail -8
echo '=== server log 10:22-10:28 ==='
grep -E '10:2[2-8]' /home/mao/nac/logs/nac-auth.out | grep -vE 'kafka|Kafka|ProducerConfig|Metadata|AppInfoParser' | tail -20
