// Pós-script: exporta as faixas de .text que NÃO pertencem a nenhum corpo de função (início fim definidos?). Uso: -postScript ExportLacunas.java <saida.tsv>
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;
import java.io.*;

public class ExportLacunas extends GhidraScript {
    @Override
    public void run() throws Exception {
        MemoryBlock b = currentProgram.getMemory().getBlock(".text");
        AddressSet livre = new AddressSet(b.getStart(), b.getEnd());
        FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
        while (it.hasNext()) livre.delete(it.next().getBody());
        PrintWriter w = new PrintWriter(new FileWriter(getScriptArgs()[0]));
        for (AddressRange r : livre) {
            Address a = r.getMinAddress();
            boolean instr = currentProgram.getListing().getInstructionAt(a) != null;
            boolean dado = currentProgram.getListing().getDefinedDataAt(a) != null;
            w.println(Long.toHexString(r.getMinAddress().getOffset()) + "\t" + r.getLength() + "\t" + (instr ? "I" : dado ? "D" : "U"));
        }
        w.close();
        println("ExportLacunas ok");
    }
}
