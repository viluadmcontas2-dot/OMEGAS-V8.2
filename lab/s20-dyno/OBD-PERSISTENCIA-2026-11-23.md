# Patch Dinamômetro Ômegas 1.0.0-persistente — 2026-11-23

**Escopo:** conexão automática OBD Bluetooth e autorização de bateria. Nenhum comando de escrita ECU nem migração de banco.

- `applicationId=com.omegas.dyno.lab`, `versionCode=10`, atualizado por `pm install-create -r`, preservando UID `10331` e dados privados.
- Certificado SHA-256: `48842bbac329eeb4ab9f7c052bf186afc37f9a64eb4b3350e746d6e9dddf9b92`, idêntico ao APK 0.9.0.
- APK SHA-256: `5c1a6c08bc5704bc87fa50370eff3c7b023e0b1357ad845eb108f17c6d1bf817`, cópia em `/sdcard/Download/Dinamometro-Omegas-1.0.0-persistente.apk`.
- Serviço `ObdKeepAliveService` foreground `connectedDevice`, `START_STICKY`, notificação permanente. `DynoRuntime` mantém um único socket OBD e um único vault entre atividades.
- Endereço OBD salvo em preferências locais. Retentativas automáticas de 2/5/10/20/30 segundos, mantendo socket único e tolerando Bluetooth temporariamente indisponível.
- UI distingue reconexão automática; pedido de isenção de bateria quando necessário, sempre requerendo autorização explícita do usuário.
- S20 já estava na allowlist do Android; FGS ativo mesmo com outro app em primeiro plano e BluetoothManager marcando o OBDII como Connected.
- Testes UI/contrato: 22/22 aprovados. Compilação Android e assinatura verificadas.
- **Ainda não validado:** recuperação espontânea após perda real de Bluetooth. Não provocamos desconexão durante condução.
- A MP48 permanece sob ciclo de vida da Activity; isso não é um patch de reconexão USB. Na primeira abertura pós-instalação surgiu "Eco MP48 divergente"; essa leitura não é tratada como conexão confirmada.
- Sem firmware flash, escrita na ECU, limpeza de dados ou publicação de capturas privadas.

Próximo: aprimorar captura independente de UI e motor de análises atualizável sem recompilar APK. A branch `OMEGASCANONICO` permanece intacta.
