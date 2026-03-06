# Status

**Version:** 0.3.0-dev  
**State:** Proof-of-concept · Tree-walking interpreter + bytecode compiler/VM · Pre-type-checker

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
- [x] Agent-contract annotations: `@tool`, `@requires(expr)`, `@ensures(expr)`, `@untrusted`, `@timeout(ms)` — parsed and stored on `FnDecl`
- [x] Immutable bindings (`let`) and mutable bindings (`mut`)
- [x] Named and positional arguments at call sites
- [x] `if` / `else if` / `else`
- [x] `while` and `for … in` loops with `break` and `continue`
- [x] Exhaustive `match` with wildcards, literals, bind, pattern guards (`x if cond`), `some`/`ok`/`err`, enum tuple/record, nested patterns
- [x] Pipeline operator `|>`
- [x] `?` propagation operator for `Result`/`Option`
- [x] Block expressions (last expression is the value)
- [x] Record literals, list literals, map literals
- [x] String escape sequences
- [x] **String interpolation** — `"Hello, ${name}!"` with arbitrary expressions
- [x] **`const` declarations** — module-level compile-time constants
- [x] **`describe` statements** — inline doc-comment statements
- [x] Semantic type alias constraints (`type T = Str where { … }`) — parsed and stored

### Interpreter (tree-walking)
- [x] Tree-walking interpreter (all constructs)
- [x] Lexical scope chain (`Environment`)
- [x] Built-in functions: `print`, `str`, `int`, `float`, `len`, `assert`, `is_some`, `is_none`, `is_ok`, `is_err`, `unwrap`, `unwrap_or`
- [x] Method calls on `List`, `Map`, `Str`
- [x] First-class functions (`FnVal` with closure capture)
- [x] `ReturnSignal` / `PropagateSignal` / `BreakSignal` / `ContinueSignal` for control flow
- [x] `const` binding at module scope before `main` runs

### Bytecode compiler + VM
- [x] `BytecodeCompiler` — single-pass AST → flat instruction list
- [x] `BytecodeVM` — register-free stack machine with call-frame deque
- [x] `Instruction` sealed hierarchy — 60+ variants (arithmetic, control flow, variables, calls, composites, pattern matchers, `?` propagation, I/O)
- [x] Correct pattern-matching stack discipline — peek-on-success / pop-on-failure; `compileBindingPattern` for payload fields
- [x] Short-circuit `AndShort` / `OrShort` with correct stack balance
- [x] `for`-loop `break` jumps past the None-cleanup Pop
- [x] `PushNone` → `NoneVal`; `Return(true)` always pushes a value (wraps null as `NoneVal`)
- [x] `VmValue` record hierarchy (`SomeVal`, `NoneVal`, `OkVal`, `ErrVal`, `EnumVal`, `RecordVal`)
- [x] `matchBodyProducesValue` — pads Unit arm bodies with `PushNone`

### Tooling
- [x] `aion run` — execute a source file (tree-walking interpreter)
- [x] `aion check` — parse/validate without running
- [x] `aion repl` — interactive REPL with multi-line brace-depth tracking
- [x] `aion compile` — compile and execute via the bytecode VM
- [x] Gradle build with ANTLR4 code generation (`generateAntlr` task)
- [x] **72 passing unit tests** across 4 suites: `AionLanguageTest` (18), `BytecodeCompilerTest` (30), `InterpreterQaTest` (5), `SmallFeaturesTest` (20)
- [x] `sample.aion` — full demo program
- [x] `bytecode-demo.aion` — bytecode-path demo

### Documentation
- [x] `docs/missing-features.md` — 18-item gap analysis rated by impact
- [x] `docs/Aion Agent Ecosystem Technical Breakdown.md` — A2A protocol, registry, arbitration, researcher-alpha manifest

---

## 🔲 Planned — Core language

- [ ] **Type checker** — infer and verify types for all expressions and statements; catch mismatches before interpretation; enforce `@requires` / `@ensures` at compile time
- [ ] **Generics at user-defined level** — type-check generic type/enum/fn declarations, not just built-ins
- [ ] **Closures / lambda literals** — anonymous function expressions: `fn(x: Int) -> Int { x * 2 }`
- [ ] **Destructuring `let`** — `let { name, age } = user` / `let (a, b) = pair`
- [ ] **Tuple type** — lightweight anonymous product type `(Int, Str, Bool)` without field names
- [ ] **Range expressions** — `0..10`, `0..=10` for use in `for` loops and slices
- [ ] **Import resolution** — multi-file module system with actual file loading, namespace, and re-export
- [ ] **Exhaustiveness checking** — static verification that `match` covers all enum variants
- [ ] **`@requires` / `@ensures` runtime enforcement** — VM checks pre/post conditions and raises `ConstraintViolation`
- [ ] **`@timeout` enforcement** — thread interrupt after declared milliseconds

## 🔲 Planned — Standard library

- [ ] `std.io` — `read_line`, `read_file`, `write_file`, `print`, `eprint`
- [ ] `std.str` — `parse[T]`, `format`, `pad_left`, `pad_right`, `repeat`
- [ ] `std.list` — `sort`, `sort_by`, `flat_map`, `zip`, `enumerate`, `reduce`, `find`, `any`, `all`
- [ ] `std.map` — `from_list`, `merge`, `map_values`
- [ ] `std.math` — `abs`, `min`, `max`, `pow`, `sqrt`, `floor`, `ceil`
- [ ] `std.result` / `std.option` — `map`, `flat_map`, `or_else`, `map_err`

## 🔲 Planned — Compilation & performance

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
- [ ] **Agent registry SDK** — `register_agent`, `search_registry`, `fetch_tool_contract` as callable Aion builtins
- [ ] **Arbiter agent runtime** — built-in `@ensures` violation detection + automatic escrow refund trigger

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
| AssertJ | 3.26.3 |
