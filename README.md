# intellij-sbt-remote

SBT support plugin for Intellij IDEA via sbt-remote-control. WIP.

## Building

The first step is necessary until
[this change](https://github.com/sbt/sbt-remote-control/pull/292)
is not merged.

### Getting fixed sbt-remote-control

1. Clone `dancingrobot84/sbt-remote-control`
2. Switch to `fix/enable-UpdateReport-serializer` branch
3. Do `publishLocal` in SBT repl

### Building plugin

1. Clone repo, enter SBT REPL
2. `updateIdea`
3. `packagePlugin`

Alternatively you can import this project into IDEA itself and run in debug mode
using predefined run configurations. You still need to run `updateIdea` in REPL
though.

