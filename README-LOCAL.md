# TFSE Phase 3 - standalone local project

This directory has no .git directory. It is an isolated copy of upstream main79bc704 with phase3 additions. Original repository, GitHub and Notion were not modified. AI/CNN/chat/multiplayer challenges are intentionally not implemented; InputAgent is the future extension contract.

## Run

Double-click Run-Tetris.bat. If the runtime package exists, this launches build/TFSE-Phase3-Local/TFSE-Phase3-Local.exe. Keep its app/ and runtime/ directories with the EXE. Otherwise the batch uses Java21 and run-local.ps1. Source projects open as Gradle in IntelliJ; main class phase3.ui.LocalMain.

The files currently live inside Windows Temp; copy this entire project to a permanent directory before cleanup. There is no upload, sync, branch or remote association. Default standalone data is stored next to its JAR in data/. run-local.ps1 explicitly uses this project's data/. All tests use build/test-data, never original settings/scores.

## Play

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
