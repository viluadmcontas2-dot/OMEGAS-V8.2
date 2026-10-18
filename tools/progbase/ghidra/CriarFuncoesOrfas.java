// Pré/pós-script: cria função no início de cada faixa de instruções que não pertence a nenhuma função (tsv de ExportLacunas, estado I).
// Funções criadas assim têm nome FUN_ e marca DESCONHECIDO (INFERIDO: início de faixa de código). Uso: -postScript CriarFuncoesOrfas.java <lacunas.tsv>
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import java.io.*;
import java.nio.file.*;

public class CriarFuncoesOrfas extends GhidraScript {
    @Override
    public void run() throws Exception {
        int n = 0, f = 0;
        for (String l : Files.readAllLines(Paths.get(getScriptArgs()[0]))) {
            String[] p = l.split("\t");
            if (p.length < 3 || !p[2].equals("I")) continue;
            Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(Long.parseLong(p[0], 16));
            if (getFunctionContaining(a) != null) continue;
            if (createFunction(a, null) != null) n++; else f++;
        }
        println("CriarFuncoesOrfas: criadas=" + n + " falhas=" + f);
    }
}
