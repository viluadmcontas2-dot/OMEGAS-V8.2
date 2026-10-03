import pathlib

root = pathlib.Path(__file__).resolve().parents[1]
agents = (root / "AGENTS.md").read_text(encoding="utf-8")
assert len(agents.splitlines()) <= 60, "AGENTS.md cabe numa tela"
for must in ("2026-10-03-omegas-platina-norte-unico-design.md", "Observar é automático", "um toque",
             "Desfazer", "readback", "GitHub Actions", "Agora", "Mapa K", "Curva K", "AutoCal", "Refino", "Sessões", "Ferramentas",
             "3b68ee52ac5481839046f36b482aab44", "3b78ee52ac548170b5c1fb69606ced21",
             # mantidos além do plano: branch, épico e fronteira SIL/CIU
             "OmegasDiamante", "SIL/CIU", "#131"):
    assert must in agents, must
for gone in ("LOCAL_SOURCE_MUTATION", "SOURCE_MUTATION_TARGET", "MMMACHINE", "Brainbase", "AgentRed", "RESET_ALL"):
    assert gone not in agents, gone
project = (root / "PROJECT.md").read_text(encoding="utf-8")
assert len(project.splitlines()) <= 10 and "OmegasDiamante" in project
status = (root / "STATUS.md").read_text(encoding="utf-8")
assert len(status.splitlines()) <= 40 and "PHYSICAL_VALIDATION_CLAIMED" in status
assert (root / "docs/archive/STATUS-ate-2026-10-03.md").is_file()
assert (root / "docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md").is_file()
assert (root / "docs/ARCHITECTURE.md").is_file() and (root / "docs/TEST_STRATEGY.md").is_file()
# nunca versionar material de assinatura ou segredos
assert not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
assert not [n for n in ("keystore.properties", ".env", "secrets.properties") if (root / n).exists()]
print("GOVERNANCE_CONTRACT=PASS")
