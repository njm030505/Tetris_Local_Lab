# P2P lobby chat

Implements the challenge on page 12 of Requirements 3: exchange simple messages while connected and waiting in the P2P lobby. Computer opponents and multiplayer are separate future work.

The existing connection carries a bounded CHAT message; no second socket or external service is used. Protocol version is now 2, so both players must update. Sender identity comes from the connected peer role. Blank, multiline, control-character and oversized messages are rejected. Each connection keeps its latest 200 messages. Matches do not erase chat; closing the connection starts a new history.

The lobby provides a read-only history, input field and Send button; Enter also sends. Input is enabled only in LOBBY or RESULT. The existing Swing refresh loop reads immutable history on the EDT. A message already in transit when a match begins is ignored during play.

## Verification, 2026-10-28

`test-local.ps1 -VerifyCoverage`: 166 discovered, 162 executed and passed, 4 actual TCP cases unstarted because the execution sandbox denies socket connections. All-authored LINE coverage 2268/2913 = 77.86%, above the 70% gate.

New tests exercise Korean/emoji exchange in both directions, sender identity, immutable history, size/content validation, bounded history, availability after results, retention across rematch, and actual Swing Enter-to-network/history behavior with gameplay disabling the input. Integration uses the real codec and background transport threads over piped streams; actual two-PC chat still needs a manual check.

The updated JAR was copied into the existing EXE package's app directory and verified to match the build JAR by SHA-256. The user reports existing P2P movement and pause work; this does not itself certify chat or the 200 ms latency requirement.

## Git delivery

Configured origin: `https://github.com/njm030505/Tetris_Local_Lab.git`. This session could not create the challenge branch because its `.git` directory is not writable. The connected GitHub app also returned 404 for that repository. No upstream team repository or Notion changes were made.
