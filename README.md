# lex-rag

Answer questions about the Brazilian Constitution (CF88) and Consumer Code (CDC) in Portuguese, cite the exact articles, and check every quoted passage against the law text.

*Em português: responde perguntas sobre a Constituição e o CDC citando os artigos exatos, e mede se a citação confere com o texto da lei.*

Not legal advice.

## What it does

Ask a layperson's question ("Comprei um celular pela internet e me arrependi, posso devolver?") and get an answer, the articles it relies on, and for each one a quoted passage with a verification status. The same code runs an eval over 61 questions that measures how often the right article is retrieved and how often the model's quotes are real.

Stack: Java 21, Spring Boot 4.1.2, LangChain4j 1.24.0 (model clients only), Postgres with pgvector (plain JDBC), [Ollama](https://ollama.com) with `bge-m3` embeddings and `qwen3.5:9b` answers. Claude is supported as an optional provider (`ANTHROPIC_API_KEY`), but no Claude numbers are reported here: no key was used.

Corpus: CF88 main body (the ADCT is excluded) and CDC (Lei 8.078/1990, consolidated), saved from planalto.gov.br on 2026-11-22 and committed under `corpus/laws/`. 406 articles, 603 chunks. Articles over 1500 characters are split at inciso/paragraph boundaries; results are deduplicated back to the article. The prompt gets the top 5 articles within a 6000-character budget.

## How citations are verified

The model never marks its own homework. It returns `{answer, citations: [{articleId, quote}], insufficient}`, and Java checks each quote against the text of the retrieved articles. Text is normalized first (lowercase, curly quotes, `°` to `º`, whitespace; only a trailing ellipsis is stripped).

| Status | Meaning |
|---|---|
| `VERIFIED` | Quote found exactly in the cited article |
| `WRONG_ARTICLE` | Quote found in another retrieved article; the article id is corrected (even if the cited id was not retrieved) |
| `FUZZY` | Not exact, but at least 80% of the quote's tokens (as a multiset) appear within a window of the cited article that is as long as the quote |
| `UNSUPPORTED` | None of the above, or the cited article was not retrieved |

FUZZY is a token-overlap heuristic over a sliding window, not a semantic check. A quote that passes it can still be a loose paraphrase.

## How scoring works

Retrieval, on the 56 answerable questions, for each of three modes:

- **Hit@1**: the top result is a gold article.
- **Recall@5**: fraction of the question's gold articles found in the top 5, averaged over questions.
- **MRR@10**: reciprocal rank of the first gold article within the top 10.

Answers, over all 61 questions:

- **Citation precision**: citations that are not `UNSUPPORTED` and point (after correction) to a gold article, divided by all citations from all answers.
- **Supported rate**: citations that are `VERIFIED`, `FUZZY` or `WRONG_ARTICLE`, divided by all citations.
- **Answer recall**: answerable questions with at least one supported citation to a gold article, divided by the 56 answerable questions. An `insufficient` answer never counts as recalled, even with a verified gold citation (its citations are still verified and counted above).
- **Abstention**: unanswerable questions answered `insufficient=true`, divided by the 5 unanswerable questions.
- **False abstention**: answerable questions answered `insufficient=true`, divided by 56.
- **Failed**: answers whose model output was not valid JSON after one retry. They have no citations and count against answer recall.

`--eval` exits 1 if the system mode's Recall@5 or the supported rate of the ollama answers falls more than 0.02 below `corpus/baseline.json` (`systemRecall5` 0.82, `supportedRate` 0.97).

## Results

61 questions (56 answerable, 5 unanswerable; 49 `single`, 7 `multi` and 5 `unanswerable` by question type), split evenly between CDC and CF88 by first gold article (28 answerable each). `bge-m3` and `qwen3.5:9b` via Ollama, one run, recorded 2026-11-23 (`results/eval-2026-11-23.json`). Reproduce with `--eval --mode all`, which replays `corpus/recordings/`.

Retrieval (n = 56):

| Mode | Hit@1 | Recall@5 | MRR@10 |
|---|---|---|---|
| Keyword (Postgres full-text, baseline) | 0.232 | 0.435 | 0.334 |
| **Dense (bge-m3, the system)** | **0.661** | **0.827** | **0.758** |
| Hybrid (keyword + dense, equal-weight RRF) | 0.393 | 0.685 | 0.544 |

Answers (n = 61), same prompt, only the retrieval mode differs:

| Retrieval | Citation precision | Supported rate | Answer recall | Abstention | False abstention | Failed |
|---|---|---|---|---|---|---|
| Dense (system) | 0.627 | 0.973 | 0.821 | 0.800 | 0.018 | 0 |
| Keyword (baseline) | 0.296 | 0.880 | 0.357 | 0.800 | 0.304 | 0 |

Reading it: the model rarely produces a quote that is not in the retrieved text (supported rate 0.973 with dense retrieval, 0.880 with keyword), but that says little about whether it cites the right article. With keyword retrieval the right article is often missing, so the model abstains or cites something else. A citation precision of 0.627 means over a third of dense-mode citations are real quotes from articles that are not the gold article for that question. Some of those may be legitimately relevant, since gold is a single article-level label per question.

## Caveats

- **Small set, one run.** 61 questions, no confidence intervals, no repeated runs. Differences of a few points are noise.
- **Hybrid lost, and the choice was made after the fact.** I expected hybrid (keyword + dense, RRF k=60) to win. On this set it lost to dense, so the system mode is dense. That decision was made after seeing results on the same 61 questions, and the RRF weights were not tuned. Treat dense versus hybrid as an observation on a small set, not a benchmark. A weak keyword list pulling noise into an equal-weight fusion is the likely cause; I did not test that.
- **Keyword search is a loose baseline.** It ORs the question's stemmed terms and ranks with `ts_rank_cd(..., 1)`, because layperson questions rarely contain every term and AND matched nothing.
- **Questions are assistant-authored and not yet reviewed by a lawyer or by the author.** The AI assistant wrote them from the article texts before any retrieval run, as paraphrases (no 5-token run shared with the gold article, enforced by a test). A reviewer subagent spot-checked 34 of the 61 gold labels against the law text. Gold is article-level; later edits go in `corpus/eval/CHANGELOG.md`.
- **Snapshots are dated 2026-11-22.** The laws change and the committed text will go stale. The ADCT is not included.
- **One local model.** Only `qwen3.5:9b` is measured. Recording the answers took about 109 minutes on a local GPU with 8 GB VRAM.
- **Verification checks the quote, not the reasoning.** A verified quote can still be irrelevant to the answer text. The optional LLM judge (`--judge`, needs a key) is not part of these results.

## Run

Requires JDK 21, Docker and, for live use, Ollama. Use `.\mvnw.cmd` on Windows.

```
# build and test (Testcontainers starts Postgres)
./mvnw -q verify

# database (pgvector/pgvector:0.8.7-pg18)
docker compose up -d db

# models: only needed for live questions and for re-recording
ollama pull bge-m3
ollama pull qwen3.5:9b

# load the laws; replays chunk embeddings from corpus/recordings, no Ollama needed
java -jar target/lex-rag-0.1.0-SNAPSHOT.jar --ingest

# eval: replay committed recordings, no Ollama needed; writes results/eval-<date>.json
java -jar target/lex-rag-0.1.0-SNAPSHOT.jar --eval --mode all

# ask one question (needs Ollama), or start the server
java -jar target/lex-rag-0.1.0-SNAPSHOT.jar --ask "Posso devolver algo comprado pela internet?"
java -jar target/lex-rag-0.1.0-SNAPSHOT.jar
curl -X POST localhost:8080/ask -H "Content-Type: application/json" -d '{"question":"..."}'
```

Flags: `--ingest`, `--ask "question" [--mode keyword|dense|hybrid] [--provider ollama|anthropic]`, `--eval [--mode all] [--retrieval-only] [--judge] [--record]`. `--record` calls live models for anything not yet recorded; delete `corpus/recordings/` to re-record after changing the prompt or model.

`--ask` and the web server answer new questions live: recorded embeddings and answers are reused when the text matches, misses go to Ollama and are not persisted (unless `--record` is given). `--eval` and `--ingest` are replay-only unless `--record` is given. `POST /ask` takes `{question, mode?}` and returns the answer, citations with status, and retrieved article ids.

Configuration (env vars): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `LEXRAG_OLLAMA_BASE_URL`, `LEXRAG_EMBEDDING_MODEL`, `LEXRAG_CHAT_PROVIDER`, `LEXRAG_CHAT_OLLAMA_MODEL`, `LEXRAG_CHAT_ANTHROPIC_MODEL`, and `ANTHROPIC_API_KEY` (read only from the environment).

Live tests (Ollama up; the Anthropic test runs only if `ANTHROPIC_API_KEY` is set): `./mvnw verify -Plive`.

CI (`.github/workflows/ci.yml`) runs `./mvnw -q verify`, starts the same pinned Postgres image, ingests, and runs the replay eval gate. It needs no Ollama.

## Disclaimer

Not legal advice. This is an engineering exercise; answers can be wrong or out of date.

## Attribution

Law texts are from planalto.gov.br (federal legislation, not subject to copyright under Lei 9.610/1998, art. 8, IV). Code is MIT licensed.
