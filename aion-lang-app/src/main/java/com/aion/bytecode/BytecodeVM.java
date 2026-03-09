package com.aion.bytecode;

import com.aion.bytecode.VmValue.*;

import java.util.*;

/**
 * Stack-based VM for Aion bytecode with a proper call stack.
 * Primitive Java types are used for scalars:
 *   Int → Long, Float → Double, Bool → Boolean, Str → String, None → null
 * Composite types use {@link VmValue} sealed subtypes:
 *   SomeVal, NoneVal, OkVal, ErrVal, EnumVal, RecordVal, ListVal, MapVal
 */
public class BytecodeVM {

    private record Frame(int returnAddr, Map<String, Object> locals) {}

    private final Stack<Object>  stack   = new Stack<>();
    private final Deque<Frame>   frames  = new ArrayDeque<>();
    private Map<String, Object>  locals  = new HashMap<>();
    private final Scanner        scanner = new Scanner(System.in);
    private List<Instruction>    currentBytecode = List.of();
    private Map<String, Bytecode.ImplMethodEntry> implMethods = Map.of();

    public void run(Bytecode bytecode) {
        frames.clear(); stack.clear(); locals = new HashMap<>();
        currentBytecode = bytecode.instructions;
        implMethods = bytecode.implMethods;
        List<Instruction> instrs = bytecode.instructions;
        int ip = 0;
        while (ip < instrs.size()) ip = execute(instrs.get(ip), ip, instrs.size());
    }

    private int execute(Instruction instr, int ip, int total) {
        switch (instr) {

            // ── Literals ─────────────────────────────────────────────────────
            case Instruction.PushInt    p -> stack.push(p.value());
            case Instruction.PushFloat  p -> stack.push(p.value());
            case Instruction.PushStr    p -> stack.push(p.value());
            case Instruction.PushBool   p -> stack.push(p.value());
            case Instruction.PushNone   ignored -> stack.push(new NoneVal());
            case Instruction.PushSome   ignored -> stack.push(new SomeVal(stack.pop()));
            case Instruction.PushOk     ignored -> stack.push(new OkVal(stack.pop()));
            case Instruction.PushErr    ignored -> stack.push(new ErrVal(stack.pop()));
            case Instruction.PushList   p -> {
                Object[] elems = new Object[p.size()];
                for (int i = p.size() - 1; i >= 0; i--) elems[i] = stack.pop();
                ArrayList<Object> list = new ArrayList<>(Arrays.asList(elems));
                stack.push(new ListVal(list));
            }
            case Instruction.PushMap    p -> {
                LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
                Object[] kvs = new Object[p.size() * 2];
                for (int i = kvs.length - 1; i >= 0; i--) kvs[i] = stack.pop();
                for (int i = 0; i < kvs.length; i += 2) map.put(kvs[i], kvs[i+1]);
                stack.push(new MapVal(map));
            }
            case Instruction.PushEnum   p -> {
                Object[] payload = new Object[p.arity()];
                for (int i = p.arity() - 1; i >= 0; i--) payload[i] = stack.pop();
                stack.push(new EnumVal(p.typeName(), p.variant(), Arrays.asList(payload)));
            }
            case Instruction.PushRecord p -> {
                Object[] vals = new Object[p.fields().size()];
                for (int i = p.fields().size() - 1; i >= 0; i--) vals[i] = stack.pop();
                LinkedHashMap<String,Object> fields = new LinkedHashMap<>();
                for (int i = 0; i < p.fields().size(); i++) fields.put(p.fields().get(i), vals[i]);
                stack.push(new RecordVal(p.typeName(), fields));
            }
            case Instruction.PushTuple p -> {
                Object[] elems = new Object[p.size()];
                for (int i = p.size() - 1; i >= 0; i--) elems[i] = stack.pop();
                stack.push(new TupleVal(java.util.Arrays.asList(elems)));
            }
            case Instruction.PushLambda p -> stack.push(new LambdaVal(p.address(), p.params()));

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
                else throw new RuntimeException("NEG expects a number, got: " + v);
            }

            // ── Comparison ───────────────────────────────────────────────────
            case Instruction.Eq  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(vmEquals(a, b)); }
            case Instruction.Ne  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(!vmEquals(a, b)); }
            case Instruction.Lt  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) < 0); }
            case Instruction.Le  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) <= 0); }
            case Instruction.Gt  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) > 0); }
            case Instruction.Ge  ignored -> { Object b = stack.pop(), a = stack.pop(); stack.push(compare(a, b) >= 0); }

            // ── Logic ─────────────────────────────────────────────────────────
            case Instruction.And ignored -> { boolean b = asBool(stack.pop()), a = asBool(stack.pop()); stack.push(a && b); }
            case Instruction.Or  ignored -> { boolean b = asBool(stack.pop()), a = asBool(stack.pop()); stack.push(a || b); }
            case Instruction.Not ignored -> stack.push(!asBool(stack.pop()));
            // Short-circuit AND: peek TOS; if false jump (leave false on stack); else pop and eval right
            case Instruction.AndShort p -> {
                if (!asBool(stack.peek())) return p.target();
                stack.pop();
            }
            // Short-circuit OR: peek TOS; if true jump (leave true on stack); else pop and eval right
            case Instruction.OrShort  p -> {
                if (asBool(stack.peek())) return p.target();
                stack.pop();
            }

            // ── String ────────────────────────────────────────────────────────
            case Instruction.Concat ignored -> {
                String b = format(stack.pop()), a = format(stack.pop());
                stack.push(a + b);
            }

            // ── Variables ─────────────────────────────────────────────────────
            case Instruction.Store st -> locals.put(st.name(), stack.pop());
            case Instruction.Load  ld -> stack.push(lookupVar(ld.name()));
            case Instruction.Dup   ignored -> stack.push(stack.peek());

            // ── Field / index access ──────────────────────────────────────────
            case Instruction.GetField gf -> stack.push(getField(stack.pop(), gf.field()));
            case Instruction.SetField sf -> {
                Object val = stack.pop(), recv = stack.peek(); // peek — don't pop the receiver
                if (recv instanceof RecordVal rv) {
                    rv.fields().put(sf.field(), val);
                } else {
                    throw new RuntimeException("SetField: expected RecordVal, got " + recv);
                }
            }
            case Instruction.GetIndex  ignored -> {
                Object idx = stack.pop(), recv = stack.pop();
                stack.push(getIndex(recv, idx));
            }
            case Instruction.SetIndex ignored -> {
                Object val = stack.pop(), idx = stack.pop(), recv = stack.pop();
                setIndex(recv, idx, val);
                // no push — assignment is a statement
            }

            // ── I/O ───────────────────────────────────────────────────────────
            case Instruction.Print    ignored -> System.out.println(format(stack.pop()));
            case Instruction.ReadLine ignored -> stack.push(scanner.nextLine());

            // ── Control flow ─────────────────────────────────────────────────
            case Instruction.Jump        j  -> { return j.target(); }
            case Instruction.JumpIfFalse jf -> { if (!asBool(stack.pop())) return jf.target(); }
            case Instruction.JumpIfTrue  jt -> { if  (asBool(stack.pop())) return jt.target(); }
            case Instruction.JumpIfNone  jn -> {
                // Peek TOS; jump if it represents "no value" (null / NoneVal)
                Object top = stack.peek();
                if (top == null || top instanceof NoneVal) return jn.target();
            }

            // ── Pattern matching ─────────────────────────────────────────────
            // Each Match instruction PEEKS TOS on success (leaving it for the next check)
            // and POPS TOS on failure (cleaning up the dup'd copy before jumping to next arm).
            case Instruction.MatchInt    m -> {
                if (!(stack.peek() instanceof Long l && l == m.value())) { stack.pop(); return m.failJump(); }
            }
            case Instruction.MatchFloat  m -> {
                if (!(stack.peek() instanceof Double d && d == m.value())) { stack.pop(); return m.failJump(); }
            }
            case Instruction.MatchStr    m -> {
                if (!m.value().equals(stack.peek())) { stack.pop(); return m.failJump(); }
            }
            case Instruction.MatchBool   m -> {
                if (!(stack.peek() instanceof Boolean b && b == m.value())) { stack.pop(); return m.failJump(); }
            }
            case Instruction.MatchNone   m -> {
                if (!(stack.peek() == null || stack.peek() instanceof NoneVal)) { stack.pop(); return m.failJump(); }
            }
            case Instruction.MatchTuple m -> {
                Object top = stack.peek();
                if (!(top instanceof TupleVal tv && tv.elements().size() == m.arity())) {
                    stack.pop(); return m.failJump();
                }
            }
            case Instruction.MatchTag    m -> {
                Object top = stack.peek();
                boolean matches = switch (top) {
                    case SomeVal  ignored when m.typeName().equals("Option")  && m.variant().equals("Some") -> true;
                    case NoneVal  ignored when m.typeName().equals("Option")  && m.variant().equals("None") -> true;
                    case OkVal    ignored when m.typeName().equals("Result")  && m.variant().equals("Ok")   -> true;
                    case ErrVal   ignored when m.typeName().equals("Result")  && m.variant().equals("Err")  -> true;
                    case EnumVal  ev -> ev.typeName().equals(m.typeName()) && ev.variant().equals(m.variant());
                    case RecordVal rv when m.typeName().equals("record") -> rv.typeName().equals(m.variant());
                    default -> false;
                };
                if (!matches) { stack.pop(); return m.failJump(); }
            }
            case Instruction.UnwrapInner ignored -> {
                Object top = stack.pop();
                switch (top) {
                    case SomeVal  sv -> stack.push(sv.inner());
                    case OkVal    ov -> stack.push(ov.inner());
                    case ErrVal   ev -> stack.push(ev.inner());
                    case EnumVal  ev -> {
                        // Push payload elements left-to-right (first = deepest, last = TOS)
                        for (Object p : ev.payload()) stack.push(p);
                    }
                    case RecordVal rv -> stack.push(rv);  // expose record for field binding
                    default -> stack.push(top);           // unwrapping a plain value is identity
                }
            }

            // ── Function call / return ────────────────────────────────────────
            case Instruction.Call c -> {
                Object[] args = new Object[c.arity()];
                for (int i = c.arity() - 1; i >= 0; i--) args[i] = stack.pop();
                frames.push(new Frame(ip + 1, locals));
                locals = new HashMap<>();
                for (int i = 0; i < c.params().size() && i < args.length; i++)
                    locals.put(c.params().get(i), args[i]);
                return c.target();
            }
            case Instruction.CallLambda cl -> {
                Object[] args = new Object[cl.arity()];
                for (int i = cl.arity() - 1; i >= 0; i--) args[i] = stack.pop();
                Object lambdaObj = stack.pop();
                if (!(lambdaObj instanceof LambdaVal lv))
                    throw new RuntimeException("CallLambda: not a lambda: " + lambdaObj);
                frames.push(new Frame(ip + 1, locals));
                locals = new HashMap<>();
                for (int i = 0; i < lv.params().size() && i < args.length; i++)
                    locals.put(lv.params().get(i), args[i]);
                return lv.address();
            }
            case Instruction.Return r -> {
                Object retVal = r.hasValue() ? stack.pop() : null;
                if (frames.isEmpty()) {
                    // Top-level return (e.g. async VM entry): push result and halt
                    if (r.hasValue()) stack.push(retVal == null ? new NoneVal() : retVal);
                    return total; // signal halt
                }
                Frame frame = frames.pop();
                locals = frame.locals();
                // Always push the return value when hasValue=true, even if it is null (none).
                // Wrap null in NoneVal so the caller sees an actual value on the stack.
                if (r.hasValue()) stack.push(retVal == null ? new NoneVal() : retVal);
                return frame.returnAddr();
            }
            case Instruction.Pop ignored -> stack.pop();

            // ── ? propagation ─────────────────────────────────────────────────
            case Instruction.Propagate p -> {
                Object top = stack.pop();
                switch (top) {
                    case OkVal   ov -> stack.push(ov.inner());
                    case SomeVal sv -> stack.push(sv.inner());
                    case ErrVal  ignored -> {
                        stack.push(top);
                        if (!frames.isEmpty()) { Frame f = frames.pop(); locals = f.locals(); return f.returnAddr(); }
                        return p.epilogueAddr();
                    }
                    case NoneVal ignored -> {
                        stack.push(new NoneVal());
                        if (!frames.isEmpty()) { Frame f = frames.pop(); locals = f.locals(); return f.returnAddr(); }
                        return p.epilogueAddr();
                    }
                    case null -> {
                        stack.push(new NoneVal());
                        if (!frames.isEmpty()) { Frame f = frames.pop(); locals = f.locals(); return f.returnAddr(); }
                        return p.epilogueAddr();
                    }
                    default -> stack.push(top); // plain value — leave it
                }
            }

            // ── Built-in method calls ─────────────────────────────────────────
            case Instruction.CallMethod cm -> {
                Object[] args = new Object[cm.arity()];
                for (int i = cm.arity() - 1; i >= 0; i--) args[i] = stack.pop();
                Object receiver = stack.pop();
                // Try built-in dispatch; fall back to impl methods
                String implKey = receiverTypeName(receiver) + "::" + cm.method();
                Bytecode.ImplMethodEntry entry = implMethods.get(implKey);
                if (entry != null) {
                    // Call impl method: self is first param, then remaining args
                    frames.push(new Frame(ip + 1, locals));
                    locals = new HashMap<>();
                    locals.put(entry.params().get(0), receiver);
                    for (int i = 0; i < args.length && i + 1 < entry.params().size(); i++)
                        locals.put(entry.params().get(i + 1), args[i]);
                    return entry.address();
                }
                Object result = dispatchMethod(receiver, cm.method(), args);
                if (result != null || cm.method().equals("get")) stack.push(result);
            }

            // ── Errors ────────────────────────────────────────────────────────
            case Instruction.Throw    ignored -> throw new RuntimeException((String) stack.pop());
            case Instruction.Break    ignored -> throw new RuntimeException("BUG: unpatched Break");
            case Instruction.Continue ignored -> throw new RuntimeException("BUG: unpatched Continue");
            case Instruction.Stringify ignored -> stack.push(format(stack.pop()));

            // ── Range ─────────────────────────────────────────────────────────
            case Instruction.MakeRange mr -> {
                long to   = (Long) stack.pop();
                long from = (Long) stack.pop();
                long end  = mr.inclusive() ? to + 1 : to;
                ArrayList<Object> elems = new ArrayList<>();
                for (long i = from; i < end; i++) elems.add(i);
                stack.push(new ListVal(elems));
            }

            // ── Async ─────────────────────────────────────────────────────────
            case Instruction.MakeAsync ma -> {
                // Pop args (last arg is TOS)
                Object[] args = new Object[ma.arity()];
                for (int i = ma.arity() - 1; i >= 0; i--) args[i] = stack.pop();
                // Snapshot shared state needed by the async call
                final List<Instruction> asyncInstrs = currentBytecode;
                final Map<String, Bytecode.ImplMethodEntry> asyncImpl = implMethods;
                final int asyncAddr = ma.address();
                final List<String> asyncParams = ma.params();
                final Object[] asyncArgs = args;
                java.util.concurrent.CompletableFuture<Object> future =
                        new java.util.concurrent.CompletableFuture<>();
                Thread.ofVirtual().start(() -> {
                    try {
                        BytecodeVM asyncVm = new BytecodeVM();
                        Bytecode asyncBytecode = new Bytecode(asyncInstrs, asyncImpl);
                        asyncVm.currentBytecode = asyncInstrs;
                        asyncVm.implMethods = asyncImpl;
                        // Set up locals for the async function
                        for (int i = 0; i < asyncParams.size(); i++)
                            asyncVm.locals.put(asyncParams.get(i), asyncArgs[i]);
                        // Run from the function address
                        int aip = asyncAddr;
                        while (aip < asyncInstrs.size()) {
                            aip = asyncVm.execute(asyncInstrs.get(aip), aip, asyncInstrs.size());
                        }
                        Object result = asyncVm.stack.isEmpty() ? null : asyncVm.stack.peek();
                        future.complete(result);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                });
                stack.push(new VmValue.FutureVal(future));
            }
            case Instruction.AwaitFuture ignored -> {
                Object top = stack.pop();
                if (top instanceof VmValue.FutureVal fv) {
                    stack.push(fv.future().join());
                } else {
                    stack.push(top); // already resolved
                }
            }

            // ── Destructuring ─────────────────────────────────────────────────
            case Instruction.DestructureRecord dr -> {
                Object val = stack.pop();
                if (!(val instanceof RecordVal rv))
                    throw new RuntimeException("Cannot record-destructure " + val);
                for (String name : dr.names()) {
                    Object field = rv.fields().get(name);
                    if (field == null && !rv.fields().containsKey(name))
                        throw new RuntimeException("Record has no field '" + name + "'");
                    locals.put(name, field);
                }
            }
            case Instruction.DestructureTuple dt -> {
                Object val = stack.pop();
                java.util.List<Object> elems = switch (val) {
                    case TupleVal tv -> tv.elements();
                    case ListVal  lv -> lv.elements();
                    default -> throw new RuntimeException("Cannot tuple-destructure " + val);
                };
                java.util.List<String> names = dt.names();
                if (names.size() != elems.size())
                    throw new RuntimeException(
                        "Destructure arity mismatch: expected " + names.size() + " but got " + elems.size());
                for (int i = 0; i < names.size(); i++) locals.put(names.get(i), elems.get(i));
            }

            // ── Refinement constraint check ───────────────────────────────────
            case Instruction.CheckConstraint cc -> {
                Object value = locals.get(cc.bindingName());
                boolean ok = runConstraint(cc.constraintCode(), value);
                if (!ok) throw new RuntimeException(
                    "Refinement constraint for type '" + cc.typeName() + "' violated" +
                    " (binding '" + cc.bindingName() + "' = " + format(value) + ")");
            }

            // ── Meta ──────────────────────────────────────────────────────────
            case Instruction.Halt ignored -> { return total; }
        }
        return ip + 1;
    }

    // ── Variable lookup ───────────────────────────────────────────────────────

    private Object lookupVar(String name) {
        if (locals.containsKey(name)) return locals.get(name);
        for (Frame f : frames) if (f.locals().containsKey(name)) return f.locals().get(name);
        throw new RuntimeException("Undefined variable: " + name);
    }

    // ── Field / index access ──────────────────────────────────────────────────

    private Object getField(Object recv, String field) {
        return switch (recv) {
            case RecordVal rv -> {
                Object v = rv.fields().get(field);
                if (v == null && !rv.fields().containsKey(field))
                    throw new RuntimeException("No field '" + field + "' on " + rv.typeName());
                yield v;
            }
            case EnumVal ev when !ev.payload().isEmpty() && ev.payload().getFirst() instanceof RecordVal rv -> {
                Object v = rv.fields().get(field);
                if (v == null && !rv.fields().containsKey(field))
                    throw new RuntimeException("No field '" + field + "' on " + ev.typeName() + "::" + ev.variant());
                yield v;
            }
            default -> throw new RuntimeException("Cannot access field '" + field + "' on " + recv);
        };
    }

    private Object getIndex(Object recv, Object idx) {
        return switch (recv) {
            case TupleVal tv -> {
                int i = ((Long) idx).intValue();
                if (i < 0 || i >= tv.elements().size())
                    throw new RuntimeException("Tuple index out of bounds: " + i);
                yield tv.elements().get(i);
            }
            case ListVal lv -> {
                int i = ((Long) idx).intValue();
                // Return Some/null like interpreter for for-loop iteration
                yield (i >= 0 && i < lv.elements().size())
                        ? new SomeVal(lv.elements().get(i))
                        : null;
            }
            case MapVal mv -> mv.entries().get(idx);
            default -> throw new RuntimeException("Cannot index " + recv);
        };
    }

    private void setIndex(Object recv, Object idx, Object val) {
        switch (recv) {
            case ListVal lv -> {
                int i = ((Long) idx).intValue();
                while (lv.elements().size() <= i) lv.elements().add(null);
                lv.elements().set(i, val);
            }
            case MapVal mv -> mv.entries().put(idx, val);
            default -> throw new RuntimeException("Cannot set index on " + recv);
        }
    }

    /** Returns the type name for impl method lookup, or null if not applicable. */
    private String receiverTypeName(Object v) {
        return switch (v) {
            case RecordVal rv -> rv.typeName();
            case EnumVal   ev -> ev.typeName();
            case Long      ignored -> "Int";
            case Double    ignored -> "Float";
            case Boolean   ignored -> "Bool";
            case String    ignored -> "Str";
            default -> null;
        };
    }

    /**
     * Execute a pre-compiled constraint expression with {@code self} bound to {@code value}.
     * Returns true if the constraint passes (TOS is boolean true after execution).
     */
    private boolean runConstraint(List<Instruction> code, Object value) {
        // Save state
        Map<String, Object> savedLocals = new HashMap<>(locals);
        Stack<Object> savedStack = new Stack<>();
        savedStack.addAll(stack);
        stack.clear();
        locals.put("self", value);
        try {
            int ip = 0;
            while (ip < code.size()) ip = execute(code.get(ip), ip, code.size());
            return stack.isEmpty() ? false : asBool(stack.pop());
        } finally {
            stack.clear();
            stack.addAll(savedStack);
            locals.clear();
            locals.putAll(savedLocals);
        }
    }

    // ── Built-in method dispatch ───────────────────────────────────────────────

    private Object dispatchMethod(Object receiver, String method, Object[] args) {
        return switch (receiver) {
            case String s -> switch (method) {
                case "len"         -> (long) s.length();
                case "trim"        -> s.trim();
                case "upper"       -> s.toUpperCase();
                case "lower"       -> s.toLowerCase();
                case "contains"    -> s.contains(format(args[0]));
                case "starts_with" -> s.startsWith(format(args[0]));
                case "ends_with"   -> s.endsWith(format(args[0]));
                case "replace"     -> s.replace(format(args[0]), format(args[1]));
                case "split" -> {
                    String[] parts = s.split(java.util.regex.Pattern.quote(format(args[0])));
                    ArrayList<Object> list = new ArrayList<>(Arrays.asList((Object[]) parts));
                    yield new ListVal(list);
                }
                default -> throw new RuntimeException("Str has no method '" + method + "'");
            };
            case ListVal lv -> switch (method) {
                case "len"      -> (long) lv.elements().size();
                case "push"     -> { lv.elements().add(args[0]); yield null; }
                case "pop"      -> lv.elements().isEmpty() ? null : lv.elements().removeLast();
                case "get"      -> {
                    int i = ((Long) args[0]).intValue();
                    yield (i >= 0 && i < lv.elements().size())
                            ? new SomeVal(lv.elements().get(i))
                            : null;  // null = None for JumpIfNone
                }
                case "contains" -> lv.elements().contains(args[0]);
                case "clear"    -> { lv.elements().clear(); yield null; }
                case "remove"   -> {
                    int i = ((Long) args[0]).intValue();
                    yield lv.elements().remove(i);
                }
                case "map" -> {
                    LambdaVal fn = (LambdaVal) args[0];
                    ArrayList<Object> result = new ArrayList<>();
                    for (Object elem : lv.elements()) result.add(callLambdaValue(fn, elem));
                    yield new ListVal(result);
                }
                case "filter" -> {
                    LambdaVal fn = (LambdaVal) args[0];
                    ArrayList<Object> result = new ArrayList<>();
                    for (Object elem : lv.elements())
                        if (Boolean.TRUE.equals(callLambdaValue(fn, elem))) result.add(elem);
                    yield new ListVal(result);
                }
                default -> throw new RuntimeException("List has no method '" + method + "'");
            };
            case MapVal mv -> switch (method) {
                case "get"      -> mv.entries().get(args[0]);
                case "set"      -> { mv.entries().put(args[0], args[1]); yield null; }
                case "remove"   -> { mv.entries().remove(args[0]); yield null; }
                case "contains" -> mv.entries().containsKey(args[0]);
                case "len"      -> (long) mv.entries().size();
                case "keys"     -> new ListVal(new ArrayList<>(mv.entries().keySet()));
                case "values"   -> new ListVal(new ArrayList<>(mv.entries().values()));
                default -> throw new RuntimeException("Map has no method '" + method + "'");
            };
            case TupleVal tv -> switch (method) {
                case "len"    -> (long) tv.elements().size();
                case "get"    -> {
                    int i = ((Long) args[0]).intValue();
                    yield (i >= 0 && i < tv.elements().size())
                            ? tv.elements().get(i)
                            : null;
                }
                case "first"  -> tv.elements().get(0);
                case "second" -> tv.elements().get(1);
                default -> throw new RuntimeException("Tuple has no method '" + method + "'");
            };
            default -> throw new RuntimeException(
                    "No method '" + method + "' on " + receiver.getClass().getSimpleName());
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Object numericOp(Object a, Object b, char op) {
        boolean fp = (a instanceof Double) || (b instanceof Double);
        if (fp) {
            double da = ((Number) a).doubleValue(), db = ((Number) b).doubleValue();
            return switch (op) {
                case '+' -> da + db; case '-' -> da - db; case '*' -> da * db;
                case '/' -> da / db; case '%' -> da % db;
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        }
        long la = ((Number) a).longValue(), lb = ((Number) b).longValue();
        return switch (op) {
            case '+' -> la + lb; case '-' -> la - lb; case '*' -> la * lb;
            case '/' -> la / lb; case '%' -> la % lb;
            default -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    private boolean vmEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double))
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return a.equals(b);
    }

    private int compare(Object a, Object b) {
        if (a instanceof Long la   && b instanceof Long lb)   return Long.compare(la, lb);
        if (a instanceof Number na && b instanceof Number nb) return Double.compare(na.doubleValue(), nb.doubleValue());
        if (a instanceof String sa && b instanceof String sb) return sa.compareTo(sb);
        throw new RuntimeException("Cannot compare " + a + " and " + b);
    }

    private boolean asBool(Object v) {
        if (v instanceof Boolean b) return b;
        throw new RuntimeException("Expected Bool, got: " + v);
    }

    private String format(Object v) {
        if (v == null) return "none";
        return switch (v) {
            case NoneVal  ignored -> "none";
            case SomeVal  sv  -> "some(" + format(sv.inner()) + ")";
            case OkVal    ov  -> "ok(" + format(ov.inner()) + ")";
            case ErrVal   ev  -> "err(" + format(ev.inner()) + ")";
            case EnumVal  ev  -> ev.payload().isEmpty()
                    ? ev.typeName() + "::" + ev.variant()
                    : ev.typeName() + "::" + ev.variant() + "(" +
                      ev.payload().stream().map(this::format).reduce((x,y)->x+", "+y).orElse("") + ")";
            case RecordVal rv -> rv.typeName() + " { " +
                    rv.fields().entrySet().stream()
                        .map(e -> e.getKey() + ": " + format(e.getValue()))
                        .reduce((x,y)->x+", "+y).orElse("") + " }";
            case ListVal  lv  -> "[" + lv.elements().stream().map(this::format).reduce((x,y)->x+", "+y).orElse("") + "]";
            case MapVal   mv  -> "{" + mv.entries().entrySet().stream()
                        .map(e -> format(e.getKey()) + ": " + format(e.getValue()))
                        .reduce((x,y)->x+", "+y).orElse("") + "}";
            case TupleVal tv  -> "(" + tv.elements().stream().map(this::format).reduce((x,y)->x+", "+y).orElse("") + ")";
            case LambdaVal lv -> "<fn@" + lv.address() + ">";
            default -> v.toString();
        };
    }

    /**
     * Call a {@link LambdaVal} with a single argument, running the VM inline
     * on the same instruction list ({@link #currentBytecode}).
     * Used by {@code list.map(fn)} and {@code list.filter(fn)}.
     */
    private Object callLambdaValue(LambdaVal fn, Object arg) {
        // Save current locals, push a return frame, then execute from the lambda address
        frames.push(new Frame(-1, locals));
        locals = new HashMap<>();
        if (!fn.params().isEmpty()) locals.put(fn.params().getFirst(), arg);
        int ip = fn.address();
        while (ip >= 0 && ip < currentBytecode.size()) {
            Instruction instr = currentBytecode.get(ip);
            if (instr instanceof Instruction.Return r) {
                Object retVal = r.hasValue() ? stack.pop() : null;
                Frame frame = frames.pop();
                locals = frame.locals();
                return retVal;
            }
            ip = execute(instr, ip, currentBytecode.size());
        }
        Frame frame = frames.pop();
        locals = frame.locals();
        return null;
    }
}
