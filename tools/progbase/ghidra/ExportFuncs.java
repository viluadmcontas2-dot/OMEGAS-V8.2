// Pós-script: exporta TODAS as funções (JSON linhas): entrada, tamanho, nome, chamadores, chamados, instruções.
// Uso: -postScript ExportFuncs.java <saida.jsonl>
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class ExportFuncs extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] a = getScriptArgs();
        PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(a[0])));
        FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
        int n = 0;
        while (it.hasNext()) {
            Function f = it.next();
            if (monitor.isCancelled()) break;
            long size = f.getBody().getNumAddresses();
            int ni = 0;
            InstructionIterator ii = currentProgram.getListing().getInstructions(f.getBody(), true);
            while (ii.hasNext()) { ii.next(); ni++; }
            TreeSet<Long> callees = new TreeSet<>();
            for (Function c : f.getCalledFunctions(monitor)) callees.add(c.getEntryPoint().getOffset());
            TreeSet<Long> callers = new TreeSet<>();
            for (Function c : f.getCallingFunctions(monitor)) callers.add(c.getEntryPoint().getOffset());
            StringBuilder sb = new StringBuilder();
            sb.append("{\"a\":").append(f.getEntryPoint().getOffset()).append(",\"n\":\"").append(f.getName().replace("\"", "")).append("\"")
              .append(",\"s\":").append(size).append(",\"i\":").append(ni).append(",\"th\":").append(f.isThunk())
              .append(",\"ext\":").append(f.isExternal()).append(",\"cc\":[");
            boolean p = false;
            for (long c : callees) { if (p) sb.append(','); sb.append(c); p = true; }
            sb.append("],\"cr\":[");
            p = false;
            for (long c : callers) { if (p) sb.append(','); sb.append(c); p = true; }
            sb.append("]}");
            w.println(sb);
            n++;
        }
        w.close();
        println("ExportFuncs: " + n + " funcoes");
    }
}
