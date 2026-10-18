# ProgBase original rodando no Wine (2026-10-18)

- Ambiente: Wine 9.0 (win32) + tela virtual 1280×720 (Xvfb), sem ECU. ProgBase.exe sha256 8a2d297c… [OBS]
- Só o `.exe`: erro "It is not possible to start the program, very important files missing". [OBS]
- Com `Resources.dll` (Drive, 3.289.600 B) ao lado do exe: o programa abre e mostra a **tela de licença** da Landirenzo (abas License / User code / Product name; botões "Request a new license" e "Load a new license"; "Base code" + "Unlock code"). Imagem: `img/progbase-original-no-wine-tela-de-licenca.png`. [OBS]
- Não passa daí sem uma licença válida (`License.dat`, 280 B, está na pasta "ARQUIVOS OMEGAS/Landi Renzo Omegas", que ainda pede login no Drive). A licença é amarrada a um "Base code" da máquina; **não contornamos o licenciamento do fabricante**. Para ver a aba "Iniettori" do original rodando, o caminho legítimo é rodá-lo na máquina do dono (onde a licença vale) e capturar a tela, ou usar uma licença emitida para este ambiente.
- Consequência: a comparação da tela emulada com o original ao vivo continua PENDENTE; a emulação segue baseada no código do EXE (OBS-COD) e nos valores do log (OBS-FIO).
