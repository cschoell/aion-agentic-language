# Aion Language

A programming language designed to be **optimal for AI agents to generate and reason about**, while remaining readable for humans.

> **Version 0.4.0-dev** — Tree-walking interpreter + bytecode compiler/VM · 82 passing tests · Pre-type-checker

## Design Principles

### 1. Every construct starts with a unique keyword
An AI can predict the full syntactic shape from the first token alone:

| First token | Shape |
|---|---|
| `fn` | function definition |
| `let` | immutable binding |
| `mut` | mutable binding |
| `type` | record / alias |
| `enum` | sum type |
| `match` | exhaustive pattern match |
| `const` | module-level constant |
| `import` | module import |
| `@pure` `@io` `@async` | effect annotation (precedes `fn`) |
| `@tool` `@requires` `@ensures` | agent-contract annotation (precedes `fn`) |
| `@on_fail` | structured failure hint for agent-callable tools |

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

### 6. Pipeline operator `|>`
```aion
let result = 3 |> double |> increment |> str
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

## Syntax Quick Reference

```aion
// Constants
const PI: Float = 3.14159

// Immutable / mutable bindings
let x: Int = 42
mut counter = 0
counter = counter + 1

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
    Shape::Circle(r)              => PI * r * r,
    Shape::Rectangle { width, height } => width * height,
}

// Option / Result
let maybe: Option[Int] = some(42)
let result: Result[Float, Str] = ok(3.14)
let val = risky_call()?     // propagate Err/None

// Pipeline
let out = input |> trim |> parse |> validate

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
```

---

## Execution Modes

Aion has two execution paths, selectable per invocation:

| Mode | Command | How it works |
|---|---|---|
| **Interpreter** | `aion run <file>` | Tree-walking interpreter — instant startup, great for development |
| **Bytecode VM** | `aion compile <file>` | Compiles to a flat instruction list, runs on a stack-based VM |

```powershell
# Tree-walking interpreter
.\gradlew.bat :aion-lang-app:run --args="run src/main/resources/sample.aion"

# Bytecode compiler + VM
.\gradlew.bat :aion-lang-app:run --args="compile src/main/resources/sample.aion"
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
│       │   ├── AionFrontend.java         # Public parse API
│       │   └── AionParseException.java
│       ├── interpreter/
│       │   ├── AionValue.java            # Runtime value types (sealed)
│       │   ├── Environment.java          # Lexical scope
│       │   └── Interpreter.java          # Tree-walking interpreter
│       ├── bytecode/
│       │   ├── Instruction.java          # Sealed instruction hierarchy (60+ variants)
│       │   ├── Bytecode.java             # Compiled program (instructions + fn table)
│       │   ├── BytecodeCompiler.java     # AST → instruction list
│       │   ├── BytecodeVM.java           # Stack machine executor
│       │   └── VmValue.java              # VM runtime value types
│       └── cli/AionCli.java              # CLI (picocli): run, check, repl, compile
└── aion-lang-app/src/main/resources/
    ├── sample.aion                       # Full feature demo
    └── bytecode-demo.aion                # Bytecode compiler demo
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

# Parse-check only
.\gradlew.bat :aion-lang-app:run --args="check src/main/resources/sample.aion"

# Run tests
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
| Long expression chains are hard to generate correctly | `\|>` pipeline makes data flow linear and verifiable |
| AI agents cannot verify each other's behaviour | `@tool` + registry + arbiter agent — pay-on-success economy |

---

## See Also

- [STATUS.md](STATUS.md) — implementation status, feature checklist, and roadmap
- [CHANGELOG.md](CHANGELOG.md) — history of changes by version
- [docs/missing-features.md](docs/missing-features.md) — gap analysis and prioritised roadmap
- [docs/Aion Agent Ecosystem Technical Breakdown.md](docs/Aion%20Agent%20Ecosystem%20Technical%20Breakdown.md) — agent protocol spec
