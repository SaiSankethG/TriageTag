# M3 report: duplicates, labels and the first real numbers

4 Nov 2026. Steps 0 to 7 of `docs/prompts/m3-m6-build.md` (M3) are done, tested and pushed. This is **STOP M3**:
the numbers are below, for sai to review before M4. Every step has a section in `docs/learning-notes.md`.

## The numbers (spring-projects/spring-boot)

| Method | recall@1 | recall@3 | recall@10 | MRR |
|---|---|---|---|---|
| Keyword baseline (Postgres full-text) | 19.2% (160) | 26.5% (221) | 37.1% (309) | 0.244 |
| **Vector (MiniLM + pgvector)** | **31.0% (258)** | **46.2% (385)** | **60.9% (507)** | **0.403** |

Vector search finds the true original in its top 3 **1.74× as often** as keyword search (385 vs 221 of 833).

**False alarms** (vector top-1 score, 1,000 random non-duplicates):

| Population | Threshold for ≤ 10% false alarms | Duplicates flagged | Original top-1 and flagged | Original in top 3 and flagged |
|---|---|---|---|---|
| **External reporters** (what the bot sees) | **0.825** | 162 of 833 | **10.8%** | **13.6%** |
| All issues (team bookkeeping included) | 0.998 | 30 of 833 | 2.5% | 3.0% |

**Labels** (top suggested label vs the real labels, 1,000 most recent issues with a real label; `status:` labels ignored):

| Population | Coverage (a suggestion was made) | Precision (top label right) |
|---|---|---|
| **External reporters** | **30.6%** (306) | **69.3%** (212) |
| All issues | 80.0% (800) | 85.5% (684) |

### What the numbers mean
- **As a private helper, vector search is useful.** For almost half of real duplicates the original is in the top 3 a
  maintainer would see, and for 61% it's in the top 10. That's clearly better than keyword search, which is what
  maintainers use today.
- **As an automatic "this is a duplicate" signal, the score alone is weak.** Many non-duplicates have a similar earlier
  issue. Keeping false alarms at 10% leaves only 13.6% of duplicates with a confident, correct flag. This backs the plan:
  **shadow mode** (a private top 3) by default, with public comments only above a high threshold (M5). M4's LLM step
  (checking a candidate pair) could raise precision.
- **Label suggestions** are right about 7 times in 10 when made, but made for only about 1 in 3 external issues at the 0.5 vote threshold.

## Scored set (exactly what was excluded and why)
| | Count |
|---|---|
| Answer-key pairs (`duplicate_of` filled, M1) | 1,036 |
| Excluded: original isn't a stored issue (most likely a pull request; PRs aren't stored) | 91 |
| Excluded: "original" is newer than the duplicate (maintainers closed the older issue in favour of a newer one) | 109 |
| Excluded: original and duplicate opened in the same second (double submissions: #18716/#18717, #28792/#28793, #36284/#36283) | 3 |
| **Scored** | **833** (the prompt expected ~836; the 3 same-second pairs are the difference) |

720 of the 833 scored duplicates were opened by external reporters.

## Spot check: 20 answer-key pairs from hedged comments
The answer key also uses maintainer comments with hedged wording ("I think this is a duplicate of #N", "This may be a
duplicate of …", "Looks like a duplicate of …").
- **How they were found:** GitHub search for 7 hedged phrases among `status: duplicate` issues gave 86 issues. 57 of those
  have `duplicate_of` filled, and **51** got their answer from the comment (the latest close has no `duplicateOf`).
- **How I judged them:** I read the hedged sentence and both titles for 20 pairs spread evenly over the 51 (ordered by number).

| Verdict | Count | Pairs |
|---|---|---|
| Looks right (clearly the same problem) | **16** | #3833→#2558, #5860→#5127, #7611→#4424, #8572→#7015, #12355→#11338, #13247→#13236, #15479→#14022, #16546→#16309, #21611→#21549, #21767→#21556, #24416→#24203, #28595→#27822, #32042→#31853, #36148→#36147, #40084→#40060, #40764→#38636 |
| Plausible, can't confirm from titles alone | 4 | #18358→#17061, #20614→#20432, #23681→#22840, #24311→#24146 (the maintainer asked "could you confirm") |
| Looks wrong | **0** | |

Keeping hedged comments in the answer key looks safe. (Judged on titles and the maintainer's sentence, not by reading the full threads.)

## What each step did

| Step | What it did | Commit |
|---|---|---|
| 0 | `issues/deleted` and `issues/transferred` remove our row (labels and, since V4, the embedding cascade) | `bcad4bd` |
| 1 | `pgvector/pgvector:pg16-trixie` (same Debian as the old image, so no collation mismatch; data kept: 44,205 issues). Flyway V4: `vector` extension, `issue_embedding(vector(384))`, HNSW cosine index | `3954994` |
| 2 | Keyword baseline: V5 generated `tsvector` (title A + first 2,000 body chars, HTML comments stripped) + GIN; the query issue's lexemes ORed, ranked by `ts_rank`, earlier same-repo issues only | `373575c` |
| 3 | LangChain4j 1.22.0 (BOM) + all-MiniLM-L6-v2 in-process behind our `EmbeddingService`; `POST /embeddings/{o}/{r}` (spring-boot: 43,810 in 333 s); the Kafka worker embeds new and changed issues | `ebb705c` |
| 4 | `VectorDuplicateSearch` (HNSW + iterative scan so filters don't shrink results; ~6 ms) and `GET /repos/{o}/{r}/issues/{n}/similar` | `7d79fc3` |
| 5 | `LabelSuggester`: the 10 nearest earlier issues vote, weighted by similarity; suggest at ≥ 50% of the weight | `3276af4` |
| 6 | `EvalService` + `POST /eval/{o}/{r}` + `docs/m3-results.md` | `7f9035a` |
| 7 | README "Results" section, this report, CLAUDE.md "Current step" | the commit that adds this report |

## Tests
**142 tests, 0 failures** (`./mvnw test`). That's 38 more than the 104 at the end of M3 step 0. New: real-pgvector tests
(schema, keyword search, vector search incl. the forced-HNSW filter case, embeddings, label voting, a small replay), the real
MiniLM model, the metrics maths, and controllers.

## Dependencies added
Only the approved ones: `langchain4j-bom` 1.22.0 (imported, manages versions), `langchain4j-core`,
`langchain4j-embeddings-all-minilm-l6-v2` (brings ONNX Runtime and the HuggingFace tokenizer). I pinned 1.22.0
(7 Oct 2026), not the newest 1.23.0 (2 Nov 2026): a release that has been out a few weeks. No Spring Boot starter.

## Open questions (for sai)
1. **Which population counts for the headline?** I report recall on all 833 scored pairs, and false alarms and labels on
   external reporters, because team bookkeeping issues (6,964 forward-port clones, 12,768 "Upgrade to X") aren't triaged.
   Agree? (Recall restricted to the 720 external duplicates could be added.)
2. **Forward-port clones in the search pool.** A duplicate's original can have a near-identical forward-port clone. If the
   clone ranks first, it counts as a miss today. Excluding `status: forward-port` issues from the candidates (or merging
   clones) would probably raise recall, but it's spring-boot-specific. Do it in M4?
3. **Threshold for M5 comment mode.** 0.825 gives 10% false alarms but flags few duplicates correctly. M5 could use a
   stricter threshold (fewer, surer comments), or an LLM check of the top pair from M4 before posting.
4. **Label vote threshold 0.5:** 69% precision at 31% coverage. A higher threshold trades coverage for precision; tune it once
   M4 shows how labels are used.
5. **The sandbox issue has no embedding yet** (the batch job ran only for petclinic and spring-boot; the worker embeds new
   sandbox issues from now on). `curl -X POST localhost:8080/embeddings/SaiSankethG/triage-sandbox` fixes it if needed.
6. **Same-second double submissions** (3 pairs) are excluded as "not older". They could count as duplicates of each other
   in either direction; with 3 pairs it doesn't change the numbers.
7. **Eval endpoint writes `docs/m3-results.md`** relative to the working directory (`triage.eval.results-file`); in a deployed
   container that path should be configured or the write turned off (M5).

## How to reproduce
```bash
docker-compose up -d && ./mvnw spring-boot:run
curl -X POST localhost:8080/embeddings/spring-projects/spring-boot   # only missing rows; ~6 min the first time
curl -X POST localhost:8080/eval/spring-projects/spring-boot         # ~100 s; writes docs/m3-results.md
```
