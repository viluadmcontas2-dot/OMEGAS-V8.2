"""Verificação de FUNÇÃO (não de forma): o que o OMEGAS lê e escreve na ECU durante o AutoCal, comparado com o que o ProgBase
original faz no fio (PROTOCOLO-LANDI doc 17). Fonte do OMEGAS: o Kotlin real (NativeAutoCalMonitor/AutoCalProtocol/ActionManager);
fonte do original: capturas Portmon já analisadas (doc 17) e o decompilado (reverse/ghidra). Estático: não abre porta nenhuma.
Uso: python3 -I tools/sessoes/verificar_protocolo_autocal.py   (sai com erro se o monitor passar a ESCREVER ou deixar de ler o bloco A)"""
import os, re, sys
ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
KT = os.path.join(ROOT, 'app/src/main/java/com/omegas/prohub')
read = lambda p: open(os.path.join(KT, p), encoding='utf-8').read()
monitor = read('autocal/NativeAutoCalMonitor.kt')
protocol = read('ecu/AutoCalProtocol.kt')
actions = read('autocal/AutoCalNativeActionManager.kt')
planner = read('autocal/NativeAutoCalRefreshPlanner.kt')

fields = {m.group(1): int(m.group(2), 16) for m in re.finditer(r'val (\w+) = Field\("\w+", (0x[0-9A-Fa-f]+)', protocol)}
def enclosing():
    """Campo -> funções do monitor em que ele é lido (pela posição no arquivo)."""
    starts = [(m.start(), m.group(1)) for m in re.finditer(r'\n    (?:private |internal )?fun (\w+)\(', monitor)]
    out = {}
    for m in re.finditer(r'AutoCalProtocol\.([A-Z_0-9]+)', monitor):
        if m.group(1) not in fields: continue
        fn = [n for pos, n in starts if pos < m.start()]
        if fn and re.match(r'(refresh|read|probe|stable)', fn[-1]): out.setdefault(m.group(1), set()).add(fn[-1])
    return out
where_used = enclosing()
# Bloco A do original (doc 17): vetores por endereço de parâmetro
ORIGINAL_BLOCK_A = {0x162: 'PETR_INJ_TBUF', 0x163: 'MNFLD_PRESS_BUF', 0x15B: 'NUM_BUF_UPD_PETR', 0x15D: 'PETR_INJ_TBUF_GAS_PREV',
                    0x15E: 'MNFLD_PRESS_BUF_GAS_PREV', 0x15F: 'PETR_INJ_TBUF_GAS', 0x160: 'MNFLD_PRESS_BUF_GAS', 0x15C: 'NUM_BUF_UPD_GAS',
                    0x161: 'MUL_ACT', 0x16F: 'ACQUIRED_ZONES_PETROL', 0x170: 'ACQUIRED_ZONES_GAS', 0x18D: 'PETR_MNFLD_PRESS_RV', 0x18E: 'GAS_MNFLD_PRESS_RV'}
rows = []
for addr, name in sorted(ORIGINAL_BLOCK_A.items()):
    fns = where_used.get(name)
    where = ', '.join(sorted(fns)) if fns else 'AUSENTE'
    rows.append((f'0x{addr:04X}', name, where))
missing = [r for r in rows if r[2] == 'AUSENTE']

writes = []
for m in re.finditer(r'AutoCalProtocol\.(write\w*|action\w*|set\w*|ACTION_\w+)', monitor):
    writes.append(m.group(0))
status_read = 'CMD_NATIVE_STATUS' in monitor        # 48 0B
count_read = 'NUM_AUTOMATCH_EXECUTED' in monitor    # 09 74 01 (fallback/leitura do contador)
acq_ms = re.search(r'ACQUISITION_INTERVAL_MS = ([\d_]+)', planner).group(1).replace('_', '')
ref_ms = re.search(r'REFERENCE_INTERVAL_MS = ([\d_]+)', planner).group(1).replace('_', '')
manual_modes = re.findall(r'"(RESET_\w+|MANUAL_AUTOMATCH|ENABLE_AUTO_CAL|DISABLE_AUTO_CAL)"', actions)
modes_in_code = {m.group(1): m.group(2) for m in re.finditer(r'"(RESET_PETROL|RESET_GAS|RESET_ALL|MANUAL_AUTOMATCH)"[^\n]*?(0x0[1248]|[1248])\b', actions)}

print('VERIFICAÇÃO DE FUNÇÃO · AutoCal OMEGAS × original (doc 17)\n')
print('1) Leituras do bloco A do original (todas só leitura):')
for addr, name, where in rows: print(f'   {addr} {name:26s} → {where}')
print(f'\n2) Status 48 0B lido: {"sim" if status_read else "NÃO"} · contador 0x0174 lido: {"sim" if count_read else "NÃO"}')
print(f'3) Cadência: aquisição {acq_ms} ms (original ≈ 2000 ms) · referência {ref_ms} ms')
print(f'4) Escritas no monitor (deve ser nenhuma): {writes or "nenhuma"}')
print(f'5) Ações manuais no código: {sorted(set(manual_modes))}')
print('\nDiferenças em relação ao original (observação, não erro):')
print('   · lê as zonas dos DOIS combustíveis a cada aquisição (o original lê só a do combustível em uso) — só leitura, ≈ 1 transação a mais por segundo')
print(f'   · aquisição a cada {int(acq_ms)/1000:.0f} s em vez de ≈ 2 s — mais tráfego no fio de 9600 baud (concorre com a telemetria 48 01)')
print('   · modos 0x01 (gasolina) e 0x02 (GNV) do botão Reaprender: bytes iguais ao decompilado do original, mas NUNCA vistos no fio (só 0x04 foi)')
bad = bool(missing) or bool(writes) or not status_read
print('\nRESULTADO:', 'FALHOU' if bad else 'OK — o monitor só lê, e lê todo o bloco A do original')
sys.exit(1 if bad else 0)
