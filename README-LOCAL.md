# TFSE Phase 3 - standalone local project

This is a standalone local project based on upstream main79bc704 with phase3 additions. Lobby chat is implemented; computer opponents and multiplayer challenges remain unimplemented. InputAgent is the future computer-player extension contract.

## Run

Double-click Run-Tetris.bat. If the runtime package exists, this launches build/TFSE-Phase3-Local/TFSE-Phase3-Local.exe. Keep its app/ and runtime/ directories with the EXE. Otherwise the batch uses Java21 and run-local.ps1. Source projects open as Gradle in IntelliJ; main class phase3.ui.LocalMain.

Repository: https://github.com/njm030505/Tetris_Local_Lab. Default standalone data is stored next to its JAR in data/. run-local.ps1 explicitly uses this project's data/. All tests use build/test-data, never original settings/scores. Player data, IDE settings, temporary files and generated builds are intentionally not versioned.

## On another Windows PC

1. Install JDK 21 and ensure `java`, `javac` and `jar` are on PATH. Confirm with `java -version` and `javac -version`.
2. Clone this repository (first use), or run `git pull` in an existing clean checkout.
3. In IntelliJ, open the repository folder as a Gradle project, select JDK 21 for both the project and Gradle JVM, and run `phase3.ui.LocalMain`. IntelliJ generates its own .idea files.
4. Alternatively, double-click `Run-Tetris.bat`, or run `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local.ps1` from the repository root. A fresh clone builds and launches the JAR because compiled packages are not in Git.
5. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\test-local.ps1 -VerifyCoverage -RequireNetwork` to verify the build and real loopback network tests. The bundled tools/lib JARs allow this without downloading test dependencies.

Use `run-local.ps1` after source updates so the latest source is always rebuilt. `Run-Tetris.bat` prefers an existing EXE package, which must be rebuilt/refreshed separately after updating source. The optional `package-local.ps1` creates an EXE with a bundled runtime; its build output stays local.

## Play

- P2P lobby chat: after connecting, type a message and press Enter or Send. Korean/emoji messages are supported, with up to 500 Java UTF-16 code units per single-line message and the latest 200 messages kept for that connection. Chat is available before a match and after results, and is disabled during gameplay. Both PCs must run the updated protocol version 2; old builds cannot connect to it. Restart the game after updating.
- Single normal and item modes preserve score/leaderboard and existing settings/accessibility.
- Local versus: choose NORMAL, ITEM or TIMED, shared difficulty, duration10..3600 seconds (default180). P1 A/D/S/W/F; P2 Left/Right/Down/Up/Space. Player-specific controls can be changed and persisted. P pauses the entire match. ESC asks before leaving. Focus loss releases keys.
- P2P: create server on one PC (default TCP24816); enter displayed IPv4 and port on the other. Host applies mode/difficulty/time. Both press ready. Both boards are displayed. On result select lobby/rematch and both ready again on the same connection. On each PC use the PLAYER1 control profile for your own board.
- Successful client address is saved. Traffic silence1500ms shows warning,5000ms ends connection; initial connect timeout3000ms. Windows firewall/private LAN connectivity may need normal user setup. No Internet matchmaking/cloud server is used.
- Versus never calls leaderboard storage. Draw on equal timed score or simultaneous simulation-event loss. Whole-match pause freezes timer/animation; heartbeat continues.

## Exact attack rules

At least2 removed rows send their real pre-clear shape, with just-locked occupied cells made empty. Gray cells have no item effects. Queue top-to-bottom max10; new attacks append below, bottom overflow discarded. After defender clears/effects current piece and before spawning next, apply queue and test displaced top/hidden3 rows/spawn loss. No cancellation or combo attack bonus. L forced row participates; bomb/weight area destruction itself does not produce attack/line score. These item interactions are team-defined choices, not extra official claims.

## Build and verify

    .\build-local.ps1
    .\test-local.ps1 -VerifyCoverage
    .\test-local.ps1 -VerifyCoverage -RequireNetwork
    .\package-local.ps1

Bundled local JUnit/JaCoCo dependencies allow offline compilation/testing. Gradle configuration also targets Java21 and70% all-authored LINE coverage; Gradle may need its normal dependency downloads. Local and Gradle report/gate scope include all authored classes, including UI. Packaging uses jpackage app-image and icon with a runtime; rebuild into a fresh destination if an existing package is present.

## Architecture

- phase3/core: thread-free synthetic-time PlayerEngine and authoritative MatchSession; existing Board/blocks/items/score reused.
- phase3/runtime:16ms single mutation owner, bounded input queue.
- phase3/net: bounded versioned binary TCP codec, host authority, ready/config/result/rematch/heartbeat. No Java object deserialization.
- phase3/ui: snapshot-only board renderer, launcher/lobby, independent control profiles.
- InputAgent.decide(PlayerSnapshot): returns the same GameAction as keyboard/network for future AI. No model dependency.

The legacy game/controller classes remain for compatibility/regression coverage; new launcher and legacy-menu entry points route gameplay to the shared phase3 engine. See docs/PHASE3-DESIGN.md for trade-offs and public contracts.

## Verification limits

See docs/VERIFICATION.md. Loopback socket connection is blocked by the execution sandbox; stream-based protocol integration is tested, actual TCP and two-PC LAN latency are not certified. Run -RequireNetwork in an ordinary Windows shell so denied sockets fail rather than being skipped. Real LAN200ms, minimum1GB/1.2GHz behavior, and Windows packaged EXE/icon acceptance remain physical acceptance checks. This project must not be described as fully evaluated against every requirement until those checks pass.
