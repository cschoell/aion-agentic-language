# Aion — Missing features for larger systems

> Analysis date: 2026-03-06  
> Last updated: 2026-03-09 — items 2 (closures/lambdas), 17 (@on_fail), 16 (refinement types), 6 (deep field assignment), 9 (tuple types), 18 (named return variables), and 19 (semicolon separator) implemented.
> Updated 2026-03-09 — items 14 (numeric literal forms: hex/binary/octal/digit separators) and 1 (module import system: recursive file loading, transitive imports, cycle detection) implemented.
> Updated 2026-03-09 — demo scripts enhanced: `math_utils.aion` (shared utility module), `import-demo.aion` (multi-file import + all numeric literal forms), `bytecode-demo.aion` (numeric literals section); 160 passing tests across 6 suites.
> Updated 2026-03-09 — items 13 (selective imports) and 15 (range expressions: `from..to` / `from..=to`) implemented; 174 passing tests across 6 suites.
> Items 4, 7, 8, 11, 12 were implemented on 2026-03-06; items 15–18 added then.  
> Code quality pass 2026-03-06 — `BytecodeVM` warnings resolved.  
> Based on: grammar (`AionParser.g4`, `AionLexer.g4`), interpreter, bytecode VM, and `sample.aion`.

Each gap is rated by impact:
**🔴 critical** — blocks real programs &nbsp;|&nbsp;
**🟠 high** — major pain &nbsp;|&nbsp;
**🟡 medium** — workaroundable but ugly &nbsp;|&nbsp;
**🟢 nice-to-have**

---

## 1. Module system  🔴 ✅ done (basic)

**What's there:** `import foo.bar` now resolves `foo/bar.aion` relative to the importing
file, parses it, and merges all its declarations into the current module before compilation.
Transitive imports and cycle detection are supported.

**What's still missing:**
- A module namespace (`foo.bar.MyType`, `foo.bar.add`)
- Visibility control (`pub` / private by default)
- Standard library modules (`std.io`, `std.str`, `std.collections`)

**Impact:** Multi-file programs now work. Namespace isolation and stdlib remain future work.

---

## 2. Closures / first-class functions  🔴 ✅ done

**What's there:** Lambda expressions with full closure semantics:
```aion
let double = fn(x: Int) -> Int { return x * 2 }
numbers.map(fn(x: Int) -> Int { return x * 2 })
numbers.filter(fn(x: Int) -> Bool { return x % 2 == 0 })
```
- Lexer: `fn` keyword already existed; no new token needed.
- Parser: new `LambdaExpr` alternative in `primaryExpr` — `fn(params) -> Type { block }`.
- AST: `Expr.Lambda(params, returnType, body, pos)` record.
- Interpreter: `Lambda` eval creates a synthetic `FnDecl` and captures the current `Environment` as its closure; `list.map(fn)` and `list.filter(fn)` dispatch to `callFn`.
- Bytecode compiler: lambda body is emitted inline as an anonymous function (name `__lambda_N__`); `PushLambda(address, params)` pushes the entry point as a value.
- Bytecode VM: `PushLambda` → `LambdaVal`; `CallLambda(arity)` pops lambda + args and calls inline via `callLambdaValue`; `list.map` and `list.filter` dispatch to `callLambdaValue`.

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

## 14. Numeric literal forms  🟢 ✅ done

**What's there:** Digit separators (`1_000_000`), hex (`0xFF`), binary (`0b1010`), and
octal (`0o17`) integer literals are now supported in the lexer and parsed correctly by
`AstBuilder`. Both the interpreter and bytecode VM handle them transparently.

**What's still missing:**
- Single-precision float: `1.5f`
- Sized integer types: `i32`, `u8`, etc. (relevant for protocol work)

---

## 15. Semantic / newtype aliases  🟠 ✅ done

**What's there:** `type Email = Str` is parsed and stored in the AST. The interpreter
registers the alias name so `let x: Email = "..."` is valid. Refinement constraints
(item 16) build directly on top of this, so the two items were implemented together.

---

## 16. Refinement types  🟠 ✅ done

**What's there:** Constrained type alias declarations with runtime enforcement:
```aion
type AgentID     = Str   where { self.starts_with("did:aion:") }
type Score       = Int   where { self >= 0 and self <= 100 }
type PositiveFloat = Float where { self > 0.0 }
```
When a value is assigned to a refinement type (`let`, `mut`, or function parameter), the interpreter evaluates the `where` predicate with `self` bound to the value and throws `AionRuntimeException` if it fails.

- Lexer: `WHERE : 'where'` keyword.
- Parser: `AliasTypeDecl` extended with optional `WHERE LBRACE expr RBRACE` clause.
- AST: `TypeDeclBody.Alias(TypeRef ref, Expr constraint)` — `constraint` is null for plain aliases.
- Interpreter: `loadModule` registers constraints in a `Map<String, Expr> refinements`; `checkRefinement` is called on every `let`/`mut` binding and on function parameter binding.
- Bytecode compiler: refinement constraints are enforced at the interpreter level; the bytecode path does not yet inject checks (planned as a future `CheckConstraint` instruction).

---

## 17. Declarative tool error hints (`@on_fail`)  🟠 ✅ done

**What's there:** Structured failure wrapping for `@tool` functions:
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
When any failure occurs inside an `@on_fail`-annotated tool (pre-condition breach, `assert`, timeout, or any runtime exception) the runtime wraps it as:
```
err(ToolError { hint: "...", cause: "..." })
```
instead of throwing. The agent loop can inspect `hint` to self-correct without unwrapping a Java stack trace.

- Lexer: `ANN_ON_FAIL : '@on_fail'` token.
- Parser: `annotation` rule gains `| ANN_ON_FAIL LPAREN STR_LIT RPAREN`.
- AST: `Annotation.OnFail(String hint)` record.
- Interpreter: `callFn` extracts the hint; wraps all `RuntimeException` in `ErrVal(RecordVal("ToolError", {hint, cause}))`.
- CLI `describe`: emits `"on_fail_hint"` field in JSON tool descriptors.

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

## 19. Semicolon as inline statement separator  🟢 ✅ done

**What's there:** Newline terminates a statement. This means multiple statements must
occupy separate lines, making tight `if`/`while` bodies verbose.

**What was added:**

```aion
// Multiple statements on one line:
let x = 1; let y = 2

// Inline if body (common in while loops and guards):
if done { count = count + 1; break }

// Trailing semicolons are silently ignored (style tolerance):
print("hello");

// While body on one line:
while k <= 5 { acc = acc + k; k = k + 1 }
```

**Implementation:**
- **Lexer** (`AionLexer.g4`): added `SEMI : ';' ;` token.
- **Parser** (`AionParser.g4`): changed `block` rule from `LBRACE stmt* RBRACE`
  to `LBRACE (stmt SEMI*)* RBRACE` — each statement may be followed by zero or
  more semicolons. No AST or interpreter/VM changes were needed.
- **Tests**: 5 interpreter tests in `SmallFeaturesTest`, 4 bytecode tests in
  `BytecodeCompilerTest`, E2E coverage via `bytecode-demo.aion`.

**Impact:** Eliminates verbosity for compact guards and loop bodies.
Allows porting code from semicolon-terminated languages without reformatting.

---

## Summary

| # | Feature | Priority | Effort | Status |
|---|---------|:--------:|:------:|:------:|
| 1 | Module system + file loading | 🔴 | Large | ✅ basic done |
| 2 | Closures / lambdas | 🔴 | Medium | ✅ done |
| 3 | Traits / interfaces | 🔴 | Large | — |
| 4 | `?` error propagation | 🟠 | Small | ✅ done |
| 5 | Generic functions (real) | 🟠 | Large | — |
| 6 | Deep mutable field assignment | 🟠 | Medium | ✅ done |
| 15 | Semantic / newtype aliases | 🟠 | Small-Medium | ✅ done |
| 16 | Refinement types (`where` constraints) | 🟠 | Large | ✅ done |
| 17 | `@on_fail` declarative tool error hints | 🟠 | Small | ✅ done |
| 7 | String interpolation | 🟡 | Small | ✅ done |
| 8 | `break` / `continue` | 🟡 | Small | ✅ done |
| 9 | Tuple types | 🟡 | Medium | ✅ done |
| 10 | `@async` / concurrency | 🟡 | Large | — |
| 11 | Pattern guards | 🟡 | Small | ✅ done |
| 18 | Named return variables | 🟢 | Small-Medium | ✅ done |
| 12 | `const` declarations | 🟢 | Small | ✅ done |
| 13 | Selective imports | 🟢 | Small | ✅ done |
| 14 | Numeric literal forms | 🟢 | Small | ✅ done |
| 19 | Semicolon as inline statement separator | 🟢 | Tiny | ✅ done |

## Recommended implementation order

Sorted by effort within each priority tier, prerequisites noted:

1. ~~`?` error propagation~~ ✅ **done**
2. ~~`break` / `continue`~~ ✅ **done**
3. ~~String interpolation~~ ✅ **done**
4. ~~`const` declarations~~ ✅ **done**
5. ~~Pattern guards~~ ✅ **done**
6. ~~`@on_fail` tool hints~~ ✅ **done** *(makes agent failures actionable)*
7. ~~**Semantic / newtype aliases**~~ ✅ **done** *(prerequisite for refinement types)*
8. ~~**Refinement types** (`where` constraints)~~ ✅ **done** *(interpreter-level; bytecode injection pending)*
9. ~~**Closures / lambdas**~~ ✅ **done** *(unlocks `map`/`filter`/`reduce`; closes 🔴 gap)*
10. ~~**Named return variables**~~ ✅ **done** *(small-medium — improves `@ensures` readability)*
11. ~~**Tuple types**~~ ✅ **done** *(medium — prerequisite for named returns and destructuring)*
12. ~~**Deep field assignment**~~ ✅ **done** *(medium — unblocks record mutation patterns)*
13. ~~**Semicolon statement separator**~~ ✅ **done** *(tiny — optional `;` after any stmt in a block)*
14. ~~**Selective imports**~~ ✅ **done** *(small — `import foo { bar, baz }` syntax)*
15. **Traits** *(large — prerequisite for generic contracts)*
16. **Generic functions** *(large — builds on traits)*
17. **Refinement type bytecode injection** *(medium — emit `CheckConstraint` in the compiler)*
18. **`@async` / concurrency** *(large — needs scheduler, best done last)*
