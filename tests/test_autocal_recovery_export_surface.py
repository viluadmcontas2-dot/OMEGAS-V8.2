from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
api = (ROOT / "app/src/main/assets/ui/core/native-api.js").read_text(encoding="utf-8")
drawers = (ROOT / "app/src/main/assets/ui/components/drawers.js").read_text(encoding="utf-8")
index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")

assert "exportData() { return this.demo ? false : invoke(this.native, 'exportData'" in api
assert 'id="toolExportData"' in drawers  # o botão agora nasce dentro do cartão "Cópia de segurança" (drawers.js)
assert "toolExportData" in drawers
assert "this.api.exportData()" in drawers

print("AUTOCAL_RECOVERY_EXPORT_SURFACE=PASS")


# OP-3 / Mestre C5 Task 17: RED estático contra o antigo retorno silencioso do
# seletor de arquivos Android. O método Kotlin Unit não confirma arquivo salvo;
# quem confirma é a rotina nativa, só depois de usar o URI escolhido.
activity = (ROOT / "app/src/main/java/com/omegas/prohub/MainActivity.kt").read_text(encoding="utf-8")
assert "if (uri == null) return@registerForActivityResult" not in activity
for name, message, state in (
    ("exportDataLauncher", "Cópia cancelada. Nenhum arquivo foi salvo.", None),
    ("exportLogsLauncher", "Registro cancelado. Nenhum arquivo foi salvo.", None),
    ("exportSessionLauncher", "Exportação da sessão cancelada. Nenhum arquivo foi salvo.", 'pendingSessionExportId = ""'),
    ("exportSummaryLauncher", "Resumo cancelado. Nenhum arquivo foi salvo.", 'pendingSummaryExportId = ""'),
):
    callback = activity.split(f"private val {name} = registerForActivityResult(", 1)[1].split("    private val ", 1)[0]
    reject = callback.split("if (uri == null) {", 1)[1].split("return@registerForActivityResult", 1)[0]
    assert message in reject, f"{name}: cancelamento não informa que não salvou"
    if state is not None:
        assert state in reject, f"{name}: cancelamento conserva ID pendente antigo"
    assert "Dados exportados" not in reject and "Sessão exportada" not in reject
# Toast de sucesso permanece somente depois da escrita real no URI escolhido.
assert 'svc.archives.exportData(contentResolver, uri,' in activity
assert 'svc.archives.exportLogs(contentResolver, uri,' in activity
assert 'JSONObject(svc.exportSession(uri,' in activity
assert 'JSONObject(svc.exportSessionSummary(uri,' in activity


# OP-3 / Task 17: IDs escolhidos no seletor devem ser capturados na thread
# da Activity ANTES do trabalho de exportação ser agendado em Dispatchers.IO.
# RED do código antigo: lia pending*Id dentro do coroutine, depois que outra
# escolha já podia ter atualizado o campo compartilhado.
for callback_name, pending, local, api_call in (
    ("exportSessionLauncher", "pendingSessionExportId", "selectedSessionId", "svc.exportSession(uri, selectedSessionId)"),
    ("exportSummaryLauncher", "pendingSummaryExportId", "selectedSummaryId", "svc.exportSessionSummary(uri, selectedSummaryId)"),
):
    callback = activity.split(f"private val {callback_name} = registerForActivityResult(", 1)[1].split("    private val ", 1)[0]
    capture = f"val {local} = {pending}"
    clear = f'{pending} = ""'
    assert capture in callback, f"{callback_name}: falta snapshot imutável"
    assert api_call in callback, f"{callback_name}: I/O usou identidade compartilhada"
    assert callback.index(capture) < callback.index("runWithServiceAsync"), f"{callback_name}: snapshot depois do agendamento"
    assert callback.index(clear, callback.index(capture)) < callback.index("runWithServiceAsync"), f"{callback_name}: limpeza tardia"
    assert f"svc.exportSession(uri, {pending})" not in callback
    assert f"svc.exportSessionSummary(uri, {pending})" not in callback
