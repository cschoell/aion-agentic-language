package com.aion;

import com.aion.bytecode.Bytecode;
import com.aion.bytecode.BytecodeCompiler;
import com.aion.bytecode.BytecodeVM;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Aion bytecode compiler + VM.
 * Each test compiles an Aion source string, runs it in the VM, and
 * asserts on trimmed stdout.
 */
class BytecodeCompilerTest {

    private String run(String source) {
        var result = AionFrontend.parseString(source, "<bc-test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        Bytecode bc = new BytecodeCompiler().compile(result.module());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream saved = System.out;
        System.setOut(new PrintStream(bos));
        try { new BytecodeVM().run(bc); } finally { System.setOut(saved); }
        return bos.toString().replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    // ── Basic arithmetic ──────────────────────────────────────────────────────
    @Test void integer_addition()  { assertThat(run("@pure fn main() -> Unit { print(3 + 4) }")).isEqualTo("7"); }
    @Test void float_division()    { assertThat(run("@pure fn main() -> Unit { print(10.0 / 4.0) }")).isEqualTo("2.5"); }
    @Test void unary_negation()    { assertThat(run("@pure fn main() -> Unit { print(-5) }")).isEqualTo("-5"); }

    // ── String interpolation ──────────────────────────────────────────────────
    @Test void string_interpolation() {
        assertThat(run("@pure fn main() -> Unit { let x = 42\nprint(\"x = ${x}\") }")).isEqualTo("x = 42");
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    @Test void module_const() {
        assertThat(run("const LIMIT: Int = 7\n@pure fn main() -> Unit { print(LIMIT) }")).isEqualTo("7");
    }

    // ── User functions ────────────────────────────────────────────────────────
    @Test void user_function_call() {
        assertThat(run("""
            @pure fn double(x: Int) -> Int { return x * 2 }
            @pure fn main() -> Unit { print(double(5)) }
            """)).isEqualTo("10");
    }
    @Test void recursive_factorial() {
        assertThat(run("""
            @pure fn fact(n: Int) -> Int {
                if n <= 1 { return 1 }
                return n * fact(n - 1)
            }
            @pure fn main() -> Unit { print(fact(6)) }
            """)).isEqualTo("720");
    }

    // ── Control flow ─────────────────────────────────────────────────────────
    @Test void if_else() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 10
                if x > 5 { print("big") } else { print("small") }
            }
            """)).isEqualTo("big");
    }
    @Test void while_break() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut i = 0
                while i < 100 { i = i + 1\nif i == 5 { break } }
                print(i)
            }
            """)).isEqualTo("5");
    }
    @Test void while_continue() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut sum = 0\nmut i = 0
                while i < 6 { i = i + 1\nif i == 3 { continue }\nsum = sum + i }
                print(sum)
            }
            """)).isEqualTo("18");
    }
    @Test void for_loop_sum() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut acc = 0
                for x in [1, 2, 3, 4, 5] { acc = acc + x }
                print(acc)
            }
            """)).isEqualTo("15");
    }

    // ── Match ─────────────────────────────────────────────────────────────────
    @Test void match_int_literal() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let s = match 2 { 1 => "one", 2 => "two", _ => "other" }
                print(s)
            }
            """)).isEqualTo("two");
    }
    @Test void match_pattern_guard() {
        assertThat(run("""
            @pure fn classify(n: Int) -> Str {
                return match n {
                    x if x < 0  => "neg",
                    0            => "zero",
                    x if x < 10 => "small",
                    _            => "big",
                }
            }
            @pure fn main() -> Unit {
                print(classify(-3))
                print(classify(0))
                print(classify(5))
                print(classify(99))
            }
            """)).isEqualTo("neg\nzero\nsmall\nbig");
    }
    @Test void match_bind_and_compute() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let v = match 7 { x => x * 2 }
                print(v)
            }
            """)).isEqualTo("14");
    }
    @Test void match_bool() {
        assertThat(run("""
            @pure fn yn(b: Bool) -> Str { return match b { true => "yes", false => "no" } }
            @pure fn main() -> Unit { print(yn(true))\nprint(yn(false)) }
            """)).isEqualTo("yes\nno");
    }

    // ── Option / Result ───────────────────────────────────────────────────────
    @Test void match_some_none() {
        assertThat(run("""
            @pure fn get_val(o: Option[Int]) -> Int {
                return match o { some(v) => v, none => -1 }
            }
            @pure fn main() -> Unit {
                print(get_val(some(42)))
                print(get_val(none))
            }
            """)).isEqualTo("42\n-1");
    }
    @Test void match_ok_err() {
        assertThat(run("""
            @pure fn safe_div(a: Float, b: Float) -> Result[Float, Str] {
                if b == 0.0 { return err("zero") }
                return ok(a / b)
            }
            @pure fn main() -> Unit {
                match safe_div(10.0, 2.0) {
                    ok(v)  => print("ok: ${v}"),
                    err(e) => print("err: ${e}"),
                }
                match safe_div(5.0, 0.0) {
                    ok(v)  => print("ok: ${v}"),
                    err(e) => print("err: ${e}"),
                }
            }
            """)).isEqualTo("ok: 5.0\nerr: zero");
    }
    @Test void question_propagation() {
        assertThat(run("""
            @pure fn safe_div(a: Float, b: Float) -> Result[Float, Str] {
                if b == 0.0 { return err("zero") }
                return ok(a / b)
            }
            @pure fn double_ratio(a: Float, b: Float) -> Result[Float, Str] {
                let v = safe_div(a, b)?
                return ok(v * 2.0)
            }
            @pure fn main() -> Unit {
                match double_ratio(10.0, 2.0) {
                    ok(v)  => print("ok: ${v}"),
                    err(e) => print("err: ${e}"),
                }
                match double_ratio(5.0, 0.0) {
                    ok(v)  => print("ok: ${v}"),
                    err(e) => print("err: ${e}"),
                }
            }
            """)).isEqualTo("ok: 10.0\nerr: zero");
    }

    // ── Collections ───────────────────────────────────────────────────────────
    @Test void list_push_and_len() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut xs = [1, 2, 3]
                xs.push(4)
                print(xs.len())
            }
            """)).isEqualTo("4");
    }
    @Test void map_set_and_get() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut m = {}
                m.set("k", 99)
                print(m.get("k"))
            }
            """)).isEqualTo("99");
    }

    // ── Records ───────────────────────────────────────────────────────────────
    @Test void record_field_access() {
        assertThat(run("""
            type Point = { x: Float, y: Float }
            @pure fn main() -> Unit {
                let p = Point { x: 3.0, y: 4.0 }
                print(p.x)
                print(p.y)
            }
            """)).isEqualTo("3.0\n4.0");
    }

    // ── Enums ─────────────────────────────────────────────────────────────────
    @Test void enum_tuple_match() {
        assertThat(run("""
            enum Shape { Circle(Float), Square(Float) }
            const PI: Float = 3.0
            @pure fn area(s: Shape) -> Float {
                return match s {
                    Shape::Circle(r) => r * r * PI,
                    Shape::Square(w) => w * w,
                }
            }
            @pure fn main() -> Unit {
                print(area(Shape::Circle(2.0)))
                print(area(Shape::Square(4.0)))
            }
            """)).isEqualTo("12.0\n16.0");
    }
    @Test void enum_record_match() {
        assertThat(run("""
            enum Shape { Rect { width: Float, height: Float } }
            @pure fn area(s: Shape) -> Float {
                return match s {
                    Shape::Rect { width: w, height: h } => w * h,
                }
            }
            @pure fn main() -> Unit {
                print(area(Shape::Rect { width: 3.0, height: 5.0 }))
            }
            """)).isEqualTo("15.0");
    }

    @Test void enum_area_interpolated_print() {
        assertThat(run("""
            enum Shape {
                Circle(Float),
                Rectangle { width: Float, height: Float },
            }
            const PI: Float = 3.14159
            @pure fn area(shape: Shape) -> Float {
                return match shape {
                    Shape::Circle(r)                   => r * r * PI,
                    Shape::Rectangle { width, height } => width * height,
                }
            }
            @pure fn main() -> Unit {
                let c = Shape::Circle(5.0)
                let r = Shape::Rectangle { width: 4.0, height: 6.0 }
                print("Circle area: ${area(shape: c)}")
                print("Rectangle area: ${area(shape: r)}")
            }
            """)).isEqualTo("Circle area: 78.53975\nRectangle area: 24.0");
    }

    @Test void enum_three_arm_match() {
        assertThat(run("""
            enum Shape { Circle(Float), Rect { width: Float, height: Float }, Tri(Float, Float) }
            const PI: Float = 3.0
            @pure fn area(s: Shape) -> Float {
                return match s {
                    Shape::Circle(r)              => r * r * PI,
                    Shape::Rect { width, height } => width * height,
                    Shape::Tri(b, h)              => b * h / 2.0,
                }
            }
            @pure fn main() -> Unit {
                print(area(Shape::Circle(2.0)))
                print(area(Shape::Rect { width: 3.0, height: 5.0 }))
                print(area(Shape::Tri(4.0, 6.0)))
            }
            """)).isEqualTo("12.0\n15.0\n12.0");
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────
    @Test void pipeline_operator() {
        assertThat(run("""
            @pure fn double(x: Int) -> Int { return x * 2 }
            @pure fn inc(x: Int)    -> Int { return x + 1 }
            @pure fn main() -> Unit { print(3 >> double >> inc) }
            """)).isEqualTo("7");
    }

    // ── Short-circuit logic ───────────────────────────────────────────────────
    @Test void short_circuit_and() {
        assertThat(run("""
            @pure fn main() -> Unit { print(true and false)\nprint(true and true) }
            """)).isEqualTo("false\ntrue");
    }
    @Test void short_circuit_or() {
        assertThat(run("""
            @pure fn main() -> Unit { print(false or true)\nprint(false or false) }
            """)).isEqualTo("true\nfalse");
    }

    // ── String methods ────────────────────────────────────────────────────────
    @Test void string_trim_upper_len() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let s = "  hello  "
                print(s.trim())
                print(s.trim().upper())
                print("hello".len())
            }
            """)).isEqualTo("hello\nHELLO\n5");
    }

    // ── Lambda expressions ────────────────────────────────────────────────────
    @Test void lambda_map_bytecode() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let xs = [1, 2, 3]
                let doubled = xs.map(fn(x: Int) -> Int { return x * 2 })
                print(doubled)
            }
            """)).isEqualTo("[2, 4, 6]");
    }

    @Test void lambda_filter_bytecode() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let xs = [1, 2, 3, 4, 5]
                let evens = xs.filter(fn(x: Int) -> Bool { return x % 2 == 0 })
                print(evens)
            }
            """)).isEqualTo("[2, 4]");
    }

    @Test void lambda_variable_called_directly() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let double = fn(x: Int) -> Int { return x * 2 }
                print(double(5))
            }
            """)).isEqualTo("10");
    }

    @Test void lambda_variable_called_in_for_loop() {
        // Regression: BytecodeCompiler used to throw "Unknown function: step"
        // when a variable holding a lambda was called inside a for-loop body.
        assertThat(run("""
            @pure fn normalise(s: Str) -> Str {
                let steps = [
                    fn(x: Str) -> Str { return x.trim() },
                    fn(x: Str) -> Str { return x.upper() },
                ]
                mut out = s
                for step in steps {
                    out = step(out)
                }
                return out
            }
            @pure fn main() -> Unit {
                print(normalise("  hello  "))
            }
            """)).isEqualTo("HELLO");
    }

    @Test void lambda_list_pipeline_two_steps() {
        assertThat(run("""
            @pure fn apply_all(s: Str) -> Str {
                let ops = [
                    fn(x: Str) -> Str { return x.trim() },
                    fn(x: Str) -> Str { return x.lower() },
                ]
                mut result = s
                for op in ops {
                    result = op(result)
                }
                return result
            }
            @pure fn main() -> Unit {
                print(apply_all("  WORLD  "))
            }
            """)).isEqualTo("world");
    }

    @Test void on_fail_requires_annotation_is_parsed() {
        // The bytecode VM does not enforce @requires/@on_fail at runtime yet —
        // those are checked by the tree-walking interpreter.  This test verifies
        // that the annotations parse and compile without error, and that a valid
        // call (b != 0) still returns the correct result.
        assertThat(run("""
            @tool
            @pure
            @requires(b != 0)
            @on_fail("b must be non-zero")
            fn safe_div(a: Int, b: Int) -> Int {
                describe "Divides a by b."
                return a / b
            }
            @pure fn main() -> Unit {
                print(safe_div(10, 2))
            }
            """)).isEqualTo("5");
    }

    @Test void on_fail_not_triggered_on_success_bytecode() {
        assertThat(run("""
            @tool
            @pure
            @requires(b != 0)
            @on_fail("b must be non-zero")
            fn safe_div(a: Int, b: Int) -> Int {
                describe "Divides a by b."
                return a / b
            }
            @pure fn main() -> Unit {
                print(safe_div(10, 2))
            }
            """)).isEqualTo("5");
    }

    // ── @on_fail structured tool errors ──────────────────────────────────────
    @Test void on_fail_describe_contains_hint() {
        // aion describe should emit on_fail_hint in JSON
        var result = com.aion.parser.AionFrontend.parseString("""
            @tool
            @pure
            @requires(b != 0)
            @on_fail("b must be non-zero")
            fn safe_div(a: Int, b: Int) -> Int {
                describe "Divides a by b."
                return a / b
            }
            """, "<test>");
        assertThat(result.errors()).isEmpty();
        // Verify annotation parsed correctly
        var fn = (com.aion.ast.Node.FnDecl) result.module().decls().getFirst();
        boolean hasOnFail = fn.annotations().stream()
                .anyMatch(a -> a instanceof com.aion.ast.Node.Annotation.OnFail of
                        && of.hint().equals("b must be non-zero"));
        assertThat(hasOnFail).isTrue();
    }

    // ── Refinement types ──────────────────────────────────────────────────────
    @Test void refinement_type_parses() {
        var result = com.aion.parser.AionFrontend.parseString("""
            type AgentID = Str where { self.starts_with("did:aion:") }
            @pure fn main() -> Unit {}
            """, "<test>");
        assertThat(result.errors()).isEmpty();
        var typeDecl = (com.aion.ast.Node.TypeDecl) result.module().decls().getFirst();
        assertThat(typeDecl.body()).isInstanceOf(com.aion.ast.Node.TypeDeclBody.Alias.class);
        var alias = (com.aion.ast.Node.TypeDeclBody.Alias) typeDecl.body();
        assertThat(alias.constraint()).isNotNull();
    }

    // ── Tuple types (feature #9) ──────────────────────────────────────────────

    @Test void tuple_construction_and_print() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let t = (1, "hello")
                print(t)
            }
            """)).isEqualTo("(1, hello)");
    }

    @Test void tuple_first_second() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let t = (42, "world")
                print(t.first())
                print(t.second())
            }
            """)).isEqualTo("42\nworld");
    }

    @Test void tuple_get_by_index() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let t = (10, 20, 30)
                print(t.get(0))
                print(t.get(2))
            }
            """)).isEqualTo("10\n30");
    }

    @Test void tuple_len() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let t = (1, 2, 3)
                print(t.len())
            }
            """)).isEqualTo("3");
    }

    @Test void tuple_returned_from_function() {
        assertThat(run("""
            @pure fn pair(a: Int, b: Int) -> (Int, Int) { return (a, b) }
            @pure fn main() -> Unit {
                let t = pair(3, 7)
                print(t.first())
                print(t.second())
            }
            """)).isEqualTo("3\n7");
    }

    @Test void tuple_pattern_match() {
        assertThat(run("""
            @pure fn classify(t: (Int, Int)) -> Str {
                return match t {
                    (0, 0) => "origin",
                    (x, 0) => "x-axis",
                    (0, y) => "y-axis",
                    (x, y) => "general",
                }
            }
            @pure fn main() -> Unit {
                print(classify((0, 0)))
                print(classify((5, 0)))
                print(classify((0, 3)))
                print(classify((2, 4)))
            }
            """)).isEqualTo("origin\nx-axis\ny-axis\ngeneral");
    }

    // ── Named return variables (feature #18) ──────────────────────────────────

    @Test void named_return_basic() {
        assertThat(run("""
            @pure
            @ensures(next_score >= score)
            fn award_point(score: Int, correct: Bool) -> (next_score: Int) {
                return match correct {
                    true  => score + 1,
                    false => score,
                }
            }
            @pure fn main() -> Unit {
                print(award_point(5, true))
                print(award_point(5, false))
            }
            """)).isEqualTo("6\n5");
    }

    // ── Deep field assignment (feature #6) ────────────────────────────────────

    @Test void deep_field_assignment_bytecode() {
        assertThat(run("""
            type Address = { city: Str, zip: Str }
            type Person  = { name: Str, addr: Address }
            @pure fn main() -> Unit {
                mut p = Person { name: "Alice", addr: Address { city: "Berlin", zip: "10115" } }
                p.addr.city = "Munich"
                print(p.addr.city)
            }
            """)).isEqualTo("Munich");
    }

    // ── Semicolon as inline statement separator ───────────────────────────────

    @Test void bc_semicolon_two_lets_one_line() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 10; let y = 20
                print(x + y)
            }
            """)).isEqualTo("30");
    }

    @Test void bc_semicolon_in_if_block() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut flag = false
                if true { flag = true; print("set") }
                print(flag)
            }
            """)).isEqualTo("set\ntrue");
    }

    @Test void bc_trailing_semicolon_ignored() {
        assertThat(run("""
            @pure fn main() -> Unit {
                print("x");
                print("y");
            }
            """)).isEqualTo("x\ny");
    }

    @Test void bc_semicolon_while_body() {
        assertThat(run("""
            @pure fn main() -> Unit {
                mut i = 0; mut acc = 0
                while i < 4 { i = i + 1; acc = acc + i }
                print(acc)
            }
            """)).isEqualTo("10");
    }

    // ── Numeric literal forms (feature #14) ──────────────────────────────────

    @Test void bc_hex_literal() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 0xFF
                print(x)
            }
            """)).isEqualTo("255");
    }

    @Test void bc_binary_literal() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 0b1010
                print(x)
            }
            """)).isEqualTo("10");
    }

    @Test void bc_octal_literal() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 0o17
                print(x)
            }
            """)).isEqualTo("15");
    }

    @Test void bc_digit_separator() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let x = 1_000_000
                print(x)
            }
            """)).isEqualTo("1000000");
    }

    @Test void bc_hex_arithmetic() {
        assertThat(run("""
            @pure fn main() -> Unit {
                let a = 0x10
                let b = 0b0100
                print(a + b)
            }
            """)).isEqualTo("20");
    }
}
