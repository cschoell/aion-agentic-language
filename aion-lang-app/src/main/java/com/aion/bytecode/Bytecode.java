package com.aion.bytecode;

import java.util.List;
import java.util.Map;
import com.aion.bytecode.Instruction;

/**
 * Represents a sequence of bytecode instructions for the Aion VM.
 */
public class Bytecode {
    public final List<Instruction> instructions;
    /**
     * Impl method registry: key = "TypeName::methodName",
     * value = [address, param0, param1, ...] where address is the instruction index.
     */
    public final Map<String, ImplMethodEntry> implMethods;

    public record ImplMethodEntry(int address, List<String> params) {}

    public Bytecode(List<Instruction> instructions) {
        this(instructions, Map.of());
    }

    public Bytecode(List<Instruction> instructions, Map<String, ImplMethodEntry> implMethods) {
        this.instructions = instructions;
        this.implMethods  = implMethods;
    }
}
