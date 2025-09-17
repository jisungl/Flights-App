#!/bin/bash

export MYPORT=`id -u`
export PS1='pgtunnel> '
ssh -o StrictHostKeyChecking=accept-new -N -L 127.0.0.1:$MYPORT:/var/run/postgresql/.s.PGSQL.5432 aziak &
MY_PID=$!
bash
kill -9 $MY_PID
