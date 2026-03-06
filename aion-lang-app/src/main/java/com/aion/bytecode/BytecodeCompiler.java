package com.aion.bytecode;

import com.aion.ast.Node;
import com.aion.ast.Node.AssignTarget;
import com.aion.ast.Node.Expr;
import com.aion.ast.Node.FnDecl;
import com.aion.ast.Node.IfBranch;
import com.aion.ast.Node.Stmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles an Aion AST to flat Aion bytecode.
 *
 * Supported constructs:
 *   - let / mut bindings
 *   - if / else if / else
 *   - while loops
 *   - print(expr) call
 *   - input() call → ReadLine
 *   - arithmetic and comparison expressions
 *   - string concatenation via + on strings
 *   - boolean literals / logic
 */
public class BytecodeCompiler {

    private final List<Instruction> out = new ArrayList<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public Bytecode compile(Node node) {
        out.clear();
        if (node instanceof Node.Module m) {
            compileModule(m);
        } else {
            throw new UnsupportedOperationException(
                    "Cannot compile top-level node: " + node.getClass().getSimpleName());
        }
        emit(new Instruction.Halt());
        return new Bytecode(List.copyOf(out));
    }

    // ── Module / declarations ─────────────────────────────────────────────────

    private void compileModule(Node.Module m) {
        // Find and compile the `main` function
        for (Node decl : m.decls()) {
            if (decl instanceof FnDecl fn && fn.name().equals("main")) {
                compileBlock(fn.body());
                return;
            }
        }
        throw new IllegalStateException("No `main` function found in module.");
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
                    emit(new Instruction.Store(f.path().get(0)));
                } else {
                    throw new UnsupportedOperationException("Only simple variable assignment is supported.");
                }
            }
            case Stmt.ExprStmt es -> compileExprStmt(es.expr());
            case Stmt.If ifStmt   -> compileIf(ifStmt);
            case Stmt.While wh    -> compileWhile(wh);
            case Stmt.Return ret  -> compileExpr(ret.value()); // leave on stack
            case Stmt.For ignored -> throw new UnsupportedOperationException("for loops not yet supported.");
            case Stmt.Block b     -> compileBlock(b);
        }
    }

    /**
     * Compiles an expression used as a statement.
     * For print() calls we emit Print; for other calls we discard the result.
     */
    private void compileExprStmt(Expr expr) {
        if (expr instanceof Expr.FnCall call && call.name().equals("print")) {
            compilePrintCall(call);
        } else {
            compileExpr(expr);
            // result unused – could emit Pop, but our VM doesn't need it
        }
    }

    // ── If / while ────────────────────────────────────────────────────────────

    private void compileIf(Stmt.If ifStmt) {
        // Generate: condition → JumpIfFalse(nextBranch) → body → Jump(end) …
        List<Integer> endJumps = new ArrayList<>();

        for (IfBranch branch : ifStmt.branches()) {
            compileExpr(branch.condition());
            int patchIdx = emitPlaceholder(new Instruction.JumpIfFalse(0));
            compileBlock(branch.body());
            endJumps.add(emitPlaceholder(new Instruction.Jump(0)));
            patch(patchIdx, new Instruction.JumpIfFalse(out.size()));
        }

        // else branch
        if (ifStmt.elseBranch() != null) {
            compileBlock(ifStmt.elseBranch());
        }

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

            default -> throw new UnsupportedOperationException(
                    "Expression not yet supported: " + expr.getClass().getSimpleName());
        }
    }

    private void compileBinOp(Expr.BinOp e) {
        compileExpr(e.left());
        compileExpr(e.right());
        switch (e.op()) {
            case ADD -> {
                // Use Concat for strings, Add for numbers
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
            default      -> throw new UnsupportedOperationException(
                    "Function call not yet supported: " + call.name());
        }
    }

    private void compilePrintCall(Expr.FnCall call) {
        if (call.args().isEmpty()) {
            emit(new Instruction.PushStr(""));
        } else {
            Node.Arg arg = call.args().get(0);
            Expr value = arg instanceof Node.Arg.Positional p ? p.value()
                       : ((Node.Arg.Named) arg).value();
            compileExpr(value);
        }
        emit(new Instruction.Print());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Is this expression statically known to produce a String value? */
    private boolean isStringExpr(Expr e) {
        return e instanceof Expr.StrLit
            || (e instanceof Expr.FnCall fc && fc.name().equals("input"))
            || (e instanceof Expr.BinOp bo
                && bo.op() == Node.BinOpKind.ADD
                && (isStringExpr(bo.left()) || isStringExpr(bo.right())));
    }

    private void emit(Instruction instr) { out.add(instr); }

    /** Emit a placeholder instruction and return its index for later patching. */
    private int emitPlaceholder(Instruction placeholder) {
        int idx = out.size();
        out.add(placeholder);
        return idx;
    }

    /** Replace the instruction at {@code index} with {@code replacement}. */
    private void patch(int index, Instruction replacement) { out.set(index, replacement); }
}
