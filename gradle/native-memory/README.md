# Native rendering resource regression

Run sequentially (ForgeGradle launch metadata is shared):

```powershell
./gradlew.bat -I gradle/native-memory-tests.gradle runClient --offline --max-workers=1
./gradlew.bat build --offline --max-workers=1
```

Search the client log for `NATIVE_MEMORY_CLIENT_PASS` / `NATIVE_MEMORY_CLIENT_FAIL`.
The harness stops Minecraft even on an assertion failure, so Gradle's exit code
alone is insufficient. It uses a separate game directory under `build/` and never
opens a player save. Test classes are excluded from a normal release build.

With a real GL context, each of the two persistent Old One buffer sources uploads
4,096 vertices and flushes 501 times. After warmup it checks source identity,
native buffer address and capacity on every iteration. This verifies reuse of
native storage, not a whole-process allocation rate or absence of driver leaks.
The check also creates and retires both effect target pairs, checks texture/FBO
deletion and repeated cleanup, exercises invalid target sizing, and initializes
both post chains before checking repeated logout cleanup.

The buffers deliberately retain their maximum capacity for reuse during the
client process. `BufferBuilder.clear()` does not free malloc storage; recreating
these builders when leaving a world would reintroduce the leak. No particle
count, geometry, attack timing or blend policy changes are made.

Full modpack Old One versus Sepulcher reproduction and native/private-commit
measurements across an entire fight remain a separate acceptance step.

2026-11-08 result: the real-context check printed `NATIVE_MEMORY_CLIENT_PASS`.
Both sources retained the same native address/capacity after warmup over 501
uploads each (2,098,688 bytes each). GL target retirement, invalid-size handling
and repeated post-chain logout cleanup passed. See
`build/native-memory-client.log`; release validation is logged separately in
`build/native-memory-build.log`.
