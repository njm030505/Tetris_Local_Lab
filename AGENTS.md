# Repository instructions

- This is the user's personal Tetris_Local_Lab repository. Do not push changes to the upstream team repository.
- Work directly on the current default branch (`master`) and push completed, verified work. The user does not require PRs or separate feature branches. Do not rename the default branch unless requested.
- Make one commit per logical change. Use `type : purpose (ID)` with `feat`, `fix`, `docs`, `refactor`, `test`, or `chore`. Use a real applicable requirement or issue ID; do not invent one. If no ID applies, omit the suffix.
- Keep source, resources, tests, documentation, build/run scripts, Gradle Wrapper, and the three offline JUnit/JaCoCo JARs in tools/lib tracked.
- Do not track IDE settings, .iml files, local player data, temporary analysis/downloads, caches, compiled files, reports, or packaged executables.
- Verify Java changes with `test-local.ps1 -VerifyCoverage`. Run real TCP tests with `-RequireNetwork` when the environment permits sockets; report any unexecuted tests accurately.
