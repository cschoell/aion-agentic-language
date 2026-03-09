# Aion Language

A programming language designed to be **optimal for AI agents to generate and reason about**, while remaining readable for humans.

> **Version 0.7.0-dev** — Tree-walking interpreter + bytecode compiler/VM · 198 passing tests · Multi-file imports · Pre-type-checker

## Design Principles

### 1. Every construct starts with a unique keyword
An AI can predict the full syntactic shape from the first token alone:

| First token | Shape |
|---|---|
| `fn` | function definition |
| `let` | immutable binding / destructuring |
| `mut` | mutable binding |
| `type` | record / alias / newtype |
| `enum` | sum type |
| `match` | exhaustive pattern match |
| `const` | module-level constant |
| `import` | module import |
| `for` | iteration / tuple destructuring loop |
| `@pure` `@io` `@async` | effect annotation (precedes `fn`) |
| `@tool` `@requires` `@ensures` | agent-contract annotation (precedes `fn`) |
| `@on_fail` | structured failure hint for agent-callable tools |
| `@test` | unit-test annotation (collected by `aion test`) |

### 2. Effect annotations on every function
```aion
@pure  fn add(a: Int, b: Int) -> Int { ... }
@io    fn read_file(path: Str) -> Result[Str, Str] { ... }
@async fn fetch(url: Str) -> Result[Str, Str] { ... }
```
An AI can reason about what a function does without reading its body.

### 3. Agent-contract annotations
```aion
@tool
@pure
@requires(b != 0)
@ensures(result * b == a - a % b)
@on_fail("b must not be zero — retry with a non-zero divisor.")
fn safe_div(a: Int, b: Int) -> Int {
    describe "Divides a by b. Safe for agent use."
    return a / b
}
```
`@tool` marks an agent-callable function. `@requires` / `@ensures` are machine-checkable pre/post-conditions. `@on_fail` wraps any failure as `err(ToolError { hint, cause })` so the agent loop can self-correct without unwrapping a Java stack trace.

### 4. No nulls — explicit Option and Result
```aion
@pure fn find(xs: List[Int], n: Int) -> Option[Int] { ... }
@pure fn divide(a: Float, b: Float) -> Result[Float, Str] { ... }

// Propagate errors with ?
let val = divide(a: x, b: y)?   // returns Err early if b == 0
```

### 5. Named arguments everywhere
```aion
greet(name: "Alice")
safe_divide(a: 10.0, b: 3.0)
User { name: "Bob", age: 25 }
```
No positional surprises. AI generates self-documenting call sites.

### 6. Pipeline operator `>>`
```aion
let result = 3 >> double >> increment >> str
```
Linear data flow — trivially verifiable left-to-right.

### 7. Exhaustive pattern matching with guards
```aion
match age {
    n if n < 13 => "child",
    n if n < 18 => "teenager",
    n if n < 65 => "adult",
    _           => "senior",
}
```
Every match must be exhaustive. Pattern guards allow inline conditions without nested `if`.

### 8. Types: records and enums only
No class hierarchies. No inheritance. No implicit interfaces.
```aion
type Point = { x: Float, y: Float }

enum Shape {
    Circle(Float),
    Rectangle { width: Float, height: Float },
}
```

---

## Language Features

### Numeric literal forms
All common bases and digit separators are supported for readability:
```aion
const MAX_BYTE:  Int = 0xFF          // hex
const FLAGS:     Int = 0b1111_0000   // binary with separator
const UNIX_RWX:  Int = 0o777         // octal
const MILLION:   Int = 1_000_000     // decimal with separator
```

### Module imports
Split programs across files; import entire modules or select specific names:
```aion
import math_utils                    // import all declarations
import math_utils as mu              // import with alias
import math_utils { abs, clamp }     // selective import — only abs and clamp
import math_utils { abs, MAX_BYTE }  // mix functions and consts
```
Imports are resolved relative to the source file. Transitive imports and cycle detection are built in.

### Range expressions
Produce a `List[Int]` inline — usable in `for` loops or as a value:
```aion
for i in 1..10  { print(i) }   // exclusive: 1 to 9
for i in 1..=10 { print(i) }   // inclusive: 1 to 10
let squares = (1..=5) >> map(fn(n: Int) -> Int { return n * n })
```

### Destructuring `let`
Unpack record fields or tuple positions in a single binding:
```aion
type Point = { x: Int, y: Int }
let p = Point { x: 3, y: 4 }
let { x, y } = p                // record destructure

let pair = (10, 20)
let (a, b) = pair               // tuple destructure
```

### Tuple `for` destructuring
Iterate over a list of tuples and unpack each element directly:
```aion
let pairs = [(1, "one"), (2, "two"), (3, "three")]
for (n, name) in pairs {
    print("${n} = ${name}")
}
```

### Lambda expressions (first-class functions)
```aion
let double = fn(x: Int) -> Int { return x * 2 }
let evens  = numbers.filter(fn(x: Int) -> Bool { return x % 2 == 0 })
let result = numbers.map(fn(x: Int) -> Int { return x * 2 })
```

### Refinement types
Constraints checked on every assignment:
```aion
type AgentID = Str where { self.starts_with("did:aion:") }
type Score   = Int where { self >= 0 and self <= 100 }

let id: AgentID = "did:aion:abc123"   // ok
// let bad: AgentID = "not-an-id"     // runtime error: constraint violated
```

### Named return variables
```aion
fn stats(xs: List[Int]) -> (Int, Int) return (min_val, max_val) {
    mut min_val = xs[0]
    mut max_val = xs[0]
    for x in xs { ... }
}
```

### Built-in test runner
Annotate functions with `@test` and run them with `aion test`:
```aion
@test
fn test_add() {
    assert add(a: 2, b: 3) == 5
}
```
```powershell
aion test src/main/resources/mymodule.aion
# PASS test_add
# 1 passed, 0 failed
```

---

## Syntax Quick Reference

```aion
// Constants — all numeric literal forms
const PI:      Float = 3.14159
const MAX_U8:  Int   = 0xFF
const FLAGS:   Int   = 0b1010_1010
const PERMS:   Int   = 0o755
const MILLION: Int   = 1_000_000

// Immutable / mutable bindings
let x: Int = 42
mut counter = 0
counter = counter + 1

// Destructuring let
let { name, age } = user          // record fields
let (first, second) = my_tuple    // tuple positions

// String interpolation
let msg = "Hello, ${name}! You are ${age} years old."

// Function with agent contract
@tool
@pure
@requires(score >= 0 && score <= total)
@ensures(result >= score)
fn award_point(score: Int, total: Int, correct: Bool) -> Int {
    describe "Increments score if correct, bounded by total."
    return match correct {
        true  => score + 1,
        false => score,
    }
}

// Record type
type User = { name: Str, age: Int }

// Enum (sum type)
enum Shape {
    Circle(Float),
    Rectangle { width: Float, height: Float },
}

// Record / enum literals
let u = User { name: "Alice", age: 30 }
let s = Shape::Circle(5.0)

// Match with guard and enum record pattern
let area = match shape {
    Shape::Circle(r)                   => PI * r * r,
    Shape::Rectangle { width, height } => width * height,
}

// Option / Result
let maybe: Option[Int] = some(42)
let result: Result[Float, Str] = ok(3.14)
let val = risky_call()?     // propagate Err/None

// Pipeline
let out = input >> trim >> parse >> validate

// Range expressions
for i in 0..10  { print(i) }    // exclusive
for i in 0..=10 { print(i) }    // inclusive

// Tuple for-destructuring
for (k, v) in pairs { print("${k}: ${v}") }

// Lambda expressions (first-class functions)
let double = fn(x: Int) -> Int { return x * 2 }
let evens = numbers.filter(fn(x: Int) -> Bool { return x % 2 == 0 })
let doubled = numbers.map(fn(x: Int) -> Int { return x * 2 })

// Refinement types — constraint checked on every assignment
type AgentID = Str   where { self.starts_with("did:aion:") }
type Score   = Int   where { self >= 0 and self <= 100 }

let id: AgentID = "did:aion:abc123"   // ok
// let bad: AgentID = "not-an-id"    // runtime error: constraint violated

// Loops with break / continue
for item in list { ... }
while cond { if skip_condition { continue }  if done { break } }

// Module imports
import math_utils                    // all declarations
import math_utils as mu              // with alias
import math_utils { abs, clamp }     // selective
```

---

## Execution Modes

Aion has two execution paths, selectable per invocation:

| Mode | Command | How it works |
|---|---|---|
| **Interpreter** | `aion run <file>` | Tree-walking interpreter — instant startup, great for development |
| **Bytecode VM** | `aion compile <file>` | Compiles to a flat instruction list, runs on a stack-based VM |
| **Test runner** | `aion test <file>` | Collects `@test` functions, runs each, reports PASS/FAIL + summary |

```powershell
# Tree-walking interpreter
.\gradlew.bat :aion-lang-app:run --args="run src/main/resources/sample.aion"

# Bytecode compiler + VM
.\gradlew.bat :aion-lang-app:run --args="compile src/main/resources/sample.aion"

# Run built-in tests
.\gradlew.bat :aion-lang-app:run --args="test src/main/resources/sample.aion"
```

---

## Agent Ecosystem

Aion is designed as the contract layer for autonomous AI-to-AI interaction.

### Agent-to-Agent (A2A) protocol
```aion
type AgentID = Str where { self.starts_with("did:aion:") }

type ServiceRequest = {
    sender:       AgentID,
    action:       Str,
    payload:      Map[Str, Str],
    budget_limit: Int,
}

@tool
@untrusted
@requires(req.budget_limit > 0)
@ensures(result.status == "ACK" || result.status == "REJECT")
fn negotiate_service(req: ServiceRequest) -> Map[Str, Str] {
    describe "Formal entry point for external agent requests."
    return match req.action {
        "reserve" => handle_reservation(req.payload),
        _         => { status: "REJECT", reason: "Unknown action" },
    }
}
```

### Automated arbitration
If an agent's response violates its own `@ensures` clause, the Aion runtime flags the breach and triggers an Arbiter Agent which audits the signed transaction and issues an automatic refund. Agents that breach are penalised in the global Registry.

See [`docs/Aion Agent Ecosystem Technical Breakdown.md`](docs/Aion%20Agent%20Ecosystem%20Technical%20Breakdown.md) for the full spec.

---

## Project Structure

```
aion-lang/
├── build-logic/                          # Gradle convention plugin
├── docs/
│   ├── missing-features.md               # Gap analysis (18 items, rated by impact)
│   └── Aion Agent Ecosystem Technical Breakdown.md
├── aion-lang-app/
│   ├── src/main/antlr4/com/aion/parser/
│   │   ├── AionLexer.g4                  # Lexer grammar
│   │   └── AionParser.g4                 # Parser grammar
│   └── src/main/java/com/aion/
│       ├── ast/Node.java                 # Sealed AST node hierarchy
│       ├── parser/
│       │   ├── AstBuilder.java           # ANTLR tree → AST
│       │   ├── AionFrontend.java         # Public parse API + import resolution
│       │   └── AionParseException.java
│       ├── interpreter/
│       │   ├── AionValue.java            # Runtime value types (sealed)
│       │   ├── Environment.java          # Lexical scope
│       │   └── Interpreter.java          # Tree-walking interpreter
│       ├── bytecode/
│       │   ├── Instruction.java          # Sealed instruction hierarchy (70+ variants)
│       │   ├── Bytecode.java             # Compiled program (instructions + fn table)
│       │   ├── BytecodeCompiler.java     # AST → instruction list
│       │   ├── BytecodeVM.java           # Stack machine executor
│       │   └── VmValue.java              # VM runtime value types
│       └── cli/AionCli.java              # CLI (picocli): run, compile, test, check
└── aion-lang-app/src/main/resources/
    ├── sample.aion                       # Full feature demo
    ├── bytecode-demo.aion                # Bytecode compiler demo
    ├── import-demo.aion                  # Module import + numeric literals demo
    └── math_utils.aion                   # Shared utility module (imported by demos)
```

---

## Build & Run

```powershell
# Build
.\gradlew.bat :aion-lang-app:build

# Run the sample (tree-walking interpreter)
.\gradlew.bat :aion-lang-app:run --args="run src/main/resources/sample.aion"

# Run via bytecode compiler + VM
.\gradlew.bat :aion-lang-app:run --args="compile src/main/resources/sample.aion"

# Run the import demo (multi-file, numeric literals, selective imports)
.\gradlew.bat :aion-lang-app:run --args="compile src/main/resources/import-demo.aion"

# Run built-in tests in a source file
.\gradlew.bat :aion-lang-app:run --args="test src/main/resources/sample.aion"

# Parse-check only
.\gradlew.bat :aion-lang-app:run --args="check src/main/resources/sample.aion"

# Run all unit tests
.\gradlew.bat :aion-lang-app:test

# Install distribution (then run directly without Gradle)
.\gradlew.bat :aion-lang-app:installDist
.\aion-lang-app\build\install\aion-lang-app\bin\aion-lang-app.bat compile src/main/resources/sample.aion
```

---

## Why Aion for AI?

| Problem in existing languages | Aion's answer |
|---|---|
| AI must read entire function body to know side effects | `@pure` / `@io` / `@async` declared in signature |
| No machine-checkable API contracts | `@requires` / `@ensures` are language-level, not comments |
| Positional args cause silent bugs when AI reorders them | All call sites use named args |
| Null reference errors are invisible at the call site | No nulls — `Option[T]` everywhere |
| Unhandled error paths | `Result[T, E]` + `?` propagation — explicit at every call site |
| Complex inheritance hierarchies confuse generation | Records + enums only — flat, composable |
| Non-exhaustive match/switch causes runtime surprises | All `match` expressions must be exhaustive |
| Implicit type conversions produce wrong types | Zero implicit coercions |
| Long expression chains are hard to generate correctly | `>>` pipeline makes data flow linear and verifiable |
| AI agents cannot verify each other's behaviour | `@tool` + registry + arbiter agent — pay-on-success economy |
| Magic numbers obscure intent | Hex/binary/octal literals + digit separators built in |
| Multi-file programs require build tooling | `import` with transitive resolution and cycle detection |

---

## See Also

- [STATUS.md](STATUS.md) — implementation status, feature checklist, and roadmap
- [CHANGELOG.md](CHANGELOG.md) — history of changes by version
- [docs/missing-features.md](docs/missing-features.md) — gap analysis and prioritised roadmap
- [docs/Aion Agent Ecosystem Technical Breakdown.md](docs/Aion%20Agent%20Ecosystem%20Technical%20Breakdown.md) — agent protocol spec
