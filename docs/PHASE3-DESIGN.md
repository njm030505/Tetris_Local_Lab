# Phase 3 local implementation design

User-authorized standalone local project based on upstream main 79bc704. No Git repository, branch, commit, push, Notion update, or original-workspace mutation. Only this directory may be changed. Existing single-player features, scoring and item rules remain available. This is a local implementation, not a claim of physical LAN certification.

## Architecture and alternatives
Use a headless PlayerEngine and MatchSession composed from existing Board, Block, GameStateManager, ScoreManager and item implementations. UI and transport consume immutable snapshots and submit GameAction commands. MatchRuntime is the single mutation owner. Preserve old classes for compatibility tests but route new gameplay through the shared engine. Duplicating two GameScreens was rejected because their input/global state and spawn boundaries conflict. A complete rewrite was rejected because it would discard tested block/item/score behavior.

Host-authoritative TCP runs the same MatchSession as local matches. Client sends commands; server sends both snapshots. No cloud service. A future InputAgent consumes observations and returns one GameAction; no AI, CNN, chat or multiplayer challenge implemented now.

## Required behavior
Local two-player and P2P, NORMAL/ITEM/TIMED. Timed defaults to180000ms and host can choose duration before ready. NORMAL and TIMED use normal pieces. Same difficulty on both sides; host selects. Preserve weighted12/10/8 I frequency, other weights10. All five items preserved; item every10 cleared lines via NEXT.

Capture removed row occupancy before compaction; remove just-locked piece occupied coordinates; send exact rows when at least2 removed rows. Rows top-to-bottom, converted to dedicated gray non-item garbage. L forced row counts toward row removal/attacks and preserves existing empty cells. Bomb/weight area effects do not themselves attack or score; naturally full row clears still use normal rules. No attack cancellation, no combo attack multipliers.

Incoming queue max10; append new rows below old, discard bottom overflow. Apply only at defender turn boundary after clear animation/own effects before spawn. Check shifted-out occupied cells, hidden3 rows and spawn collision. Visible20x10. Clear animation300ms advances with simulation time and freezes during pause.

Match results terminal once; ties/simultaneous losses draw. Timed deadline freezes authoritative score, earlier topout ends match. Whole match pause is available to either player, includes timer and animations. Network continues heartbeat while paused. Versus never invokes ScoreStorage/name prompt. Solo retains leaderboard registration. Focus loss releases held keys. Independent player bindings including harddrop, reserved P/Escape match controls; persisted separately from upstream data.

TCP framed bounded versioned messages, no Java object deserialization. Host mode/ready handshake and automatic start only when both ready. Host config changes invalidate ready flags. Connected result returns to lobby and both must ready again. Heartbeat every500ms; warning after1500ms without peer traffic; disconnect after5000ms, connection timeout3000ms. EOF immediate error. Recent successful IP persisted. Server shows usable localIPv4 addresses and port; default24816, configurable. Background socket I/O never EDT/runtime. Stale match commands rejected by matchId. Bounded output coalesces snapshots; reliable control not discarded. LAN target input-to-displayed state <=200ms must be physically verified separately.

## Verification
Preserve upstream tests; add attack shape/queue/order, turn timing/topout, five items, score/difficulty, pause/timed/draw, immutability, codec validation, loopback connect/ready/start/input/snapshot/rematch/pause/disconnect tests. JaCoCo report/gate same scope, all authored classes, minimum70%. Windows EXE with icon/runtime, easy build/test/run scripts, separate data folder. No claims that environment checks prove1GB/1.2GHz or physical LAN latency.

## Public contracts (new package phase3)
core.GameAction enum LEFT, RIGHT, SOFT_DROP, ROTATE, HARD_DROP, WAIT.
core.MatchMode enum NORMAL, ITEM, TIMED.
core.MatchConfig record(MatchMode mode, Difficulty difficulty, long timeLimitMillis, long seed); validate duration10000..3600000 and nonnull values.
core.PieceSnapshot record(int[][] cells,int x,int y) deep copies constructor/accessor; cells contain encoded board appearance, not raw shape marker values.
core.PlayerSnapshot record(int[][] board,PieceSnapshot current,PieceSnapshot next,int score,int level,int lines,int dropMillis,boolean gameOver,List<Integer> clearingRows,List<int[]> pendingRows) deep copies nested data.
core.MatchSnapshot record(long matchId,MatchConfig config,PlayerSnapshot first,PlayerSnapshot second,long remainingMillis,boolean paused,String result,long tick) result empty until terminal then P1/P2/DRAW.
core.MatchSession(MatchConfig config,long matchId,boolean solo); synchronized apply(int player,GameAction); advance(long elapsedMillis); togglePause(); snapshot(). Internal PlayerEngine has no clocks/threads/timers.
core.InputAgent functional interface GameAction decide(PlayerSnapshot observation); extension seam only.
runtime.MatchRuntime wraps MatchSession single-thread scheduled loop, start(), command(int,GameAction), togglePause(), snapshot(), close(); callback optional Consumer<MatchSnapshot>. Network host owns runtime; local UI owns runtime.
net.PeerSession implements AutoCloseable: static host(int port), static connect(String host,int port); setConfig(MatchConfig) host-only; ready(); command(GameAction); togglePause(); snapshot(); view(); close(); host boundPort(). view is immutable PeerView(Role role,State state,boolean localReady,boolean remoteReady,MatchConfig config,String status,long lastReceiveAgeMillis,String remoteAddress), enum states LISTENING,CONNECTING,LOBBY,PLAYING,RESULT,DISCONNECTED,CLOSED; role HOST/CLIENT. Methods thread-safe/nonblocking after creation; connect factory may block so UI uses worker.

Names/changes to these contracts require informing the coordinator before dependent work.
