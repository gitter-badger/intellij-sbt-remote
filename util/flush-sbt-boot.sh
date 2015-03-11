#!/usr/bin/env sh

pushd $HOME/.sbt/boot 2>/dev/null
    find . -name "com.typesafe.sbtrc" | xargs rm -rfv
popd 2>/dev/null
