"""Revisão de código (2026-10-20): a fila de trabalho do motor serial nunca era esvaziada quando o motor parava, e uma escrita do dono
enfileirada durante uma queda do link podia executar minutos depois. O motor depende de android.os.SystemClock (não roda em JVM puro),
então o contrato é conferido no texto e a compilação/testes reais rodam no CI remoto. Cada afirmação abaixo falha se o código voltar atrás."""
from pathlib import Path

SRC = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt").read_text(encoding="utf-8")


def corpo(inicio: str, fim: str) -> str:
    a = SRC.index(inicio)
    return SRC[a:SRC.index(fim, a)]


def test_motor_que_para_falha_toda_a_fila():
    finally_run_loop = SRC[SRC.rindex("} finally {", 0, SRC.index("private fun performHandshakeOrResume")):SRC.index("private fun performHandshakeOrResume")]
    assert "running.set(false)" in finally_run_loop
    assert 'failAllQueued("Engine MP48 parou; nada foi enviado")' in finally_run_loop
    assert finally_run_loop.index("running.set(false)") < finally_run_loop.index("failAllQueued")


def test_descarte_usa_poll_em_laco_e_nao_foreach_clear():
    assert "queue.forEach" not in SRC and "queue.clear()" not in SRC  # forEach+clear perdia um offer concorrente (future pendurado)
    assert "val work = queue.poll() ?: break" in SRC


def test_escrita_do_dono_nao_executa_depois_que_o_link_caiu():
    assert "failQueuedManualWrites" in corpo("if (!usb.connected) {", "Thread.sleep(250L)")
    sem_sessao = corpo("if (!sessionReady) {", "performHandshakeOrResume")
    assert "if (hadOnlineSession) failQueuedManualWrites" in sem_sessao  # no handshake inicial a escrita ainda espera a vez
    # saída segura (SAFETY) e leituras NÃO são descartadas por isso
    assert "work.workClass == Mp48WorkClass.MANUAL_WRITE" in corpo("private fun failQueuedManualWrites", "@Synchronized")


def test_corrida_entre_enfileirar_e_parar():
    assert 'if (!running.get()) failAllQueued("Engine MP48 parou; nada foi enviado")' in corpo("private fun <T> enqueue", "fun statusJson")


if __name__ == "__main__":
    for nome, fn in list(globals().items()):
        if nome.startswith("test_"):
            fn()
    print("ENGINE_FILA_DESCARTADA=PASS")
