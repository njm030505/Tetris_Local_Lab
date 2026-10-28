# Phase 3 Implementation Plan

Goal: isolated Java21 local project implementing phase3 with challenge extension seams.
Architecture: PHASE3-DESIGN.md. Execution: native coordinator plus scoped sequential implementers/reviewers; no Git operations.

Global constraints: user original repository and external services read-only; all writes confined to this standalone directory. No challenge implementation. Retain original scoring/items/accessibility.

Review focus: attacks during clear animation; overflow loses instead of disappearing; stale inputs/rematch; disconnect while paused/lobby; conflicting keys and focus loss.

- [ ] Task1: headless core, immutable model, deterministic piece source, attack queue, shared session. Test required behaviors before implementation. Modify Board only inside copy for dedicated garbage and perfect-clear correctness.
- [ ] Task2: runtime owner, TCP codec/peer session/heartbeat/config+ready+rematch, tests against loopback sockets and malformed frames.
- [ ] Task3: Swing menu/local/solo/network lobby and two-board game, input profiles/settings, next/attacks/timer/result, no versus score writes. Preserve upstream settings/scoreboard and solo behavior.
- [ ] Task4: run/test/package scripts, identical70% coverage report/gate, docs/requirements evidence. Build EXE/icon/runtime.
- [ ] Task5: independent review, full regression and automated network tests, visual Windows smoke test, original workspace unchanged verification.

Record actual evidence and unresolved physical-network/minimum-hardware checks in VERIFICATION.md.
