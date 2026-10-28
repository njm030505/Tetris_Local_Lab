# Verification - 2026-10-28

Latest fresh complete command: .\test-local.ps1 -VerifyCoverage. JaCoCo append=false; report and gate include all authored classes without UI exclusions.

163 tests discovered;159 started and passed;0 failed. PeerSessionTest container aborted because actual loopback connect returns java.net.SocketException: Permission denied in this execution sandbox; its4 tests did not start. This is not TCP validation. -RequireNetwork treats permission denial as a failure, for verification in an ordinary Windows shell.

All-authored LINE coverage2224/2869=77.52%;70% gate passed. Baseline probability tests preserve actual weighted generation; additional core tests cover exact attack shape/animation/cap/queue/topout/items/seed/pause/deadline/draw/immutability. Codec3, runtime2 and piped-stream peer integration4 tests cover protocol/state handling, not OS networking. Swing smoke3 tests construct real windows on EDT without showing them, render snapshots in every color/size, exercise independent inputs and preserve legacy settings/leaderboard navigation.

Build JAR succeeds Java21. jpackage app-image generated EXE + runtime + app configuration; its resource editor emitted Windows access-denied warning even though exit code0. Packaged EXE startup and final icon appearance must be checked manually; do not treat exit0 alone as icon certification. The direct JAR launcher remains available through run-local.ps1.

## Remaining external acceptance

- Run .\test-local.ps1 -VerifyCoverage -RequireNetwork in normal Windows shell: all actual TCP tests must execute and pass.
- Two Windows11 PCs same LAN: server IP, host mode/config, ready, both views, simultaneous controls, exact incoming attacks, time limit, pause, result, same-connection rematch.
- Measure input-to-display on both host/client<=200ms; RTT alone is insufficient. Test congestion and deliberate socket silence/disconnect. Current runtime16ms and display16ms are budgets, not measured LAN proof.
- Verify packaged EXE double-click/icon/runtime/settings/score persistence on the target machine. The current sandbox resource-edit warning is open until inspected.
- Measure CPU/RAM/playability on or near required1.2GHz/1GB/500MB environment. Host environment capacity checks do not prove minimum-hardware performance.

Original workspace was read-only: git status remained ?? .idea/; ?? src/menu/; ?? src/storage/, git diff empty. No .git in standalone output and no GitHub/Notion mutation, issue, comment, commit or branch creation.
