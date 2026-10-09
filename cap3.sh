#!/bin/bash
echo MAO5201314sm | sudo -S timeout 85 tcpdump -ni enp3s0 -w /tmp/rad3.pcap 'udp port 1812' 2>/tmp/rad3.err
echo "tcpdump exit=$?"
ls -la /tmp/rad3.pcap
