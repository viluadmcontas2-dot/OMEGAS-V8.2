create schema if not exists omegas_science;

comment on schema omegas_science is 'OMEGAS vehicle science evidence ledger. Raw/private logs remain outside Postgres.';

create table if not exists omegas_science.corpus_version (
  corpus_key text primary key,
  parser_version text not null,
  code_sha text not null,
  source_count integer not null check (source_count >= 0),
  logical_session_count integer not null check (logical_session_count >= 0),
  telemetry_count bigint not null check (telemetry_count >= 0),
  map_k_batch_count integer not null check (map_k_batch_count >= 0),
  k_factor_batch_count integer not null check (k_factor_batch_count >= 0),
  autocal_snapshot_count integer not null check (autocal_snapshot_count >= 0),
  corpus_digest_sha256 text not null check (length(corpus_digest_sha256) = 64),
  status text not null check (status in ('BUILDING','PUBLISHED','SUPERSEDED','FAILED')),
  created_at timestamptz not null default now(),
  published_at timestamptz
);

create table if not exists omegas_science.ingestion_run (
  run_key text primary key,
  corpus_key text references omegas_science.corpus_version(corpus_key) on delete set null,
  parser_version text not null,
  code_sha text not null,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  status text not null check (status in ('RUNNING','PASS','FAIL')),
  cache_hits integer not null default 0 check (cache_hits >= 0),
  parsed_sources integer not null default 0 check (parsed_sources >= 0),
  malformed_records bigint not null default 0 check (malformed_records >= 0),
  notes jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.source_blob (
  source_sha256 text primary key check (length(source_sha256) = 64),
  bytes bigint not null check (bytes >= 0),
  source_class text not null,
  parser_version text,
  ingestion_status text not null check (ingestion_status in ('PENDING','PARSED','OPAQUE','EXCLUDED','ERROR')),
  first_seen_at timestamptz not null default now(),
  last_ingested_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.logical_session (
  session_key text primary key,
  started_at timestamptz,
  ended_at timestamptz,
  app_version text,
  source_count integer not null default 0 check (source_count >= 0),
  fuel_evidence text,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.session_source (
  session_key text not null references omegas_science.logical_session(session_key) on delete cascade,
  source_sha256 text not null references omegas_science.source_blob(source_sha256) on delete cascade,
  relationship text not null default 'MEMBER',
  primary key (session_key, source_sha256)
);

create table if not exists omegas_science.cache_artifact (
  artifact_key text primary key,
  corpus_key text not null references omegas_science.corpus_version(corpus_key) on delete cascade,
  format text not null,
  schema_version text not null,
  sqlite_sha256 text not null check (length(sqlite_sha256) = 64),
  compressed_sha256 text check (compressed_sha256 is null or length(compressed_sha256) = 64),
  sqlite_bytes bigint not null check (sqlite_bytes >= 0),
  compressed_bytes bigint check (compressed_bytes is null or compressed_bytes >= 0),
  drive_file_id text,
  drive_parent_id text,
  created_at timestamptz not null default now(),
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.map_k_batch (
  adjustment_key text primary key,
  session_key text references omegas_science.logical_session(session_key) on delete set null,
  occurred_at timestamptz,
  source_sha256 text references omegas_science.source_blob(source_sha256) on delete set null,
  old_map_hash text,
  new_map_hash text,
  cell_count integer not null check (cell_count >= 0),
  confirmed boolean not null,
  batch_finalized boolean,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.map_k_cell_change (
  adjustment_key text not null references omegas_science.map_k_batch(adjustment_key) on delete cascade,
  row_index integer not null check (row_index between 0 and 11),
  column_index integer not null check (column_index between 0 and 11),
  rpm_axis integer,
  petrol_ms_axis double precision,
  before_k integer check (before_k is null or before_k between 0 and 255),
  after_k integer check (after_k is null or after_k between 0 and 255),
  readback_k integer check (readback_k is null or readback_k between 0 and 255),
  confirmed boolean not null,
  primary key (adjustment_key, row_index, column_index)
);

create table if not exists omegas_science.k_factor_batch (
  adjustment_key text primary key,
  session_key text references omegas_science.logical_session(session_key) on delete set null,
  occurred_at timestamptz,
  source_sha256 text references omegas_science.source_blob(source_sha256) on delete set null,
  old_curve_hash text,
  new_curve_hash text,
  point_count integer not null check (point_count >= 0),
  confirmed boolean not null,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.k_factor_point_change (
  adjustment_key text not null references omegas_science.k_factor_batch(adjustment_key) on delete cascade,
  point_index integer not null check (point_index between 0 and 29),
  petrol_ms double precision,
  before_raw integer check (before_raw is null or before_raw between 0 and 65535),
  after_raw integer check (after_raw is null or after_raw between 0 and 65535),
  before_factor double precision,
  after_factor double precision,
  confirmed boolean not null,
  primary key (adjustment_key, point_index)
);

create table if not exists omegas_science.autocal_snapshot (
  snapshot_key text primary key,
  session_key text references omegas_science.logical_session(session_key) on delete set null,
  source_sha256 text references omegas_science.source_blob(source_sha256) on delete set null,
  occurred_at timestamptz,
  module_version integer,
  partial boolean,
  temporal_coherent boolean,
  field_count integer not null default 0 check (field_count >= 0),
  invalid_field_count integer not null default 0 check (invalid_field_count >= 0),
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.autocal_field (
  snapshot_key text not null references omegas_science.autocal_snapshot(snapshot_key) on delete cascade,
  field_key text not null,
  element_count integer check (element_count is null or element_count >= 0),
  valid boolean,
  failure_reason text,
  values_json jsonb,
  raw_sha256 text check (raw_sha256 is null or length(raw_sha256) = 64),
  primary key (snapshot_key, field_key)
);

create table if not exists omegas_science.portmon_capture_summary (
  capture_sha256 text primary key check (length(capture_sha256) = 64),
  bytes bigint not null check (bytes >= 0),
  transaction_count bigint not null check (transaction_count >= 0),
  write_attempt_count bigint check (write_attempt_count is null or write_attempt_count >= 0),
  distinct_command_count integer not null check (distinct_command_count >= 0),
  map_k_write_count integer not null default 0 check (map_k_write_count >= 0),
  k_factor_write_count integer not null default 0 check (k_factor_write_count >= 0),
  autocal_toggle_count integer not null default 0 check (autocal_toggle_count >= 0),
  role text,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists omegas_science.rpm_map_summary (
  corpus_key text not null references omegas_science.corpus_version(corpus_key) on delete cascade,
  session_key text not null references omegas_science.logical_session(session_key) on delete cascade,
  fuel text not null,
  rpm_bin integer not null,
  map_bin_mbar integer not null,
  sample_count bigint not null check (sample_count > 0),
  mean_petrol_ms double precision,
  median_petrol_ms double precision,
  std_petrol_ms double precision,
  p10_petrol_ms double precision,
  p90_petrol_ms double precision,
  mean_map_bar double precision,
  mean_rpm double precision,
  mean_gas_ms double precision,
  source_digest_sha256 text not null check (length(source_digest_sha256) = 64),
  primary key (corpus_key, session_key, fuel, rpm_bin, map_bin_mbar)
);

create table if not exists omegas_science.analysis_result (
  result_key text primary key,
  corpus_key text not null references omegas_science.corpus_version(corpus_key) on delete cascade,
  analysis_type text not null,
  code_sha text not null,
  parameters jsonb not null default '{}'::jsonb,
  metrics jsonb not null default '{}'::jsonb,
  artifact_sha256 text check (artifact_sha256 is null or length(artifact_sha256) = 64),
  claim_scope text not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_omegas_session_started on omegas_science.logical_session(started_at);
create index if not exists idx_omegas_map_batch_time on omegas_science.map_k_batch(occurred_at);
create index if not exists idx_omegas_curve_batch_time on omegas_science.k_factor_batch(occurred_at);
create index if not exists idx_omegas_autocal_time on omegas_science.autocal_snapshot(occurred_at);
create index if not exists idx_omegas_rpm_map on omegas_science.rpm_map_summary(corpus_key, fuel, rpm_bin, map_bin_mbar);
create index if not exists idx_omegas_analysis_type on omegas_science.analysis_result(corpus_key, analysis_type);

revoke all on schema omegas_science from anon, authenticated;
revoke all on all tables in schema omegas_science from anon, authenticated;
revoke all on all sequences in schema omegas_science from anon, authenticated;
alter default privileges in schema omegas_science revoke all on tables from anon, authenticated;
alter default privileges in schema omegas_science revoke all on sequences from anon, authenticated;
