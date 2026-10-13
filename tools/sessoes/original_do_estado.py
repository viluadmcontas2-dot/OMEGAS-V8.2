"""Renderiza a tela AutoCal ORIGINAL (emulador do PROTOCOLO-LANDI) a partir do MESMO estado que alimenta o OMEGAS nos testes
(tests/ui/fixtures/autocal-captura-ac-t1000.json). Assim original e OMEGAS são comparados com os mesmos números.
Uso: python3 -I tools/sessoes/original_do_estado.py <saida.html> [--fuel gas|petrol]
Requer o repositório PROTOCOLO-LANDI clonado (variável PROTOCOLO_LANDI, padrão /home/user/protocolo-landi)."""
import json, os, sys
LANDI = os.environ.get('PROTOCOLO_LANDI', '/home/user/protocolo-landi')
sys.path.insert(0, os.path.join(LANDI, 'tools'))
import orig_emulator as E  # noqa: E402

BASE = os.path.join(os.path.dirname(__file__), '..', '..', 'tests', 'ui', 'fixtures', 'autocal-captura-ac-t1000.json')

def to_o(state, fuel):
    f = {x['key']: x for x in state['snapshot']['fields']}
    phys = lambda k: [v if v is not None else 0 for v in f[k]['physicalValues']] if k in f else []
    raw = lambda k: [int(v) for v in f[k]['rawValues']] if k in f else []
    o = {}
    o['axis'] = phys('PETR_INJ_TBP'); o['thd'] = phys('MNFLD_PRESS_THD')
    o['limMin'] = phys('LIMIT_PRESSURE_MIN')[0] if 'LIMIT_PRESSURE_MIN' in f else o['thd'][0]
    o['limMax'] = phys('LIMIT_PRESSURE_MAX')[0] if 'LIMIT_PRESSURE_MAX' in f else o['thd'][-1]
    pair = lambda a, b: [(x, y) for x, y in zip(phys(a), phys(b)) if x and y]
    o['petrol'] = pair('PETR_INJ_TBUF', 'MNFLD_PRESS_BUF'); o['gas'] = pair('PETR_INJ_TBUF_GAS', 'MNFLD_PRESS_BUF_GAS')
    o['gasPrev'] = []   # decisão do dono: sem leitura anterior no OMEGAS; a comparação ignora a série no original também
    o['K'] = phys('MUL_ACT'); o['curP'] = phys('PETR_MNFLD_PRESS_RV'); o['curG'] = phys('GAS_MNFLD_PRESS_RV')
    o['zonesP'] = raw('ACQUIRED_ZONES_PETROL'); o['zonesG'] = raw('ACQUIRED_ZONES_GAS')
    o['cntP'] = raw('NUM_BUF_UPD_PETR'); o['cntG'] = raw('NUM_BUF_UPD_GAS')
    live = state.get('live') or {}
    if live:
        o['rpm'] = live['rpm']; o['tinjP'] = live['petrol_ms']; o['tinjG'] = live.get('gas_ms_diagnostic', 0); o['map'] = live['load_bar']
        o['fuelByte'] = 0x90
    o['n'] = int((raw('NUM_AUTOMATCH_EXECUTED') or [0])[0]); o['max'] = int((raw('MAX_AUTOMATCH') or [3])[0]); o['done'] = 0
    o['calv'] = raw('CALIBRATION_VAL_1') or [1, 3, 3, 1, 3, 3, 1, 3, 3, 1]
    o['u8_0'] = int((raw('VECT_AUTOCAL_U8_0') or [3])[0]); o['u8_1'] = int((raw('VECT_AUTOCAL_U8_1') or [3])[0])
    o['fuel'] = fuel
    o['mapBufP'] = phys('MNFLD_PRESS_BUF'); o['mapBufG'] = phys('MNFLD_PRESS_BUF_GAS')
    return o

if __name__ == '__main__':
    out = sys.argv[1]
    fuel = sys.argv[sys.argv.index('--fuel') + 1] if '--fuel' in sys.argv else 'gas'
    state = json.load(open(BASE))
    o = to_o(state, fuel)
    html = E.render(o)
    open(out, 'w').write(f'<!doctype html><meta charset="utf-8"><body style="margin:0">{html}</body>')
    print(json.dumps({'petrol_points': len(o['petrol']), 'gas_points': len(o['gas']), 'zones_gas': o['zonesG'], 'zones_petrol': o['zonesP']}))
