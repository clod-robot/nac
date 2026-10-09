#!/bin/bash
mysql -uroot -pMAO5201314sm nac_db 2>/dev/null <<'SQL'
select username,enabled,is_verified,vlan_id,session_timeout from sys_radius_user where username='test';
select id,acct_session_id,status,framed_ipaddress,login_time,last_update from sys_radius_session where username='test' order by id desc limit 1;
SQL
