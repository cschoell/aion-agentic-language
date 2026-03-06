# Aion Language

A programming language designed to be **optimal for AI agents to generate and reason about**, while remaining readable for humans.

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
| `import` | module import |
| `@pure` `@io` `@async` | effect annotation (precedes `fn`) |

### 2. Effect annotations on every function
```aion
@pure  fn add(a: Int, b: Int) -> Int { ... }     // no side effects
@io    fn read_file(path: Str) -> Result[Str, Str] { ... }
@async fn fetch(url: Str) -> Result[Str, Str] { ... }
@throws(IoError) fn open(path: Str) -> Unit { ... }
```
An AI can reason about what a function does without reading its body.

### 3. No nulls — explicit Option and Result
```aion
@pure fn find(xs: List[Int], n: Int) -> Option[Int] { ... }
@pure fn divide(a: Float, b: Float) -> Result[Float, Str] { ... }

// Propagate errors with ?
let val = divide(a: x, b: y)?   // returns Err early if b == 0
```

### 4. Named arguments everywhere
```aion
greet(name: "Alice")
safe_divide(a: 10.0, b: 3.0)
User { name: "Bob", age: 25 }
```
No positional surprises. AI generates self-documenting call sites.

### 5. Pipeline operator `|>`
```aion
let result = 3 |> double |> increment |> str
```
Linear data flow — AI generates pipelines that are trivially verifiable left-to-right.

### 6. Exhaustive pattern matching
```aion
match shape {
    Shape::Circle(r)                   => 3.14159 * r * r,
    Shape::Rectangle { width, height } => width * height,
    Shape::Triangle(a, b, c)           => ...,
}
```
Every match must be exhaustive. No hidden `else` paths.

### 7. Types: records and enums only
No class hierarchies. No inheritance. No implicit interfaces.
```aion
type Point = { x: Float, y: Float }

enum Result[T, E] {
    Ok(T),
    Err(E),
}
```

### 8. Module = file, no package declarations
The filename is the module name. Import with:
```aion
import std.io
import utils.math as math
```

---

## Syntax Quick Reference

```aion
// Immutable binding
let x: Int = 42
let x = 42           // type inferred

// Mutable binding
mut counter = 0
counter = counter + 1

// Function
@pure fn add(a: Int, b: Int) -> Int {
    return a + b
}

// Record type
type User = {
    name: Str,
    age:  Int,
}

// Enum (sum type)
enum Shape {
    Circle(Float),
    Rectangle { width: Float, height: Float },
}

// Record literal
let u = User { name: "Alice", age: 30 }

// Match
let area = match shape {
    Shape::Circle(r)                   => 3.14159 * r * r,
    Shape::Rectangle { width, height } => width * height,
}

// Option
let maybe: Option[Int] = some(42)
let nothing: Option[Int] = none

// Result
let result: Result[Float, Str] = ok(3.14)
let failure: Result[Float, Str] = err("not a number")

// ? propagation
let val = risky_call()?

// Pipeline
let out = input |> trim |> parse |> validate

// Loops
for item in list { ... }
while cond { ... }

// Lists and Maps
let nums = [1, 2, 3]
let map  = { "a" -> 1, "b" -> 2 }
```

---

## Project Structure

```
aion-lang/
├── build-logic/                          # Gradle convention plugin
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
│       └── cli/AionCli.java              # CLI (picocli)
└── aion-lang-app/src/main/resources/
    └── sample.aion                       # Example program
```

---

## Build & Run

```powershell
# Build
.\gradlew.bat :aion-lang-app:build

# Run the sample
.\gradlew.bat :aion-lang-app:run --args="run src/main/resources/sample.aion"

# Parse-check only
.\gradlew.bat :aion-lang-app:run --args="check src/main/resources/sample.aion"

# Run tests
.\gradlew.bat :aion-lang-app:test
```

---

## Why Aion for AI?

| Problem in existing languages | Aion's answer |
|---|---|
| AI must read entire function body to know side effects | `@pure` / `@io` / `@async` declared in signature |
| Positional args cause silent bugs when AI reorders them | All call sites use named args |
| Null reference errors are invisible at the call site | No nulls — `Option[T]` everywhere |
| Unhandled error paths | `Result[T, E]` + `?` propagation — explicit at every callsite |
| Complex inheritance hierarchies confuse generation | Records + enums only — flat, composable |
| Non-exhaustive match/switch causes runtime surprises | All `match` expressions must be exhaustive |
| Implicit type conversions produce wrong types | Zero implicit coercions |
| Long expression chains are hard to generate correctly | `|>` pipeline makes data flow linear and verifiable |

---

## See Also

- [STATUS.md](STATUS.md) — implementation status, feature checklist, and roadmap
- [CHANGELOG.md](CHANGELOG.md) — history of changes by version


