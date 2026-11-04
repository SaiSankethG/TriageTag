# M4 results: the LLM layer with a real model (STOP A)

4 Nov 2026. Model: **Claude Haiku 5.5** (`claude-haiku-5-5`, $0.10 / $0.50 per million input / output tokens), thinking
off, max 1,024 output tokens, through `langchain4j-anthropic` 1.22.0. Generated files: `docs/m4-duplicate-check.md`
(eval output) and `docs/m4-scoring.md` (30 reports for hand-scoring).

## 1. Comment-mode rule: score alone vs score + LLM "same root problem?" (sai's decision 3)

Replay on spring-boot, **external reporters only**: 720 scored duplicates, and the 1,000 random non-duplicates of the M3
false-alarm sample. Threshold 0.825 on the vector top-1 score. The LLM checks the top match only when it passes the threshold
(204 calls, 247,819 tokens, 1 answer with invalid JSON twice → counted as "no").

| | Score only | Score + LLM "yes" |
|---|---|---|
| Duplicates flagged (of 720) | 103 | 75 |
| ... top match is the true original (a correct comment) | **55** (7.6%) | **48** (6.7%) |
| ... top match is another issue | 48 | 27 |
| Non-duplicates flagged = false alarms (of 1,000) | **101 (10.1%)** | **46 (4.6%)** |

- **The LLM check halves the false alarms** (10.1% → 4.6%) and **keeps 48 of the 55 correct flags (87%)**.
- Among flagged duplicates, the share whose top match is the true original rises from 53% (55/103) to 64% (48/75).

### What it would mean for public comments (an estimate)
External issues are mostly not duplicates: 720 marked duplicates among 17,685 external issues (4.1%). Per 1,000 new external
issues, that gives:

| | Comments posted | Correct (true original) | Correct share |
|---|---|---|---|
| Score ≥ 0.825 only | ~103 | ~3.1 | **~3%** |
| Score ≥ 0.825 + LLM "yes" | ~48 | ~2.7 | **~6%** |

**At 0.825, even with the LLM check, most public comments would point at the wrong issue.** Caveat: "non-duplicate" means
"not marked as a duplicate by a maintainer". Some flagged "false alarms" are real but unmarked duplicates, and some "other
issue" matches are the same problem filed elsewhere, so the true share is higher than this. That can only be checked by
reading them. Comment mode (M5) needs a stricter threshold or more checks; shadow mode (private top 3) stays the default.
See open question 1 in docs/m4-report.md.

## 2. 30 recent issues for hand-scoring

`docs/m4-scoring.md`: the 30 most recent spring-boot issues by external reporters, each with its full report (88,554 tokens).
Quick counts before sai's scoring:
- **Draft replies**: 22 written, 8 "nothing relevant found" (no source above the thresholds, so no LLM call). No draft
  cites a source as "[n]" (prompt v2). One draft contains a link that isn't a source URL: a wiki link it found inside a
  docs section's text. The check flagged it.
- **LLM duplicate check** ran for 2 of 30 (top score ≥ 0.825): #52500 → #52499 (0.98, near-identical twins filed
  back to back; LLM said **UNSURE**), #52465 → #51843 (0.85, LLM: **NO**).
- **Labels** suggested for 8 of 30; **missing details** flagged for 20 of 30 (10 had nothing missing).

## 3. Cost

All LLM calls on 4 Nov (smoke tests + eval + 30 reports): **339,156 tokens in 259 calls**, logged in `llm_usage`.
That's at most $0.17 if every token were output, and about $0.04–0.06 at the real mix (mostly input). The daily budget for these runs was set to 1.5M tokens.
One full report costs about 1,000–5,600 tokens (≈ $0.0002–0.001).
