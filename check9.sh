#!/bin/bash
echo "=== FULL pcap ==="
python3 /tmp/radpeap.py /tmp/peap5.pcap 2>&1 | head -60
