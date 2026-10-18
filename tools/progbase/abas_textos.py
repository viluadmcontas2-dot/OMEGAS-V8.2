#!/usr/bin/env python3
"""Textos didaticos (linguagem leiga) e citacoes do manual Landi Renzo Omegas SW 2.16.4 C para os controles das abas."""
import re

TEXTOS = {}


def _man(nome, pag, frase):
    TEXTOS[nome] = (frase, 'manual SW 2.16.4 C p.%s' % pag, 'OBS-COD')


def _dica(nome, chave, frase):
    TEXTOS[nome] = (frase, 'dica do proprio ProgBase (%s)' % chave, 'OBS-COD')


def _rot(nome, frase):
    TEXTOS[nome] = (frase + ' [so pelo rotulo; sem fonte alem dele]', 'sem fonte (so o rotulo do componente)', 'INFERIDO')


# ---- TabCambio
_man('DrawDelayGasTemp', 13, 'Tabela de espera: quantos segundos o sistema aguarda, conforme a temperatura, antes de passar da gasolina para o gas (variante por temperatura do gas).')
_man('DrawGridPassaggio', 13, 'Tabela de espera: quantos segundos o sistema aguarda, conforme a temperatura da agua do motor, antes de passar da gasolina para o gas; cada celula pode ser mudada em modo absoluto, linear ou percentual.')
_man('DrawGridPassaggioGas', 13, 'Tabela de espera pela temperatura do gas (usada quando nao ha sensor de agua): segundos de atraso antes de passar da gasolina para o gas.')
_man('EditSovrapposizione', 14, 'Tempo (0 a 2 s) em que a injecao de gasolina e a de gas ficam abertas juntas na troca, para o gas encher o regulador e evitar tranco depois; normalmente fica em 0.')
_dica('EditCicchetto', 'HINTCICCHETTO', 'Tempo da pequena injecao de gas de "escorva" (priming) que prepara o circuito antes de rodar a gas.')
_rot('EditCicchettoRpm', 'Rotacao abaixo da qual vale a injecao de escorva (limiar de rpm "crank", durante a partida).')
_man('CheckSegnaleGiri', 11, 'Marcada: a ECU le a rotacao pelo fio do sinal; desmarcada: o fio marrom fica solto e a rotacao e calculada pelos tempos de injecao (e o tipo de ignicao deixa de valer).')
_man('CheckDisabilitaCommutatore', 13, 'Forca o motor a rodar so a gas (menos na partida a frio, com erro de diagnostico salvo ou sem combustivel), ignorando o botao de troca do painel.')
_man('CheckValvetronik', 12, 'Faz o sistema continuar funcionando quando o sinal de rotacao some (motores Valvetronic), considerando so a ignicao ligada.')
_man('ComboTipoIniettore', 10, 'Tipo de injetor de gas instalado (Matrix, Keihin, Landi...); a ECU o reconhece pela fiacao e a lista so confirma.')
_man('EditTipoIniettore', 10, 'Valor numerico do tipo de injetor de gas instalado (a mesma escolha da lista "Tipo de injetor").')
_man('ComboTurbo', 10, 'Informa se o motor e aspirado ou turbo.')
_man('ComboSegnaleGiri', 11, 'Tipo do sinal de rotacao: "Padrao" para sinais de 5 a 12 V e "Fraco" para sinais de 2 a 5 V (ex.: transistor da bobina).')
_man('ComboCilindri', 11, 'Numero de cilindros do motor (2, 3, 4, 4 com 2 injetores, 5, 6 ou 8); a ECU usa para calcular o regime.')
_man('ComboAccensione', 11, 'Tipo de ignicao (bobina simples, dupla, tacometro, tacometro 2) para a ECU calcular a rotacao a partir do sinal.')
_man('ComboCambio', 12, 'Quando trocar de gasolina para gas: acelerando (passou da rotacao de troca) ou desacelerando (rotacao acima do limite e corte de gasolina), conforme o motor.')
_man('EditGiriCambio', 12, 'Rotacao minima (550 a 3000 rpm, padrao 1600) acima da qual a ECU troca de gasolina para gas.')
_man('EditTempMotore', 12, 'Temperatura da agua (40 a 90 graus C) que, junto com a rotacao, libera a troca de gasolina para gas.')
_man('EditTempCambio', 22, 'Temperatura do gas acima da qual a troca para gas e permitida (quando a troca por temperatura do gas esta ligada).')
_man('EditRitMin', 13, 'Atraso minimo (35 a 240 s) depois da partida antes de passar de gasolina para gas.')
_dica('EditTInjBenzMaxPerCambio', 'HINTTINJ...', 'Tempo de injecao de gasolina acima do qual a troca para gas fica temporariamente proibida (motor muito exigido).')
_man('ButtonReset', 14, 'Apaga todos os parametros e volta aos parametros de base da central; o programa pede confirmacao antes.')
_man('ComboCarburante', 10, 'Escolhe o combustivel usado: GLP ou metano (GNV).')
_man('EditCilindrata', 11, 'Cilindrada do motor em cm3 (900 a 8000); o programa calcula a cilindrada por cilindro e manda um mapa-base para a calibracao partir de um ponto razoavel.')
_man('ComboTaglia', 10, 'Tamanho do injetor instalado (pequeno, medio ou grande nos Landi), conforme a potencia por cilindro.')
_man('CheckSpegniBuzzer', 13, 'Buzzer com tempo: o alarme toca so 5 segundos quando o sistema volta para gasolina por falta de gas ou falha.')
_man('CheckPartenzaGasCaldo', 12, 'Se a agua estiver acima da temperatura definida (50 a 90 graus C) na partida, o motor ja parte direto a gas.')
_man('EditTempGasCaldo', 12, 'Temperatura da agua (50 a 90 graus C) acima da qual a partida e feita direto a gas.')
_man('CheckStartAndStop', 12, 'Para carros Start & Stop: o sistema segue funcionando quando o motor para e o sinal de rotacao some, e o motor religa a gas sem demora.')
_dica('EditSogliaGiri', 'HINTSOGLIAGIRI', 'Limiar de tensao do sinal de rotacao (usado quando o tipo de sinal e "Personalizado").')
_dica('EditIstantaneoValue', 'HINTISTANTANEOVALUE', 'Numero de ciclos do motor para passar de gasolina a gas; aumente se a troca estiver brusca.')
_dica('CheckFlexFuel', 'HINTFLEXFUEL', 'Liga o modo flex fuel.')
_rot('EditCicchettoInj', 'Tempo de injecao do gas na escorva (cicchetto).')
_rot('EditCicchettoNum', 'Quantidade de injecoes de gas na escorva (cicchetto).')
_rot('CheckGiriAlberoCamme', 'Usa o sinal do arvore de comando (came) para medir a rotacao.')
_rot('EditNumDenti', 'Numero de dentes da roda do comando usada para medir a rotacao.')
_rot('EditRpmTachimetro', 'Voce digita a rotacao que o conta-giros do carro mostra e aperta Enter, para o programa ajustar a leitura do sinal de rotacao.')
_rot('ButtonSequenceAcquisition', 'Inicia a aquisicao (leitura) da sequencia de injecao do motor.')
_rot('CheckInjAcquisition', 'Marca para informar a sequencia de injecao manualmente em vez de adquirir.')
_rot('ComboFullGroup', 'Escolhe o modo de grupo de injetores (full group).')
_rot('ButtonSwitchSettings', 'Abre os ajustes do comutador (switch).')
_man('CheckCambioConSovrapp', 14, 'Troca com sobreposicao: gasolina e gas abertos juntos durante a troca, para evitar irregularidade depois dela (nesta ECU o parametro nao respondeu no LOG).')
_rot('EditParamProgress0', 'Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO.')
_rot('EditParamProgress1', 'Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO.')
_rot('EditParamProgress2', 'Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO.')
_rot('EditParamProgress3', 'Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO.')
_rot('CheckProgressioni', 'Liga a troca gradual de combustivel ("progressoes") nas situacoes da tabela (saida do corte, marcha lenta, troca...).')
_rot('DrawGridProgressioni', 'Tabela das progressoes: para cada situacao (saida do corte, minimo, troca, outras funcoes de gasolina), numeros de injecoes - significado de cada coluna DESCONHECIDO.')
_rot('CheckGiriRuotaFonica', 'Usa a roda fonica (roda dentada) como fonte do sinal de rotacao.')
_rot('chkRitardoPassaggioInMinuti', 'Faz a tabela de atraso da troca ser lida em minutos em vez de segundos.')
_rot('CheckBoxEnableExtLedKLine', 'Liga um LED externo ligado a linha K.')
# ---- TabLambda
_man('ComboTipoLambda', 16, 'Tipo da sonda lambda do carro (0-1 V, 0-5 V, 5-0 V ou 0,8-1,6 V), para a ECU poder conferir o sinal.')
_man('ComboNumeroSonde', 15, 'Quantas sondas lambda (antes do catalisador) o carro tem; com duas, os tempos e sinais sao duplicados.')
_man('EditCorrBancata2', 15, 'Correcao (-20 a +20) da mistura do segundo banco quando os dois bancos estao levemente desequilibrados; nao tem efeito em motor de 4 cilindros.')
_man('ComboSonda1', 16, 'Lembrete de onde esta ligado o fio violeta da sonda 1: nao conectado, antes (Pre) ou depois (Post) do catalisador.')
_man('ComboSonda2', 16, 'Lembrete de onde esta ligado o fio violeta/preto da sonda 2: nao conectado, antes ou depois do catalisador.')
for _n in ('EditRifLambda', 'EditRitardoLbd', 'EditSondaFredda', 'EditRifSupFredda', 'EditRifInfFredda', 'EditRifSupCalda', 'EditRifInfCalda'):
    TEXTOS[_n] = ('Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele]',
                  'sem fonte (so o rotulo do componente)', 'INFERIDO')
_rot('CheckFlashLube', 'Liga a diagnostico do nivel de oleo/lubrificante (Flash Lube) no sistema.')
_rot('EditFlashLube0', 'Tensao-limite do nivel de reserva do lubrificante.')
_rot('EditFlashLube1', 'Tempo de filtro (segundos) antes de aceitar a leitura do nivel de lubrificante.')
_rot('EditFlashLube2', 'Horas de nivel baixo antes de voltar para gasolina (retropassagem).')
_rot('EditFlashLube3', 'Horas de nivel alto para zerar o diagnostico.')
# ---- TabEmissioni
_rot('DrawGridEmissioniMultipoint', 'Tabela de pontos da emulacao da sonda lambda (varios pontos).')
_rot('EditLivEmulAlto', 'Nivel de tensao alto da emulacao da sonda lambda (o que a ECU da gasolina "ve").')
_rot('EditLivEmulBasso', 'Nivel de tensao baixo da emulacao da sonda lambda.')
_rot('EditRitardoGiriEmulazione', 'Atraso para a emulacao comecar depois de atingir a rotacao.')
_rot('CheckInibisciEmulazione', 'Desliga a emulacao da lambda em rotacao baixa.')
_man('EditSmagrRientroCutoff', 17, 'Empobrece ou enriquece a mistura (em %, de -100 a 100) logo depois do corte de desaceleracao; util se o motor apaga ou quase apaga na volta a marcha lenta.')
_man('EditNumeroIniettateSmagr', 17, 'Por quantas injecoes (0 a 255) vale o ajuste de empobrecimento na saida do corte.')
_rot('EditCorrIntInf', 'Limite inferior da correcao integral.')
_rot('EditCorrIntSup', 'Limite superior da correcao integral.')
_rot('DrawGridEmissioni', 'Tabela da emulacao de emissoes (valores por ponto).')
_rot('DrawGridAnticipoMultipoint', 'Tabela de antecipacao (varios pontos) da emulacao.')
_rot('EditLivEmulStabilizzato', 'Nivel de tensao da emulacao quando a mistura esta estabilizada.')
_rot('EditLivEmulCutoff', 'Nivel de tensao da emulacao durante o corte de desaceleracao.')
# ---- TabSensore
_man('ComboSensore', 18, 'Tipo do sensor de nivel de gas do tanque (AEB, 0-90 ohm, Landi Renzo, Sensata HD ou nao padrao); "nao padrao" deixa voce ajustar os niveis.')
_man('EditLivello1', 18, 'Ponto de reserva do sensor de nivel (so editavel nos tipos nao padrao); termine apertando Aceitar.')
_man('EditLivello2', 18, 'Ponto de 1/4 do tanque no sensor de nivel (so nos tipos nao padrao).')
_man('EditLivello3', 18, 'Ponto de 2/4 do tanque no sensor de nivel (so nos tipos nao padrao).')
_man('EditLivello4', 18, 'Ponto de 3/4 do tanque no sensor de nivel (so nos tipos nao padrao).')
_dica('ButtonAccetta', 'HINTBUTTONACCETTA', 'Confirma os niveis do sensor nao padrao digitados.')
_dica('ButtonMinLevel', 'HINTBUTTONMINLEVEL', 'Grava o nivel minimo do sensor (tanque vazio) lido no momento.')
_dica('ButtonMaxLevel', 'HINTBUTTONMAXLEVEL', 'Grava o nivel maximo do sensor (tanque cheio) lido no momento.')
_dica('ButtonDoLevel', 'HINTBUTTONDOLEVEL', 'Procura sozinho os niveis do sensor.')
_man('CheckRitornoBenzina', 19, 'Volta sozinho para gasolina quando a pressao do gas cai; sem isso, com tanque vazio o motor roda irregular ate apagar.')
_man('EditRitornoBenzina', 19, 'Tempo (0 a 20 s) de pressao baixa antes de voltar para gasolina.')
_dica('CheckPassPRail', 'HINTCHECKPASSPRAIL', 'Volta automatica para gasolina usando a pressao do rail.')
_man('CheckBassaPressione', 19, 'Deixa definir o valor de pressao baixa que dispara a volta para gasolina, independente do item de volta automatica.')
_man('EditBassaPressione', 19, 'Pressao (0,4 a 2 bar) abaixo da qual a ECU volta para gasolina, com buzzer e LEDs de gasolina e gas acesos.')
_man('CheckElettrovalvole', 19, 'Abre as eletrovalvulas de gas uns 5 segundos antes dos injetores; a bomba de gasolina nao e desligada; pode dar problema com chips de avanco.')
_rot('CheckAlimentazione', 'Usa o fio da EV2 para alimentar o STAP.')
_man('DrawGridPressPass', 19, 'Tabela de pressoes (por tempo de injecao) abaixo das quais volta para gasolina; cada valor pode ser mudado em absoluto, linear ou percentual (Landi e Keihin, nao Matrix).')
_dica('EditBassaPressioneMp', 'HINTBASSAPRESSIONEMP', 'Valor de pressao baixa para a volta a gasolina quando o sensor MAP esta configurado.')
_rot('EditAnticipoEV1', 'Quanto antes a eletrovalvula 1 abre em relacao a injecao.')
_rot('EditAnticipoEV2', 'Quanto antes a eletrovalvula 2 abre em relacao a injecao.')
_rot('CheckTaglioPompaBenzina', 'Corta (desliga) a bomba de gasolina enquanto roda a gas.')
_rot('EditRpmAttivPompa', 'Rotacao em que a bomba de gasolina e reativada na contribuicao automatica de gasolina.')
_rot('EditRpmAnticipoPompa', 'Rotacao de antecipacao para reativar a bomba na contribuicao voluntaria.')
_rot('EditTempoAttivPompa', 'Tempo minimo que a bomba de gasolina fica ligada quando reativada.')
_rot('EditPeriodoRiattivPompa', 'Periodo entre reativacoes da bomba de gasolina.')
_rot('CheckEnabledDhlpDiagnosis', 'Liga o diagnostico de vazamento de gas.')
_rot('CheckAttivaPompaInRiserva', 'Reativa a bomba de gasolina quando o gas esta na reserva.')
_rot('CheckRiconoscimentoPieno', 'Reconhece quando o tanque de gas foi cheio.')
_rot('CheckBoxEV2NotConnected', 'Informa que a eletrovalvula 2 nao esta ligada.')
# ---- TabPressione
_man('ComboPressioneMisurata', 21, 'Define se existe sensor MAP ligado (fio vermelho/amarelo no sensor original); sem MAP, a volta a gasolina usa a tabela de pressao nos trilhos.')
_man('ComboTipoMisuratore', 20, 'Tipo do sensor de pressao do gas instalado (Bosch/Sensata, LRE 165 Matrix, AEB 025...), conforme a versao do sistema.')
_rot('ComboMisuratorePressione', 'Escolhe qual medidor de pressao esta disponivel.')
_rot('EditPendenzaDiff', 'Inclinacao (ganho) da reta tensao-pressao do sensor de pressao diferencial.')
_rot('EditOffsetDiff', 'Deslocamento (offset) da reta tensao-pressao do sensor de pressao diferencial.')
_man('ButtonRicercaParametri', 21, 'Procedimento guiado que aprende o sensor MAP original do carro: chave ligada (1 bar), troca a 3000 rpm e leitura em marcha lenta.')
_rot('EditPendenza', 'Inclinacao (ganho) da reta tensao-pressao do sensor MAP externo.')
_rot('EditOffset', 'Deslocamento (offset) da reta tensao-pressao do sensor MAP externo.')
_rot('CheckMapConfigurato', 'Indica que o sensor MAP externo ja foi configurado corretamente.')
_rot('EditSogliaPress', 'Limiar de fluxo subsonico (condicao de escoamento do gas no injetor).')
_rot('EditTempAcqua', 'Temperatura da agua usada na verificacao do MAP.')
_rot('EditSogliaMap', 'Limiar de tensao do MAP.')
_rot('EditPressRid', 'Pressao do redutor (regulador) em bar.')
# ---- TabTemp
_man('CheckTempMotore', 23, 'Usa o sensor de temperatura original do motor do carro em vez de um sensor extra; os parametros do sinal sao ajustados com um scanner OBD.')
_man('CheckTempAcqua', 22, 'Liga a troca condicionada a temperatura da agua (so disponivel se o fio laranja estiver ligado ao sensor); nao se marca na mao.')
_man('CheckTempGas', 22, 'Liga a troca condicionada a temperatura do gas lida no sensor da linha de baixa pressao; depende da ligacao do fio, nao se marca na mao.')
_man('EditDeltaT', 23, 'Velocidade com que o programa adquire a leitura de tensao do sensor do carro.')
_man('EditRPullUp', 23, 'Valor do resistor pull-up da central de gasolina (se nao souber, deixe o padrao).')
_man('EditTMax', 23, 'Temperatura maxima da parametrizacao do sensor do carro (sugerido 100 graus C).')
_man('EditTMin', 23, 'Temperatura minima da parametrizacao do sensor do carro (sugerido: a menor lida com o motor frio no scanner OBD).')
_man('ComboTempRiduttore', 22, 'Tipo do sensor de temperatura do gas instalado (o 4K7 e o padrao nos kits Landi e Keihin).')
_man('ButtonNuoviParametri', 23, 'Abre a tela para ajustar os parametros do sensor de temperatura do motor (pull-up, delta T, Tmin e Tmax) e montar a tabela temperatura x tensao.')
_man('DrawGridTemp', 23, 'Tabela temperatura x tensao do sensor do carro; as tensoes devem sempre diminuir quando a temperatura sobe.')
_rot('CheckFlashLubeSuTempAcqua', 'Diagnostico no sensor de temperatura da agua para o sistema VP.')
# ---- TabKCoefficienti e sub-abas
_man('DrawGridK', 24, 'Mapa principal de carburacao: rotacao na horizontal, tempo de injecao de gasolina na vertical e o coeficiente K em cada celula.')
_man('ButtonTaratura', 25, 'Inicia a calibracao manual ponto a ponto do mapa K.')
_man('ButtonModificaRifTar', 24, 'Muda os pontos de referencia do mapa (rotacao 200 a 8000 rpm; tempos de injecao 0 a 40 ms); faca antes de calibrar.')
_rot('ButtonCommutatore', 'Alterna o comutador gas/gasolina pelo programa.')
_rot('RadioMapPetrol', 'Mostra/edita o mapa K do combustivel gasolina.')
_rot('RadioMapAlcol', 'Mostra/edita o mapa K do combustivel alcool.')
_rot('RadioMapAdatta', 'Mostra/edita o mapa K usado como alvo da adaptatividade OBD.')
_man('DrawGridOpenLoop', 25, 'Tabela K em malha aberta (sem realimentacao da lambda); o quadrado vermelho em "Correctors" indica que esta em malha aberta.')
_man('ComboK', 25, 'Fator K: multiplica os valores do mapa K por 1,5 ou 2; so para quando a media do mapa passa de 230-240 apos a autocalibracao e a mistura ainda esta pobre; depois e obrigatorio refazer a autocalibracao.')
_rot('EditKMappaNeutro', 'Valor K do mapa neutro (usado como referencia do mapa).')
_rot('EditTInj1', 'Primeiro ponto de tempo de injecao da curva do K de referencia.')
_rot('EditK1', 'Valor K no primeiro ponto da curva de referencia.')
_rot('EditTInj2', 'Segundo ponto de tempo de injecao da curva do K de referencia.')
_rot('EditK2', 'Valor K no segundo ponto da curva de referencia.')
_man('DrawGridTempRid', 24, 'Compensacao do tempo de injecao do gas pela temperatura do gas.')
_man('DrawGridTempMot', 24, 'Compensacao do tempo de injecao do gas pela temperatura da agua.')
_man('DrawGridPressDiff', 24, 'Compensacao do tempo de injecao do gas pela pressao do gas.')
_rot('DrawGridPressAss', 'Correcoes de pressao em funcao da relacao pressao do coletor / pressao do trilho.')
_man('EditTempoMortoBenz', 25, 'Tempo morto do injetor de gasolina: compensa o atraso de abrir/fechar; o manual recomenda nao mexer.')
_man('EditTempoMortoGas', 25, 'Tempo morto do injetor de gas: compensa o atraso de abrir/fechar; o manual recomenda nao mexer.')
_man('EditTempoBenzExtra', 26, 'Tempo morto da gasolina nas extra-injecoes (0 a 5 ms); em ECU 184-188 coloque 1,4 para cortar as extra-injecoes.')
_man('EditTempoGasExtra', 26, 'Tempo morto do gas nas extra-injecoes (0 a 5 ms), onde o injetor de gas nao e linear.')
_man('DrawGridTempiLr', 25, 'Tabela de tempos mortos dos injetores de gas Landi Renzo (ms), por ponto de funcionamento.')


def carregar_manual(caminho):
    """Indice do manual: lista de (linha, pagina, texto_normalizado)."""
    if not caminho:
        return None
    with open(caminho, encoding='utf-8', errors='replace') as fh:
        linhas = fh.read().split('\n')
    ff, out = 0, []
    for n, l in enumerate(linhas, 1):
        ff += l.count('\f')
        t = re.sub(r'\s+', ' ', l.replace('\f', ' ')).strip()
        out.append((n, ff + 1, t))
    return out


def norm(s):
    return re.sub(r'[^a-z0-9 ]+', ' ', str(s).lower().replace('&&', ' ')).split()


def citar_manual(idx, rec):
    if not idx:
        return None
    cands = []
    for k in ('rotulo', 'rotulo_do_campo'):
        r = rec.get(k) or {}
        if r.get('eng'):
            cands.append(r['eng'])
    for e in cands:
        pal = norm(e)
        if len(pal) < 2:
            continue
        alvo = ' '.join(pal)
        for n, pg, t in idx:
            if alvo in ' '.join(norm(t)):
                trecho = ' '.join(x[2] for x in idx[n - 1:n + 3] if x[2])
                return dict(pagina=pg, linha=n, trecho=trecho[:400], chave=e, marca='OBS-COD',
                            fonte='sw.txt (manual SW 2.16.4 C), linha %d, pagina %d do PDF' % (n, pg))
    return None


def explicar(c, rec, idx, i18n):
    t = TEXTOS.get(c.nome)
    if t:
        return dict(texto=t[0], fonte=t[1], marca=t[2])
    return dict(texto=None, fonte='sem fonte', marca='DESCONHECIDO')


GENERICO_EDIT = (
    "- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto "
    "`(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. "
    "Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico "
    "`EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): "
    "o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). "
    "Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.\n"
    "- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o "
    "handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.\n"
    "- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).\n"
    "- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). "
    "Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).\n"
    "- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.")
