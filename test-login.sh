#!/bin/bash
curl -s -m 5 -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"wangfang","password":"Wf$secure99"}'
echo
