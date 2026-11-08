"""Gate estático: workflow manual do APK Aura seleciona ARM32 no build e no lint.

Isto só protege a seleção da ABI; instalação e compatibilidade reais exigem APK e hardware.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/apk.yml").read_text(encoding="utf-8")

assert "workflow_dispatch:" in workflow, "APK deve permanecer sob autorização manual"
assert "build_apk == true" in workflow, "APK não pode ser gerado automaticamente"
assert workflow.count("-PomegasAbis=armeabi-v7a") == 2, (
    "assembleDebug e lintDebug devem usar armeabi-v7a na candidata à multimídia ARM32"
)
assert "-PomegasAbis=arm64-v8a" not in workflow, (
    "ARM64 isolado não comprova compatibilidade com a multimídia ARM32"
)
assert "PHYSICAL_INSTALL_NOT_YET_CLAIMED=true" in workflow, (
    "A evidência do build não deve se passar por teste físico"
)
print("AURA_ARM32_APK_SELECTION_CONTRACT=PASS")
