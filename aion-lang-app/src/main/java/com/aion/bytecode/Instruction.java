package com.aion.bytecode;

/**
 * Represents a single bytecode instruction for the Aion VM.
 */
public sealed interface Instruction permits
        Instruction.PushInt, Instruction.PushFloat, Instruction.PushStr,
        Instruction.PushBool, Instruction.PushNone,
        Instruction.PushSome, Instruction.PushOk, Instruction.PushErr,
        Instruction.PushList, Instruction.PushMap,
        Instruction.PushEnum, Instruction.PushRecord, Instruction.PushTuple,
        Instruction.PushLambda,
        Instruction.Add, Instruction.Sub, Instruction.Mul, Instruction.Div, Instruction.Mod,
        Instruction.Neg,
        Instruction.Eq, Instruction.Ne, Instruction.Lt, Instruction.Le,
        Instruction.Gt, Instruction.Ge,
        Instruction.And, Instruction.Or, Instruction.Not,
        Instruction.AndShort, Instruction.OrShort,
        Instruction.Concat,
        Instruction.Store, Instruction.Load,
        Instruction.GetField, Instruction.SetField, Instruction.GetIndex, Instruction.SetIndex,
        Instruction.Propagate,
        Instruction.Print, Instruction.ReadLine,
        Instruction.Jump, Instruction.JumpIfFalse, Instruction.JumpIfTrue,
        Instruction.JumpIfNone,
        Instruction.MatchTag, Instruction.MatchInt, Instruction.MatchFloat,
        Instruction.MatchStr, Instruction.MatchBool, Instruction.MatchNone,
        Instruction.MatchTuple,
        Instruction.UnwrapInner,
        Instruction.Call, Instruction.CallLambda, Instruction.Return,
        Instruction.Pop, Instruction.Dup,
        Instruction.CallMethod,
        Instruction.Break, Instruction.Continue, Instruction.Stringify,
        Instruction.Throw,
        Instruction.MakeRange,
        Instruction.Halt {

    // ── Literals ─────────────────────────────────────────────────────────────
    record PushInt(long value)     implements Instruction {}
    record PushFloat(double value) implements Instruction {}
    record PushStr(String value)   implements Instruction {}
    record PushBool(boolean value) implements Instruction {}
    record PushNone()              implements Instruction {}
    /** Compile inner expr first, then emit PushSome to wrap TOS. */
    record PushSome()              implements Instruction {}
    /** Compile inner expr first, then emit PushOk to wrap TOS. */
    record PushOk()                implements Instruction {}
    /** Compile inner expr first, then emit PushErr to wrap TOS. */
    record PushErr()               implements Instruction {}
    /** Pop {@code size} values (bottom-of-group pushed first) and build a List. */
    record PushList(int size)      implements Instruction {}
    /** Pop {@code size*2} key/value pairs (key0 deepest) and build a Map. */
    record PushMap(int size)       implements Instruction {}
    /** Pop {@code arity} payload values and build an EnumVal. */
    record PushEnum(String typeName, String variant, int arity) implements Instruction {}
    /** Pop {@code fields.size()} values (last field is TOS) and build a RecordVal. */
    record PushRecord(String typeName, java.util.List<String> fields) implements Instruction {}
    /**
     * Push a lambda value (entry-point address + parameter names) onto the stack.
     * The resulting {@link com.aion.bytecode.VmValue.LambdaVal} can be passed as an
     * argument, stored in a variable, or called via {@link CallLambda}.
     */
    record PushLambda(int address, java.util.List<String> params) implements Instruction {}

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
    /**
     * Short-circuit AND: peek TOS; if false jump to {@code target} (keep false on stack).
     * If true, pop and continue evaluating the right operand.
     */
    record AndShort(int target) implements Instruction {}
    /**
     * Short-circuit OR: peek TOS; if true jump to {@code target} (keep true on stack).
     * If false, pop and continue evaluating the right operand.
     */
    record OrShort(int target)  implements Instruction {}

    // ── String ────────────────────────────────────────────────────────────────
    record Concat() implements Instruction {}

    // ── Variables ─────────────────────────────────────────────────────────────
    record Store(String name) implements Instruction {}
    record Load(String name)  implements Instruction {}

    /** Pop {@code size} values (bottom pushed first) and build a TupleVal. */
    record PushTuple(int size)     implements Instruction {}

    // ...existing code...

    // ── Field / index access ─────────────────────────────────────────────────
    /** Pop receiver; push receiver.fieldName. */
    record GetField(String field) implements Instruction {}
    /**
     * Mutate a record field in-place.
     * Stack before: receiver (RecordVal), new-value.
     * The receiver must be the top-most value, so emit Load → Load-value → SetField.
     * Actually stack order: TOS = new-value, TOS-1 = receiver.
     * After: nothing pushed (statement).
     */
    record SetField(String field) implements Instruction {}
    /** Pop index, pop receiver; push receiver[index]. */
    record GetIndex()             implements Instruction {}
    /** Pop value, pop index, pop receiver; receiver[index] = value (push nothing). */
    record SetIndex()             implements Instruction {}

    // ── Option / Result ? propagation ────────────────────────────────────────
    /**
     * Implements the {@code ?} operator.
     * TOS = Ok(v)/Some(v): pop wrapper, push inner v, continue.
     * TOS = Err(e)/None:   pop, push wrapper back, pop current call frame,
     *                      restore caller, push wrapper as return value.
     * {@code epilogueAddr} is the index of the Return instruction that ends
     * the enclosing function's body (set by the compiler).
     */
    record Propagate(int epilogueAddr) implements Instruction {}

    // ── I/O ───────────────────────────────────────────────────────────────────
    record Print()    implements Instruction {}
    record ReadLine() implements Instruction {}

    // ── Control flow ─────────────────────────────────────────────────────────
    record Jump(int target)        implements Instruction {}
    record JumpIfFalse(int target) implements Instruction {}
    record JumpIfTrue(int target)  implements Instruction {}
    /** Peek TOS (Option/Result); jump if it is None or Err, leaving the value on stack. */
    record JumpIfNone(int target)  implements Instruction {}

    // ── Pattern matching ─────────────────────────────────────────────────────
    /** Peek TOS; if it is an Enum/Option/Result with matching tag — continue. Else jump. */
    record MatchTag(String typeName, String variant, int failJump) implements Instruction {}
    /** Peek TOS (must be Long); if != value jump to failJump. */
    record MatchInt(long value, int failJump)    implements Instruction {}
    /** Peek TOS (must be Double); if != value jump. */
    record MatchFloat(double value, int failJump) implements Instruction {}
    /** Peek TOS (must be String); if not equal jump. */
    record MatchStr(String value, int failJump)  implements Instruction {}
    /** Peek TOS (must be Boolean); if != value jump. */
    record MatchBool(boolean value, int failJump) implements Instruction {}
    /** Peek TOS; if it is not null (None) jump to failJump. */
    record MatchNone(int failJump)               implements Instruction {}
    /**
     * Peek TOS; if it is not a TupleVal with exactly {@code arity} elements, jump to failJump.
     * On success, leaves the TupleVal on the stack for subsequent element binding.
     */
    record MatchTuple(int arity, int failJump)   implements Instruction {}
    /**
     * Pop wrapper (Some/Ok/Err/EnumVal) and push its inner payload values
     * left-to-right (so last payload element ends up as TOS).
     */
    record UnwrapInner() implements Instruction {}

    // ── Calls ─────────────────────────────────────────────────────────────────
    record Call(int target, int arity, java.util.List<String> params) implements Instruction {}
    /**
     * Call a lambda value that is on TOS (below the arguments).
     * Stack before: [lambda, arg0, arg1, …, argN-1]  (argN-1 = TOS).
     * Stack after:  [returnValue].
     */
    record CallLambda(int arity) implements Instruction {}
    record Return(boolean hasValue) implements Instruction {}
    record Pop()    implements Instruction {}
    record Dup()    implements Instruction {}
    record CallMethod(String method, int arity) implements Instruction {}

    // ── Errors / control signals ──────────────────────────────────────────────
    record Throw()     implements Instruction {}
    record Break()     implements Instruction {}
    record Continue()  implements Instruction {}
    record Stringify() implements Instruction {}

    // ── Meta ──────────────────────────────────────────────────────────────────
    record Halt() implements Instruction {}

    // ── Range ─────────────────────────────────────────────────────────────────
    /** Pop to (TOS) and from (TOS-1); push a ListVal of integers [from..to). inclusive=true → [from..=to]. */
    record MakeRange(boolean inclusive) implements Instruction {}
}
