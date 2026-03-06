package com.aion.bytecode;

import java.util.List;
import com.aion.bytecode.Instruction;

/**
 * Represents a sequence of bytecode instructions for the Aion VM.
 */
public class Bytecode {
    public final List<Instruction> instructions;

    public Bytecode(List<Instruction> instructions) {
        this.instructions = instructions;
    }
}
