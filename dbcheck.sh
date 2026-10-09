#!/bin/bash
mysql -uroot -pMAO5201314sm -h127.0.0.1 -N -e "SHOW DATABASES LIKE 'nac_db';" 2>/dev/null
mysql -uroot -pMAO5201314sm -h127.0.0.1 nac_db -N -e "SELECT username,password,auth_type,enabled FROM sys_radius_user; SELECT CONCAT('sys_user rows=',COUNT(*)) FROM sys_user;" 2>/dev/null
