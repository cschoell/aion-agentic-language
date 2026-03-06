package com.aion.bytecode;

import com.aion.ast.Node;
import com.aion.ast.Node.*;
import com.aion.ast.Node.Expr.*;

import java.util.*;

/**
 * Compiles an Aion AST to flat Aion bytecode.
 *
 * Layout:
 *   [ Jump→main ]  [ fn1…Return ]  [ fn2…Return ]  [ const inits ] [ main…Halt ]
 *
 * Function addresses are resolved in two passes; forward references are patched
 * after each function body is emitted.
 */
public class BytecodeCompiler {

    private final List<Instruction>          out          = new ArrayList<>();
    private final Map<String, FnDecl>        fnDecls      = new HashMap<>();
    private final Map<String, Integer>       fnAddrs      = new HashMap<>();
    private final Map<String, List<Integer>> forwardCalls = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public Bytecode compile(Node node) {
        out.clear(); fnDecls.clear(); fnAddrs.clear(); forwardCalls.clear();

        if (!(node instanceof Node.Module m))
            throw new UnsupportedOperationException("Cannot compile: " + node.getClass().getSimpleName());

        for (Node decl : m.decls())
            if (decl instanceof FnDecl fn) fnDecls.put(fn.name(), fn);

        if (!fnDecls.containsKey("main"))
            throw new IllegalStateException("No `main` function found.");

        int mainJumpIdx = emitPlaceholder(new Instruction.Jump(0));

        for (FnDecl fn : fnDecls.values())
            if (!fn.name().equals("main")) compileFnDecl(fn);

        patch(mainJumpIdx, new Instruction.Jump(out.size()));

        for (Node decl : m.decls())
            if (decl instanceof Node.ConstDecl c) {
                compileExpr(c.value());
                emit(new Instruction.Store(c.name()));
            }

        compileFnBody(fnDecls.get("main"));
        emit(new Instruction.Halt());

        if (!forwardCalls.isEmpty())
            throw new IllegalStateException("Unresolved forward calls: " + forwardCalls.keySet());

        return new Bytecode(List.copyOf(out));
    }

    // ── Function compilation ──────────────────────────────────────────────────

    private void compileFnDecl(FnDecl fn) {
        fnAddrs.put(fn.name(), out.size());
        patchForwardCalls(fn.name());
        int bodyStart = out.size();
        compileBlock(fn.body());
        int epilogueIdx = out.size();
        emit(new Instruction.Return(false));
        patchPropagates(bodyStart, epilogueIdx);
    }

    private void compileFnBody(FnDecl fn) {
        fnAddrs.put(fn.name(), out.size());
        patchForwardCalls(fn.name());
        int bodyStart = out.size();
        compileBlock(fn.body());
        patchPropagates(bodyStart, out.size());
    }

    /** Back-patch all unresolved {@code Propagate(-1)} in [from, end) to use epilogueIdx. */
    private void patchPropagates(int from, int epilogueIdx) {
        for (int i = from; i < epilogueIdx && i < out.size(); i++)
            if (out.get(i) instanceof Instruction.Propagate p && p.epilogueAddr() == -1)
                out.set(i, new Instruction.Propagate(epilogueIdx));
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private void compileBlock(Stmt.Block block) {
        for (Stmt s : block.stmts()) compileStmt(s);
    }

    private void compileStmt(Stmt stmt) {
        switch (stmt) {
            case Stmt.Let    let -> { compileExpr(let.value());  emit(new Instruction.Store(let.name())); }
            case Stmt.Mut    mut -> { compileExpr(mut.value());  emit(new Instruction.Store(mut.name())); }
            case Stmt.Assign asgn -> compileAssign(asgn);
            case Stmt.ExprStmt es -> compileExprStmt(es.expr());
            case Stmt.If   s      -> compileIf(s);
            case Stmt.While s     -> compileWhile(s);
            case Stmt.For   s     -> compileFor(s);
            case Stmt.Return ret  -> {
                if (ret.value() != null) { compileExpr(ret.value()); emit(new Instruction.Return(true)); }
                else emit(new Instruction.Return(false));
            }
            case Stmt.Block    b   -> compileBlock(b);
            case Stmt.Assert   a   -> compileAssert(a);
            case Stmt.Describe ign -> {}
            case Stmt.Break    ign -> emit(new Instruction.Break());
            case Stmt.Continue ign -> emit(new Instruction.Continue());
        }
    }

    private void compileAssign(Stmt.Assign asgn) {
        switch (asgn.target()) {
            case AssignTarget.Field f -> {
                compileExpr(asgn.value());
                emit(new Instruction.Store(f.path().getFirst()));
            }
            case AssignTarget.Index ix -> {
                emit(new Instruction.Load(ix.name()));
                compileExpr(ix.index());
                compileExpr(asgn.value());
                emit(new Instruction.SetIndex());
            }
        }
    }

    private void compileAssert(Stmt.Assert a) {
        compileExpr(a.condition());
        int ok = emitPlaceholder(new Instruction.JumpIfTrue(0));
        String msg = a.message() != null ? a.message().toString() : "Assertion failed";
        emit(new Instruction.PushStr("AssertionError: " + msg));
        emit(new Instruction.Throw());
        patch(ok, new Instruction.JumpIfTrue(out.size()));
    }

    private void compileExprStmt(Expr expr) {
        if (expr instanceof FnCall call && call.name().equals("print")) {
            compilePrintCall(call); return;
        }
        if (expr instanceof MethodCall mc) {
            compileExpr(mc.receiver());
            for (Node.Arg arg : mc.args()) compileExpr(argVal(arg));
            emit(new Instruction.CallMethod(mc.method(), mc.args().size()));
            // Methods that mutate and return nothing need no pop;
            // methods that return a value do.
            boolean returnsUnit = switch (mc.method()) {
                case "push", "pop", "set", "remove", "clear" -> true;
                default -> false;
            };
            if (!returnsUnit) emit(new Instruction.Pop());
            return;
        }
        compileExpr(expr);
        if (producesValue(expr)) emit(new Instruction.Pop());
    }

    // ── Control flow ─────────────────────────────────────────────────────────

    private void compileIf(Stmt.If s) {
        List<Integer> endJumps = new ArrayList<>();
        for (IfBranch branch : s.branches()) {
            compileExpr(branch.condition());
            int skip = emitPlaceholder(new Instruction.JumpIfFalse(0));
            compileBlock(branch.body());
            endJumps.add(emitPlaceholder(new Instruction.Jump(0)));
            patch(skip, new Instruction.JumpIfFalse(out.size()));
        }
        if (s.elseBranch() != null) compileBlock(s.elseBranch());
        int end = out.size();
        for (int i : endJumps) patch(i, new Instruction.Jump(end));
    }

    private void compileWhile(Stmt.While s) {
        int loopStart = out.size();
        compileExpr(s.condition());
        int exitPatch = emitPlaceholder(new Instruction.JumpIfFalse(0));
        int bodyStart = out.size();
        compileBlock(s.body());
        emit(new Instruction.Jump(loopStart));
        int loopEnd = out.size();
        patch(exitPatch, new Instruction.JumpIfFalse(loopEnd));
        patchLoopJumps(bodyStart, loopEnd - 1, loopStart, loopEnd);
    }

    /**
     * for VAR in LIST { body }
     *
     * Compiles to index-based iteration using list.get(idx) → Some/null:
     *   Store __iter; PushInt 0; Store __idx
     *   [loop]: Load __iter; Load __idx; CallMethod "get" 1
     *   JumpIfNone [end]
     *   UnwrapInner; Store VAR
     *   Load __idx; PushInt 1; Add; Store __idx
     *   [body]; Jump [loop]
     *   [end]: Pop (None left by JumpIfNone)
     */
    private void compileFor(Stmt.For s) {
        String iterVar = "__iter_" + out.size();
        String idxVar  = "__idx_"  + out.size();

        compileExpr(s.iterable());
        emit(new Instruction.Store(iterVar));
        emit(new Instruction.PushInt(0L));
        emit(new Instruction.Store(idxVar));

        int loopStart = out.size();
        emit(new Instruction.Load(iterVar));
        emit(new Instruction.Load(idxVar));
        emit(new Instruction.CallMethod("get", 1));
        int exitPatch = emitPlaceholder(new Instruction.JumpIfNone(0));

        emit(new Instruction.UnwrapInner());
        emit(new Instruction.Store(s.var()));

        emit(new Instruction.Load(idxVar));
        emit(new Instruction.PushInt(1L));
        emit(new Instruction.Add());
        emit(new Instruction.Store(idxVar));

        int bodyStart = out.size();
        compileBlock(s.body());
        emit(new Instruction.Jump(loopStart));

        int loopEnd = out.size();
        patch(exitPatch, new Instruction.JumpIfNone(loopEnd));
        emit(new Instruction.Pop());  // discard None

        patchLoopJumps(bodyStart, loopEnd - 1, loopStart, loopEnd);
    }

    private void patchLoopJumps(int from, int to, int contTarget, int breakTarget) {
        for (int i = from; i <= to && i < out.size(); i++) {
            if (out.get(i) instanceof Instruction.Break)
                out.set(i, new Instruction.Jump(breakTarget));
            else if (out.get(i) instanceof Instruction.Continue)
                out.set(i, new Instruction.Jump(contTarget));
        }
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    private void compileExpr(Expr expr) {
        switch (expr) {
            case IntLit    e -> emit(new Instruction.PushInt(e.value()));
            case FloatLit  e -> emit(new Instruction.PushFloat(e.value()));
            case StrLit    e -> emit(new Instruction.PushStr(e.value()));
            case BoolLit   e -> emit(new Instruction.PushBool(e.value()));
            case NoneLit   e -> emit(new Instruction.PushNone());
            case SomeLit   e -> { compileExpr(e.inner()); emit(new Instruction.PushSome()); }
            case OkLit     e -> { compileExpr(e.inner()); emit(new Instruction.PushOk());  }
            case ErrLit    e -> { compileExpr(e.inner()); emit(new Instruction.PushErr()); }

            case VarRef    e -> emit(new Instruction.Load(e.name()));

            case EnumVariantRef e ->
                emit(new Instruction.PushEnum(e.typeName(), e.variant(), 0));
            case EnumTupleLit   e -> {
                for (Node.Arg a : e.args()) compileExpr(argVal(a));
                emit(new Instruction.PushEnum(e.typeName(), e.variant(), e.args().size()));
            }
            case EnumRecordLit  e -> {
                List<String> names = new ArrayList<>();
                for (NamedArg f : e.fields()) { compileExpr(f.value()); names.add(f.name()); }
                emit(new Instruction.PushRecord(e.variant(), names));
                emit(new Instruction.PushEnum(e.typeName(), e.variant(), 1));
            }
            case RecordLit      e -> {
                List<String> names = new ArrayList<>();
                for (NamedArg f : e.fields()) { compileExpr(f.value()); names.add(f.name()); }
                emit(new Instruction.PushRecord(e.typeName(), names));
            }
            case ListLit e -> {
                for (Expr el : e.elements()) compileExpr(el);
                emit(new Instruction.PushList(e.elements().size()));
            }
            case MapLit  e -> {
                for (MapEntry me : e.entries()) { compileExpr(me.key()); compileExpr(me.value()); }
                emit(new Instruction.PushMap(e.entries().size()));
            }
            case FieldAccess e -> { compileExpr(e.receiver()); emit(new Instruction.GetField(e.field())); }
            case IndexAccess e -> { compileExpr(e.receiver()); compileExpr(e.index()); emit(new Instruction.GetIndex()); }

            case BinOp     e -> compileBinOp(e);
            case UnaryOp   e -> { compileExpr(e.operand()); emit(e.op() == Node.UnaryOpKind.NEG ? new Instruction.Neg() : new Instruction.Not()); }
            case Pipe      e -> compilePipe(e);
            case Match     e -> compileMatch(e);
            case BlockExpr e -> { for (Stmt s : e.stmts()) compileStmt(s); compileExpr(e.value()); }
            case FnCall    e -> compileFnCallExpr(e);
            case MethodCall e -> {
                compileExpr(e.receiver());
                for (Node.Arg a : e.args()) compileExpr(argVal(a));
                emit(new Instruction.CallMethod(e.method(), e.args().size()));
            }
            case Propagate e -> { compileExpr(e.inner()); emit(new Instruction.Propagate(-1)); }
            case TrustedExpr   e -> compileExpr(e.inner());
            case UntrustedExpr e -> compileExpr(e.inner());
            case InterpolatedStr e -> compileInterpStr(e);
        }
    }

    private void compileBinOp(BinOp e) {
        if (e.op() == Node.BinOpKind.AND) {
            compileExpr(e.left());
            int sc = emitPlaceholder(new Instruction.AndShort(0));
            compileExpr(e.right());
            emit(new Instruction.And());
            patch(sc, new Instruction.AndShort(out.size()));
            return;
        }
        if (e.op() == Node.BinOpKind.OR) {
            compileExpr(e.left());
            int sc = emitPlaceholder(new Instruction.OrShort(0));
            compileExpr(e.right());
            emit(new Instruction.Or());
            patch(sc, new Instruction.OrShort(out.size()));
            return;
        }
        compileExpr(e.left());
        compileExpr(e.right());
        switch (e.op()) {
            case ADD -> { if (isStrExpr(e.left()) || isStrExpr(e.right())) emit(new Instruction.Concat()); else emit(new Instruction.Add()); }
            case SUB -> emit(new Instruction.Sub());
            case MUL -> emit(new Instruction.Mul());
            case DIV -> emit(new Instruction.Div());
            case MOD -> emit(new Instruction.Mod());
            case EQ  -> emit(new Instruction.Eq());
            case NEQ -> emit(new Instruction.Ne());
            case LT  -> emit(new Instruction.Lt());
            case LE  -> emit(new Instruction.Le());
            case GT  -> emit(new Instruction.Gt());
            case GE  -> emit(new Instruction.Ge());
            default  -> throw new AssertionError("unhandled: " + e.op());
        }
    }

    private void compilePipe(Pipe e) {
        compileExpr(e.left());
        if (!(e.right() instanceof VarRef ref))
            throw new UnsupportedOperationException("Pipe RHS must be a function reference.");
        FnDecl fn = fnDecls.get(ref.name());
        if (fn == null) throw new UnsupportedOperationException("Pipe target not found: " + ref.name());
        List<String> params = fn.params().stream().map(Param::name).toList();
        Integer addr = fnAddrs.get(ref.name());
        if (addr != null) emit(new Instruction.Call(addr, 1, params));
        else {
            int idx = emitPlaceholder(new Instruction.Call(0, 1, params));
            forwardCalls.computeIfAbsent(ref.name(), k -> new ArrayList<>()).add(idx);
        }
    }

    /**
     * Compile a match expression.
     *
     * Per arm layout:
     *   Dup               ← copy subject so pattern can inspect without consuming
     *   [pattern tests → MatchXxx / MatchTag with failJump to next-arm start]
     *   [bind variables]
     *   Pop               ← discard the dup'd copy
     *   [guard? JumpIfFalse → next-arm start]
     *   [arm body]
     *   Jump [end]
     *   [next arm…]
     * [no-match: throw]
     * [end]
     * Pop                 ← discard original subject
     */
    /**
     * Compile a match expression.
     *
     * Stack discipline per arm:
     *   Before arm:   [... subject]
     *   Dup           [... subject subject_copy]
     *   pattern tests on subject_copy (peek); on fail → jump to next arm with [... subject] intact
     *   Pop copy      [... subject]
     *   (guard?)      —— only entered when pattern matched ——
     *   Pop subject   [...]               ← consume original before body
     *   arm body      [... result]
     *   Jump end
     *
     * Guard failure: we popped the copy but NOT the subject yet.
     * The guard fail-jump lands at the next arm start where [... subject] is still on stack.
     * So the Pop(subject) must come AFTER the guard check.
     * Layout with guard:
     *   Pop copy
     *   Dup subject (to leave copy for guard)      ← NOT needed if we just use the subject
     *   guard eval
     *   JumpIfFalse nextArm                       ← [... subject] still live on fail
     *   Pop subject
     *   arm body
     *   Jump end
     */
    private void compileMatch(Match e) {
        compileExpr(e.subject());

        List<Integer> endJumps = new ArrayList<>();

        for (MatchArm arm : e.arms()) {
            emit(new Instruction.Dup());                    // [subject, copy]

            List<Integer> armFails = new ArrayList<>();
            compilePatternIntoList(arm.pattern(), armFails); // tests on copy; binds store copies
            emit(new Instruction.Pop());                    // pop copy → [subject]

            // Optional guard — evaluated with [subject] on stack; if fails, subject survives
            int guardFail = -1;
            if (arm.guard() != null) {
                compileExpr(arm.guard());                   // [subject, boolGuard]
                guardFail = emitPlaceholder(new Instruction.JumpIfFalse(0)); // on fail → [subject]
            }

            // Consume original subject — arm body starts with clean stack
            emit(new Instruction.Pop());                    // [  ]
            compileExpr(arm.body());                        // [result]
            endJumps.add(emitPlaceholder(new Instruction.Jump(0)));

            // Patch fail-jumps to next arm
            int nextArm = out.size();
            for (int fi : armFails) out.set(fi, patchFail(out.get(fi), nextArm));
            if (guardFail >= 0) patch(guardFail, new Instruction.JumpIfFalse(nextArm));
        }

        // No arm matched → subject still on stack; throw after popping it
        emit(new Instruction.Pop());
        emit(new Instruction.PushStr("Non-exhaustive match"));
        emit(new Instruction.Throw());

        int end = out.size();
        for (int i : endJumps) patch(i, new Instruction.Jump(end));
    }

    /**
     * Compile a pattern check against the value currently on TOS.
     * CONTRACT: the TOS value is PEEKED (not consumed) by this method.
     * Literal match instructions (MatchInt, MatchStr, MatchTag, …) peek TOS.
     * For Bind and Wildcard, we Dup first so the outer Pop still sees the original.
     * The CALLER is responsible for popping TOS after this returns.
     *
     * Exception: when called from a field-binding context the field value IS
     * consumed — use {@link #compileBindingPattern} for that.
     */
    private void compilePatternIntoList(Node.Pattern pat, List<Integer> fails) {
        switch (pat) {
            case Node.Pattern.Wildcard ign -> {}  // always matches; caller pops
            case Node.Pattern.Bind b -> {
                // Dup so caller's Pop still has TOS; store the dup'd copy as the binding
                emit(new Instruction.Dup());
                emit(new Instruction.Store(b.name()));
            }
            case Node.Pattern.IntPat   p -> fails.add(emitPlaceholder(new Instruction.MatchInt(p.value(), 0)));
            case Node.Pattern.FloatPat p -> fails.add(emitPlaceholder(new Instruction.MatchFloat(p.value(), 0)));
            case Node.Pattern.StrPat   p -> fails.add(emitPlaceholder(new Instruction.MatchStr(p.value(), 0)));
            case Node.Pattern.BoolPat  p -> fails.add(emitPlaceholder(new Instruction.MatchBool(p.value(), 0)));
            case Node.Pattern.NonePat  ign -> fails.add(emitPlaceholder(new Instruction.MatchNone(0)));
            case Node.Pattern.SomePat  p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag("Option", "Some", 0)));
                // Dup wrapper, unwrap inner, test inner, pop inner, then caller pops wrapper
                emit(new Instruction.Dup()); emit(new Instruction.UnwrapInner());
                List<Integer> inner = new ArrayList<>();
                compilePatternIntoList(p.inner(), inner);
                emit(new Instruction.Pop()); // pop inner (already bound/checked)
                fails.addAll(inner);
            }
            case Node.Pattern.OkPat  p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag("Result", "Ok", 0)));
                emit(new Instruction.Dup()); emit(new Instruction.UnwrapInner());
                List<Integer> inner = new ArrayList<>();
                compilePatternIntoList(p.inner(), inner);
                emit(new Instruction.Pop());
                fails.addAll(inner);
            }
            case Node.Pattern.ErrPat p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag("Result", "Err", 0)));
                emit(new Instruction.Dup()); emit(new Instruction.UnwrapInner());
                List<Integer> inner = new ArrayList<>();
                compilePatternIntoList(p.inner(), inner);
                emit(new Instruction.Pop());
                fails.addAll(inner);
            }
            case Node.Pattern.EnumPat p ->
                fails.add(emitPlaceholder(new Instruction.MatchTag(p.typeName(), p.variant(), 0)));
            case Node.Pattern.EnumTuplePat p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag(p.typeName(), p.variant(), 0)));
                emit(new Instruction.Dup()); emit(new Instruction.UnwrapInner());
                // UnwrapInner pushes payload left-to-right; last field = TOS
                // Consume each field (in reverse TOS order) via binding or discard
                for (int i = p.fields().size() - 1; i >= 0; i--) {
                    Node.Pattern fp = p.fields().get(i);
                    compileBindingPattern(fp, fails);
                }
            }
            case Node.Pattern.EnumRecordPat p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag(p.typeName(), p.variant(), 0)));
                // Dup enum copy, unwrap to get RecordVal
                emit(new Instruction.Dup()); emit(new Instruction.UnwrapInner());
                // RecordVal is now TOS (above the original enum copy on the Dup'd side)
                for (Node.FieldPat fp : p.fields()) {
                    // GetField consumes nothing — Dup first so RecordVal stays
                    emit(new Instruction.Dup());
                    emit(new Instruction.GetField(fp.field())); // [RecordVal, fieldVal]
                    compileBindingPattern(fp.pattern(), fails); // consumes fieldVal
                }
                emit(new Instruction.Pop()); // pop the RecordVal
            }
            case Node.Pattern.RecordPat p -> {
                fails.add(emitPlaceholder(new Instruction.MatchTag("record", p.typeName(), 0)));
                for (Node.FieldPat fp : p.fields()) {
                    emit(new Instruction.Dup());
                    emit(new Instruction.GetField(fp.field()));
                    compileBindingPattern(fp.pattern(), fails);
                }
            }
        }
    }

    /**
     * Compile a pattern that CONSUMES the TOS value (used for payload fields).
     * Bind → Store (consumes). Wildcard → Pop. Literals → MatchXxx (peek) then Pop.
     */
    private void compileBindingPattern(Node.Pattern pat, List<Integer> fails) {
        switch (pat) {
            case Node.Pattern.Wildcard ign  -> emit(new Instruction.Pop());
            case Node.Pattern.Bind b        -> emit(new Instruction.Store(b.name()));
            default -> {
                // For literal/structural patterns: peek then consume
                compilePatternIntoList(pat, fails);
                emit(new Instruction.Pop());
            }
        }
    }

    /** Return the same instruction type with its fail-jump updated to {@code target}. */
    private static Instruction patchFail(Instruction instr, int target) {
        return switch (instr) {
            case Instruction.MatchInt    p -> new Instruction.MatchInt(p.value(), target);
            case Instruction.MatchFloat  p -> new Instruction.MatchFloat(p.value(), target);
            case Instruction.MatchStr    p -> new Instruction.MatchStr(p.value(), target);
            case Instruction.MatchBool   p -> new Instruction.MatchBool(p.value(), target);
            case Instruction.MatchNone   p -> new Instruction.MatchNone(target);
            case Instruction.MatchTag    p -> new Instruction.MatchTag(p.typeName(), p.variant(), target);
            default -> instr;
        };
    }

    // ── Function calls ────────────────────────────────────────────────────────

    private void compileFnCallExpr(FnCall call) {
        switch (call.name()) {
            case "print" -> compilePrintCall(call);
            case "input" -> emit(new Instruction.ReadLine());
            case "str"   -> {
                if (!call.args().isEmpty()) compileExpr(argVal(call.args().getFirst()));
                else emit(new Instruction.PushStr(""));
                emit(new Instruction.Stringify());
            }
            case "len"   -> {
                if (!call.args().isEmpty()) compileExpr(argVal(call.args().getFirst()));
                emit(new Instruction.CallMethod("len", 0));
            }
            case "some"  -> { compileExpr(argVal(call.args().getFirst())); emit(new Instruction.PushSome()); }
            case "ok"    -> { compileExpr(argVal(call.args().getFirst())); emit(new Instruction.PushOk());  }
            case "err"   -> { compileExpr(argVal(call.args().getFirst())); emit(new Instruction.PushErr()); }
            case "none"  -> emit(new Instruction.PushNone());
            default -> {
                FnDecl fn = fnDecls.get(call.name());
                if (fn == null) throw new UnsupportedOperationException("Unknown function: " + call.name());

                for (Node.Arg arg : call.args()) compileExpr(argVal(arg));

                List<String> params = fn.params().stream().map(Param::name).toList();
                Integer addr = fnAddrs.get(call.name());
                if (addr != null) {
                    emit(new Instruction.Call(addr, call.args().size(), params));
                } else {
                    int idx = emitPlaceholder(new Instruction.Call(0, call.args().size(), params));
                    forwardCalls.computeIfAbsent(call.name(), k -> new ArrayList<>()).add(idx);
                }
            }
        }
    }

    private void compilePrintCall(FnCall call) {
        if (call.args().isEmpty()) emit(new Instruction.PushStr(""));
        else compileExpr(argVal(call.args().getFirst()));
        emit(new Instruction.Print());
    }

    private void compileInterpStr(InterpolatedStr e) {
        boolean first = true;
        for (Object part : e.parts()) {
            if (part instanceof String s) emit(new Instruction.PushStr(s));
            else { compileExpr((Expr) part); emit(new Instruction.Stringify()); }
            if (!first) emit(new Instruction.Concat());
            first = false;
        }
    }

    // ── Forward-call patching ─────────────────────────────────────────────────

    private void patchForwardCalls(String fnName) {
        List<Integer> pending = forwardCalls.remove(fnName);
        if (pending == null) return;
        FnDecl fn = fnDecls.get(fnName);
        List<String> params = fn.params().stream().map(Param::name).toList();
        int addr = fnAddrs.get(fnName);
        for (int idx : pending)
            patch(idx, new Instruction.Call(addr, fn.params().size(), params));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private boolean producesValue(Expr e) {
        if (e instanceof FnCall call) {
            if (call.name().equals("print")) return false;
            FnDecl fn = fnDecls.get(call.name());
            if (fn == null) return false;
            return !(fn.returnType() instanceof Node.TypeRef.UnitT);
        }
        return true;
    }

    private boolean isStrExpr(Expr e) {
        return e instanceof StrLit || e instanceof InterpolatedStr
            || (e instanceof FnCall fc && (fc.name().equals("str") || fc.name().equals("input")))
            || (e instanceof BinOp bo && bo.op() == Node.BinOpKind.ADD
                && (isStrExpr(bo.left()) || isStrExpr(bo.right())));
    }

    private static Expr argVal(Node.Arg arg) {
        return switch (arg) {
            case Node.Arg.Positional p -> p.value();
            case Node.Arg.Named      n -> n.value();
        };
    }

    private void emit(Instruction i)              { out.add(i); }
    private int  emitPlaceholder(Instruction h)   { int i = out.size(); out.add(h); return i; }
    private void patch(int idx, Instruction repl) { out.set(idx, repl); }
}
