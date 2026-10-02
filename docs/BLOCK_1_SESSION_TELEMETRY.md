# Bloco 1 — Sessão, telemetria e uso contínuo

## Resultado observável
A tela Agora passa a representar o estado real da sessão, e não apenas números instantâneos. A mesma autoridade de estado decide rótulos, alertas, modo de uso e permissão visual de escrita.

## Estados cobertos
- serviço indisponível;
- permissão USB pendente;
- ECU desconectada;
- telemetria atrasada;
- telemetria expirada;
- comunicação travada;
- sessão em observação;
- condução provável;
- modo oficina ativo;
- modo oficina suspenso;
- aplicativo em segundo plano.

## Regras de segurança
- modo oficina é ativado manualmente;
- conexão, timer, retomada ou sugestão nunca ativam modo oficina;
- telemetria acima de 2,5 s deixa de ser adequada para edição;
- telemetria acima de 8 s é tratada como expirada;
- RPM a partir de 1.200 indica condução provável e suspende escrita;
- desconexão desativa o modo oficina;
- somente modo oficina ativo, ECU conectada e telemetria fresca permitem confirmar escrita;
- checkpoint, ACK e readback permanecem obrigatórios;
- a inferência de condução é preventiva e não substitui velocidade real do veículo.

## Uso contínuo
Suspensão, retomada, foco e segundo plano atualizam a apresentação sem criar outro timer. O aplicativo preserva um único `setInterval`, controlado pelo shell principal.

## Evidência automatizada
- testes determinísticos de todos os estados;
- 500 combinações pseudoaleatórias de conexão, idade da telemetria, RPM, serviço e modo oficina;
- desconexão e reconexão;
- suspensão e retomada visual;
- garantia de que nenhuma transição chama o writer;
- responsividade horizontal, 1024×600 e celular vertical.

## Limites
Sem velocidade do veículo, `condução provável` é inferida por RPM e telemetria. A validação de uso real ainda exige celular, multimídia, USB e veículo.
