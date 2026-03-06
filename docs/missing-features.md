# Aion — Missing features for larger systems

> Analysis date: 2026-03-06  
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

## 4. `?` error propagation  🟠

**What's there:** The `?` operator (`PropagateOp`) parses and appears in the AST, but the
interpreter throws `UnsupportedOperationException` on it.

**What's missing:** `?` should unwrap `ok(v)` → `v`, or early-return `err(e)` from the
enclosing function:

```aion
let v = safe_divide(10.0, b)?   // returns err(...) immediately if b == 0
```

**Impact:** Without `?`, every fallible call requires a full `match` block.
Error handling becomes 5× more verbose and programmers abandon `Result` for silent failures.

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

## 7. String interpolation  🟡

**What's there:** String concatenation via `+` only.

**What's missing:**
```aion
let msg = "Hello, ${name}! You scored ${score} / ${total}."
```

**Impact:** Producing formatted output requires chained `+ str(x) +` concatenation that
is noisy, error-prone, and unreadable — especially in diagnostic messages and log strings.

---

## 8. `break` / `continue` in loops  🟡

**What's there:** `while` and `for` loop to completion; the only exit is the condition.

**What's missing:**
```aion
while true {
    let line = input()
    if line == "quit" { break }
    process(line)
}
```

**Impact:** Any loop that needs early exit must use a `mut` flag variable and extra
conditionals. Common patterns like "read until sentinel" or "find first match" become verbose.

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

## 11. Pattern guards  🟡

**What's there:** `match` arms with patterns, but no condition on a pattern.

**What's missing:**
```aion
match score {
    n if n >= 90 => "A",
    n if n >= 80 => "B",
    _            => "C",
}
```

**Impact:** Ranges and conditional matches require nested `if` inside each arm, making
match arms verbose and defeating the purpose of exhaustive matching.

---

## 12. Constant declarations  🟢

**What's there:** Only `let` (inside functions) and `mut`.

**What's missing:**
```aion
const MAX_RETRIES: Int = 3
const API_BASE: Str = "https://api.example.com"
```

**Impact:** Magic literals are scattered through code. There is no way to define a
module-level named constant without a zero-argument function.

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

## Summary

| # | Feature | Priority | Effort |
|---|---------|:--------:|:------:|
| 1 | Module system + file loading | 🔴 | Large |
| 2 | Closures / lambdas | 🔴 | Medium |
| 3 | Traits / interfaces | 🔴 | Large |
| 4 | `?` error propagation | 🟠 | Small |
| 5 | Generic functions (real) | 🟠 | Large |
| 6 | Deep mutable field assignment | 🟠 | Medium |
| 7 | String interpolation | 🟡 | Small |
| 8 | `break` / `continue` | 🟡 | Small |
| 9 | Tuple types | 🟡 | Medium |
| 10 | `@async` / concurrency | 🟡 | Large |
| 11 | Pattern guards | 🟡 | Small |
| 12 | `const` declarations | 🟢 | Small |
| 13 | Selective imports | 🟢 | Small |
| 14 | Numeric literal forms | 🟢 | Small |

## Recommended implementation order

Getting the most leverage with the least effort:

1. `?` error propagation *(small effort, unblocks all `Result`-heavy code)*
2. `break` / `continue` *(small effort, unblocks common loop patterns)*
3. String interpolation *(small effort, big readability win)*
4. `const` declarations *(small effort, eliminates magic literals)*
5. Pattern guards *(small effort, makes `match` much more expressive)*
6. Closures / lambdas *(medium effort, unlocks `map`/`filter`/`reduce`)*
7. Module file loading *(large effort, prerequisite for all multi-file programs)*
8. Traits *(large effort, prerequisite for generic contracts)*
9. Generic functions *(large effort, builds on traits)*
10. `@async` / concurrency *(large effort, needs scheduler)*

