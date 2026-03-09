# Changelog

All notable changes to the Aion language project are documented here.
Versioning follows **date-based** releases while the project is pre-1.0.

---

## [0.10.0] — 2026-03-09 · Generic functions + async/await

### Language
- **Generic functions** — `fn identity[T](x: T) -> T { x }` and `fn show[T: Display](x: T) -> Str { … }`.
  Type parameters are parsed with optional trait constraints (`T: TraitName`); runtime is type-erased
  (Aion values carry no static type, so generics work without monomorphisation).
- **`async fn` + `await`** — `async fn compute() -> Int { … }` marks a function as asynchronous;
  calling it returns a `Future[T]` value immediately. `await expr` blocks until the future resolves
  and unwraps the result. Backed by Java virtual threads (`Thread.ofVirtual()`).
- **Implicit return** — a trailing expression statement in a function body is now treated as the
  return value in both the interpreter and bytecode VM (enables idiomatic `fn f() -> T { expr }`).
- **`Future[T]` type** — new `TypeRef.FutureT` in the AST; `T_FUTURE` lexer token; `FutureType`
  parser rule; `FutureVal` in `AionValue` and `VmValue`.

### Grammar / Parser
- `AionLexer.g4` — new `ASYNC`, `AWAIT`, `T_FUTURE` tokens.
- `AionParser.g4` — `fnDecl` extended with optional `ASYNC?`; `typeParams` refactored to
  `typeParam` rule supporting `T: TraitName` constraints; `AwaitExpr` primary expression;
  `FutureType` type reference.
- `Node.FnDecl` — added `isAsync` flag and `typeParamConstraints` map.
- `Node.Expr.Await(inner, pos)` — new AST node.
- `Node.TypeRef.FutureT(inner)` — new type reference.

### Interpreter
- `callFn` — detects `isAsync`, spawns a virtual thread, returns `FutureVal` immediately.
- `Expr.Await` — evaluated by calling `.join()` on the `FutureVal`'s `CompletableFuture`.
- `execBlockWithImplicitReturn` — new helper for trailing-expression implicit return.

### Bytecode
- `Instruction.MakeAsync(address, arity, params)` — spawns async VM on virtual thread, pushes `FutureVal`.
- `Instruction.AwaitFuture()` — pops `FutureVal`, calls `.join()`, pushes result.
- `BytecodeCompiler` — emits `MakeAsync` for async fn calls; implicit-return detection in
  `compileFnDeclNamed`; `Expr.Await` compiles to `AwaitFuture`.
- `BytecodeVM` — `Return` with empty frame stack halts cleanly (enables async sub-VM entry);
  `MakeAsync` / `AwaitFuture` cases added.

### Tests
- 4 new interpreter generic tests + 4 async tests in `SmallFeaturesTest`.
- 4 new bytecode generic tests + 3 async tests in `BytecodeCompilerTest`.
- Total: **213 passing tests** across 7 suites.

### Demo
- `bytecode-demo.aion` — new generic functions section (`identity`, `first`, `show`) and
  async section (`async_double`, `async_add` with `await`).
- `ResourceScriptE2ETest` expected output updated with 6 new lines.

---

## [0.9.0] — 2026-03-09 · Refinement type bytecode injection

### Bytecode
- **`CheckConstraint` instruction** — new `Instruction.CheckConstraint(typeName, bindingName, constraintCode)`
  carries a pre-compiled constraint expression as a sub-list of instructions.
- **`BytecodeCompiler`** — collects `TypeDecl` alias constraints into a `refinements` map;
  emits `CheckConstraint` after `Store` for `let`/`mut` bindings and function parameters
  whose declared type has a registered refinement.
- **`BytecodeVM`** — executes `CheckConstraint` via `runConstraint`: saves VM state, binds
  `self` to the stored value, runs the constraint code, restores state, throws on failure.

### Tests
- 5 new bytecode tests in `BytecodeCompilerTest`: valid `let`, invalid `let` (throws),
  valid param, invalid param (throws), string constraint (`starts_with`).
- Total: **198 passing tests** across 7 suites.

### Docs
- `docs/missing-features.md` — refinement bytecode injection marked ✅ done.
- `STATUS.md`, `README.md` — version bumped to `0.9.0-dev`, test count updated.

---

## [0.8.0] — 2026-03-09 · Trait / impl system

### Language
- **Trait declarations** — `trait Foo { fn method(self: Self) -> T }` defines an abstract
  interface with one or more method signatures (no body).
- **Impl blocks** — `impl Foo for MyType { fn method(self: MyType) -> T { … } }` provides
  a concrete implementation for any record or enum type.
- Method dispatch in both the interpreter and bytecode VM: `value.method()` first checks
  impl methods before falling back to built-in dispatch.

### Grammar / Parser
- `AionLexer.g4` — new `TRAIT` and `IMPL` keywords.
- `AionParser.g4` — new `traitDecl` (with `traitMember` abstract signatures) and `implDecl`
  rules as `topDecl` alternatives.
- `Node.TraitDecl` and `Node.ImplDecl` AST records.
- `AstBuilder` — `visitTraitDecl` / `visitImplDecl`; `convertTraitMember` handles abstract sigs.

### Interpreter
- `implMethods` registry (`TypeName::methodName → FnDecl`); populated from `ImplDecl` in
  `loadModule`; `evalMethodCall` falls back to impl lookup; `receiverTypeName` helper.

### Bytecode
- `Bytecode.implMethods` map (`ImplMethodEntry(address, params)`).
- `BytecodeCompiler` — impl methods compiled as named functions (`TypeName::methodName`);
  registry embedded in returned `Bytecode`.
- `BytecodeVM` — `CallMethod` checks `implMethods` before built-in dispatch.

### Tests
- 10 new tests in `TraitTest`: basic trait call, self-field access, multiple methods,
  two types same trait, extra params — all for both interpreter and bytecode.
- Total: **193 passing tests** across 7 suites.

### Demo
- `bytecode-demo.aion`, `agent-tools.aion`, `qa-demo.aion` updated with trait/impl examples.
- `ResourceScriptE2ETest` expected output updated.

---

## [0.7.0] — 2026-03-09 · Destructuring let + aion test command

### Language
- **Destructuring `let`** — `let { name, age } = user` (record destructuring) and
  `let (a, b) = pair` (tuple destructuring). Both interpreter and bytecode VM supported.
- **Tuple `for` destructuring** — `for (k, v) in pairs { … }` iterates a list of tuples,
  binding each element positionally per iteration.

### Grammar / Parser
- `AionParser.g4` — `letStmt` extended with `LetRecordDestructure` and `LetTupleDestructure`
  alternatives; `forStmt` extended with `ForTupleDestructure` alternative.
- `Node.Stmt.LetDestructure(List<String> names, boolean isTuple, Expr value, Pos pos)` — new AST node.
- `Node.Stmt.ForTupleDestructure(List<String> vars, Expr iterable, Stmt.Block body, Pos pos)` — new AST node.
- `AstBuilder` — `buildLetStmt` and `buildForStmt` dispatch on new context subclasses.

### Interpreter
- `execLetDestructure` — record path looks up each name in `RecordVal.fields()`; tuple path
  checks arity and binds positionally from `TupleVal` or `ListVal`.
- `execForTupleDestructure` — iterates a list, destructuring each item as a tuple per iteration.

### Bytecode
- `Instruction.DestructureRecord(List<String> names)` — pops `RecordVal`, stores each field.
- `Instruction.DestructureTuple(List<String> names)` — pops `TupleVal`/`ListVal`, stores positionally.
- `BytecodeCompiler` — emits `DestructureRecord`/`DestructureTuple` for `LetDestructure`;
  new `compileForTupleDestructure` method for `ForTupleDestructure`.
- `BytecodeVM` — executes both new instructions.

### CLI
- `aion test <file>` — new subcommand; collects all `@test`-annotated functions, runs each
  via the interpreter, and reports `PASS`/`FAIL` per function plus a summary count.

### Tests
- 5 new interpreter tests in `SmallFeaturesTest` (record destructure, tuple destructure 2/3
  elements, for-tuple-destructure, partial record destructure).
- 4 new bytecode tests in `BytecodeCompilerTest`.
- Total: **183 passing tests** across 6 suites.

### Demo
- `bytecode-demo.aion` — new destructuring section: `Vec2` record destructure, tuple
  destructure, and `for (k, v2) in pairs` loop.
- `ResourceScriptE2ETest` expected output updated with 3 new lines.

---

## [0.6.0] — 2026-03-09 · Range expressions

### Language
- **Range expressions** (feature #15) — `from..to` (exclusive) and `from..=to` (inclusive)
  range literals. Ranges evaluate to a `List[Int]` and work in `for … in` loops or anywhere
  a list is expected. Both interpreter and bytecode VM supported.

### Grammar / Parser
- `AionLexer.g4` — new `DOTDOTEQ` (`..=`) token (placed before `DOTDOT` to avoid ambiguity).
- `AionParser.g4` — `expr` rule extended with `ExclusiveRange` and `InclusiveRange` alternatives
  using `pipeExpr` operands to avoid left-recursion.
- `Node.Expr.RangeLit` — new AST node with `from`, `to`, `inclusive`, `pos` fields.
- `AstBuilder.buildExpr` — dispatches on `ExclusiveRangeContext` / `InclusiveRangeContext` /
  `ExprPipeContext`.

### Interpreter
- `Interpreter.evalExpr` — `RangeLit` case materialises a `ListVal` of `IntVal` elements.

### Bytecode
- `Instruction.MakeRange(boolean inclusive)` — new instruction; pops `to` and `from`, pushes `ListVal`.
- `BytecodeCompiler` — emits `MakeRange` for `Expr.RangeLit`.
- `BytecodeVM` — executes `MakeRange`.

### Tests
- 5 new interpreter tests in `SmallFeaturesTest` (exclusive/inclusive for-loop, range as list,
  variable bounds, sum 1..=10).
- 4 new bytecode tests in `BytecodeCompilerTest`.
- Total: **174 passing tests** across 6 suites.

### Demo
- `bytecode-demo.aion` — new range expressions section: `sum(1..=10)`, `digits 0..5`, even-sum.
- `ResourceScriptE2ETest` expected output updated.

---

## [0.5.0] — 2026-03-09 · Selective imports

### Language
- **Selective imports** (feature #13) — `import foo { bar, baz }` syntax imports only the
  named declarations from a module. Works for functions, consts, types, and enums. Both
  lowercase (`IDENT`) and uppercase (`TYPE_IDENT`) names are supported in the name list.

### Grammar / Parser
- `AionParser.g4` — `importDecl` extended with optional `LBRACE importName (COMMA importName)* RBRACE`;
  new `importName` rule accepts both `IDENT` and `TYPE_IDENT`.
- `AstBuilder` — `visitImportDecl` now populates `ImportDecl.names` from `ctx.importName()`.
- `Node.ImportDecl` — added `List<String> names` field (empty = import all).
- `AionFrontend.loadRecursive` — when `names` is non-empty, loads the module into a
  temporary list and filters to only the requested declarations before merging.

### Tests
- 5 new tests in `ModuleImportTest`: selective function import (interpreter + bytecode),
  selective const import, multiple selective names, and exclusion verification.
- Total: **165 passing tests** across 6 suites.

---

## [0.4.0] — 2026-03-09 · Demo scripts, numeric literals & module imports
### Language
- **Numeric literal forms** — hex (`0xFF`), binary (`0b1010`), octal (`0o755`), and underscore digit separators (`1_000_000`) in both the lexer and `AstBuilder`.
- **Module import system** — `AionFrontend.parseFileWithImports(Path)`: depth-first recursive file loading, transitive imports, cycle detection, and missing-file errors; flat `Module` merge.
- `AionCli` `run` and `compile` subcommands now use `parseFileWithImports` so multi-file programs work end-to-end.
### Demo scripts
- **`math_utils.aion`** (new) — shared utility module: `abs`, `clamp`, `isqrt`, `sum`, `gcd`, `low_byte`, `is_power_of_two`; showcases all new literal forms.
- **`import-demo.aion`** (new) — full demo of `import math_utils` plus hex/binary/octal/underscore literals and every imported function.
- **`bytecode-demo.aion`** — added `MAX_BYTE`, `FLAGS`, `UNIX_RWX`, `MILLION` consts and a numeric-literals section in `main()`.
### Tests
- 10 new numeric-literal tests in `SmallFeaturesTest` and `BytecodeCompilerTest`.
- 7 new import tests in new `ModuleImportTest` class (interpreter + bytecode, transitive imports, error case).
- Total: **160 passing tests** across 6 suites.
### Docs
- `docs/missing-features.md` updated — items #1 (imports) and #14 (numeric literals) marked implemented.
- `STATUS.md`, `README.md`, and `CHANGELOG.md` updated to reflect current state.
- `.github/copilot-instructions.md` expanded with architecture overview, key file map, feature checklist, and build commands.
- `.junie/guidelines.md` created as a single-line pointer to `copilot-instructions.md`.
---
## [0.1.0] — 2026-03-06 · Initial implementation

### Language design
- Defined the Aion language specification: AI-agent-optimal, minimal-keyword, effect-annotated, no-null, pipeline-first.
- Effect annotation system: `@pure`, `@io`, `@mut`, `@async`, `@throws(T)`, `@test`, `@deprecated` on function declarations.
- Type system: built-in primitives (`Int`, `Float`, `Bool`, `Str`, `Unit`), generic containers (`Option[T]`, `Result[T,E]`, `List[T]`, `Map[K,V]`), record types, enum (sum) types, type aliases, function types.
- Named arguments at all call sites; positional args also supported.
- Pattern matching (`match`) — exhaustive by design; wildcards, literals, bind patterns, `some`/`ok`/`err` patterns, enum tuple/record patterns, nested patterns.
- Pipeline operator `>>` for linear data flow.
- `?` propagation operator — short-circuits `Err`/`None` up the call stack.
- Block expressions (last expression is the value).
- Loops: `for … in`, `while`.
- Identifier convention: `lowercase_start` for values/functions, `UpperCamelCase` for types/variants.
- Module = file; `import path.to.module [as alias]` syntax.

### Grammar (ANTLR4)
- `AionLexer.g4` — full lexer with 27 keywords, 5 effect annotations, 9 built-in type tokens, 40+ symbols.
- `AionParser.g4` — complete parser grammar with labelled alternatives for all expression, statement, pattern, and type-reference productions. Visitor generation enabled.

### AST
- `Node.java` — sealed/record-based AST hierarchy covering all language constructs: `Module`, `FnDecl`, `TypeDecl`, `EnumDecl`, `ImportDecl`, all `Stmt` variants, all `Expr` variants, all `Pattern` variants, all `TypeRef` variants.

### Parser front-end
- `AstBuilder` — ANTLR visitor that converts parse trees to the typed AST. All helper methods named `build*`/`convert*` to avoid clashing with ANTLR visitor interface.
- `AionFrontend` — public API: `parseFile(Path)` and `parseString(String, String)` returning a `ParseResult` with collected errors.
- `AionParseException` — carries source position for error reporting.

### Interpreter
- `AionValue` — sealed runtime value hierarchy (`IntVal`, `FloatVal`, `BoolVal`, `StrVal`, `UnitVal`, `NoneVal`, `SomeVal`, `OkVal`, `ErrVal`, `ListVal`, `MapVal`, `RecordVal`, `EnumVal`, `FnVal`).
- `Environment` — lexical scope chain with `define`, `assign`, `lookup`.
- `Interpreter` — tree-walking interpreter:
  - All statement forms (`let`, `mut`, assign, `return`, `if`/`else if`/`else`, `while`, `for … in`, block).
  - All expression forms including `match`, block-expr, `>>`, `?`, list/map literals, record literals, enum variant references.
  - Method calls on `List`, `Map`, `Str` (`.push`, `.pop`, `.get`, `.len`, `.contains`, `.map`, `.filter`, `.keys`, `.values`, `.upper`, `.lower`, `.trim`, `.split`, `.starts_with`, `.ends_with`).
  - Built-in functions: `print`, `str`, `int`, `float`, `len`, `assert`, `is_some`, `is_none`, `is_ok`, `is_err`, `unwrap`, `unwrap_or`.
  - Control flow via `ReturnSignal` / `PropagateSignal` exceptions.

### CLI
- `AionCli` — picocli-based CLI with three subcommands:
  - `aion run <file> [entryPoint]` — parse + run a source file.
  - `aion check <file>` — parse/validate without executing.
  - `aion repl` — interactive REPL with brace-depth tracking for multi-line input.

### Build
- Standalone Gradle project with `build-logic` convention plugin.
- Convention plugin `aion-lang.java-app` handles Java 21 toolchain + ANTLR4 code generation (`generateAntlr` task, `-visitor -no-listener`).
- Version catalog `gradle/libs.versions.toml` (ANTLR4 4.13.2, picocli 4.7.6, JUnit Jupiter 5.10.3, AssertJ 3.26.3).
- Isolated `build-logic/settings.gradle.kts` to prevent version catalog conflicts.

### Tests
- `AionLanguageTest` — 18 tests covering: parse smoke tests, error recovery, integer/float/string arithmetic, `let`/`mut` bindings, conditionals, `match` with int literals and `Option`, list operations, `for` loop accumulation, pipeline operator, `Result` propagation with `?`.

### Sample program
- `sample.aion` — demonstrates records, enums, `@pure`/`@io` annotations, `match`, `Option`, `Result`, pipeline, named args, `for` loops, list operations.

---

## [0.1.9] — 2026-03-09 · Numeric literals & module imports

### Language
- **Numeric literal forms** (feature #14): hex (`0xFF`), binary (`0b1010`), octal (`0o17`),
  and digit-separator (`1_000_000`) integer literals now supported in the lexer and parsed
  correctly by `AstBuilder`. Both interpreter and bytecode VM handle them transparently.
- **Module import system** (feature #1, basic): `import foo.bar` now resolves
  `foo/bar.aion` relative to the importing file, parses it, and merges all its declarations
  into the current module before compilation. Transitive imports and cycle detection are
  supported. Namespace isolation and stdlib remain future work.

### Parser / Frontend
- `AionLexer.g4` — `INT_LIT` rule extended with `0x…`, `0b…`, `0o…` prefixes and `_`
  digit separators; `FLOAT_LIT` also accepts `_` separators.
- `AstBuilder` — new `parseIntLit` helper strips `_` and dispatches to `Long.parseLong`
  with the correct radix; used for `IntLit`, `IntPat`, and `@timeout` annotation values.
- `AionFrontend` — new `parseFileWithImports(Path)` method: depth-first recursive import
  resolution, cycle detection, missing-file error reporting, flat `Module` merge.

### CLI
- `aion run` and `aion compile` now use `parseFileWithImports` so multi-file programs
  work out of the box.

### Tests
- 10 new tests in `SmallFeaturesTest` and `BytecodeCompilerTest` covering all numeric
  literal forms (hex, binary, octal, digit separator, mixed arithmetic).
- 7 new tests in `ModuleImportTest` covering: function import, const import, transitive
  imports, and missing-module error — for both interpreter and bytecode backends.

---

## [0.8.0] 2026-03-09
### Added
- **Trait system** (`trait` + `impl … for …`) — structural, no inheritance:
  - `trait` declares a set of function signatures (abstract members).
  - `impl TraitName for TypeName { fn … }` provides concrete implementations.
  - Method calls (`value.method()`) dispatch to impl methods when no built-in matches.
  - Both interpreter and bytecode VM support trait dispatch.
  - `self` is the first parameter of every impl method; extra parameters supported.
  - Multiple types can implement the same trait; one type can have multiple impls.
- **10 new tests** in `TraitTest.java` (5 interpreter + 5 bytecode); total 193 tests.
- **Demo** — `bytecode-demo.aion` updated with `HasArea` + `Labeled` traits on `Rect` and `Circle`.

---

## [Unreleased] — upcoming

- Type checker (see STATUS.md roadmap).
- Standard library modules.
- Namespace isolation for imported modules.

