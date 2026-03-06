package com.aion.bytecode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

/**
 * Simple stack-based VM for executing Aion bytecode.
 */
public class BytecodeVM {
    private final Stack<Object>     stack   = new Stack<>();
    private final Map<String,Object> vars   = new HashMap<>();
    private final Scanner           scanner = new Scanner(System.in);

    public void run(Bytecode bytecode) {
        List<Instruction> instrs = bytecode.instructions;
        int ip = 0;
        while (ip < instrs.size()) {
            Instruction instr = instrs.get(ip);
            ip = execute(instr, ip, instrs.size());
        }
    }

    private int execute(Instruction instr, int ip, int total) {
        switch (instr) {
            // ── Literals ─────────────────────────────────────────────────────
            case Instruction.PushInt    p -> stack.push(p.value());
            case Instruction.PushFloat  p -> stack.push(p.value());
            case Instruction.PushStr    p -> stack.push(p.value());
            case Instruction.PushBool   p -> stack.push(p.value());
            case Instruction.PushNone   p -> stack.push(null);

            // ── Arithmetic ───────────────────────────────────────────────────
            case Instruction.Add a -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(numericOp(a2, b, '+'));
            }
            case Instruction.Sub s -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(numericOp(a2, b, '-'));
            }
            case Instruction.Mul m -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(numericOp(a2, b, '*'));
            }
            case Instruction.Div d -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(numericOp(a2, b, '/'));
            }
            case Instruction.Mod m -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(numericOp(a2, b, '%'));
            }
            case Instruction.Neg n -> {
                Object v = stack.pop();
                if (v instanceof Long l)   stack.push(-l);
                else if (v instanceof Double d) stack.push(-d);
                else throw new IllegalStateException("NEG expects a number, got: " + v);
            }

            // ── Comparison ───────────────────────────────────────────────────
            case Instruction.Eq e -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(objectsEqual(a2, b));
            }
            case Instruction.Ne n -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(!objectsEqual(a2, b));
            }
            case Instruction.Lt l -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(compare(a2, b) < 0);
            }
            case Instruction.Le l -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(compare(a2, b) <= 0);
            }
            case Instruction.Gt g -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(compare(a2, b) > 0);
            }
            case Instruction.Ge g -> {
                Object b = stack.pop(), a2 = stack.pop();
                stack.push(compare(a2, b) >= 0);
            }

            // ── Logic ─────────────────────────────────────────────────────────
            case Instruction.And an -> {
                boolean b = (Boolean) stack.pop(), a2 = (Boolean) stack.pop();
                stack.push(a2 && b);
            }
            case Instruction.Or or -> {
                boolean b = (Boolean) stack.pop(), a2 = (Boolean) stack.pop();
                stack.push(a2 || b);
            }
            case Instruction.Not nt -> stack.push(!(Boolean) stack.pop());

            // ── String ────────────────────────────────────────────────────────
            case Instruction.Concat c -> {
                String b = String.valueOf(stack.pop());
                String a2 = String.valueOf(stack.pop());
                stack.push(a2 + b);
            }

            // ── Variables ─────────────────────────────────────────────────────
            case Instruction.Store st -> vars.put(st.name(), stack.pop());
            case Instruction.Load  ld -> stack.push(vars.get(ld.name()));

            // ── I/O ───────────────────────────────────────────────────────────
            case Instruction.Print    pr -> System.out.println(format(stack.pop()));
            case Instruction.ReadLine rl -> stack.push(scanner.nextLine());

            // ── Control flow ─────────────────────────────────────────────────
            case Instruction.Jump j         -> { return j.target(); }
            case Instruction.JumpIfFalse jf -> {
                boolean cond = (Boolean) stack.pop();
                if (!cond) return jf.target();
            }

            // ── Meta ──────────────────────────────────────────────────────────
            case Instruction.Halt h -> { return total; }
        }
        return ip + 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Object numericOp(Object a, Object b, char op) {
        boolean floating = (a instanceof Double) || (b instanceof Double);
        if (floating) {
            double da = ((Number) a).doubleValue(), db = ((Number) b).doubleValue();
            return switch (op) {
                case '+' -> da + db;
                case '-' -> da - db;
                case '*' -> da * db;
                case '/' -> da / db;
                case '%' -> da % db;
                default  -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        }
        long la = ((Number) a).longValue(), lb = ((Number) b).longValue();
        return switch (op) {
            case '+' -> la + lb;
            case '-' -> la - lb;
            case '*' -> la * lb;
            case '/' -> la / lb;
            case '%' -> la % lb;
            default  -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double))
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) return Long.compare(la, lb);
        if (a instanceof Number na && b instanceof Number nb) return Double.compare(na.doubleValue(), nb.doubleValue());
        if (a instanceof String sa && b instanceof String sb) return sa.compareTo(sb);
        throw new IllegalStateException("Cannot compare " + a + " and " + b);
    }

    private String format(Object v) {
        return v == null ? "none" : v.toString();
    }
}
