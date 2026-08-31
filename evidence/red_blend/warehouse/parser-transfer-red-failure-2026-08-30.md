# Warehouse parser transfer — RED evidence

GitHub Actions run `33346403530`, job `99351274357`, proved the transferred `tools/science/warehouse_cache.py` was not equivalent to the local parser that built the real cache.

Observed failures:

- `sqlite3.OperationalError: table telemetry has no column named map_bap`;
- malformed `VALUES(...))` SQL in `map_k_batch` / cell insert;
- transferred child inserts used `key` instead of the derived `akey`.

The local parser at `/mnt/data/omegas_warehouse_work/warehouse_cache.py` that generated the real cache contains `map_bar`, balanced SQL placeholders and `akey`. The remediation is to replace the transferred repository blob with that already-exercised local parser, then rerun the same 10 warehouse contracts before allowing causal/performance/Android jobs to proceed.
