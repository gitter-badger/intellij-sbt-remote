# intellij-sbt-remote

[![Join the chat at https://gitter.im/dancingrobot84/intellij-sbt-remote](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/dancingrobot84/intellij-sbt-remote?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

SBT support plugin for Intellij IDEA via sbt-remote-control. WIP.

## Installation

Currently, there is no public release available. If you're eager to try you
could build it yourself.

## Building

### Building patched sbt-remote-control

1. Clone `dancingrobot84/sbt-remote-control`
2. Switch to `hack/bundled-server` branch
3. Do `publishLocal` in SBT repl

### Building plugin

1. Clone repo, run SBT REPL
2. `updateIdea`
3. `packagePlugin`
4. Run `util/flush-sbt-boot.sh` script to remove any previous versions
   of sbt-remote-control from SBT boot cache

After steps above are done you will have `intellij-sbt-remote-plugin.zip` in
`target` directory. Install it using IDEA's "Install plugin from disk..."
feature.

Alternatively you can import this project into IDEA itself and run in debug mode
using predefined run configurations. You still need to run `updateIdea` in REPL
though.

