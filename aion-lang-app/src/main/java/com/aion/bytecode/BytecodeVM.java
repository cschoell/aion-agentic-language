package com.aion.bytecode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

/**
 * Stack-based VM for Aion bytecode with a proper call stack.
 *
 * Each function call pushes a Frame containing:
 *   - the return address (ip to resume in the caller)
 *   - the caller's local variable scope
 *
 * The operand stack is shared across frames (like the JVM).
 * Local variables are per-frame and are looked up bottom-up through the
 * frame stack so that called functions have their own clean scope.
 */
public class BytecodeVM {

    // ── Frame ─────────────────────────────────────────────────────────────────

    private record Frame(int returnAddr, Map<String, Object> locals) {}

    // ── VM state ──────────────────────────────────────────────────────────────

    private final Stack<Object>   stack   = new Stack<>();
    private final Deque<Frame>    frames  = new ArrayDeque<>();
    /** Current frame's locals — always equals frames.peek().locals() */
    private Map<String, Object>   locals  = new HashMap<>();
    private final Scanner         scanner = new Scanner(System.in);

    public void run(Bytecode bytecode) {
        frames.clear();
        stack.clear();
        locals = new HashMap<>();

        List<Instruction> instrs = bytecode.instructions;
        int ip = 0;
        while (ip < instrs.size()) {
            ip = execute(instrs.get(ip), ip, instrs.size(), instrs);
        }
    }

    // ── Instruction dispatch ──────────────────────────────────────────────────

    private int execute(Instruction instr, int ip, int total,
                        List<Instruction> instrs) {
        switch (instr) {
            // ── Literals ─────────────────────────────────────────────────────
            case Instruction.PushInt    p -> stack.push(p.value());
            case Instruction.PushFloat  p -> stack.push(p.value());
            case Instruction.PushStr    p -> stack.push(p.value());
            case Instruction.PushBool   p -> stack.push(p.value());
            case Instruction.PushNone   p -> stack.push(null);

            // ── Arithmetic ───────────────────────────────────────────────────
            case Instruction.Add ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(numericOp(a, b, '+')); }
            case Instruction.Sub ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(numericOp(a, b, '-')); }
            case Instruction.Mul ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(numericOp(a, b, '*')); }
            case Instruction.Div ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(numericOp(a, b, '/')); }
            case Instruction.Mod ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(numericOp(a, b, '%')); }
            case Instruction.Neg ignored -> {
                Object v = stack.pop();
                if (v instanceof Long l)        stack.push(-l);
                else if (v instanceof Double d) stack.push(-d);
                else throw new IllegalStateException("NEG expects a number, got: " + v);
            }

            // ── Comparison ───────────────────────────────────────────────────
            case Instruction.Eq  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(objectsEqual(a, b)); }
            case Instruction.Ne  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(!objectsEqual(a, b)); }
            case Instruction.Lt  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) < 0); }
            case Instruction.Le  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) <= 0); }
            case Instruction.Gt  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) > 0); }
            case Instruction.Ge  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) >= 0); }

            // ── Logic ─────────────────────────────────────────────────────────
            case Instruction.And ignored -> { boolean b = (Boolean) stack.pop(), a = (Boolean) stack.pop(); stack.push(a && b); }
            case Instruction.Or  ignored -> { boolean b = (Boolean) stack.pop(), a = (Boolean) stack.pop(); stack.push(a || b); }
            case Instruction.Not ignored -> stack.push(!(Boolean) stack.pop());

            // ── String ────────────────────────────────────────────────────────
            case Instruction.Concat ignored -> {
                String b = String.valueOf(stack.pop()), a = String.valueOf(stack.pop());
                stack.push(a + b);
            }

            // ── Variables (frame-local) ───────────────────────────────────────
            case Instruction.Store st -> locals.put(st.name(), stack.pop());
            case Instruction.Load  ld -> stack.push(lookupVar(ld.name()));

            // ── I/O ───────────────────────────────────────────────────────────
            case Instruction.Print    ignored -> System.out.println(format(stack.pop()));
            case Instruction.ReadLine ignored -> stack.push(scanner.nextLine());

            // ── Control flow ─────────────────────────────────────────────────
            case Instruction.Jump j         -> { return j.target(); }
            case Instruction.JumpIfFalse jf -> { if (!(Boolean) stack.pop()) return jf.target(); }
            case Instruction.JumpIfTrue  jt -> { if  ((Boolean) stack.pop()) return jt.target(); }

            // ── Function call / return ────────────────────────────────────────
            case Instruction.Call c -> {
                // Pop arguments from stack in reverse order and bind to params
                Object[] args = new Object[c.arity()];
                for (int i = c.arity() - 1; i >= 0; i--) args[i] = stack.pop();

                // Push a new call frame: save return address and current locals
                frames.push(new Frame(ip + 1, locals));
                locals = new HashMap<>();
                for (int i = 0; i < c.params().size(); i++)
                    locals.put(c.params().get(i), args[i]);

                return c.target();   // jump to the function entry point
            }
            case Instruction.Return r -> {
                Object retVal = r.hasValue() ? stack.pop() : null;
                // Restore caller's frame
                Frame frame = frames.pop();
                locals = frame.locals();
                if (retVal != null) stack.push(retVal);
                return frame.returnAddr();   // resume after the Call instruction
            }
            case Instruction.Pop ignored -> stack.pop();

            // ── Built-in method calls ─────────────────────────────────────────
            case Instruction.CallMethod cm -> {
                // Stack: [..., receiver, arg0, …, argN-1]  (receiver deepest)
                Object[] args = new Object[cm.arity()];
                for (int i = cm.arity() - 1; i >= 0; i--) args[i] = stack.pop();
                Object receiver = stack.pop();
                stack.push(dispatchMethod(receiver, cm.method(), args));
            }

            // ── Errors ────────────────────────────────────────────────────────
            case Instruction.Throw    ignored -> throw new RuntimeException((String) stack.pop());
            case Instruction.Break    ignored -> throw new RuntimeException("BUG: unpatched Break instruction");
            case Instruction.Continue ignored -> throw new RuntimeException("BUG: unpatched Continue instruction");
            case Instruction.Stringify ignored -> stack.push(String.valueOf(stack.pop()));

            // ── Meta ──────────────────────────────────────────────────────────
            case Instruction.Halt ignored -> { return total; }
        }
        return ip + 1;
    }

    // ── Variable lookup ───────────────────────────────────────────────────────

    /**
     * Look up a variable, searching from the current frame upward through
     * the call stack so that inner scopes can read caller variables.
     * (In practice, Aion's scoping is function-level, so this just checks locals.)
     */
    private Object lookupVar(String name) {
        if (locals.containsKey(name)) return locals.get(name);
        for (Frame f : frames) {
            if (f.locals().containsKey(name)) return f.locals().get(name);
        }
        throw new RuntimeException("Undefined variable: " + name);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Object numericOp(Object a, Object b, char op) {
        boolean floating = (a instanceof Double) || (b instanceof Double);
        if (floating) {
            double da = ((Number) a).doubleValue(), db = ((Number) b).doubleValue();
            return switch (op) {
                case '+' -> da + db; case '-' -> da - db;
                case '*' -> da * db; case '/' -> da / db; case '%' -> da % db;
                default  -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        }
        long la = ((Number) a).longValue(), lb = ((Number) b).longValue();
        return switch (op) {
            case '+' -> la + lb; case '-' -> la - lb;
            case '*' -> la * lb; case '/' -> la / lb; case '%' -> la % lb;
            default  -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if ((a instanceof Long || a instanceof Double)
                && (b instanceof Long || b instanceof Double))
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return a.equals(b);
    }

    private int compare(Object a, Object b) {
        if (a instanceof Long la   && b instanceof Long lb)   return Long.compare(la, lb);
        if (a instanceof Number na && b instanceof Number nb) return Double.compare(na.doubleValue(), nb.doubleValue());
        if (a instanceof String sa && b instanceof String sb) return sa.compareTo(sb);
        throw new IllegalStateException("Cannot compare " + a + " and " + b);
    }

    private String format(Object v) { return v == null ? "none" : v.toString(); }

    // ── Built-in method dispatch ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object dispatchMethod(Object receiver, String method, Object[] args) {
        return switch (receiver) {
            case String s -> switch (method) {
                case "len"        -> (long) s.length();
                case "trim"       -> s.trim();
                case "upper"      -> s.toUpperCase();
                case "lower"      -> s.toLowerCase();
                case "contains"   -> s.contains((String) args[0]);
                case "starts_with"-> s.startsWith((String) args[0]);
                case "ends_with"  -> s.endsWith((String) args[0]);
                case "split"      -> {
                    String[] parts = s.split(java.util.regex.Pattern.quote((String) args[0]));
                    yield new java.util.ArrayList<>(java.util.Arrays.asList(parts));
                }
                default -> throw new RuntimeException("Str has no method '" + method + "'");
            };
            case java.util.List list -> switch (method) {
                case "len"     -> (long) list.size();
                case "push"    -> { list.add(args[0]); yield null; }
                case "pop"     -> list.isEmpty() ? null : list.remove(list.size() - 1);
                case "get"     -> {
                    int i = (int)((Long) args[0]).longValue();
                    yield (i >= 0 && i < list.size()) ? list.get(i) : null;
                }
                case "contains"-> list.contains(args[0]);
                default -> throw new RuntimeException("List has no method '" + method + "'");
            };
            case java.util.Map map -> switch (method) {
                case "get"     -> map.get(args[0]);
                case "set"     -> { map.put(args[0], args[1]); yield null; }
                case "remove"  -> { map.remove(args[0]); yield null; }
                case "contains"-> map.containsKey(args[0]);
                case "len"     -> (long) map.size();
                default -> throw new RuntimeException("Map has no method '" + method + "'");
            };
            default -> throw new RuntimeException(
                    "No method '" + method + "' on " + receiver.getClass().getSimpleName());
        };
    }
}
