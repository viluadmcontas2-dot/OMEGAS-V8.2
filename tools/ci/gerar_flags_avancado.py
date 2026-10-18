#!/usr/bin/env python3
"""Gera app/src/main/assets/ui/core/advanced-flags-data.js a partir de docs/evidence/flags-do-original.json.

Regra (a mesma que o teste tests/ui/avancado-leitura.test.cjs refaz sozinho): um bit só é DECODIFICADO se o JSON do
original traz uma caixa/lista da tela para ele (PROVADO), a condição é 'sempre' e nenhuma outra entrada condicional
dá outro sentido ao mesmo bit. Os demais bits ficam fora da tabela: a tela os conta como 'desconhecido', nunca 0.
Os rótulos em português são tradução livre dos rótulos do original (italiano/inglês) pelo nome do controle.
Uso: python3 tools/ci/gerar_flags_avancado.py [--check]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FONTE = ROOT / 'docs/evidence/flags-do-original.json'
SAIDA = ROOT / 'app/src/main/assets/ui/core/advanced-flags-data.js'
VETORES = ('FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3')

# Nome do controle da tela do original -> rótulo em português simples.
PT = {
    'ComboCarburante': 'Lista «Combustível»',
    'ComboMisuratorePressione': 'Lista «Medidor de pressão»',
    'CheckSequenzaInj': 'Sequência de injeção manual',
    'CheckElettrovalvole': 'Válvulas de gás abrem com antecipação',
    'ComboPressioneMisurata': 'Lista «Pressão medida»',
    'CheckTempAcqua': 'Trocar de combustível pela temperatura da água',
    'CheckTempGas': 'Trocar de combustível pela temperatura do gás',
    'CheckTempMotore': 'Usar o sensor de temperatura do motor do carro',
    'MenuControlloIniettori': 'Controle dos injetores habilitado',
    'ComboTurbo': 'Lista «Turbo»',
    'CheckIstantaneo': 'Passar da gasolina ao gás em modo sequencial',
    'CheckParzializza': 'Gasolina entra sozinha em rotação alta',
    'CheckAdattativita': 'Adaptatividade OBD',
    'CheckTagliaExtraIniettate': 'Corte das injeções extras',
    'CheckPartenzaGasCaldo': 'Partida a gás com o motor quente',
    'CheckValvetronik': 'Valvetronic',
    'CheckAzzeraErroriObd': 'Apagar erros OBD',
    'CheckDisabilitaCommutatore': 'Desabilitar o comutador',
    'CheckRiscaldamento': 'Aquecimento dos injetores',
    'CheckAzzeraBlackListObd': 'Apagar erros OBD de forma seletiva',
    'CheckSpegniBuzzer': 'Buzzer por tempo',
    'CheckSplitFuel': 'Dividir combustível (split fuel)',
    'CheckStartAndStop': 'Start & Stop',
    'CheckSwitchOnDiagnosis': 'Ligar o diagnóstico (SwitchOnDiagnosis)',
    'CheckContributi': 'Mapa de adição de gasolina',
    'CheckFlexFuel': 'Flex fuel (álcool)',
    'CheckboxPreheatOnlyAfterSleepMode': 'Aquecer injetores só depois do modo repouso',
    'CheckIncrementoMaxCorrente': 'Aumentar a corrente máxima na troca para gás',
    'CheckboxPreheatAsyncMode': 'Aquecimento dos injetores assíncrono',
    'CheckInjAcquisition': 'Sequência de injeção manual (aquisição)',
    'CheckFlashLube': 'Diagnóstico do nível de lubrificante',
    'CheckTaglioPompaBenzina': 'Corte da bomba de gasolina',
    'CheckPetrolObdConnection': 'Conexão OBD quando roda a gasolina',
    'CheckCambioConSovrapp': 'Troca com sobreposição (= progressões)',
    'CheckAntiStallo': 'Anti-apagão (anti-stallo)',
    'CheckDeleteErrorMotoreSpento': 'Apagar erros OBD com o motor desligado',
    'CheckFlashLubeSuTempAcqua': 'Diagnóstico da temperatura da água (sistema VP)',
    'CheckOutDiagInfoSuKLine': 'Nível alto na linha K em erro de diagnóstico',
    'CheckUsaCanPerObd': 'Usar CAN para o OBD',
    'CheckAttivaPompaInRiserva': 'Reativar a bomba na reserva',
    'CheckRiconoscimentoPieno': 'Reconhecimento de tanque cheio',
    'CheckGiriRuotaFonica': 'Roda fônica',
    'CheckEnableLandiConnect': 'Landi Connect habilitado',
    'CheckCambioAutomatico': 'Troca automática',
    'chkRitardoPassaggioInMinuti': 'Atraso da passagem em minutos',
    'CheckBoxEnableExtLedKLine': 'LED externo na linha K',
    'CheckBoxEnDiagDtcPowertrain': 'Diagnóstico DTC do Powertrain',
}


def decodificados(dados):
    por_bit = {}
    for f in dados['flags']:
        if f['vetor'] in VETORES:
            por_bit.setdefault((f['vetor'], f['palavra'], int(f['mascara'], 16)), []).append(f)
    saida = {v: [] for v in VETORES}
    for (vetor, palavra, mascara), itens in sorted(por_bit.items()):
        provados = [f for f in itens if f['controles']]
        if len(provados) != 1 or provados[0]['condicao'] != 'sempre':
            continue  # sem caixa, ou ambíguo (outra entrada condicional dá outro sentido ao bit)
        f = provados[0]
        principal = next(c for c in f['controles'] if c['papel'].startswith(('caixa', 'seletor')))
        saida[vetor].append({
            'palavra': palavra, 'mascara': mascara, 'caso': f['caso'], 'controle': principal['nome'],
            'tipo': 'lista' if principal['papel'].startswith('seletor') else 'caixa',
            'rotulo': PT[principal['nome']],
        })
    return saida


def gerar():
    dados = json.loads(FONTE.read_text(encoding='utf-8'))
    codigos = {v['vetor']: v['serial_code'] for v in dados['flags'] if v['vetor'] in VETORES}
    tabela = decodificados(dados)
    linhas = [
        '// GERADO por tools/ci/gerar_flags_avancado.py a partir de docs/evidence/flags-do-original.json. Não editar à mão.',
        '// Só entram bits PROVADOS (caixa ou lista da tela do ProgBase original, condição "sempre", sem ambiguidade).',
        '(function (root) {',
        "  'use strict';",
        '  const ns = root.OmegasUi = root.OmegasUi || {};',
        '  ns.AdvancedFlagsData = {',
    ]
    for i, v in enumerate(VETORES):
        linhas.append(f'    {v}: {{ code: {codigos[v]}, bits: [')
        for b in tabela[v]:
            linhas.append('      { word: %d, mask: 0x%04X, kase: %d, control: %s, kind: %s, label: %s },' % (
                b['palavra'], b['mascara'], b['caso'], json.dumps(b['controle']), json.dumps(b['tipo']),
                json.dumps(b['rotulo'], ensure_ascii=False)))
        linhas.append('    ] }' + (',' if i < len(VETORES) - 1 else ''))
    linhas += ['  };', "})(typeof window !== 'undefined' ? window : globalThis);", '']
    return '\n'.join(linhas)


if __name__ == '__main__':
    texto = gerar()
    if '--check' in sys.argv:
        if not SAIDA.exists() or SAIDA.read_text(encoding='utf-8') != texto:
            raise SystemExit('advanced-flags-data.js fora de sincronia com flags-do-original.json')
        print('ok')
    else:
        SAIDA.write_text(texto, encoding='utf-8')
        print('gravado', SAIDA.relative_to(ROOT))
