# intellij-sbt-remote

SBT support plugin for Intellij IDEA via sbt-remote-control. WIP.

## Building

The first two steps are necessary until
[these](https://github.com/sbt/sbt-remote-control/pull/284)
[changes](https://github.com/sbt/sbt-remote-control/pull/286) are not merged.

### Getting fixed sbt-core-next

1. Clone `sbt/sbt-core-next`
2. Change `serializationVersion` in build.sbt to `0.1.1`
3. Do `+publishLocal`

### Getting fixed sbt-remote-control

1. Clone `dancingrobot84/sbt-remote-control`
2. Switch to `wip/dancingrobot84` branch
3. Do `+publishLocal`. Don't worry about `integration-test` project error.

### Building plugin

1. Clone repo, enter SBT REPL
2. `updateIdea`
3. `assembly`

Alternatively you can import this project into IDEA itself and run in debug mode
using predefined run configurations. You still need to run `updateIdea` in REPL
though.

