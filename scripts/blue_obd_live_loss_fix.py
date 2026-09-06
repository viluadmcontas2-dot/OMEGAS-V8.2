#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
text = TARGET.read_text(encoding="utf-8")
old = '''            while (running.get() && current.isConnected) pollCycle(current)
        } catch (error: SecurityException) {
'''
new = '''            while (running.get() && current.isConnected) pollCycle(current)
            if (running.get() && connectionState.snapshot().stage == ElmStage.LIVE) {
                val lost = connectionState.fail(
                    "LIVE_LINK_LOST",
                    "Conexão ELM foi encerrada durante aquisição STFT",
                    System.currentTimeMillis(),
                    retryable = true,
                )
                publishConnectionStatus(lost, device = deviceLabel)
                log.add("WARN", "OBD", "Conexão ELM perdida após entrar em LIVE")
                onStateChanged()
            }
        } catch (error: SecurityException) {
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"LIVE loss patch expected one match, found {count}")
TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")
print("BLUE_OBD_LIVE_LOSS_PATCH=APPLIED")
