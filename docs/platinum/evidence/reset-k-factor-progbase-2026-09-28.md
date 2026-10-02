# Ômegas Platina — prova direta do Reset k-factor do ProgBase

Data: 2026-09-28  
Fonte: `Copy of ProgBase (3).exe` do Drive `OMEGAS`  
SHA-256 confirmado: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

## Conclusão

`ActionResetKFactorExecute` não é o write do vetor EEPROM `VECT_AUTOCAL_EE 0x0164[4]`.

O handler canônico `0x0051A070` percorre o componente `MUL_ACT` do `TAutoCalDM` e atribui **1.0** a todos os elementos. No wire do Ômegas, `MUL_ACT` é `0x0161[30]`, Q14, portanto 1.0 = `0x4000`.

O contrato v1 em `tests/fixtures/progbase-autocal-dump-contract-v1.json` fica preservado apenas como evidência histórica e está **superado especificamente em protocol.resetKFactor** pelo v2.

## Cadeia de prova

### 1. Method table

No executável original, a tabela de métodos contém:

`ActionResetKFactorExecute -> 0x0051A070`

A associação aparece diretamente junto ao nome do callback; não depende de inferência por proximidade de strings.

### 2. RTTI do TAutoCalDM

A tabela RTTI de campos do `TAutoCalDM` coloca:

- `NUM_BUF_UPD_GAS` em offset `0x009C`;
- `MUL_ACT` em offset `0x00A0`;
- `MNFLD_PRESS_BUF_GAS` em offset `0x00A4`.

O DFM do próprio `MUL_ACT` confirma `SerialCode=0x0161`, `DataLength=2` e transformação de fator.

Separadamente, `TAutoCalDM_EE` contém `VECT_AUTOCAL_EE`, `SerialCode=0x0164`, `ArrayDimension=4`. É outra superfície.

### 3. Handler 0x0051A070

Trecho funcional reconstruído do x86:

```text
51A104  xor ebx, ebx
51A108  mov eax, [0x00AEBEEC]
51A10D  push 0x3FF00000
51A112  push 0
51A114  mov edx, [eax]
51A116  mov eax, [edx+0xA0]
51A11C  mov edx, ebx
51A11E  call 0x00979EE8
51A123  inc ebx
51A124  ...
51A12C  call 0x00513208
51A131  cmp ebx, eax
51A133  jl  0x0051A108
```

`0x3FF0000000000000` é o double IEEE-754 **1.0**.

O helper `0x00513208` obtém o número de elementos da mesma propriedade `+0xA0`, fechando que o handler itera o vetor inteiro.

### 4. Implicação de protocolo

A Platina mantém o comportamento equivalente usando os 30 elementos de `MUL_ACT` e exige readback completo:

- ponto 0: `14 61 01 00 00 40 B6`;
- ...
- ponto 29: `14 61 01 1D 00 40 D3`.

A implementação não transforma `VECT_AUTOCAL_EE` em sinônimo de Curva K.

## Regra de segurança

Esta prova autoriza somente a equivalência do reset explícito escolhido pelo operador. Não autoriza reset automático, AutoMatch host-side automático nem qualquer writer derivado por hipótese.
