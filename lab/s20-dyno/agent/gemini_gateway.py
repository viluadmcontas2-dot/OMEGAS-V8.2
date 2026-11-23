"""Gemini UX and API availability states. No secrets or vehicle controls here."""
PREFERRED=("gemini-3.8-flash","gemini-3.6-flash","gemini-3.1-flash-lite")

def preferred_models(available, limit=3):
    keys={x.split("/")[-1] for x in available}
    return [m for m in PREFERRED if m in keys][:max(0,int(limit))]

def normalized_gateway_status(configured,authenticated,status,http_status=None,**rest):
    payload={"configured":bool(configured),"authenticated":bool(authenticated),"status":str(status)}
    if http_status is not None:payload["httpStatus"]=int(http_status)
    payload.update(rest)
    return payload

def status_label(s):
    state=s.get("status") or "NAO_DISPONIVEL"
    if not s.get("configured") or state=="SEM_CHAVE":
        return "Gemini não configurado. A análise local continua funcionando."
    if state=="CREDENCIAL_INVALIDA":
        return "Chave inválida ou sem permissão. Corrija no Google AI Studio."
    if state=="VALIDANDO_CHAVE":
        return "Chave salva no S20. Validando acesso à API Google..."
    if state=="CREDENCIAL_VALIDADA":
        return "Chave validada na API Google. Aguardando evidências para analisar."
    if state=="ANALISE_EM_ANDAMENTO":
        return "Gemini autenticado. Analisando evidências agora..."
    if state=="SUGESTAO_NAO_E_PROVA":
        return "Gemini respondeu. Interpretação disponível — não é prova."
    if state=="MODELO_INDISPONIVEL":
        return "Gemini autenticado; modelo com sobrecarga (HTTP 503). Recuperação automática ativa."
    if state=="LIMITE_API":
        return "Gemini autenticado; limite de requisições da API atingido. Análise local ativa."
    if state=="FALHA_REDE":
        return "Chave salva; falha de rede. Análise local continua funcionando."
    if state=="SEM_EVIDENCIAS":
        return "Gemini configurado; aguardando medições atuais."
    return "Gemini configurado; analisador local ativo."

def generation_settings(model):
    """Small reasoning budget prevents thought tokens consuming the answer."""
    level = 'MINIMAL' if model in ('gemini-3.6-flash','gemini-3.1-flash-lite') else 'LOW'
    return {'temperature':0.15,'maxOutputTokens':2400,
            'thinkingConfig':{'thinkingLevel':level}}

def complete_gemini_response(body):
    candidates=body.get('candidates') or []
    if not candidates or candidates[0].get('finishReason')!='STOP':return False
    return any(isinstance(p.get('text'),str) and p['text'].strip()
       for p in candidates[0].get('content',{}).get('parts',[]))
