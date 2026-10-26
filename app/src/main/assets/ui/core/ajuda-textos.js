(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Conteúdo da ajuda por tela (um único arquivo, português leigo). Chave = rota.
  // Cada item: { titulo, eoque, fazer, acontece, quando? }. "quando" só existe nas visões do Ajuste fino.
  // Regras: "central" (nunca a sigla), "giros do motor", "pressão da admissão"; só afirma o que o app faz hoje
  // (nada grava sem o toque do dono; toda gravação é conferida lendo de volta; existe Desfazer).
  const telas = {
    regular: {
      titulo: 'Tela Regular',
      eoque: 'É o ponto de partida. Mostra em que passo você está e a próxima coisa a fazer.',
      fazer: 'Toque no botão grande. Ele sempre diz o que fazer agora.',
      acontece: 'Ler não muda nada no carro. Só grava quando você toca em gravar.',
    },
    carro: {
      titulo: 'Tela Carro',
      eoque: 'Mostra o que o carro está fazendo agora: giros do motor, pressão da admissão, tensão e nível do gás.',
      fazer: 'Só olhe. Ligue o carro e dirija; os números mudam sozinhos.',
      acontece: 'Nada é gravado aqui. Tensão e nível do gás são estimativas, ainda não comprovadas.',
    },
    autocal: {
      titulo: 'Aprendizado',
      eoque: 'A central aprende sozinha, ponto a ponto, enquanto você dirige. O app acompanha e mostra no gráfico.',
      fazer: 'Dirija com calma, com o motor quente e variando o pé no acelerador. Se um ponto ficou ruim, limpe-o.',
      acontece: 'Quem aprende é a central, não o app. Limpar um ponto faz a central aprender aquele ponto de novo.',
      quando: 'Use no começo. Quando o aprendizado terminar, vá para Automático. Para mexer à mão, use Região ou Geral.',
    },
    refino: {
      titulo: 'Automático',
      eoque: 'Depois do aprendizado, o app confere onde o motor ainda engasga e monta uma correção pronta.',
      fazer: 'Espere a central terminar de aprender. Quando a correção estiver pronta, confira e toque em gravar.',
      acontece: 'Só grava com o seu toque. O app lê de volta para conferir e você pode desfazer.',
      quando: 'Use depois do Aprendizado. Para acertar só uma faixa de uso, use Região; para o carro todo, Geral.',
    },
    map: {
      titulo: 'Região',
      eoque: 'Ajusta o gás só numa faixa de uso, como marcha lenta ou aceleração forte.',
      fazer: 'Toque na região, escolha quanto mudar e toque em gravar.',
      acontece: 'Nada muda antes de gravar. Depois o app lê de volta na central e mostra o resultado.',
      quando: 'Use se o problema aparece só numa situação. Se aparece em todo lugar, use Geral.',
    },
    curve: {
      titulo: 'Geral',
      eoque: 'Ajusta o gás no carro todo de uma vez, por uma curva de 30 pontos.',
      fazer: 'Toque num ponto da curva, prepare a mudança e grave. Os limites da central são respeitados.',
      acontece: 'Voltar a curva a 1,0 de uma vez, se ela estava muito longe disso, pode desligar o GNV acima de 14,1 V.',
      quando: 'Use para um ajuste geral. Para uma faixa só, use Região. Para o app sugerir, use Automático.',
    },
    tools: {
      titulo: 'Ferramentas',
      eoque: 'Reúne o que não é do dia a dia: cópia de segurança, anti-apagão, gravações e o estado do app.',
      fazer: 'Escolha um cartão. Cada um diz o que faz e mostra o estado atual escrito.',
      acontece: 'Mudar um valor pede confirmação, é conferido na central e tem Desfazer.',
    },
    sessions: {
      titulo: 'Sessões',
      eoque: 'São as gravações das suas viagens, feitas pelo app, para você revisar depois.',
      fazer: 'Toque em começar a gravar ao sair e em parar ao chegar. Para levar uma gravação, salve o arquivo.',
      acontece: 'Gravar só registra o que o app lê. Não muda nada na central.',
    },
  };

  const primeiroUso = [
    { titulo: 'O que é regular o GNV',
      texto: 'O carro a gás precisa de um acerto fino para andar no GNV como anda na gasolina. Este app ajuda a fazer isso, passo a passo, sem você precisar saber de motor.' },
    { titulo: 'Ligar e conferir',
      texto: 'Conecte o cabo USB na central e ligue a ignição. O app mostra "Central conectada" quando está tudo certo. Sem conexão, ele só explica o que fazer.' },
    { titulo: 'Dirigir',
      texto: 'Dirija como no dia a dia, com o motor quente. A central aprende sozinha e o app acompanha. Nada é gravado enquanto você dirige.' },
    { titulo: 'Aplicar e conferir',
      texto: 'Quando houver uma correção pronta, você confere e toca em gravar. O app lê de volta na central para confirmar. Se não gostar, toque em Desfazer.' },
  ];

  ns.AjudaTextos = { telas, primeiroUso };
})(typeof window !== 'undefined' ? window : globalThis);
