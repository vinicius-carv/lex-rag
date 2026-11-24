# lex-rag — design

Date: 2026-11-22. Status: draft for approval.

## Intent

A public portfolio project that backs the "RAG" claim on the author's profile with measured numbers, not a demo. It answers questions in Portuguese about Brazilian federal law and cites the exact articles it relies on. An eval harness measures retrieval quality and answer faithfulness, and the README reports both against baselines. Audience: reviewers hiring for mid-level backend/AI roles (international remote and Brazilian companies). Measurement style follows the sibling project doc-extract (deterministic checks, committed baseline, CI gate, honest small-sample caveats).

## Goals

- Ingest the consolidated text of two laws from committed snapshots into Postgres + pgvector, one record per article.
- Answer a question with: answer text plus citations `[{articleId, quote}]`, where every quote is checked against the stored article text.
- Eval harness over a hand-written question set with gold article IDs, reporting retrieval and faithfulness metrics for three retrieval modes (keyword, dense, hybrid).
- Reproducible in CI without network or GPU (recordings replay), with a baseline gate.
- Offline unit tests; opt-in tests that call real models.
- English README with one pt-BR line, results table, how to run.

## Non-goals (v1)

- No UI. CLI + a small REST endpoint only.
- No CLT, no other laws (v2 candidate; ingestion should not hard-code two laws, but no work for a third).
- No case law, no doutrina, no legal advice framing beyond a disclaimer.
- No reranker model, no query rewriting, no agents. (Possible v2 rows in the results table.)
- No auth, no deployment, no Docker image publishing.

## Anti-goals

- Numbers that flatter: questions copied from article wording (keyword search would win trivially) or an eval set tuned after looking at failures without saying so.
- Any API key, token or secret in the repo or in recordings.
- Live calls to planalto.gov.br from code or CI.
- Client work or client names anywhere.

## Constraints

- Java 21, Spring Boot (same major line as doc-extract, 4.x), Maven wrapper, LangChain4j.
- Postgres 16+ with pgvector, run via Docker Compose locally and as a service container in CI.
- Local models via Ollama: `bge-m3` for embeddings (to be pulled), `qwen3.5:9b` for answers (already installed). Claude API optional, enabled only when `ANTHROPIC_API_KEY` is set in the environment.
- planalto.gov.br resets scripted TLS connections from the author's network; the built-in browser loads it. Snapshots are therefore saved once by hand/browser and committed. Federal law texts are not copyright-protected (Lei 9.610/1998, art. 8, IV).
- Weekly usage budget is limited: keep the build lean.

## Corpus

- Constituição Federal de 1988 (main body; ADCT excluded in v1): `https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm`
- Código de Defesa do Consumidor, Lei 8.078/1990, consolidated: `https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm`
- Snapshots already saved on 2026-11-22 with Node `fetch` and a browser User-Agent (curl/Python/Java-schannel clients get their TLS connection reset; Node works). Both files are windows-1252. CF uses `<strike>`, CDC uses `<s>` for superseded text.
- Stored as `corpus/laws/<law>.htm` exactly as served (original encoding preserved), plus `corpus/laws/manifest.json` with law id (`cf88`, `cdc`), source URL, retrieval date, SHA-256.
- Article ID format: `<law>:art<N>[-<letter>]`, e.g. `cdc:art49`, `cf88:art5`, `cf88:art103-a`.

## Parsing rules

- Planalto HTML: revoked/superseded text is inside `<strike>` (and sometimes `<s>`/`<del>`): drop it. Amendment notes like "(Redação dada pela Lei nº …)", "(Incluído pela …)", "(Vide …)" are stripped from the text used for embedding and quoting, but kept as `notes` metadata.
- Article boundaries: a paragraph starting with `Art. N` (ordinal `º`/`o` up to 9, plain number from 10, optional `-A` suffix). Everything until the next article start belongs to the article (parágrafos, incisos, alíneas).
- CF parsing stops at the heading "ATO DAS DISPOSIÇÕES CONSTITUCIONAIS TRANSITÓRIAS" (ADCT out of scope). A duplicate article ID within a law fails ingestion. (assistant's call)
- Articles whose entire text is revoked ("Revogado") are kept with a `revoked=true` flag and excluded from retrieval.
- Structure headings (TÍTULO/CAPÍTULO/SEÇÃO) are captured as a `path` string on each article (e.g. "Título II > Capítulo I") and prepended to the embedded text, not to the quotable text.
- Parser has unit tests on small HTML fixtures cut from the real snapshots, including a strike case and a `-A` article.

## Chunking

- One chunk per article. Articles above ~1,500 characters (e.g. CF art. 5) are split at inciso/parágrafo boundaries into pieces under that size; each piece keeps the parent `articleId` and an ordinal. Retrieval results are deduplicated to article level (best piece score wins) before scoring and before building the prompt. Each piece embeds `path + article heading + piece text`. The prompt gets, per retrieved article, its best-scoring pieces up to 6,000 characters across all five articles. (assistant's call)

## Retrieval

- Keyword: Postgres full-text, `to_tsvector('portuguese', text)` with a GIN index, `websearch_to_tsquery`, ranked by `ts_rank_cd`.
- Dense: pgvector cosine similarity over `bge-m3` embeddings (1024 dims) via LangChain4j's pgvector embedding store or plain JDBC (builder's call).
- Hybrid: reciprocal rank fusion of keyword top-50 and dense top-50, `k=60`. Final top-k to the prompt: k=5 articles.
- Mode is a parameter on both the CLI and the eval, so all three run on the same question set.

## Answering

- Prompt (Portuguese) gives the question plus the top-5 articles (ID + text) and requires JSON: `{"answer": str, "citations": [{"articleId": str, "quote": str}], "insufficient": bool}`. The model must say `insufficient=true` when the articles don't answer the question.
- Verification in Java, no model involved, mirroring doc-extract's statuses:
  - `VERIFIED`: quote found in that article's text after normalization (whitespace, quotes, case-folded accents kept).
  - `FUZZY`: close match (same normalization + token overlap ≥ 0.8; builder may pick the exact similarity measure, documented in README).
  - `WRONG_ARTICLE`: found in another retrieved article; citation corrected.
  - `UNSUPPORTED`: not found in any retrieved article, or cited article was not retrieved.
- Ollama is called with JSON response format and thinking off. On malformed JSON: one retry, then the answer counts as a failure (no citations, not an abstention) and is listed in the eval output. (assistant's call)
- Providers behind one LangChain4j `ChatModel`: Ollama (`qwen3.5:9b`, default) or Anthropic (opt-in, model id from config, default the current Sonnet). Config via `application.yml` + env vars; keys only from env.

## Eval set

- `corpus/eval/questions.jsonl`, ~60 questions: ~30 CDC, ~30 CF. Each: `id`, `question` (pt-BR), `gold` (one or more article IDs, all acceptable), `type` (`single` | `multi` | `unanswerable`), `note`.
- ~5 unanswerable questions (topic not covered by either law, e.g. a CLT-only question) to measure `insufficient` behaviour.
- Gold is article-level in v1, including for long split articles (no inciso-level gold).
- Written as a layperson would ask, paraphrased: a question must not share a run of 5+ consecutive tokens with its gold article text, tokens = lowercased, accents stripped, split on non-letters/digits (checked by a unit test over the file). (5 rather than 4 so fixed legal terms like "fornecedor de produtos" stay usable; assistant's call)
- Authored by the AI assistant from the article texts and reviewed by the author; the README says so. Written once before running any retriever; any later edit is logged in `corpus/eval/CHANGELOG.md`.

## Metrics

Retrieval (per mode, answerable questions only):
- Hit@1 (top result is a gold article), Recall@5 (fraction of the question's gold articles in top 5, averaged; equals hit@5 for single-gold questions), MRR@10 (first gold article's rank).

Answer (hybrid mode by default, per provider):
- Citation precision: verified-or-fuzzy citations whose article is in gold / all citations.
- Supported rate: citations VERIFIED, FUZZY or WRONG_ARTICLE / all citations (doc-extract wording).
- Answer recall: answerable questions with ≥1 supported citation to a gold article / answerable questions.
- Denominators: citation precision and supported rate are over all citations from all answers (an answer with zero citations adds nothing); citation precision uses the corrected article ID for `WRONG_ARTICLE`. An `insufficient=true` answer counts as an abstention even if it has citations, which are still verified. Failed answers (malformed JSON) count against answer recall.
- Abstention: unanswerable questions answered `insufficient=true` / unanswerable questions; and false abstention rate on answerable ones.
- Optional LLM judge (Claude, only with key): per answer, "is every claim in the answer supported by the quoted text?" yes/no. Reported as a separate column, never in the gate.

Baselines: keyword-only and dense-only rows are the retrieval baselines; hybrid is the system. Generation baseline: same prompt with retrieval mode `keyword`.

## Reproducibility and CI

- Recordings are committed as gzipped JSON (about 500 chunks × 1024 floats, a few MB; no LFS). `--ingest` reads chunk embeddings from recordings when present, so CI never calls a model. Postgres image is pinned to a versioned tag (e.g. `pgvector/pgvector:0.8.0-pg16`), not a floating one.
- Recordings: `corpus/recordings/` stores embeddings for every chunk and every eval question (keyed by model id + text hash) and model answers (keyed by provider/model + prompt hash). The eval replays recordings when present; `--record` refreshes them against live models.
- CI (GitHub Actions): JDK 21 + `pgvector/pgvector:pg16` service container; `./mvnw -q verify` (unit tests + Testcontainers or service-container integration test, builder's call), then `--eval` in replay mode; fails if hybrid Recall@5 or the Ollama-recording supported rate drops more than 0.02 below `corpus/baseline.json`. The baseline is updated by hand in the same commit that changes recordings, with the reason in the message.
- Opt-in live tests: JUnit tag `live`, excluded by default; run with `-Plive` when Ollama is up (and Anthropic tests only if `ANTHROPIC_API_KEY` is set).

## Interfaces

- CLI (Spring Boot app with flags, as in doc-extract): `--ingest`, `--ask "pergunta" [--mode hybrid] [--provider ollama|anthropic]`, `--eval [--mode all] [--record]`.
- REST: `POST /ask {question, mode?}` returns answer, citations with status, retrieved article IDs. No UI.
- Eval writes `results/eval-<date>.json` and a Markdown table printed to stdout for the README.

## README

English, one pt-BR line under the title. Sections: what it does, how citations are verified, how scoring works, results table (three retrieval rows + answer metrics), honest caveats (small hand-written set, assistant-authored questions, single run), run instructions, disclaimer (not legal advice).

## Decided by the author

- Stack: Java 21 + Spring Boot + LangChain4j.
- Defaults accepted: CF + CDC in v1, pgvector, bge-m3 + qwen3.5:9b via Ollama, Claude opt-in via env key, ~60 hand-written paraphrased questions, CLI + API only, three-mode retrieval baselines, deterministic quote verification.
- Approaches (picked by the assistant, since the author handed over the how):
  - Chunk = article (split by inciso when long). Fixed-size token chunks were rejected because they blur which article was cited.
  - Hybrid retrieval with RRF, keyword and dense kept as baselines. Dense-only was rejected because it leaves no free baseline.

## Left to the builder

- Package layout, class names, exact SQL schema.
- LangChain4j pgvector store vs plain JDBC for vectors.
- FUZZY similarity measure (documented).
- Integration-test mechanism (Testcontainers vs CI service container).
- Exact prompt wording (Portuguese, versioned as `v1`).
- Project name `lex-rag` and folder `lex-rag` (assistant's call; easy to rename before publishing).

## Amendment 2026-11-23

Measured on the real corpus (56 answerable questions, bge-m3, replay):

| Mode | Hit@1 | Recall@5 | MRR@10 |
|---|---|---|---|
| KEYWORD | 0.214 | 0.417 | 0.324 |
| DENSE | 0.661 | 0.827 | 0.758 |
| HYBRID (RRF k=60) | 0.393 | 0.702 | 0.544 |

(Numbers as measured when the decision was made, before a later keyword-query fix; current numbers are in the README.)

Equal-weight RRF lets the weak keyword list pull noise in, so hybrid loses to dense. The shipped system mode is therefore DENSE (`Mode.SYSTEM`), not HYBRID. Hybrid stays as a reported retrieval row; RRF weights and k are not tuned. The regression gate keys on the system mode (`systemRecall5` in `corpus/baseline.json`). This choice was made after seeing results on the same eval set; the README must disclose it.
