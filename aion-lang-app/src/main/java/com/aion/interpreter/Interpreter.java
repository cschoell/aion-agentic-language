package com.aion.interpreter;

import com.aion.ast.Node;
import com.aion.ast.Node.*;

import java.util.*;

/**
 * Tree-walking interpreter for Aion.
 *
 * Control flow is handled via exceptions (ReturnSignal, PropagateSignal)
 * rather than return values so nested calls work naturally without threading
 * a result type through every recursive call.
 */
public final class Interpreter {

    // ── Control-flow signals ──────────────────────────────────────────────────

    /** Thrown by `return expr` — caught at the function call boundary. */
    static final class ReturnSignal extends RuntimeException {
        final AionValue value;
        ReturnSignal(AionValue v) { super(null, null, true, false); this.value = v; }
    }

    /** Thrown by the `?` operator on Err/None — caught at the function call boundary. */
    static final class PropagateSignal extends RuntimeException {
        final AionValue value;
        PropagateSignal(AionValue v) { super(null, null, true, false); this.value = v; }
    }

    /** Thrown by `break` — caught by the enclosing while/for loop. */
    static final class BreakSignal extends RuntimeException {
        BreakSignal() { super(null, null, true, false); }
    }

    /** Thrown by `continue` — caught by the enclosing while/for loop. */
    static final class ContinueSignal extends RuntimeException {
        ContinueSignal() { super(null, null, true, false); }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Environment globals;

    public Interpreter() {
        this.globals = Environment.global();
        registerBuiltins();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void loadModule(Node.Module module) {
        // First pass: register all top-level functions and constants
        for (Node decl : module.decls()) {
            if (decl instanceof FnDecl fn) {
                globals.define(fn.name(), new AionValue.FnVal(fn, globals));
            } else if (decl instanceof Node.ConstDecl c) {
                globals.define(c.name(), evalExpr(c.value(), globals));
            }
        }
    }

    public AionValue callFunction(String name, List<AionValue> args) {
        AionValue fn = globals.lookup(name);
        if (!(fn instanceof AionValue.FnVal fnVal)) {
            throw new AionRuntimeException("'" + name + "' is not a function");
        }
        return callFn(fnVal, args);
    }

    // ── Function call ─────────────────────────────────────────────────────────

    private AionValue callFn(AionValue.FnVal fn, List<AionValue> args) {
        Environment env = fn.closure().child();
        List<Param> params = fn.decl().params();
        if (params.size() != args.size()) {
            throw new AionRuntimeException(
                    "'" + fn.decl().name() + "' expects " + params.size() + " args, got " + args.size());
        }
        for (int i = 0; i < params.size(); i++) {
            env.define(params.get(i).name(), args.get(i));
        }

        // ── Enforce @requires pre-conditions ──────────────────────────────────
        for (Node.Annotation ann : fn.decl().annotations()) {
            if (ann instanceof Node.Annotation.Requires req) {
                AionValue cond = evalExpr(req.condition(), env);
                if (!isTruthy(cond))
                    throw new AionRuntimeException(
                            "Pre-condition violated in '" + fn.decl().name() + "': " + req.condition());
            }
        }

        // ── Enforce @timeout ──────────────────────────────────────────────────
        long timeoutMs = fn.decl().annotations().stream()
                .filter(a -> a instanceof Node.Annotation.Timeout)
                .mapToLong(a -> ((Node.Annotation.Timeout) a).millis())
                .findFirst().orElse(-1L);

        AionValue result;
        try {
            if (timeoutMs > 0) {
                result = runWithTimeout(fn, env, timeoutMs);
            } else {
                execBlock(fn.decl().body(), env);
                result = new AionValue.UnitVal();
            }
        } catch (ReturnSignal r) {
            result = r.value;
        } catch (PropagateSignal p) {
            // ? operator early-returned an Err or None — that IS the function's result
            result = p.value;
        }

        // ── Enforce @ensures post-conditions ──────────────────────────────────
        env.define("result", result);
        for (Node.Annotation ann : fn.decl().annotations()) {
            if (ann instanceof Node.Annotation.Ensures ens) {
                AionValue cond = evalExpr(ens.condition(), env);
                if (!isTruthy(cond))
                    throw new AionRuntimeException(
                            "Post-condition violated in '" + fn.decl().name() + "': " + ens.condition());
            }
        }
        return result;
    }

    private AionValue runWithTimeout(AionValue.FnVal fn, Environment env, long timeoutMs) {
        var result = new AionValue[]{new AionValue.UnitVal()};
        var error  = new RuntimeException[]{null};
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                execBlock(fn.decl().body(), env);
            } catch (ReturnSignal r) {
                result[0] = r.value;
            } catch (RuntimeException e) {
                error[0] = e;
            }
        });
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) {
            t.interrupt();
            throw new AionRuntimeException(
                    "Function '" + fn.decl().name() + "' exceeded timeout of " + timeoutMs + "ms");
        }
        if (error[0] != null) throw error[0];
        return result[0];
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private void execBlock(Stmt.Block block, Environment env) {
        for (Stmt s : block.stmts()) execStmt(s, env);
    }

    private void execStmt(Stmt stmt, Environment env) {
        switch (stmt) {
            case Stmt.Let s -> env.define(s.name(), evalExpr(s.value(), env));
            case Stmt.Mut s -> env.define(s.name(), evalExpr(s.value(), env));
            case Stmt.Assign s -> execAssign(s, env);
            case Stmt.Return s -> throw new ReturnSignal(
                    s.value() != null ? evalExpr(s.value(), env) : new AionValue.UnitVal());
            case Stmt.ExprStmt s -> evalExpr(s.expr(), env);
            case Stmt.If s -> execIf(s, env);
            case Stmt.While s -> execWhile(s, env);
            case Stmt.For s -> execFor(s, env);
            case Stmt.Block s -> execBlock(s, env.child());
            case Stmt.Assert s -> {
                AionValue cond = evalExpr(s.condition(), env);
                if (!isTruthy(cond)) {
                    String msg = s.message() != null
                            ? evalExpr(s.message(), env).toString()
                            : "Assertion failed at " + s.pos();
                    throw new AionRuntimeException("AssertionError: " + msg);
                }
            }
            case Stmt.Describe s -> { /* doc-string — no runtime effect */ }
            case Stmt.Break    s -> throw new BreakSignal();
            case Stmt.Continue s -> throw new ContinueSignal();
        }
    }

    private void execAssign(Stmt.Assign s, Environment env) {
        AionValue val = evalExpr(s.value(), env);
        switch (s.target()) {
            case AssignTarget.Field f -> {
                if (f.path().size() == 1) {
                    env.assign(f.path().get(0), val);
                } else {
                    // nested field: obj.field = val
                    AionValue obj = env.lookup(f.path().get(0));
                    for (int i = 1; i < f.path().size() - 1; i++) {
                        obj = getField(obj, f.path().get(i));
                    }
                    if (obj instanceof AionValue.RecordVal r) {
                        r.fields().put(f.path().get(f.path().size() - 1), val);
                    } else {
                        throw new AionRuntimeException("Cannot assign field on " + obj);
                    }
                }
            }
            case AssignTarget.Index ix -> {
                AionValue container = env.lookup(ix.name());
                AionValue index     = evalExpr(ix.index(), env);
                switch (container) {
                    case AionValue.ListVal l -> l.elements().set((int)((AionValue.IntVal)index).value(), val);
                    case AionValue.MapVal  m -> m.entries().put(index, val);
                    default -> throw new AionRuntimeException("Not indexable: " + container);
                }
            }
        }
    }

    private void execIf(Stmt.If s, Environment env) {
        for (IfBranch b : s.branches()) {
            AionValue cond = evalExpr(b.condition(), env);
            if (isTruthy(cond)) { execBlock(b.body(), env.child()); return; }
        }
        if (s.elseBranch() != null) execBlock(s.elseBranch(), env.child());
    }

    private void execWhile(Stmt.While s, Environment env) {
        while (isTruthy(evalExpr(s.condition(), env))) {
            try {
                execBlock(s.body(), env.child());
            } catch (BreakSignal ignored) {
                return;
            } catch (ContinueSignal ignored) {
                // continue to next iteration — re-evaluate condition
            }
        }
    }

    private void execFor(Stmt.For s, Environment env) {
        AionValue iter = evalExpr(s.iterable(), env);
        List<AionValue> items = switch (iter) {
            case AionValue.ListVal l -> l.elements();
            default -> throw new AionRuntimeException("Cannot iterate over " + iter);
        };
        for (AionValue item : new ArrayList<>(items)) {
            Environment loopEnv = env.child();
            loopEnv.define(s.var(), item);
            try {
                execBlock(s.body(), loopEnv);
            } catch (BreakSignal ignored) {
                return;
            } catch (ContinueSignal ignored) {
                // continue to next item
            }
        }
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    public AionValue evalExpr(Expr expr, Environment env) {
        return switch (expr) {
            case Expr.IntLit   e -> new AionValue.IntVal(e.value());
            case Expr.FloatLit e -> new AionValue.FloatVal(e.value());
            case Expr.StrLit   e -> new AionValue.StrVal(e.value());
            case Expr.BoolLit  e -> new AionValue.BoolVal(e.value());
            case Expr.NoneLit  ignored -> new AionValue.NoneVal();
            case Expr.SomeLit  e -> new AionValue.SomeVal(evalExpr(e.inner(), env));
            case Expr.OkLit    e -> new AionValue.OkVal(evalExpr(e.inner(), env));
            case Expr.ErrLit   e -> new AionValue.ErrVal(evalExpr(e.inner(), env));
            // trusted/untrusted are transparent at runtime — taint is a static property
            case Expr.TrustedExpr   e -> evalExpr(e.inner(), env);
            case Expr.UntrustedExpr e -> evalExpr(e.inner(), env);
            case Expr.VarRef   e -> env.lookup(e.name());
            case Expr.EnumVariantRef e -> new AionValue.EnumVal(e.typeName(), e.variant(), List.of());
            case Expr.EnumRecordLit  e -> {
                Map<String, AionValue> fields = new LinkedHashMap<>();
                for (NamedArg arg : e.fields()) fields.put(arg.name(), evalExpr(arg.value(), env));
                yield new AionValue.EnumVal(e.typeName(), e.variant(),
                        List.of(new AionValue.RecordVal(e.variant(), fields)));
            }
            case Expr.EnumTupleLit   e -> {
                List<AionValue> payload = new ArrayList<>();
                for (Arg arg : e.args()) payload.add(resolveArg(arg, env));
                yield new AionValue.EnumVal(e.typeName(), e.variant(), payload);
            }
            case Expr.RecordLit e -> evalRecordLit(e, env);
            case Expr.FnCall   e -> evalFnCall(e, env);
            case Expr.MethodCall e -> evalMethodCall(e, env);
            case Expr.FieldAccess e -> getField(evalExpr(e.receiver(), env), e.field());
            case Expr.IndexAccess e -> evalIndex(evalExpr(e.receiver(), env), evalExpr(e.index(), env));
            case Expr.BinOp    e -> evalBinOp(e, env);
            case Expr.UnaryOp  e -> evalUnaryOp(e, env);
            case Expr.Pipe     e -> evalPipe(e, env);
            case Expr.Match    e -> evalMatch(e, env);
            case Expr.BlockExpr e -> evalBlockExpr(e, env);
            case Expr.ListLit  e -> {
                List<AionValue> elems = new ArrayList<>();
                for (Expr el : e.elements()) elems.add(evalExpr(el, env));
                yield new AionValue.ListVal(elems);
            }
            case Expr.MapLit e -> {
                Map<AionValue, AionValue> map = new LinkedHashMap<>();
                for (MapEntry me : e.entries()) map.put(evalExpr(me.key(), env), evalExpr(me.value(), env));
                yield new AionValue.MapVal(map);
            }
            case Expr.Propagate e -> {
                AionValue inner = evalExpr(e.inner(), env);
                yield switch (inner) {
                    case AionValue.OkVal ok   -> ok.inner();
                    case AionValue.SomeVal sv -> sv.inner();
                    case AionValue.ErrVal err -> throw new PropagateSignal(err);
                    case AionValue.NoneVal ignored -> throw new PropagateSignal(new AionValue.NoneVal());
                    default -> inner;
                };
            }
            case Expr.InterpolatedStr e -> {
                StringBuilder sb = new StringBuilder();
                for (Object part : e.parts()) {
                    if (part instanceof String s) {
                        sb.append(s);
                    } else {
                        sb.append(evalExpr((Expr) part, env));
                    }
                }
                yield new AionValue.StrVal(sb.toString());
            }
        };
    }

    private AionValue evalRecordLit(Expr.RecordLit e, Environment env) {
        Map<String, AionValue> fields = new LinkedHashMap<>();
        for (NamedArg arg : e.fields()) fields.put(arg.name(), evalExpr(arg.value(), env));
        return new AionValue.RecordVal(e.typeName(), fields);
    }

    private AionValue evalFnCall(Expr.FnCall e, Environment env) {
        // Check builtins first, then user functions
        Optional<AionValue> builtin = tryCallBuiltin(e.name(), e.args(), env);
        if (builtin.isPresent()) return builtin.get();

        AionValue fn = env.lookup(e.name());
        if (!(fn instanceof AionValue.FnVal fnVal)) {
            throw new AionRuntimeException("'" + e.name() + "' is not callable");
        }
        List<AionValue> argVals = resolveArgs(e.args(), fnVal.decl().params(), env);
        return callFn(fnVal, argVals);
    }

    private AionValue evalMethodCall(Expr.MethodCall e, Environment env) {
        AionValue receiver = evalExpr(e.receiver(), env);
        List<AionValue> argVals = new ArrayList<>();
        for (Arg a : e.args()) argVals.add(resolveArg(a, env));
        return callMethod(receiver, e.method(), argVals);
    }

    private AionValue evalPipe(Expr.Pipe e, Environment env) {
        AionValue left = evalExpr(e.left(), env);
        // right must resolve to a callable
        AionValue right = evalExpr(e.right(), env);
        if (right instanceof AionValue.FnVal fn) {
            return callFn(fn, List.of(left));
        }
        throw new AionRuntimeException("Right side of |> must be a function, got: " + right);
    }

    private AionValue evalBlockExpr(Expr.BlockExpr e, Environment env) {
        Environment inner = env.child();
        for (Stmt s : e.stmts()) execStmt(s, inner);
        return evalExpr(e.value(), inner);
    }

    private AionValue evalBinOp(Expr.BinOp e, Environment env) {
        // Short-circuit for AND / OR
        if (e.op() == BinOpKind.AND) {
            AionValue l = evalExpr(e.left(), env);
            return isTruthy(l) ? evalExpr(e.right(), env) : new AionValue.BoolVal(false);
        }
        if (e.op() == BinOpKind.OR) {
            AionValue l = evalExpr(e.left(), env);
            return isTruthy(l) ? l : evalExpr(e.right(), env);
        }
        AionValue left  = evalExpr(e.left(), env);
        AionValue right = evalExpr(e.right(), env);
        return switch (e.op()) {
            case ADD -> arith(left, right, (a,b)->a+b, (a,b)->a+b);
            case SUB -> arith(left, right, (a,b)->a-b, (a,b)->a-b);
            case MUL -> arith(left, right, (a,b)->a*b, (a,b)->a*b);
            case DIV -> arith(left, right, (a,b)->a/b, (a,b)->a/b);
            case MOD -> arith(left, right, (a,b)->a%b, (a,b)->a%b);
            case EQ  -> new AionValue.BoolVal(aionEquals(left, right));
            case NEQ -> new AionValue.BoolVal(!aionEquals(left, right));
            case LT  -> cmp(left, right, c -> c < 0);
            case LE  -> cmp(left, right, c -> c <= 0);
            case GT  -> cmp(left, right, c -> c > 0);
            case GE  -> cmp(left, right, c -> c >= 0);
            default  -> throw new AionRuntimeException("Unexpected op: " + e.op());
        };
    }

    private AionValue evalUnaryOp(Expr.UnaryOp e, Environment env) {
        AionValue v = evalExpr(e.operand(), env);
        return switch (e.op()) {
            case NEG -> switch (v) {
                case AionValue.IntVal i   -> new AionValue.IntVal(-i.value());
                case AionValue.FloatVal f -> new AionValue.FloatVal(-f.value());
                default -> throw new AionRuntimeException("Cannot negate " + v);
            };
            case NOT -> new AionValue.BoolVal(!isTruthy(v));
        };
    }

    // ── Pattern matching ──────────────────────────────────────────────────────

    private AionValue evalMatch(Expr.Match e, Environment env) {
        AionValue subject = evalExpr(e.subject(), env);
        for (MatchArm arm : e.arms()) {
            Environment armEnv = env.child();
            if (matchPattern(arm.pattern(), subject, armEnv)) {
                // Check optional guard: pattern [if guard] => body
                if (arm.guard() != null && !isTruthy(evalExpr(arm.guard(), armEnv))) continue;
                return evalExpr(arm.body(), armEnv);
            }
        }
        throw new AionRuntimeException("Non-exhaustive match — no arm matched: " + subject);
    }

    private boolean matchPattern(Pattern pat, AionValue val, Environment bindings) {
        return switch (pat) {
            case Pattern.Wildcard ignored       -> true;
            case Pattern.Bind b                 -> { bindings.define(b.name(), val); yield true; }
            case Pattern.IntPat p               -> val instanceof AionValue.IntVal i && i.value() == p.value();
            case Pattern.FloatPat p             -> val instanceof AionValue.FloatVal f && f.value() == p.value();
            case Pattern.StrPat p               -> val instanceof AionValue.StrVal s && s.value().equals(p.value());
            case Pattern.BoolPat p              -> val instanceof AionValue.BoolVal b && b.value() == p.value();
            case Pattern.NonePat ignored        -> val instanceof AionValue.NoneVal;
            case Pattern.SomePat p              -> val instanceof AionValue.SomeVal sv && matchPattern(p.inner(), sv.inner(), bindings);
            case Pattern.OkPat p                -> val instanceof AionValue.OkVal ok && matchPattern(p.inner(), ok.inner(), bindings);
            case Pattern.ErrPat p               -> val instanceof AionValue.ErrVal err && matchPattern(p.inner(), err.inner(), bindings);
            case Pattern.EnumPat p              -> val instanceof AionValue.EnumVal ev
                                                    && ev.typeName().equals(p.typeName())
                                                    && ev.variant().equals(p.variant());
            case Pattern.EnumTuplePat p         -> {
                if (!(val instanceof AionValue.EnumVal ev)) yield false;
                if (!ev.typeName().equals(p.typeName()) || !ev.variant().equals(p.variant())) yield false;
                if (ev.payload().size() != p.fields().size()) yield false;
                for (int i = 0; i < p.fields().size(); i++) {
                    if (!matchPattern(p.fields().get(i), ev.payload().get(i), bindings)) yield false;
                }
                yield true;
            }
            case Pattern.EnumRecordPat p        -> {
                if (!(val instanceof AionValue.EnumVal ev)) yield false;
                if (!ev.typeName().equals(p.typeName()) || !ev.variant().equals(p.variant())) yield false;
                // payload index 0 is the record value
                if (ev.payload().isEmpty() || !(ev.payload().get(0) instanceof AionValue.RecordVal rv)) yield false;
                for (FieldPat fp : p.fields()) {
                    AionValue fv = rv.fields().get(fp.field());
                    if (fv == null || !matchPattern(fp.pattern(), fv, bindings)) yield false;
                }
                yield true;
            }
            case Pattern.RecordPat p -> {
                if (!(val instanceof AionValue.RecordVal rv)) yield false;
                if (!rv.typeName().equals(p.typeName())) yield false;
                for (FieldPat fp : p.fields()) {
                    AionValue fv = rv.fields().get(fp.field());
                    if (fv == null || !matchPattern(fp.pattern(), fv, bindings)) yield false;
                }
                yield true;
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AionValue getField(AionValue val, String field) {
        return switch (val) {
            case AionValue.RecordVal r -> {
                AionValue f = r.fields().get(field);
                if (f == null) throw new AionRuntimeException("No field '" + field + "' on " + r.typeName());
                yield f;
            }
            default -> throw new AionRuntimeException("Cannot access field '" + field + "' on " + val);
        };
    }

    private AionValue evalIndex(AionValue container, AionValue index) {
        return switch (container) {
            case AionValue.ListVal l -> {
                int i = (int) ((AionValue.IntVal) index).value();
                if (i < 0 || i >= l.elements().size())
                    throw new AionRuntimeException("Index out of bounds: " + i);
                yield l.elements().get(i);
            }
            case AionValue.MapVal m -> {
                AionValue v = m.entries().get(index);
                yield v != null ? new AionValue.SomeVal(v) : new AionValue.NoneVal();
            }
            default -> throw new AionRuntimeException("Not indexable: " + container);
        };
    }

    private interface LongBiOp  { long   apply(long a, long b); }
    private interface DoubleBiOp{ double apply(double a, double b); }

    private AionValue arith(AionValue l, AionValue r, LongBiOp iop, DoubleBiOp fop) {
        return switch (l) {
            case AionValue.IntVal a -> switch (r) {
                case AionValue.IntVal b   -> new AionValue.IntVal(iop.apply(a.value(), b.value()));
                case AionValue.FloatVal b -> new AionValue.FloatVal(fop.apply(a.value(), b.value()));
                default -> throw new AionRuntimeException("Type mismatch in arithmetic: " + r);
            };
            case AionValue.FloatVal a -> switch (r) {
                case AionValue.FloatVal b -> new AionValue.FloatVal(fop.apply(a.value(), b.value()));
                case AionValue.IntVal b   -> new AionValue.FloatVal(fop.apply(a.value(), b.value()));
                default -> throw new AionRuntimeException("Type mismatch in arithmetic: " + r);
            };
            case AionValue.StrVal a -> switch (r) {
                case AionValue.StrVal b -> new AionValue.StrVal(a.value() + b.value()); // + only
                default -> throw new AionRuntimeException("Cannot add Str and " + r);
            };
            default -> throw new AionRuntimeException("Not a number: " + l);
        };
    }

    private AionValue cmp(AionValue l, AionValue r, java.util.function.IntPredicate pred) {
        int c = switch (l) {
            case AionValue.IntVal a -> switch (r) {
                case AionValue.IntVal b   -> Long.compare(a.value(), b.value());
                case AionValue.FloatVal b -> Double.compare(a.value(), b.value());
                default -> throw new AionRuntimeException("Cannot compare " + l + " with " + r);
            };
            case AionValue.FloatVal a -> switch (r) {
                case AionValue.FloatVal b -> Double.compare(a.value(), b.value());
                case AionValue.IntVal b   -> Double.compare(a.value(), b.value());
                default -> throw new AionRuntimeException("Cannot compare " + l + " with " + r);
            };
            case AionValue.StrVal a -> switch (r) {
                case AionValue.StrVal b -> a.value().compareTo(b.value());
                default -> throw new AionRuntimeException("Cannot compare " + l + " with " + r);
            };
            default -> throw new AionRuntimeException("Not comparable: " + l);
        };
        return new AionValue.BoolVal(pred.test(c));
    }

    private boolean aionEquals(AionValue a, AionValue b) {
        return switch (a) {
            case AionValue.IntVal   x -> b instanceof AionValue.IntVal   y && x.value() == y.value();
            case AionValue.FloatVal x -> b instanceof AionValue.FloatVal y && x.value() == y.value();
            case AionValue.BoolVal  x -> b instanceof AionValue.BoolVal  y && x.value() == y.value();
            case AionValue.StrVal   x -> b instanceof AionValue.StrVal   y && x.value().equals(y.value());
            case AionValue.UnitVal  ignored -> b instanceof AionValue.UnitVal;
            case AionValue.NoneVal  ignored -> b instanceof AionValue.NoneVal;
            default -> a == b;
        };
    }

    private boolean isTruthy(AionValue v) {
        return switch (v) {
            case AionValue.BoolVal b -> b.value();
            case AionValue.NoneVal ignored -> false;
            case AionValue.UnitVal ignored -> false;
            default -> true;
        };
    }

    private List<AionValue> resolveArgs(List<Arg> args, List<Param> params, Environment env) {
        if (args.isEmpty()) return List.of();
        // Build name→index map for named args
        Map<String, Integer> paramIndex = new HashMap<>();
        for (int i = 0; i < params.size(); i++) paramIndex.put(params.get(i).name(), i);

        AionValue[] result = new AionValue[params.size()];
        int pos = 0;
        for (Arg a : args) {
            switch (a) {
                case Arg.Named n  -> result[paramIndex.get(n.name())] = evalExpr(n.value(), env);
                case Arg.Positional p -> result[pos++] = evalExpr(p.value(), env);
            }
        }
        return Arrays.asList(result);
    }

    private AionValue resolveArg(Arg a, Environment env) {
        return switch (a) {
            case Arg.Named n    -> evalExpr(n.value(), env);
            case Arg.Positional p -> evalExpr(p.value(), env);
        };
    }

    // ── Built-in functions ────────────────────────────────────────────────────

    private java.util.Scanner stdinScanner;

    private java.util.Scanner stdin() {
        if (stdinScanner == null) stdinScanner = new java.util.Scanner(System.in);
        return stdinScanner;
    }

    private void registerBuiltins() {
        // No-op: builtins are dispatched in tryCallBuiltin
    }

    private Optional<AionValue> tryCallBuiltin(String name, List<Arg> args, Environment env) {
        List<AionValue> vals = new ArrayList<>();
        for (Arg a : args) vals.add(resolveArg(a, env));

        return switch (name) {
            case "print"   -> { System.out.println(vals.stream().map(Object::toString)
                                    .reduce((a,b)->a+" "+b).orElse("")); yield Optional.of(new AionValue.UnitVal()); }
            case "input"   -> Optional.of(new AionValue.StrVal(stdin().nextLine()));
            case "str"     -> Optional.of(new AionValue.StrVal(vals.get(0).toString()));
            case "int"     -> Optional.of(switch (vals.get(0)) {
                                case AionValue.StrVal s   -> new AionValue.IntVal(Long.parseLong(s.value()));
                                case AionValue.FloatVal f -> new AionValue.IntVal((long) f.value());
                                default -> vals.get(0);
                             });
            case "float"   -> Optional.of(switch (vals.get(0)) {
                                case AionValue.StrVal s   -> new AionValue.FloatVal(Double.parseDouble(s.value()));
                                case AionValue.IntVal i   -> new AionValue.FloatVal(i.value());
                                default -> vals.get(0);
                             });
            case "len"     -> Optional.of(switch (vals.get(0)) {
                                case AionValue.ListVal l -> new AionValue.IntVal(l.elements().size());
                                case AionValue.StrVal  s -> new AionValue.IntVal(s.value().length());
                                case AionValue.MapVal  m -> new AionValue.IntVal(m.entries().size());
                                default -> throw new AionRuntimeException("len() not supported for " + vals.get(0));
                             });
            case "assert"  -> {
                if (!isTruthy(vals.get(0)))
                    throw new AionRuntimeException("Assertion failed" +
                            (vals.size() > 1 ? ": " + vals.get(1) : ""));
                yield Optional.of(new AionValue.UnitVal());
            }
            case "is_some" -> Optional.of(new AionValue.BoolVal(vals.get(0) instanceof AionValue.SomeVal));
            case "is_none" -> Optional.of(new AionValue.BoolVal(vals.get(0) instanceof AionValue.NoneVal));
            case "is_ok"   -> Optional.of(new AionValue.BoolVal(vals.get(0) instanceof AionValue.OkVal));
            case "is_err"  -> Optional.of(new AionValue.BoolVal(vals.get(0) instanceof AionValue.ErrVal));
            case "unwrap"  -> Optional.of(switch (vals.get(0)) {
                                case AionValue.SomeVal sv -> sv.inner();
                                case AionValue.OkVal  ov -> ov.inner();
                                default -> throw new AionRuntimeException("unwrap() called on " + vals.get(0));
                             });
            case "unwrap_or" -> Optional.of(switch (vals.get(0)) {
                                case AionValue.SomeVal sv -> sv.inner();
                                case AionValue.OkVal  ov -> ov.inner();
                                default -> vals.get(1);
                             });
            default -> Optional.empty();
        };
    }

    private AionValue callMethod(AionValue receiver, String method, List<AionValue> args) {
        return switch (receiver) {
            case AionValue.ListVal l -> switch (method) {
                case "push"    -> { l.elements().add(args.get(0)); yield new AionValue.UnitVal(); }
                case "pop"     -> l.elements().isEmpty() ? new AionValue.NoneVal()
                                    : new AionValue.SomeVal(l.elements().remove(l.elements().size()-1));
                case "get"     -> {
                    int i = (int)((AionValue.IntVal)args.get(0)).value();
                    yield (i >= 0 && i < l.elements().size())
                            ? new AionValue.SomeVal(l.elements().get(i))
                            : new AionValue.NoneVal();
                }
                case "len"     -> new AionValue.IntVal(l.elements().size());
                case "contains"-> new AionValue.BoolVal(l.elements().stream().anyMatch(v -> aionEquals(v, args.get(0))));
                case "map"     -> {
                    AionValue.FnVal fn = (AionValue.FnVal) args.get(0);
                    List<AionValue> result = new ArrayList<>();
                    for (AionValue v : l.elements()) result.add(callFn(fn, List.of(v)));
                    yield new AionValue.ListVal(result);
                }
                case "filter"  -> {
                    AionValue.FnVal fn = (AionValue.FnVal) args.get(0);
                    List<AionValue> result = new ArrayList<>();
                    for (AionValue v : l.elements()) if (isTruthy(callFn(fn, List.of(v)))) result.add(v);
                    yield new AionValue.ListVal(result);
                }
                default -> throw new AionRuntimeException("List has no method '" + method + "'");
            };
            case AionValue.MapVal m -> switch (method) {
                case "get"     -> {
                    AionValue v = m.entries().get(args.get(0));
                    yield v != null ? new AionValue.SomeVal(v) : new AionValue.NoneVal();
                }
                case "set"     -> { m.entries().put(args.get(0), args.get(1)); yield new AionValue.UnitVal(); }
                case "remove"  -> { m.entries().remove(args.get(0)); yield new AionValue.UnitVal(); }
                case "contains"-> new AionValue.BoolVal(m.entries().containsKey(args.get(0)));
                case "len"     -> new AionValue.IntVal(m.entries().size());
                case "keys"    -> new AionValue.ListVal(new ArrayList<>(m.entries().keySet()));
                case "values"  -> new AionValue.ListVal(new ArrayList<>(m.entries().values()));
                default -> throw new AionRuntimeException("Map has no method '" + method + "'");
            };
            case AionValue.StrVal s -> switch (method) {
                case "len"       -> new AionValue.IntVal(s.value().length());
                case "contains"  -> new AionValue.BoolVal(s.value().contains(((AionValue.StrVal)args.get(0)).value()));
                case "starts_with"->new AionValue.BoolVal(s.value().startsWith(((AionValue.StrVal)args.get(0)).value()));
                case "ends_with" -> new AionValue.BoolVal(s.value().endsWith(((AionValue.StrVal)args.get(0)).value()));
                case "upper"     -> new AionValue.StrVal(s.value().toUpperCase());
                case "lower"     -> new AionValue.StrVal(s.value().toLowerCase());
                case "trim"      -> new AionValue.StrVal(s.value().trim());
                case "split"     -> {
                    String delim = ((AionValue.StrVal)args.get(0)).value();
                    List<AionValue> parts = new ArrayList<>();
                    for (String p : s.value().split(java.util.regex.Pattern.quote(delim)))
                        parts.add(new AionValue.StrVal(p));
                    yield new AionValue.ListVal(parts);
                }
                default -> throw new AionRuntimeException("Str has no method '" + method + "'");
            };
            default -> throw new AionRuntimeException("No method '" + method + "' on " + receiver);
        };
    }
}

