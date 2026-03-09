Aion is intentionally designed around **algebraic data types + traits-style composition** rather than OOP inheritance. Here's the reasoning:

#### The Core Design Philosophy

Aion is optimized for **AI agent generation and reasoning**. Class hierarchies introduce several problems for AI:

- **Implicit behavior** — a subclass silently overrides a parent method; an AI must trace the full inheritance chain to know what actually runs.
- **Fragile base class problem** — changing a parent breaks unknown subclasses; AI can't safely refactor.
- **Ambiguous dispatch** — virtual method resolution depends on runtime type; AI can't statically reason about which code path executes.

#### What Aion Uses Instead

| OOP concept | Aion equivalent |
|---|---|
| Interface / abstract class | `enum` (sum type) + `match` (exhaustive dispatch) |
| Subtype polymorphism | `enum` variants with different payloads |
| Mixin / trait | Module imports + function composition |
| Data class | `type` record |
| Newtype wrapper | `type Meters = Int` (newtype alias) |

#### Example: Polymorphism Without Inheritance

Instead of:
```java
interface Shape { double area(); }
class Circle extends Shape { ... }
class Rect extends Shape { ... }
```

Aion uses:
```aion
enum Shape {
    Circle { radius: Float },
    Rect   { width: Float, height: Float },
}

@pure fn area(s: Shape) -> Float {
    match s {
        Circle { radius } => 3.14159 * radius * radius,
        Rect { width, height } => width * height,
    }
}
```

**Why this is better for AI:**
- `match` is **exhaustive** — the compiler (and AI) knows every case is handled.
- Adding a new variant forces every `match` site to be updated — no silent omissions.
- No hidden dispatch; the AI reads one function and sees all behavior.

#### Planned: Traits System

The `STATUS.md` / `missing-features.md` do list a **Traits/interface system** as a planned feature (`[ ] Traits/interface system — structural typing, not nominal inheritance`). The key word is *structural* — Aion will likely adopt a Rust/Haskell-style trait system where:

- A `trait` declares a set of function signatures.
- Any `type` or `enum` can implement a trait without inheriting from it.
- No runtime vtable surprises; dispatch is resolved at compile time.

This gives you the **code-reuse benefits** of interfaces without the **fragile-hierarchy problems** of OOP.

#### Summary

Aion deliberately avoids class hierarchies because:
1. **Exhaustive `match` on `enum`** is safer and more AI-readable than virtual dispatch.
2. **Algebraic types** make all data shapes explicit and statically knowable.
3. **Module imports** provide code reuse without coupling via inheritance.
4. A **structural trait system** is planned for the future — giving interface-like contracts without subtype polymorphism.

If you'd like, I can implement a basic `trait` / `impl` system as the next language feature!
