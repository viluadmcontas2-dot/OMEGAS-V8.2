#!/usr/bin/env python3
"""Reescreve o bloco SUGESTAO de app/src/main/assets/ui/core/anti-apagao-valores.js a partir do JSON dos logs do dono (fonte única).
Uso: python3 tools/sessoes/gerar_sugestao_js.py [--check]  (--check só confere e sai com erro se estiver diferente)."""
import json, pathlib, re, sys
RAIZ = pathlib.Path(__file__).resolve().parents[2]
J = json.loads((RAIZ / 'docs/progbase/bins/sugestao-antiapagao-carro-do-dono.json').read_text(encoding='utf-8'))
def bloco():
    por = J['corpus_quedas']['por_rotacao_maxima']
    faixa = [por[k]['ativacoes_por_hora'] for k in ('1200', '1400', '1600', '1800', '2000')]
    sug = dict(valores=J['sugerido']['valores'], ligadoPior=J['resultado']['ligado_pior_minimo'], desligadoPior=J['resultado']['desligado_pior_minimo'],
               marcha=J['marcha_lenta_tipica_rpm'], maiorInicioQueda=max(e['rpm_inicio_da_queda'] for e in J['eventos']), faixaMax=J['faixa_rotacao_maxima']['maximo'],
               gasLigadoPct=J['gasolina']['LIGADO']['pct_fora_do_gnv'], gasDesligadoPct=J['gasolina']['DESLIGADO']['pct_fora_do_gnv'],
               ativMin=min(faixa), ativMax=max(faixa), quase1200=por['1200']['terminaram_abaixo_de_350'], quase1600=por['1600']['terminaram_abaixo_de_350'], quase2000=por['2000']['terminaram_abaixo_de_350'],
               tabela=[dict(m=int(k), porHora=v['ativacoes_por_hora'], quase=v['terminaram_abaixo_de_350']) for k, v in por.items() if int(k) <= 2000],
               horas=J['corpus_quedas']['horas'], gasLigado=dict(leituras=J['gasolina']['LIGADO']['leituras'], fora=J['gasolina']['LIGADO']['fora_do_gnv']))
    return '  // <SUGESTAO:INICIO> gerado de docs/progbase/bins/sugestao-antiapagao-carro-do-dono.json (tools/sessoes/gerar_sugestao_js.py); um teste confere os dois.\n  const SUGESTAO = %s;\n  // <SUGESTAO:FIM>\n' % json.dumps(sug, ensure_ascii=False)
def main():
    p = RAIZ / 'app/src/main/assets/ui/core/anti-apagao-valores.js'
    s = p.read_text(encoding='utf-8')
    novo = re.sub(r'  // <SUGESTAO:INICIO>.*?// <SUGESTAO:FIM>\n', lambda m: bloco(), s, flags=re.S)
    if '--check' in sys.argv: sys.exit(0 if novo == s else 1)
    p.write_text(novo, encoding='utf-8')
if __name__ == '__main__': main()
