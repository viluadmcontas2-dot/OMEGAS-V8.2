# OMEGAS Verde — final remote-only verification

Source authority: GitHub remote.

Base product SHA before this evidence-only commit:
`e84f801d4991a89e003f4866a4ed53478a2de25f`

Acceptance gate for this verification SHA:
- focused product/storage contracts;
- Python + UI quality gate;
- Android unit tests;
- lint;
- debug APK build;
- APK ZIP integrity;
- APK SHA-256 receipt;
- no local/MMMACHINE execution or evidence.

Product requirements covered by the current remote head:
- Curva K reset exposed through the existing verified writer;
- manual Curva K backup published to `Download/Omegas`;
- session public mirror rooted at `Download/Omegas`;
- session retention default/minimum 20;
- retention/log controls remain stable while the panel is open;
- AutoCal/Curve write safety retains ACK + readback and no RPM threshold block.
