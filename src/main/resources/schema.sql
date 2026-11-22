create extension if not exists vector;
create table if not exists chunk (
  article_id text not null,
  ordinal int not null,
  text text not null,
  embed_text text not null,
  tsv tsvector generated always as (to_tsvector('portuguese', embed_text)) stored,
  embedding vector(1024) not null,
  primary key (article_id, ordinal)
);
create index if not exists chunk_tsv on chunk using gin (tsv);
