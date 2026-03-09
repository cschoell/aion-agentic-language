# GitHub Copilot — Project Instructions (aion-lang)

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

