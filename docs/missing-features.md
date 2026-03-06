# Aion — Missing features for larger systems

> Analysis date: 2026-03-06  
> Last updated: 2026-03-06 — items 4, 7, 8, 11, 12 implemented; items 15–18 added.  
> Based on: grammar (`AionParser.g4`, `AionLexer.g4`), interpreter, bytecode VM, and `sample.aion`.

Each gap is rated by impact:
**🔴 critical** — blocks real programs &nbsp;|&nbsp;
**🟠 high** — major pain &nbsp;|&nbsp;
**🟡 medium** — workaroundable but ugly &nbsp;|&nbsp;
**🟢 nice-to-have**

---

## 1. Module system  🔴

**What's there:** `import foo.bar` parses, but the compiler and interpreter ignore it entirely —
there is no file loading, no namespace, no re-export.

**What's missing:**
- Loading imported files from disk at parse time
- A module namespace (`foo.bar.MyType`, `foo.bar.add`)
- Visibility control (`pub` / private by default)
- Standard library modules (`std.io`, `std.str`, `std.collections`)

**Impact:** Every real program beyond a single file is blocked.
Without modules you cannot split logic across files, share types, or reuse utility functions.

---

## 2. Closures / first-class functions  🔴

**What's there:** `FnType` exists in the type grammar (`(Int) -> Int`) and `FnVal` exists in
the interpreter, but:
- There is no lambda / anonymous function syntax
- There is no way to pass a function literal as an argument
- `List.map` and `List.filter` exist in the interpreter but can never be called from Aion
  source because you can't write the callback

**What's missing:**
```aion
let double = fn(x: Int) -> Int { x * 2 }
numbers.map(fn(x) { x * 2 })
```

**Impact:** Higher-order patterns (map, filter, reduce, callbacks, event handlers, strategies)
are impossible. This also means `@async` can never be useful.

---

## 3. Trait / interface system  🔴

**What's there:** Records and enums with no shared behaviour contracts.

**What's missing:** A way to say "any type that implements `Display`":

```aion
trait Display {
    fn to_str(self: Self) -> Str
}

impl Display for User {
    fn to_str(self: User) -> Str { "User(" + self.name + ")" }
}
```

**Impact:** Without traits you cannot write generic algorithms, cannot define shared
contracts between modules, and cannot implement the standard patterns expected in any
medium-sized system (serialisation, validation, equality, hashing).

---

## 4. `?` error propagation  🟠 ✅ done

**What's there:** `?` unwraps `ok(v)` / `some(v)` or early-returns `err(e)` / `none` from
the enclosing function. Caught at the function call boundary.

---

## 5. Generic / parametric functions  🟠

**What's there:** `typeParams` on `fnDecl` parses (`fn id[T](x: T) -> T`), but the
interpreter ignores type parameters — there is no substitution or type-checking.

**What's missing:**
- Type parameter substitution at call sites
- Constraints (`[T: Display]`)
- Generic types used in function bodies (`List[T]`, `Option[T]`)

**Impact:** You cannot write `fn max[T](a: T, b: T) -> T` or any truly reusable utility.
Every "generic" function must be copy-pasted for each concrete type.

---

## 6. Deep mutable field assignment  🟠

**What's there:** `assignTarget` supports `IDENT DOT IDENT` paths in the grammar and
interpreter, but only for single-level paths on `mut`-bound variables.

**What's missing:**
- Deep path assignment: `user.address.city = "Berlin"`
- Index + field: `users[0].name = "Bob"`
- `mut` record semantics with clear copy-on-write or in-place mutation rules

---

## 7. String interpolation  🟡 ✅ done

`"Hello, ${name}! Score: ${score}"` — implemented in lexer, parser, AST, interpreter,
and bytecode VM.

---

## 8. `break` / `continue` in loops  🟡 ✅ done

`break` and `continue` work in both `while` and `for` loops in the interpreter and
bytecode VM.

---

## 9. Tuple types  🟡

**What's there:** `FnType` uses a tuple-like list of types in the grammar, but there are no
tuple values — `(Int, Str)` cannot be constructed or destructured.

**What's missing:**
```aion
fn min_max(xs: List[Int]) -> (Int, Int) { ... }
let (lo, hi) = min_max(numbers)
```

**Impact:** Functions that return multiple values must define a named record type even for
trivial pairs, adding boilerplate and cluttering the namespace.

---

## 10. `@async` / concurrency  🟡

**What's there:** `@async` annotation parses; nothing more.

**What's missing:**
- `async fn`, `await expr` syntax
- A runtime scheduler (even a simple one built on Java 21 virtual threads)
- Channel / actor primitives or at minimum `Future[T]`

**Impact:** Any I/O-bound work (network calls, parallel tool invocations in an agent
pipeline) blocks the whole thread. For an AI-agent language this is particularly painful.

---

## 11. Pattern guards  🟡 ✅ done

`match n { x if x >= 90 => "A", ... }` — optional `if` guard on any match arm,
implemented in parser, AST, and interpreter.

---

## 12. Constant declarations  🟢 ✅ done

`const MAX: Int = 42` at module level — implemented in lexer, parser, AST, interpreter,
and bytecode VM.

---

## 13. Selective / aliased imports  🟢

**What's there:** `import foo.bar` (once implemented).

**What's missing:**
```aion
import std.collections.{ HashMap, HashSet }
import std.io.{ read_file as read }
```

---

## 14. Numeric literal forms  🟢

**What's there:** All integers are `Long` (64-bit signed); `FLOAT_LIT` is `Double`.

**What's missing:**
- Digit separators: `1_000_000`
- Hex / binary / octal: `0xFF`, `0b1010`, `0o777`
- Single-precision float: `1.5f`
- Sized integer types: `i32`, `u8`, etc. (relevant for protocol work)

---

## 15. Semantic / newtype aliases  🟠

**What's there:** `type` declarations create record types; there is no way to make a
type-safe wrapper around a primitive.

**What's missing:** Lightweight newtypes that carry intent and prevent accidental mixing:

```aion
type Email  = Str
type UserId = Int
type CleanStr = Str
```

At this level the compiler treats `Email` as a distinct type from `Str` — you cannot pass
a raw `Str` where an `Email` is expected without an explicit conversion.  This alone
eliminates whole classes of AI-agent bugs where the LLM passes the wrong string to the
wrong argument.

**Why it makes sense:** Aion is positioned as an AI-agent language.  Agents see function
signatures as prompts.  `fn send(to: Email, subject: Str)` is far more informative — and
harder to misuse — than `fn send(to: Str, subject: Str)`.

**Effort:** Small-Medium — newtypes without constraints are just alias tracking in the
type-checker; the interpreter can treat them transparently at runtime.

---

## 16. Refinement types  🟠

**What's there:** `@requires` checks pre-conditions at the function boundary; there is no
way to encode a constraint *into a type* so it is checked wherever a value is created.

**What's missing:** Constrained type declarations:

```aion
type ValidatedInput = Str where { self.trim().len() > 0 }
type Score          = Int  where { self >= 0 and self <= 100 }
type PositiveFloat  = Float where { self > 0.0 }
```

When a value is assigned to a refinement type (from a `let`, a function argument, or a
return), the compiler inserts the `where` predicate as a runtime assertion.  The `sanitise`
tool then becomes:

```aion
@tool @io @timeout(500)
fn sanitise(raw: Untrusted[Str]) -> ValidatedInput {
    describe "Cleans agent input. Fails if input is blank after trimming."
    return raw.trim()   // compiler checks ValidatedInput constraint here
}
```

No manual `assert` needed — the constraint is part of the type contract.

**Why it makes sense:** Refinement types are the natural completion of `@requires`/`@ensures`.
They let constraints travel with values across module boundaries, something annotations on
function boundaries cannot do.  For AI agents this is especially powerful because an agent
can be told "this argument must be a `Score`" and the runtime will enforce it automatically.

**Relationship to #15:** Refinement types build on semantic aliases — a refinement type IS
a newtype alias plus a `where` clause.  Implement #15 first.

**Effort:** Large — requires constraint propagation through the type system, and a runtime
check injection pass.

---

## 17. Declarative tool error hints (`@on_fail`)  🟠

**What's there:** When a `@tool` function fails (`@requires` not met, `assert` fires,
timeout exceeded) the agent receives a raw Java exception message with no guidance on
how to recover.

**What's missing:** A structured failure annotation that surfaces a human-readable hint
back to the calling agent:

```aion
@tool
@io
@untrusted
@timeout(500)
@on_fail("Please provide a non-empty, non-whitespace string.")
fn sanitise(raw_input: Str) -> Str {
    describe "Cleans agent input."
    let trimmed = raw_input.trim()
    assert trimmed != "", "Input must not be blank after trimming"
    return trimmed
}
```

When any failure occurs inside an `@on_fail`-annotated tool the runtime wraps the error
in a structured `ToolError { hint: Str, cause: Str }` value that the agent loop can
read directly rather than unwrapping a Java stack trace.

**Why it makes sense:** Current `assert` failures produce opaque crashes.  An AI agent
cannot self-correct from a stack trace.  `@on_fail` is the minimal change needed to make
tool failures *actionable* — it is the difference between a crashed agent and a retrying
agent.

**Effort:** Small — one new annotation token, one new `Annotation` record in the AST,
and a one-line change in the interpreter's tool-call error handler to wrap the message.

---

## 18. Named return variables  🟢

**What's there:** `@ensures` uses the magic name `result` to refer to the return value,
with no indication of its type or meaning in the signature itself.

**What's missing:** Named return in the function signature so the post-condition is
self-documenting:

```aion
@tool
@pure
@requires(score >= 0 and score <= total)
@ensures(next_score >= score and next_score <= total)
fn award_point(score: Int, total: Int, correct: Bool) -> (next_score: Int) {
    describe "Increments score if correct. Boundary-checked."
    return match correct {
        true  => score + 1,
        false => score,
    }
}
```

The name `next_score` is bound to the return value inside `@ensures` expressions,
replacing the magic `result` binding.  This also makes the signature more informative
for AI agents reading the `describe` output.

**Relationship to #9 (Tuples):** Named returns generalise tuples — `-> (x: Int, y: Int)`
is a named tuple.  A named single return `-> (n: Int)` is a degenerate case.  Implement
#9 first, then named returns fall out naturally.

**Effort:** Small-Medium — grammar change (optional name in `returnType`), AST change
(`Param`-like record instead of bare `TypeRef`), and a one-line change in the `@ensures`
evaluator to bind the name instead of `result`.

---

## Summary

| # | Feature | Priority | Effort | Status |
|---|---------|:--------:|:------:|:------:|
| 1 | Module system + file loading | 🔴 | Large | — |
| 2 | Closures / lambdas | 🔴 | Medium | — |
| 3 | Traits / interfaces | 🔴 | Large | — |
| 4 | `?` error propagation | 🟠 | Small | ✅ done |
| 5 | Generic functions (real) | 🟠 | Large | — |
| 6 | Deep mutable field assignment | 🟠 | Medium | — |
| 15 | Semantic / newtype aliases | 🟠 | Small-Medium | — |
| 16 | Refinement types (`where` constraints) | 🟠 | Large | — |
| 17 | `@on_fail` declarative tool error hints | 🟠 | Small | — |
| 7 | String interpolation | 🟡 | Small | ✅ done |
| 8 | `break` / `continue` | 🟡 | Small | ✅ done |
| 9 | Tuple types | 🟡 | Medium | — |
| 10 | `@async` / concurrency | 🟡 | Large | — |
| 11 | Pattern guards | 🟡 | Small | ✅ done |
| 18 | Named return variables | 🟢 | Small-Medium | — |
| 12 | `const` declarations | 🟢 | Small | ✅ done |
| 13 | Selective imports | 🟢 | Small | — |
| 14 | Numeric literal forms | 🟢 | Small | — |

## Recommended implementation order

Sorted by effort within each priority tier, prerequisites noted:

1. ~~`?` error propagation~~ ✅ **done**
2. ~~`break` / `continue`~~ ✅ **done**
3. ~~String interpolation~~ ✅ **done**
4. ~~`const` declarations~~ ✅ **done**
5. ~~Pattern guards~~ ✅ **done**
6. **`@on_fail` tool hints** *(small — makes agent failures actionable immediately)*
7. **Semantic / newtype aliases** *(small-medium — prerequisite for refinement types)*
8. **Named return variables** *(small-medium — builds on tuple syntax, improves `@ensures`)*
9. **Closures / lambdas** *(medium — unlocks `map`/`filter`/`reduce`)*
10. **Tuple types** *(medium — prerequisite for named returns and destructuring)*
11. **Deep field assignment** *(medium — unblocks record mutation patterns)*
12. **Module file loading** *(large — prerequisite for all multi-file programs)*
13. **Traits** *(large — prerequisite for generic contracts)*
14. **Generic functions** *(large — builds on traits)*
15. **Refinement types** *(large — builds on semantic aliases and traits)*
16. **`@async` / concurrency** *(large — needs scheduler, best done last)*
