#!/bin/bash
set -e
HTML=/usr/share/nginx/html
cp -a "$HTML/nce" "$HTML/nce.bak-0917-oplog"
rm -rf "$HTML/nce"/*
cd "$HTML/nce"
unzip -oq /tmp/dist.zip
echo "--- deployed files ---"
ls -la "$HTML/nce/" | head
stat -c '%y %n' "$HTML/index.html" 2>/dev/null || true
stat -c '%y %n' "$HTML/nce/index.html"
