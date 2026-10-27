"""Peças do contrato de fluxo para mockups Design Component.

Uso: painel(conteudo) monta um aside de 370×496 com conteúdo superior livre
(até 186px) e controles fixos. Inclua LOGICA na classe Component e combine
this.fluxoVals(tipo) por último no renderVals; tipo: proposta/certo/piorou/espera.
Os dados são exemplos de mockup, nunca comandos da central. data-fluxo identifica
principal, ultima e desfazer; conta/historico também são controles persistentes.
"""


def painel(conteudo):
    return '''<div style="height:186px;flex:none">''' + conteudo + '''</div>
<div style="margin-top:auto;display:flex;flex-direction:column;gap:6px">
<button data-fluxo="conta" type="button" disabled="{{ contaBloqueada }}" onClick="{{ fluxoAssumir }}" style="height:48px;flex:none;border:1px solid #263342;border-radius:12px;background:#151e29;color:{{ contaCor }};font:600 16px Inter,Roboto,Arial,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center"><span>Refinar por minha conta</span><span style="font-size:14px">{{ contaMotivo }}</span></button>
<section data-fluxo="ultima" aria-label="Última gravação" style="height:142px;flex:none;box-sizing:border-box;padding:8px 10px;border:1px solid #263342;border-radius:12px;background:#151e29">
<div style="height:22px;font-size:17px;font-weight:700">Última gravação</div>
<div style="height:54px;font-size:14px;line-height:18px">{{ ultimaTexto }}</div>
<div style="display:flex;gap:6px">
<button data-fluxo="desfazer" type="button" disabled="{{ desfazerBloqueado }}" onClick="{{ fluxoDesfazer }}" style="flex:1;height:46px;border:1px solid {{ desfazerBorda }};border-radius:10px;background:{{ desfazerFundo }};color:{{ desfazerCor }};font:600 16px Inter,Roboto,Arial,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center"><span>Desfazer</span><span style="font-size:14px">{{ desfazerMotivo }}</span></button>
<button data-fluxo="historico" type="button" onClick="{{ fluxoHistorico }}" style="width:142px;height:46px;border:1px solid #263342;border-radius:10px;background:#151e29;color:#9aa9bc;font:600 14px Inter,Roboto,Arial,sans-serif">Últimas gravações</button>
</div></section>
<button data-fluxo="principal" type="button" disabled="{{ principalBloqueado }}" onClick="{{ fluxoGravar }}" style="height:58px;flex:none;width:100%;box-sizing:border-box;border:1px solid {{ principalBorda }};border-radius:12px;background:{{ principalFundo }};color:{{ principalCor }};font:700 20px Inter,Roboto,Arial,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center"><span>Gravar correção</span><span style="font-size:14px;font-weight:600">{{ principalMotivo }}</span></button>
</div>
<sc-if value="{{ historicoAberto }}" hint-placeholder-val="{{ false }}">
<div role="dialog" aria-label="Últimas gravações" style="position:fixed;left:440px;top:160px;width:650px;padding:20px;z-index:8;box-sizing:border-box;border:1px solid #263342;border-radius:14px;background:#151e29;font-size:18px">
<b>Últimas gravações</b><div style="margin:14px 0;line-height:1.5">{{ historicoTexto }}</div>
<button type="button" onClick="{{ fluxoHistorico }}" style="height:46px;padding:0 22px;border:1px solid #263342;border-radius:10px;background:#1b2633;color:#f4f7fb;font:600 16px Inter,Roboto,Arial,sans-serif">Voltar</button>
</div></sc-if>'''

LOGICA = r'''
  fluxoVals(tipo) {
    const st = this.state || {};
    const registros = st.registros || [];
    const n = st.gravacoes || 0;
    const espera = tipo === 'espera' && !st.assumiu;
    const pronta = (tipo === 'proposta' || tipo === 'espera') && n < 2 && !espera;
    const inicial = tipo === 'piorou' ? 'Curva K · você via Refino · 2 pontos: 1,05 → 1,17' : '';
    const ultima = st.ultima === undefined ? inicial : st.ultima;
    const motivo = espera ? 'Espere a central terminar de aprender' : pronta ? '' : 'Nada mais para gravar agora';
    const antesDepois = n === 0 ? '6 células: 200 → 210 (+5 % de gás)' : '12 pontos: 1,05 → 1,10 (Curva K)';
    return {
      principalBloqueado: !pronta, principalMotivo: motivo,
      principalFundo: pronta ? '#1f6fd8' : '#1b2633', principalCor: pronta ? '#ffffff' : '#9aa9bc', principalBorda: pronta ? '#1f6fd8' : '#263342',
      ultimaTexto: ultima || 'Nenhuma gravação ainda',
      desfazerBloqueado: !ultima, desfazerMotivo: ultima ? '' : 'Nada para desfazer',
      desfazerFundo: tipo === 'piorou' && ultima ? '#2e1618' : '#151e29', desfazerCor: ultima ? (tipo === 'piorou' ? '#ff6b6b' : '#f4f7fb') : '#9aa9bc', desfazerBorda: tipo === 'piorou' && ultima ? '#7a3036' : '#263342',
      contaBloqueada: tipo !== 'espera' || !!st.assumiu,
      contaCor: tipo === 'espera' && !st.assumiu ? '#f4f7fb' : '#9aa9bc',
      contaMotivo: tipo !== 'espera' ? 'A central já terminou' : st.assumiu ? 'Você assumiu o refino' : 'A central ainda aprende',
      previa: pronta || espera ? antesDepois : 'O app segue medindo o resultado.',
      seloOk: !!st.selo, seloTexto: st.selo || '',
      historicoAberto: !!st.historico,
      historicoTexto: [...(inicial ? [inicial] : []), ...registros].join(' · ') || 'Nenhuma gravação ainda',
      fluxoHistorico: () => this.setState({ historico: !st.historico }),
      fluxoAssumir: () => { if (tipo === 'espera' && !st.assumiu) this.setState({ assumiu: true, fase: 'conta' }); },
      fluxoGravar: () => {
        if (!pronta) return;
        const texto = (n === 0 ? 'Mapa K' : 'Curva K') + ' · você via Refino · ' + antesDepois;
        this.setState({ gravacoes: n + 1, ultima: texto, registros: [...registros, texto], fase: 'gravado', selo: 'Gravado e conferido' });
      },
      fluxoDesfazer: () => {
        if (!ultima) return;
        this.setState({ gravacoes: Math.max(0, n - 1), ultima: '', registros: [...registros, 'Desfeito: ' + ultima], fase: tipo === 'piorou' ? 'restaurado' : tipo === 'espera' ? 'conta' : 'proposta', selo: 'Desfeito e conferido' });
      }
    };
  }
'''
