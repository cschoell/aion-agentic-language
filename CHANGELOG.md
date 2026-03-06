# Changelog

All notable changes to the Aion language project are documented here.
Versioning follows **date-based** releases while the project is pre-1.0.

---

## [0.1.0] — 2026-03-06 · Initial implementation

### Language design
- Defined the Aion language specification: AI-agent-optimal, minimal-keyword, effect-annotated, no-null, pipeline-first.
- Effect annotation system: `@pure`, `@io`, `@mut`, `@async`, `@throws(T)`, `@test`, `@deprecated` on function declarations.
- Type system: built-in primitives (`Int`, `Float`, `Bool`, `Str`, `Unit`), generic containers (`Option[T]`, `Result[T,E]`, `List[T]`, `Map[K,V]`), record types, enum (sum) types, type aliases, function types.
- Named arguments at all call sites; positional args also supported.
- Pattern matching (`match`) — exhaustive by design; wildcards, literals, bind patterns, `some`/`ok`/`err` patterns, enum tuple/record patterns, nested patterns.
- Pipeline operator `|>` for linear data flow.
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
  - All expression forms including `match`, block-expr, `|>`, `?`, list/map literals, record literals, enum variant references.
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

## [Unreleased] — upcoming

- Type checker (see STATUS.md roadmap).
- Standard library modules.
- Bytecode or JVM compilation target.

