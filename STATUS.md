# Status

**Version:** 0.1.0-dev  
**State:** Proof-of-concept · Fully functional interpreter · Pre-type-checker

---

## ✅ Implemented

### Language
- [x] Full lexer + parser grammar (ANTLR4)
- [x] Sealed/record AST hierarchy — exhaustive Java pattern matching throughout
- [x] All primitive types: `Int`, `Float`, `Bool`, `Str`, `Unit`
- [x] Generic built-in types: `Option[T]`, `Result[T,E]`, `List[T]`, `Map[K,V]`
- [x] Record type declarations (`type Foo = { … }`) and type aliases
- [x] Enum (sum type) declarations with tuple and record variants
- [x] Function declarations with effect annotations (`@pure`, `@io`, `@async`, `@mut`, `@throws`, `@test`, `@deprecated`)
- [x] Immutable bindings (`let`) and mutable bindings (`mut`)
- [x] Named and positional arguments at call sites
- [x] `if` / `else if` / `else`
- [x] `while` and `for … in` loops
- [x] Exhaustive `match` with wildcards, literals, bind, `some`/`ok`/`err`, enum tuple/record, nested patterns
- [x] Pipeline operator `|>`
- [x] `?` propagation operator for `Result`/`Option`
- [x] Block expressions (last expression is the value)
- [x] Record literals, list literals, map literals
- [x] String escape sequences

### Interpreter
- [x] Tree-walking interpreter (all constructs)
- [x] Lexical scope chain (`Environment`)
- [x] Built-in functions: `print`, `str`, `int`, `float`, `len`, `assert`, `is_some`, `is_none`, `is_ok`, `is_err`, `unwrap`, `unwrap_or`
- [x] Method calls on `List`, `Map`, `Str`
- [x] First-class functions (`FnVal` with closure capture)
- [x] `ReturnSignal` / `PropagateSignal` for control flow

### Tooling
- [x] `aion run` — execute a source file
- [x] `aion check` — parse/validate without running
- [x] `aion repl` — interactive REPL with multi-line brace-depth tracking
- [x] Gradle build with ANTLR4 code generation (`generateAntlr` task)
- [x] 18 passing unit tests (parser + interpreter)
- [x] Sample program (`sample.aion`)

---

## 🔲 Planned — Core language

- [ ] **Type checker** — infer and verify types for all expressions and statements; catch mismatches before interpretation
- [ ] **Generics at user-defined level** — type-check generic type/enum/fn declarations, not just built-ins
- [ ] **Closures / lambda literals** — anonymous function expressions: `fn(x: Int) -> Int { x * 2 }`
- [ ] **Destructuring `let`** — `let { name, age } = user` / `let (a, b) = pair`
- [ ] **String interpolation** — `"Hello, {name}!"` syntax
- [ ] **Tuple type** — lightweight anonymous product type `(Int, Str, Bool)` without field names
- [ ] **Range expressions** — `0..10`, `0..=10` for use in `for` loops and slices
- [ ] **`const` declarations** — module-level compile-time constants
- [ ] **Import resolution** — multi-file module system with actual file loading
- [ ] **Exhaustiveness checking** — static verification that `match` covers all enum variants

## 🔲 Planned — Standard library

- [ ] `std.io` — `read_line`, `read_file`, `write_file`, `print`, `eprint`
- [ ] `std.str` — `parse[T]`, `format`, `pad_left`, `pad_right`, `repeat`
- [ ] `std.list` — `sort`, `sort_by`, `flat_map`, `zip`, `enumerate`, `reduce`, `find`, `any`, `all`
- [ ] `std.map` — `from_list`, `merge`, `map_values`
- [ ] `std.math` — `abs`, `min`, `max`, `pow`, `sqrt`, `floor`, `ceil`
- [ ] `std.result` / `std.option` — `map`, `flat_map`, `or_else`, `map_err`

## 🔲 Planned — Compilation & performance

- [ ] **Bytecode compiler** — compile to a compact instruction set for faster execution than tree-walking
- [ ] **JVM bytecode target** — emit `.class` files via ASM; enables JVM ecosystem interop
- [ ] **Ahead-of-time compilation** — GraalVM Native Image support for zero-cold-start CLI
- [ ] **Optimiser pass** — constant folding, dead code elimination, inline pure functions

## 🔲 Planned — AI/agent tooling

- [ ] **LSP server** — Language Server Protocol implementation for IDE integration (completions, hover, diagnostics)
- [ ] **MCP tool** — expose `check_aion`, `run_aion`, `explain_aion` as MCP tools so AI agents can validate code inline
- [ ] **Effect inference reporting** — endpoint that analyses a function and confirms its declared `@pure`/`@io` annotation is correct
- [ ] **Schema-to-type generator** — generate Aion `type`/`enum` declarations from JSON Schema, OpenAPI, or Avro
- [ ] **AI prompt template** — curated system prompt teaching an LLM Aion's conventions for zero-shot code generation
- [ ] **Test harness** — `@test`-annotated functions collected and run via `aion test`

## 🔲 Planned — Developer experience

- [ ] **Better error messages** — point at the offending token with a source excerpt and a suggested fix
- [ ] **`aion fmt`** — canonical formatter / pretty-printer
- [ ] **`aion doc`** — extract doc-comments into Markdown or HTML
- [ ] **REPL improvements** — tab-completion, command history, pretty-printed output
- [ ] **VS Code extension** — syntax highlighting grammar (TextMate), snippets

---

## Tech

| | |
|---|---|
| Java | 21 |
| ANTLR4 | 4.13.2 |
| picocli | 4.7.6 |
| JUnit Jupiter | 5.10.3 |
| Build | Gradle 9.x, `build-logic` convention plugin |
| Test coverage | 18 tests, all passing |

