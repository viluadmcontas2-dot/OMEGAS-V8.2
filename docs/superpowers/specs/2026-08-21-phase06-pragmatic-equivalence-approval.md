# Phase 06 Pragmatic Equivalence — Owner Approval Receipt

Date: 2026-08-21
Status: OWNER_APPROVED

Approved design: `docs/superpowers/specs/2026-08-21-phase06-pragmatic-equivalence-design.md`

The owner explicitly approved proceeding with the design after the clarification that the operational behavior is:

- RPM + MAP locate the engine operating state;
- petrol `Tinj` is the equivalence signal;
- physically valid evidence is retained with continuous weight instead of ordinary stability rejection;
- gasoline builds the reference surface with more conservative weighting than CNG;
- valid CNG evidence is retained in bounded state even when local gasoline support does not yet exist;
- pressure, temperatures, K2/K3/K4, deadtime and A/C state are not primary equivalence gates;
- the optimized V8.2 performance architecture remains binding;
- Predictor remains downstream and will be reviewed/closed as a separate projection responsibility.

Any future change that materially alters one of these bullets is a new owner/architectural decision and must stop the dependent workstream until explicit authorization. Independent safe work may continue.
