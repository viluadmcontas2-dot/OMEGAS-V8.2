# PLATINA-ENCODERS-180

Issue: #180. Owner-selected branch: `work/platina-obsidian-enxuto`.

Correct short U16 vector encoding against the retained ProgBase SetVector VA0092617C and share vector framing with U8. Consolidate the three duplicated K encoders without changing index/raw guards or emitted bytes.

Acceptance: literal compact/extended and length-limit JVM checks; existing AutoCal/K tests; independent review; exact-SHA remote CI. Preserve manual intent, ACK/readback and all no-auto-write policies.

Limits: static/original-derived encoder evidence; no physical ECU claim. Transport96/CA remains separately unresolved; no invented timeout/retry behavior.
