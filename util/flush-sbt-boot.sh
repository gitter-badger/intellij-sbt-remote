#!/usr/bin/env bash

pushd $HOME/.sbt/boot 2>/dev/null 1>/dev/null
    find . -name "com.typesafe.sbtrc" | xargs rm -rfv
popd 2>/dev/null 1>/dev/null
