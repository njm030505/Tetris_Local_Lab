# Phase 3 network/runtime implementation report

Implemented `phase3.runtime.MatchRuntime`, `phase3.net.PeerSession`, and internal `WireCodec` in the standalone local project. The original repository and external services were not changed.

## Runtime and transport

`MatchRuntime` owns all ongoing `MatchSession` mutations on one daemon scheduled thread at 16 ms intervals. It consumes a bounded 256-command queue, publishes immutable snapshots through a volatile reference and optional callback, supports idempotent start/close, and freezes simulation time during pause. User and network threads enqueue actions; socket reading and writing run on their own daemon threads.

The host binds the configured port (including 0 for an ephemeral test port), accepts one opponent, and owns both player engines. The connect factory budgets 3000 ms across bounded background DNS resolution and TCP connect. After creation, UI methods enqueue commands and return. Host configuration is authoritative and invalidates both ready flags. A configuration revision and match ID protect ready/input messages from prior configurations or matches. Both ready flags are required to start. Results remain available until both players ready again; the same transport is reused with a new match ID. Either player can pause the complete match.

The binary protocol uses `DataInputStream`/`DataOutputStream`, version 1, a maximum 32768-byte frame, message type, monotonic sequence and match ID. It never deserializes Java objects. Snapshot decoding bounds board dimensions to 23x10, piece dimensions, encoded cell values, pending garbage (maximum 10 rows), clearing rows, enums, scores, timing and configuration. Snapshots include both boards, current/next encoded pieces, score/level/lines/drop speed/game-over, pending rows, configuration, pause, remaining time, result and tick. Constructor/accessor copying remains in the core snapshot records.

Each direction has a bounded 128-entry reliable control queue and a separate single newest-snapshot slot. Intermediate snapshots coalesce. Control saturation disconnects with a reason instead of silently dropping controls. Heartbeats run every 500 ms independently of simulation; traffic silence produces a warning after 1500 ms and disconnect after 5000 ms. EOF, malformed frames and sequence replay disconnect immediately. Close stops runtime/monitor and closes sockets to release blocking I/O.

## Verification evidence

- Initial red check: `PeerSessionTest` did not compile because `PeerSession` was absent.
- `WireCodecTest`: 3 tests passed. Deep snapshot round trip (including garbage, encoded bonus/weight pieces, clearing/pending rows), immutable access, bounds/version/type/sequence/truncation/trailing-data rejection, bad dimensions/cells/configuration.
- `MatchRuntimeTest`: 2 tests passed. Queued mutation, callback thread, idempotent start/close, pause/time freeze, input validation, bounded burst handling.
- `PeerSessionMemoryTest`: 4 tests passed using Java piped byte streams and a test-only `Socket` adapter. These exercise the actual background framing/read/write threads: ready/config invalidation, swapped local/remote readiness, both players' input, pause with live heartbeat, result/rematch, stale match input, replay rejection, malformed frame disconnect, warning/timeout and cleanup.
- `PeerSessionTest`: the class is explicitly aborted at its capability probe in this sandbox. Actual TCP connect fails with `java.net.SocketException: Permission denied: connect/getsockopt`. Only this exact permission condition aborts; ordinary network/protocol failures still fail tests. The existing behavioral tests were retained unchanged.

The pipe tests verify protocol/state behavior, not TCP connectivity or physical LAN latency. Run `./test-local.ps1` from a normal Windows shell outside the restricted execution environment to execute the four real loopback tests. A two-PC LAN test is still required to verify the <=200 ms input-to-display target and physical network behavior. No such certification is claimed here.
