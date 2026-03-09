# AI Assistant — Project Instructions (aion-lang)

> These instructions apply to GitHub Copilot, JetBrains Junie, Claude, and any
> other AI assistant working on this repository.

---

## Project overview

Aion is a statically-typed, AI-agent-optimal programming language implemented in Java 21.
It has two execution backends that must always be kept in sync:

| Backend | Entry point | Test class |
|---------|-------------|------------|
| Tree-walking interpreter | `Interpreter.java` | `SmallFeaturesTest`, `InterpreterQaTest` |
| Bytecode compiler + VM | `BytecodeCompiler.java` + `BytecodeVM.java` | `BytecodeCompilerTest` |

The CLI (`AionCli.java`) exposes `run` (interpreter), `compile` (bytecode), `check`,
`repl`, and `describe` subcommands.

---

## Key source locations

```
aion-lang-app/src/main/
  antlr4/com/aion/parser/   ← AionLexer.g4, AionParser.g4
  java/com/aion/
    ast/Node.java            ← sealed/record AST hierarchy (single file)
    parser/AstBuilder.java   ← ANTLR visitor → AST
    parser/AionFrontend.java ← public parse API (parseString, parseFile, parseFileWithImports)
    interpreter/Interpreter.java
    interpreter/AionValue.java
    interpreter/Environment.java
    bytecode/BytecodeCompiler.java
    bytecode/BytecodeVM.java
    bytecode/Instruction.java
    bytecode/VmValue.java
    cli/AionCli.java

aion-lang-app/src/test/java/com/aion/
  SmallFeaturesTest.java     ← interpreter feature tests
  BytecodeCompilerTest.java  ← bytecode feature tests
  InterpreterQaTest.java     ← interpreter QA / regression tests
  ModuleImportTest.java      ← multi-file import tests (uses @TempDir)
  ResourceScriptE2ETest.java ← end-to-end tests running .aion resource files

docs/missing-features.md    ← prioritised backlog of unimplemented features
```

---

## Testing rule

Whenever a new language feature, compiler pass, VM instruction, or interpreter
behaviour is added or changed, **always add at least one unit test for each
affected backend**:

- **Bytecode backend** — add a test in `BytecodeCompilerTest` that compiles and
  runs Aion source through `BytecodeCompiler` + `BytecodeVM` and asserts the
  expected output or return value.
- **Interpreter backend** — add a test in `SmallFeaturesTest`,
  `InterpreterQaTest`, or an appropriate existing test class that runs the same
  scenario through the tree-walking `Interpreter` and asserts the same
  expected result.

Both tests must pass before the change is considered complete.

---

## How to add a new language feature (checklist)

1. **Lexer** (`AionLexer.g4`) — add any new tokens.
2. **Parser** (`AionParser.g4`) — add grammar rules / alternatives.
3. **Regenerate ANTLR sources** — `./gradlew :aion-lang-app:generateAntlr`.
4. **AST** (`Node.java`) — add sealed record(s) for new nodes.
5. **AstBuilder** — implement `visit*` or extend `build*` helpers.
6. **Interpreter** — handle new `Node` types in `evalExpr` / `execStmt`.
7. **BytecodeCompiler** — emit instructions for new nodes.
8. **BytecodeVM** — execute new `Instruction` variants.
9. **Tests** — add tests in both `SmallFeaturesTest` and `BytecodeCompilerTest`.
10. **Docs** — update `docs/missing-features.md` and `CHANGELOG.md`.

---

## Coding conventions

- All AST nodes are `sealed interface` / `record` in `Node.java` — add new ones there.
- `Instruction` is a sealed interface in `Instruction.java` — add new VM ops there.
- `AionValue` (interpreter) and `VmValue` (bytecode VM) are separate hierarchies.
- Test helpers: `run(String source)` in `SmallFeaturesTest` returns `List<String>` of
  printed lines; `run(String source)` in `BytecodeCompilerTest` returns trimmed stdout.
- Use `@TempDir Path dir` (JUnit 5) for tests that need real files on disk.
- ANTLR generated sources go to `build/generated/sources/antlr4/` — never commit them.

---

## Build

```powershell
# Generate ANTLR sources
./gradlew :aion-lang-app:generateAntlr

# Compile
./gradlew :aion-lang-app:compileJava

# Run all tests
./gradlew :aion-lang-app:test

# Run the interpreter on a file
./gradlew :aion-lang-app:run --args="run path/to/file.aion"
```
