package com.aion.bytecode;

/**
 * Represents a single bytecode instruction for the Aion VM.
 */
public sealed interface Instruction permits
        Instruction.PushInt, Instruction.PushFloat, Instruction.PushStr,
        Instruction.PushBool, Instruction.PushNone,
        Instruction.Add, Instruction.Sub, Instruction.Mul, Instruction.Div, Instruction.Mod,
        Instruction.Neg,
        Instruction.Eq, Instruction.Ne, Instruction.Lt, Instruction.Le,
        Instruction.Gt, Instruction.Ge,
        Instruction.And, Instruction.Or, Instruction.Not,
        Instruction.Concat,
        Instruction.Store, Instruction.Load,
        Instruction.Print, Instruction.ReadLine,
        Instruction.Jump, Instruction.JumpIfFalse, Instruction.JumpIfTrue,
        Instruction.Call, Instruction.Return, Instruction.Pop,
        Instruction.CallMethod,
        Instruction.Throw,
        Instruction.Halt {

    // ── Literals ─────────────────────────────────────────────────────────────
    record PushInt(long value)    implements Instruction {}
    record PushFloat(double value) implements Instruction {}
    record PushStr(String value)  implements Instruction {}
    record PushBool(boolean value) implements Instruction {}
    record PushNone()             implements Instruction {}

    // ── Arithmetic ───────────────────────────────────────────────────────────
    record Add()  implements Instruction {}
    record Sub()  implements Instruction {}
    record Mul()  implements Instruction {}
    record Div()  implements Instruction {}
    record Mod()  implements Instruction {}
    record Neg()  implements Instruction {}

    // ── Comparison ───────────────────────────────────────────────────────────
    record Eq()   implements Instruction {}
    record Ne()   implements Instruction {}
    record Lt()   implements Instruction {}
    record Le()   implements Instruction {}
    record Gt()   implements Instruction {}
    record Ge()   implements Instruction {}

    // ── Logic ─────────────────────────────────────────────────────────────────
    record And()  implements Instruction {}
    record Or()   implements Instruction {}
    record Not()  implements Instruction {}

    // ── String ────────────────────────────────────────────────────────────────
    /** Pop two strings; push their concatenation. */
    record Concat() implements Instruction {}

    // ── Variables ─────────────────────────────────────────────────────────────
    /** Pop top-of-stack and store in named slot. */
    record Store(String name) implements Instruction {}
    /** Push value of named slot onto stack. */
    record Load(String name)  implements Instruction {}

    // ── I/O ───────────────────────────────────────────────────────────────────
    /** Pop top-of-stack and print it (with newline). */
    record Print()    implements Instruction {}
    /** Read one line from stdin; push as String. */
    record ReadLine() implements Instruction {}

    // ── Control flow ─────────────────────────────────────────────────────────
    /** Unconditional jump to instruction index. */
    record Jump(int target)           implements Instruction {}
    /** Pop boolean; jump to target if false. */
    record JumpIfFalse(int target)    implements Instruction {}
    /** Pop boolean; jump to target if true. */
    record JumpIfTrue(int target)     implements Instruction {}
    /**
     * Call a user-defined function.
     * @param target  entry-point instruction index of the function
     * @param arity   number of arguments already pushed on the stack (left-to-right)
     * @param params  parameter names in order, used to populate the new frame's locals
     */
    record Call(int target, int arity, java.util.List<String> params) implements Instruction {}
    /** Return from a function call — TOS is the return value (or nothing for Unit). */
    record Return(boolean hasValue)   implements Instruction {}
    /** Discard top-of-stack (used to drop unused expression results). */
    record Pop()                      implements Instruction {}
    /**
     * Call a built-in method on the receiver.
     * Stack before: [..., receiver, arg0, arg1, …, argN-1]  (receiver deepest)
     * Stack after:  [..., result]
     */
    record CallMethod(String method, int arity) implements Instruction {}
    /** Pop String message from TOS and throw a runtime error. */
    record Throw()                    implements Instruction {}

    // ── Meta ──────────────────────────────────────────────────────────────────
    record Halt() implements Instruction {}
}
