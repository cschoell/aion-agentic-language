package com.aion.bytecode;

import com.aion.ast.Node;
import com.aion.ast.Node.AssignTarget;
import com.aion.ast.Node.Expr;
import com.aion.ast.Node.FnDecl;
import com.aion.ast.Node.IfBranch;
import com.aion.ast.Node.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles an Aion AST to flat Aion bytecode.
 *
 * All top-level functions are compiled into a single flat instruction list.
 * The layout is:
 *
 *   [ Jump → main ]  [ fn1 body … Return ]  [ fn2 body … Return ]  [ main body … Halt ]
 *
 * A function address table maps each function name to its entry-point index.
 * User-defined calls emit Call(target, arity, params); the VM manages a call
 * stack with per-frame local variable scopes.
 */
public class BytecodeCompiler {

    private final List<Instruction>   out       = new ArrayList<>();
    /** name → entry-point index in {@code out} */
    private final Map<String, FnDecl> fnDecls   = new HashMap<>();
    private final Map<String, Integer> fnAddrs  = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public Bytecode compile(Node node) {
        out.clear();
        fnDecls.clear();
        fnAddrs.clear();

        if (!(node instanceof Node.Module m))
            throw new UnsupportedOperationException("Cannot compile: " + node.getClass().getSimpleName());

        // Collect all function declarations
        for (Node decl : m.decls())
            if (decl instanceof FnDecl fn)
                fnDecls.put(fn.name(), fn);

        if (!fnDecls.containsKey("main"))
            throw new IllegalStateException("No `main` function found in module.");

        // Emit a Jump placeholder to main — patched after all functions are compiled
        int mainJumpIdx = emitPlaceholder(new Instruction.Jump(0));

        // Compile all non-main functions first so their addresses are known
        for (FnDecl fn : fnDecls.values()) {
            if (!fn.name().equals("main")) compileFnDecl(fn);
        }

        // Compile main and patch the initial jump to it
        patch(mainJumpIdx, new Instruction.Jump(out.size()));
        compileFnBody(fnDecls.get("main"));  // main ends with Halt, not Return
        emit(new Instruction.Halt());

        return new Bytecode(List.copyOf(out));
    }

    // ── Function declarations ─────────────────────────────────────────────────

    private void compileFnDecl(FnDecl fn) {
        fnAddrs.put(fn.name(), out.size());
        compileBlock(fn.body());
        // Functions without an explicit return still need to return Unit
        emit(new Instruction.Return(false));
    }

    private void compileFnBody(FnDecl fn) {
        // Record address even for main (used if main calls itself recursively)
        fnAddrs.put(fn.name(), out.size());
        compileBlock(fn.body());
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private void compileBlock(Stmt.Block block) {
        for (Stmt s : block.stmts()) compileStmt(s);
    }

    private void compileStmt(Stmt stmt) {
        switch (stmt) {
            case Stmt.Let let -> {
                compileExpr(let.value());
                emit(new Instruction.Store(let.name()));
            }
            case Stmt.Mut mut -> {
                compileExpr(mut.value());
                emit(new Instruction.Store(mut.name()));
            }
            case Stmt.Assign asgn -> {
                compileExpr(asgn.value());
                if (asgn.target() instanceof AssignTarget.Field f && f.path().size() == 1) {
                    emit(new Instruction.Store(f.path().getFirst()));
                } else {
                    throw new UnsupportedOperationException("Only simple variable assignment is supported.");
                }
            }
            case Stmt.ExprStmt es -> compileExprStmt(es.expr());
            case Stmt.If ifStmt   -> compileIf(ifStmt);
            case Stmt.While wh    -> compileWhile(wh);
            case Stmt.Return ret  -> {
                if (ret.value() != null) {
                    compileExpr(ret.value());
                    emit(new Instruction.Return(true));
                } else {
                    emit(new Instruction.Return(false));
                }
            }
            case Stmt.For ignored -> throw new UnsupportedOperationException("for loops not yet supported.");
            case Stmt.Block b     -> compileBlock(b);
            case Stmt.Assert a    -> compileAssert(a);
            case Stmt.Describe ignored -> { /* doc-string — no bytecode */ }
        }
    }

    private void compileAssert(Stmt.Assert a) {
        compileExpr(a.condition());
        int okJump = emitPlaceholder(new Instruction.JumpIfTrue(0));
        String msg = a.message() != null ? a.message().toString() : "Assertion failed";
        emit(new Instruction.PushStr("AssertionError: " + msg));
        emit(new Instruction.Throw());
        patch(okJump, new Instruction.JumpIfTrue(out.size()));
    }

    private void compileExprStmt(Expr expr) {
        if (expr instanceof Expr.FnCall call && call.name().equals("print")) {
            compilePrintCall(call);
        } else if (expr instanceof Expr.MethodCall mc) {
            compileMethodCall(mc);
            // Str/List/Map methods that return Unit (push/pop/set/remove) leave nothing;
            // ones that return a value (trim, upper, get, …) leave a value — drop it.
            String m = mc.method();
            boolean returnsUnit = m.equals("push") || m.equals("pop") || m.equals("set") || m.equals("remove");
            if (!returnsUnit) emit(new Instruction.Pop());
        } else {
            compileExpr(expr);
            if (producesValue(expr)) emit(new Instruction.Pop());
        }
    }

    // ── If / while ────────────────────────────────────────────────────────────

    private void compileIf(Stmt.If ifStmt) {
        List<Integer> endJumps = new ArrayList<>();
        for (IfBranch branch : ifStmt.branches()) {
            compileExpr(branch.condition());
            int patchIdx = emitPlaceholder(new Instruction.JumpIfFalse(0));
            compileBlock(branch.body());
            endJumps.add(emitPlaceholder(new Instruction.Jump(0)));
            patch(patchIdx, new Instruction.JumpIfFalse(out.size()));
        }
        if (ifStmt.elseBranch() != null) compileBlock(ifStmt.elseBranch());
        int end = out.size();
        for (int idx : endJumps) patch(idx, new Instruction.Jump(end));
    }

    private void compileWhile(Stmt.While wh) {
        int loopStart = out.size();
        compileExpr(wh.condition());
        int exitPatch = emitPlaceholder(new Instruction.JumpIfFalse(0));
        compileBlock(wh.body());
        emit(new Instruction.Jump(loopStart));
        patch(exitPatch, new Instruction.JumpIfFalse(out.size()));
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    private void compileExpr(Expr expr) {
        switch (expr) {
            case Expr.IntLit   e -> emit(new Instruction.PushInt(e.value()));
            case Expr.FloatLit e -> emit(new Instruction.PushFloat(e.value()));
            case Expr.StrLit   e -> emit(new Instruction.PushStr(e.value()));
            case Expr.BoolLit  e -> emit(new Instruction.PushBool(e.value()));
            case Expr.NoneLit  e -> emit(new Instruction.PushNone());
            case Expr.VarRef   e -> emit(new Instruction.Load(e.name()));
            case Expr.BinOp    e -> compileBinOp(e);
            case Expr.UnaryOp  e -> compileUnaryOp(e);
            case Expr.FnCall   e -> compileFnCallExpr(e);
            case Expr.MethodCall e -> compileMethodCall(e);
            case Expr.TrustedExpr   e -> compileExpr(e.inner());
            case Expr.UntrustedExpr e -> compileExpr(e.inner());
            default -> throw new UnsupportedOperationException(
                    "Expression not yet supported in bytecode: " + expr.getClass().getSimpleName());
        }
    }

    private void compileBinOp(Expr.BinOp e) {
        compileExpr(e.left());
        compileExpr(e.right());
        switch (e.op()) {
            case ADD -> {
                if (isStringExpr(e.left()) || isStringExpr(e.right()))
                    emit(new Instruction.Concat());
                else
                    emit(new Instruction.Add());
            }
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
            case AND -> emit(new Instruction.And());
            case OR  -> emit(new Instruction.Or());
        }
    }

    private void compileUnaryOp(Expr.UnaryOp e) {
        compileExpr(e.operand());
        switch (e.op()) {
            case NEG -> emit(new Instruction.Neg());
            case NOT -> emit(new Instruction.Not());
        }
    }

    private void compileFnCallExpr(Expr.FnCall call) {
        switch (call.name()) {
            case "print" -> compilePrintCall(call);
            case "input" -> emit(new Instruction.ReadLine());
            default -> {
                FnDecl fn = fnDecls.get(call.name());
                if (fn == null)
                    throw new UnsupportedOperationException(
                            "Unknown function: " + call.name());

                // Push all arguments left-to-right
                for (Node.Arg arg : call.args()) {
                    Expr value = switch (arg) {
                        case Node.Arg.Positional p -> p.value();
                        case Node.Arg.Named      n -> n.value();
                    };
                    compileExpr(value);
                }

                // Resolve address — may not be known yet (forward call); we'll
                // patch it. For simplicity we do a two-pass: compile all fn bodies
                // first, then patch. But our layout guarantees non-main fns are
                // compiled before main, so for calls within main the address IS
                // already known. For mutual recursion we use a fixup list.
                List<String> paramNames = fn.params().stream()
                        .map(Node.Param::name).toList();

                Integer addr = fnAddrs.get(call.name());
                if (addr != null) {
                    emit(new Instruction.Call(addr, call.args().size(), paramNames));
                } else {
                    // Forward reference — emit placeholder, remember for fixup
                    int callIdx = emitPlaceholder(
                            new Instruction.Call(0, call.args().size(), paramNames));
                    forwardCalls.computeIfAbsent(call.name(), k -> new ArrayList<>())
                            .add(callIdx);
                }
            }
        }
    }

    /** Pending forward-call indices that need patching once the target is compiled. */
    private final Map<String, List<Integer>> forwardCalls = new HashMap<>();

    /** Called after each function is compiled; patches any forward references to it. */
    private void patchForwardCalls(String fnName) {
        List<Integer> pending = forwardCalls.remove(fnName);
        if (pending == null) return;
        FnDecl fn = fnDecls.get(fnName);
        List<String> paramNames = fn.params().stream().map(Node.Param::name).toList();
        int addr = fnAddrs.get(fnName);
        for (int idx : pending)
            patch(idx, new Instruction.Call(addr, fn.params().size(), paramNames));
    }

    private void compileMethodCall(Expr.MethodCall e) {
        // Push receiver, then each argument
        compileExpr(e.receiver());
        for (Node.Arg arg : e.args()) {
            Expr value = switch (arg) {
                case Node.Arg.Positional p -> p.value();
                case Node.Arg.Named      n -> n.value();
            };
            compileExpr(value);
        }
        emit(new Instruction.CallMethod(e.method(), e.args().size()));
    }

    private void compilePrintCall(Expr.FnCall call) {
        if (call.args().isEmpty()) {
            emit(new Instruction.PushStr(""));
        } else {
            Node.Arg arg = call.args().getFirst();
            Expr value = arg instanceof Node.Arg.Positional p ? p.value()
                       : ((Node.Arg.Named) arg).value();
            compileExpr(value);
        }
        emit(new Instruction.Print());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** True if the expression produces a value that needs to be popped when used as a statement. */
    private boolean producesValue(Expr e) {
        if (e instanceof Expr.FnCall call) {
            if (call.name().equals("print") || call.name().equals("input")) return false;
            FnDecl fn = fnDecls.get(call.name());
            if (fn == null) return false;
            return !(fn.returnType() instanceof Node.TypeRef.UnitT);
        }
        return false;
    }

    private boolean isStringExpr(Expr e) {
        return e instanceof Expr.StrLit
            || (e instanceof Expr.FnCall fc && fc.name().equals("input"))
            || (e instanceof Expr.BinOp bo
                && bo.op() == Node.BinOpKind.ADD
                && (isStringExpr(bo.left()) || isStringExpr(bo.right())));
    }

    private void emit(Instruction instr) { out.add(instr); }

    private int emitPlaceholder(Instruction placeholder) {
        int idx = out.size();
        out.add(placeholder);
        return idx;
    }

    private void patch(int index, Instruction replacement) { out.set(index, replacement); }
}
