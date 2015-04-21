#!/usr/bin/env bash

PIDS=`ps aux | awk '/launchers\/sbt-launch/{ print $2 }'`
echo $PIDS
kill -9 $PIDS 2>/dev/null
