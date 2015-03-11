#!/usr/bin/env sh

ps aux | grep launchers/sbt-launch | awk '{ print $2 }' | xargs kill -9
