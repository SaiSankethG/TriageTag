# M4 report: the LLM layer (STOP A done)

4 Nov 2026. M4 steps 0 to 7 of `docs/prompts/m3-m6-build.md` are done, tested and pushed. STOP A is done too: sai chose
Anthropic Claude Haiku 5.5 and set `ANTHROPIC_API_KEY`; the real model is wired and was run on the replay set and on 30 recent
issues. Numbers: **docs/m4-results.md**. For sai to score: **docs/m4-scoring.md**. Every step has a section in
`docs/learning-notes.md`. M5 has not started.

## What each step did

| Step | What it did | Commit |
|---|---|---|
| 0 | Per-repo candidate filter (`triage.candidates.exclude-labels`); replay with and without it. Excluding spring-boot's forward-port clones **lowered** recall@3 (45.6% vs 46.2%), because maintainers sometimes name a clone as the original. Also fixed a missing transaction that made `SET LOCAL` (pgvector iterative scan) do nothing on one search path | `d2c8afd` |
| 0b | sai's decision: spring-boot filter entry removed, unfiltered numbers are the headline again in `docs/m3-results.md` (recall@3 46.2%, threshold 0.825) | `c1aa41b` |
| 1 | `TriageLlm` interface, `FakeTriageLlm`, prompts in `src/main/resources/prompts/<name>-v<N>.txt` | `307d9e2` |
| 2 | Missing-details check: strict JSON (present / missing / not_applicable), one retry, then UNKNOWN | `0b8cf0a` |
| 2b | sai's decision 3: LLM "same root problem?" check of the top match (`DuplicateVerifier`) + `POST /eval/{o}/{r}/duplicate-check` | `33235ea` |
| 3 | "Already fixed?" hint without an LLM: GitHub GraphQL closer + milestone (cached in `issue_fix`, V6) vs the reporter's Spring Boot version from the body | `8230f32` |
| 4 | RAG: spring-boot reference docs at tag v4.1.2 (123 pages → 1,318 chunks in `doc_chunk`, V7, HNSW; 13 s), draft reply citing sources, no draft if nothing scores ≥ 0.55 | `437f4ca` |
| 5 | `triage_report` (V8), `TriageService`, created by the Kafka worker on `opened` (once per issue), `GET`/`POST /reports/{o}/{r}/{n}` | `5aabba1` |
| 6 | Demo page `http://localhost:8080/` (plain JS, text-only rendering) | `36a5d0d` |
| 7 | Daily token budget: `llm_usage` (V9) + `GuardedTriageLlm` decorator; over budget, LLM parts say why and the report notes it | `e8ad2ef` |
| STOP A | `langchain4j-anthropic` (BOM 1.22.0), `LangChain4jTriageLlm`, provider config (`fake` / `anthropic` / `auto`), tests forced to `fake`; draft prompt v2; duplicate-check eval and 30 reports with the real model | the commit that adds this report |

## Tests
**215 tests, 0 failures** (`./mvnw test`), 73 more than at STOP M3 (142). Tests never call the real model:
`src/test/resources/application.properties` sets `triage.llm.provider=fake` even if the key is in the shell.

## Dependencies added
Only `dev.langchain4j:langchain4j-anthropic` (version from the existing LangChain4j BOM, 1.22.0), at STOP A as planned.
No Spring Boot starter; wired by hand in `LlmConfig`.

## How to run with the real model
```bash
./mvnw -q -DskipTests package
```
```bash
scripts/run-with-llm.sh --triage.llm.daily-token-budget=200000
```
The script is an interactive zsh so `ANTHROPIC_API_KEY` from `~/.zshrc` is loaded; it never prints the key.
`./mvnw spring-boot:run` also uses the real model if the key is in the environment (`provider: auto`).

## Open questions (for sai)
1. **Comment mode isn't ready at 0.825.** With the LLM check, false alarms drop from 10.1% to 4.6% of external
   non-duplicates, but duplicates are only ~4% of external issues. So an estimated ~94% of public comments would still not
   point at the marked original (some are unmarked real duplicates; unknown how many). Options for M5: (a) keep shadow-only
   and drop public comments; (b) raise the threshold (e.g. 0.90, re-run `/duplicate-check` with
   `--triage.duplicates.comment-threshold=0.90`, about $0.02); (c) hand-read the 46 LLM-confirmed "false alarms" to see how many are
   real duplicates. I recommend (c) then (b) before building comment mode.
2. **Score docs/m4-scoring.md** (useful / partly / wrong per issue). That decides whether the draft-reply thresholds (docs
   0.55, issues 0.75) and prompts need a v3.
3. **The "already fixed?" hint is Spring Boot-specific** (the reporter-version regexes). Fine for now; another repo would need
   its own patterns (config, or ask the LLM for the version).
4. **Thinking is off** for Haiku 5.5 (cheaper, faster, short tasks). Worth one A/B on the duplicate check with
   `--triage.llm.anthropic.thinking=adaptive` if (1b) is tried.
5. **Docs version**: reference docs are pinned to v4.1.2. A reporter on 3.x gets 4.1 docs. Loading per-version docs is
   possible (doc_version is stored) but not done.
6. From M3, still open: the eval endpoints write files under `docs/` relative to the working directory (turn off or configure
   in the deployed app, M5).
