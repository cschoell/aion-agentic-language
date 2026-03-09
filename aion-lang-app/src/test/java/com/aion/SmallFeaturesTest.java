package com.aion;

import com.aion.interpreter.Interpreter;
import com.aion.interpreter.AionValue;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the 5 "small effort" missing features:
 *   1. const declarations
 *   2. break / continue in loops
 *   3. String interpolation  "Hello ${name}"
 *   4. ? error propagation
 *   5. Pattern guards         n if n > 0 => ...
 */
class SmallFeaturesTest {

    private List<String> run(String source) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        var orig = System.out;
        System.setOut(ps);
        try {
            var interp = new Interpreter();
            interp.loadModule(result.module());
            interp.callFunction("main", List.of());
        } finally {
            System.setOut(orig);
            ps.flush();
        }
        String out = baos.toString(StandardCharsets.UTF_8).stripTrailing();
        return out.isEmpty() ? List.of() : List.of(out.split("\r?\n", -1));
    }

    private AionValue runFn(String source, String fn) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        var interp = new Interpreter();
        interp.loadModule(result.module());
        return interp.callFunction(fn, List.of());
    }

    // ── Semicolon as inline statement separator ───────────────────────────────

    @Test void semicolon_separates_two_stmts_on_one_line() {
        var out = run("""
                @pure fn main() -> Unit {
                    let x = 1; let y = 2
                    print(x + y)
                }
                """);
        assertThat(out).containsExactly("3");
    }

    @Test void semicolon_in_if_block() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut done = false
                    if true { done = true; print("ok") }
                    print(done)
                }
                """);
        assertThat(out).containsExactly("ok", "true");
    }

    @Test void trailing_semicolon_ignored() {
        var out = run("""
                @pure fn main() -> Unit {
                    print("a");
                    print("b");
                }
                """);
        assertThat(out).containsExactly("a", "b");
    }

    @Test void semicolons_in_while_body() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut i = 0; mut s = 0
                    while i < 3 { i = i + 1; s = s + i }
                    print(s)
                }
                """);
        assertThat(out).containsExactly("6");
    }

    @Test void semicolons_break_continue() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut i = 0
                    while i < 5 {
                        i = i + 1
                        if i == 2 { continue }
                        if i == 4 { break }
                        print(i)
                    }
                }
                """);
        // i=1 printed, i=2 skipped, i=3 printed, i=4 breaks
        assertThat(out).containsExactly("1", "3");
    }

    // ── Pipeline operator (>>) ─────────────────────────────────────────────────


    @Test void pipeline_interp_in_string_literal() {
        // The string ">>" should be preserved literally in a non-interpolated string
        var out = run("""
            @pure fn main() -> Unit {
                print("a >> b")
            }
            """);
        assertThat(out).containsExactly("a >> b");
    }

    @Test void pipeline_interp_in_interpolated_string() {
        // The string ">>" should be preserved in an interpolated string
        var out = run("""
            @pure fn main() -> Unit {
                let x = 42
                print("result: ${x} >> done")
            }
            """);
        assertThat(out).containsExactly("result: 42 >> done");
    }

    @Test void pipeline_pipe_before_hole_in_interpolated_string() {
        // >> appearing BEFORE the ${} hole — the case in sample.aion
        var out = run("""
            @pure fn inc(x: Int) -> Int { return x + 1 }
            @pure fn main() -> Unit {
                let r = 3 >> inc
                print("Pipeline 3 >> inc = ${r}")
            }
            """);
        assertThat(out).containsExactly("Pipeline 3 >> inc = 4");
    }

    @Test void pipeline_operator_executes() {
        var out = run("""
            @pure fn double(x: Int) -> Int { return x * 2 }
            @pure fn inc(x: Int) -> Int { return x + 1 }
            @pure fn main() -> Unit {
                let r = 3 >> double >> inc
                print(r)
            }
            """);
        assertThat(out).containsExactly("7");
    }

    // ── 1. const declarations ─────────────────────────────────────────────────

    @Test void const_int_in_main() {
        var out = run("const MAX: Int = 42\n@pure fn main() -> Unit { print(MAX) }");
        assertThat(out).containsExactly("42");
    }

    @Test void const_str_in_fn() {
        var out = run(
            "const GREETING: Str = \"hello\"\n" +
            "@pure fn greet() -> Str { return GREETING }\n" +
            "@pure fn main() -> Unit { print(greet()) }");
        assertThat(out).containsExactly("hello");
    }

    @Test void const_in_arithmetic() {
        var out = run(
            "const BASE: Int = 10\n" +
            "const FACTOR: Int = 3\n" +
            "@pure fn main() -> Unit { print(BASE * FACTOR) }");
        assertThat(out).containsExactly("30");
    }

    // ── 2. break / continue ───────────────────────────────────────────────────

    @Test void break_exits_while_early() {
        var out = run(
            "@pure fn main() -> Unit {\n" +
            "    mut i = 0\n" +
            "    while i < 10 {\n" +
            "        if i == 3 { break }\n" +
            "        print(i)\n" +
            "        i = i + 1\n" +
            "    }\n" +
            "}");
        assertThat(out).containsExactly("0", "1", "2");
    }

    @Test void continue_skips_iteration() {
        var out = run(
            "@pure fn main() -> Unit {\n" +
            "    mut i = 0\n" +
            "    while i < 5 {\n" +
            "        i = i + 1\n" +
            "        if i == 3 { continue }\n" +
            "        print(i)\n" +
            "    }\n" +
            "}");
        assertThat(out).containsExactly("1", "2", "4", "5");
    }

    @Test void break_in_infinite_loop() {
        var out = run(
            "@pure fn main() -> Unit {\n" +
            "    mut x = 0\n" +
            "    while true {\n" +
            "        x = x + 1\n" +
            "        if x == 3 { break }\n" +
            "    }\n" +
            "    print(x)\n" +
            "}");
        assertThat(out).containsExactly("3");
    }

    @Test void continue_skips_multiple() {
        var out = run(
            "@pure fn main() -> Unit {\n" +
            "    mut i = 0\n" +
            "    while i < 6 {\n" +
            "        i = i + 1\n" +
            "        if i == 2 { continue }\n" +
            "        if i == 4 { continue }\n" +
            "        print(i)\n" +
            "    }\n" +
            "}");
        assertThat(out).containsExactly("1", "3", "5", "6");
    }

    // ── 3. String interpolation ───────────────────────────────────────────────

    @Test void interp_single_var() {
        // Use concat to build the interp string so Java text blocks don't interfere
        String src = "@pure fn main() -> Unit {\n" +
                     "    let name = \"World\"\n" +
                     "    print(\"Hello ${name}!\")\n" +
                     "}";
        assertThat(run(src)).containsExactly("Hello World!");
    }

    @Test void interp_two_holes() {
        String src = "@pure fn main() -> Unit {\n" +
                     "    let score = 3\n" +
                     "    let total = 5\n" +
                     "    print(\"Score: ${score} / ${total}\")\n" +
                     "}";
        assertThat(run(src)).containsExactly("Score: 3 / 5");
    }

    @Test void interp_expr_in_hole() {
        String src = "@pure fn main() -> Unit {\n" +
                     "    let x = 6\n" +
                     "    let y = 7\n" +
                     "    print(\"${x} * ${y} = ${x * y}\")\n" +
                     "}";
        assertThat(run(src)).containsExactly("6 * 7 = 42");
    }

    @Test void interp_const_in_hole() {
        String src = "const LIMIT: Int = 100\n" +
                     "@pure fn main() -> Unit {\n" +
                     "    let val = 42\n" +
                     "    print(\"${val} of ${LIMIT}\")\n" +
                     "}";
        assertThat(run(src)).containsExactly("42 of 100");
    }

    // ── 4. ? error propagation ────────────────────────────────────────────────

    @Test void propagate_ok_unwraps() {
        var result = runFn(
            "@pure fn get_ok() -> Result[Int, Str] { return ok(42) }\n" +
            "@pure fn use_it() -> Result[Int, Str] {\n" +
            "    let v = get_ok()?\n" +
            "    return ok(v + 1)\n" +
            "}", "use_it");
        assertThat(result).isInstanceOf(AionValue.OkVal.class);
        assertThat(((AionValue.OkVal) result).inner()).isEqualTo(new AionValue.IntVal(43));
    }

    @Test void propagate_err_early_returns() {
        var result = runFn(
            "@pure fn get_err() -> Result[Int, Str] { return err(\"oops\") }\n" +
            "@pure fn use_it() -> Result[Int, Str] {\n" +
            "    let v = get_err()?\n" +
            "    return ok(v + 1)\n" +
            "}", "use_it");
        assertThat(result).isInstanceOf(AionValue.ErrVal.class);
        assertThat(((AionValue.ErrVal) result).inner()).isEqualTo(new AionValue.StrVal("oops"));
    }

    @Test void propagate_some_unwraps() {
        var result = runFn(
            "@pure fn get_some() -> Option[Int] { return some(10) }\n" +
            "@pure fn use_it() -> Option[Int] {\n" +
            "    let v = get_some()?\n" +
            "    return some(v * 2)\n" +
            "}", "use_it");
        assertThat(result).isInstanceOf(AionValue.SomeVal.class);
        assertThat(((AionValue.SomeVal) result).inner()).isEqualTo(new AionValue.IntVal(20));
    }

    @Test void propagate_none_early_returns() {
        var result = runFn(
            "@pure fn get_none() -> Option[Int] { return none }\n" +
            "@pure fn use_it() -> Option[Int] {\n" +
            "    let v = get_none()?\n" +
            "    return some(v * 2)\n" +
            "}", "use_it");
        assertThat(result).isInstanceOf(AionValue.NoneVal.class);
    }

    // ── 5. Pattern guards ─────────────────────────────────────────────────────

    @Test void guard_grade_levels() {
        var out = run(
            "@pure fn grade(n: Int) -> Str {\n" +
            "    return match n {\n" +
            "        x if x >= 90 => \"A\",\n" +
            "        x if x >= 80 => \"B\",\n" +
            "        x if x >= 70 => \"C\",\n" +
            "        _            => \"F\",\n" +
            "    }\n" +
            "}\n" +
            "@pure fn main() -> Unit {\n" +
            "    print(grade(95))\n" +
            "    print(grade(85))\n" +
            "    print(grade(72))\n" +
            "    print(grade(50))\n" +
            "}");
        assertThat(out).containsExactly("A", "B", "C", "F");
    }

    @Test void guard_negative_zero_positive() {
        var out = run(
            "@pure fn sign(n: Int) -> Str {\n" +
            "    return match n {\n" +
            "        x if x < 0 => \"neg\",\n" +
            "        0          => \"zero\",\n" +
            "        _          => \"pos\",\n" +
            "    }\n" +
            "}\n" +
            "@pure fn main() -> Unit {\n" +
            "    print(sign(-1))\n" +
            "    print(sign(0))\n" +
            "    print(sign(1))\n" +
            "}");
        assertThat(out).containsExactly("neg", "zero", "pos");
    }

    @Test void guard_on_some_pattern() {
        var out = run(
            "@pure fn check(v: Option[Int]) -> Str {\n" +
            "    return match v {\n" +
            "        some(x) if x > 10 => \"big\",\n" +
            "        some(x)           => \"small\",\n" +
            "        none              => \"nothing\",\n" +
            "    }\n" +
            "}\n" +
            "@pure fn main() -> Unit {\n" +
            "    print(check(some(20)))\n" +
            "    print(check(some(3)))\n" +
            "    print(check(none))\n" +
            "}");
        assertThat(out).containsExactly("big", "small", "nothing");
    }

    @Test void guard_uses_const() {
        var out = run(
            "const LIMIT: Int = 5\n" +
            "@pure fn classify(n: Int) -> Str {\n" +
            "    return match n {\n" +
            "        x if x >= LIMIT => \"high\",\n" +
            "        x if x > 0     => \"low\",\n" +
            "        _              => \"zero\",\n" +
            "    }\n" +
            "}\n" +
            "@pure fn main() -> Unit {\n" +
            "    print(classify(0))\n" +
            "    print(classify(3))\n" +
            "    print(classify(7))\n" +
            "}");
        assertThat(out).containsExactly("zero", "low", "high");
    }

    // ── Combined ──────────────────────────────────────────────────────────────

    @Test void all_features_combined() {
        String src =
            "const PASS: Int = 60\n" +
            "@pure fn label(n: Int) -> Str {\n" +
            "    return match n {\n" +
            "        x if x >= 90 => \"excellent\",\n" +
            "        x if x >= PASS => \"pass\",\n" +
            "        _ => \"fail\",\n" +
            "    }\n" +
            "}\n" +
            "@pure fn main() -> Unit {\n" +
            "    mut i = 0\n" +
            "    while i < 4 {\n" +
            "        i = i + 1\n" +
            "        if i == 2 { continue }\n" +
            "        let tag = label(i * 30)\n" +
            "        print(\"${i}: ${tag}\")\n" +
            "    }\n" +
            "}";
        var out = run(src);
        assertThat(out).hasSize(3);
        assertThat(out.get(0)).isEqualTo("1: fail");
        assertThat(out.get(1)).isEqualTo("3: excellent");
        assertThat(out.get(2)).isEqualTo("4: excellent");
    }

    // ── Tuple types (feature #9) ──────────────────────────────────────────────

    @Test void tuple_construction_and_print() {
        var out = run("""
            @pure fn main() -> Unit {
                let t = (1, "hello")
                print(t)
            }
            """);
        assertThat(out).containsExactly("(1, hello)");
    }

    @Test void tuple_field_access_via_methods() {
        var out = run("""
            @pure fn main() -> Unit {
                let t = (42, "world")
                print(t.first())
                print(t.second())
            }
            """);
        assertThat(out).containsExactly("42", "world");
    }

    @Test void tuple_get_by_index() {
        var out = run("""
            @pure fn main() -> Unit {
                let t = (10, 20, 30)
                print(t.get(0))
                print(t.get(2))
            }
            """);
        assertThat(out).containsExactly("10", "30");
    }

    @Test void tuple_len() {
        var out = run("""
            @pure fn main() -> Unit {
                let t = (1, 2, 3)
                print(t.len())
            }
            """);
        assertThat(out).containsExactly("3");
    }

    @Test void tuple_returned_from_function() {
        var out = run("""
            @pure fn min_max(a: Int, b: Int) -> (Int, Int) {
                return (a, b)
            }
            @pure fn main() -> Unit {
                let t = min_max(3, 7)
                print(t.first())
                print(t.second())
            }
            """);
        assertThat(out).containsExactly("3", "7");
    }

    @Test void tuple_pattern_match_in_match() {
        var out = run("""
            @pure fn classify(t: (Int, Int)) -> Str {
                return match t {
                    (0, 0) => "origin",
                    (x, 0) => "on x-axis",
                    (0, y) => "on y-axis",
                    (x, y) => "general",
                }
            }
            @pure fn main() -> Unit {
                print(classify((0, 0)))
                print(classify((5, 0)))
                print(classify((0, 3)))
                print(classify((2, 4)))
            }
            """);
        assertThat(out).containsExactly("origin", "on x-axis", "on y-axis", "general");
    }

    // ── Named return variables (feature #18) ──────────────────────────────────

    @Test void named_return_binds_in_ensures() {
        // @ensures uses the named return variable instead of magic "result"
        var src = """
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
            """;
        var out = run(src);
        assertThat(out).containsExactly("6", "5");
    }

    @Test void named_return_result_also_bound() {
        // "result" backward-compat name also works in @ensures
        var src = """
            @pure
            @ensures(result >= 0)
            fn abs_val(n: Int) -> (magnitude: Int) {
                return match n {
                    x if x < 0 => -x,
                    x          => x,
                }
            }
            @pure fn main() -> Unit {
                print(abs_val(-7))
                print(abs_val(3))
            }
            """;
        var out = run(src);
        assertThat(out).containsExactly("7", "3");
    }

    // ── Numeric literal forms (feature #14) ──────────────────────────────────

    @Test void hex_literal_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let x = 0xFF
                print(x)
            }
            """);
        assertThat(out).containsExactly("255");
    }

    @Test void binary_literal_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let x = 0b1010
                print(x)
            }
            """);
        assertThat(out).containsExactly("10");
    }

    @Test void octal_literal_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let x = 0o17
                print(x)
            }
            """);
        assertThat(out).containsExactly("15");
    }

    @Test void digit_separator_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let x = 1_000_000
                print(x)
            }
            """);
        assertThat(out).containsExactly("1000000");
    }

    @Test void hex_arithmetic_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let a = 0x10
                let b = 0b0100
                print(a + b)
            }
            """);
        assertThat(out).containsExactly("20");
    }

    // ── Range expressions (feature #15) ──────────────────────────────────────

    @Test void exclusive_range_for_loop() {
        var out = run("""
            @pure fn main() -> Unit {
                for i in 0..5 { print(i) }
            }
            """);
        assertThat(out).containsExactly("0", "1", "2", "3", "4");
    }

    @Test void inclusive_range_for_loop() {
        var out = run("""
            @pure fn main() -> Unit {
                for i in 1..=3 { print(i) }
            }
            """);
        assertThat(out).containsExactly("1", "2", "3");
    }

    @Test void range_as_list_value() {
        var out = run("""
            @pure fn main() -> Unit {
                let xs = 0..4
                print(xs)
            }
            """);
        assertThat(out).containsExactly("[0, 1, 2, 3]");
    }

    @Test void range_with_variable_bounds() {
        var out = run("""
            @pure fn main() -> Unit {
                let start = 2
                let end   = 5
                for i in start..end { print(i) }
            }
            """);
        assertThat(out).containsExactly("2", "3", "4");
    }

    @Test void range_sum() {
        var out = run("""
            @pure fn main() -> Unit {
                mut sum = 0
                for i in 1..=10 { sum = sum + i }
                print(sum)
            }
            """);
        assertThat(out).containsExactly("55");
    }

    // ── Destructuring let (feature) ───────────────────────────────────────────

    @Test void let_record_destructure_interpreter() {
        var out = run("""
            type Point = { x: Int, y: Int }
            @pure fn main() -> Unit {
                let p = Point { x: 3, y: 7 }
                let { x, y } = p
                print(x + y)
            }
            """);
        assertThat(out).containsExactly("10");
    }

    @Test void let_tuple_destructure_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let pair = (10, 20)
                let (a, b) = pair
                print(a + b)
            }
            """);
        assertThat(out).containsExactly("30");
    }

    @Test void let_tuple_destructure_three_elements() {
        var out = run("""
            @pure fn main() -> Unit {
                let t = (1, 2, 3)
                let (x, y, z) = t
                print(x + y + z)
            }
            """);
        assertThat(out).containsExactly("6");
    }

    @Test void for_tuple_destructure_interpreter() {
        var out = run("""
            @pure fn main() -> Unit {
                let pairs = [(1, 10), (2, 20), (3, 30)]
                for (k, v) in pairs {
                    print(k + v)
                }
            }
            """);
        assertThat(out).containsExactly("11", "22", "33");
    }

    @Test void let_record_destructure_partial() {
        var out = run("""
            type User = { name: Str, age: Int, active: Bool }
            @pure fn main() -> Unit {
                let u = User { name: "Alice", age: 30, active: true }
                let { name, age } = u
                print(name)
                print(age)
            }
            """);
        assertThat(out).containsExactly("Alice", "30");
    }

    // ── Deep field assignment (feature #6) ────────────────────────────────────

    @Test void deep_field_assignment_interpreter() {
        var out = run("""
            type Address = { city: Str, zip: Str }
            type Person  = { name: Str, addr: Address }
            @pure fn main() -> Unit {
                mut p = Person { name: "Alice", addr: Address { city: "Berlin", zip: "10115" } }
                p.addr.city = "Munich"
                print(p.addr.city)
            }
            """);
        assertThat(out).containsExactly("Munich");
    }
}

