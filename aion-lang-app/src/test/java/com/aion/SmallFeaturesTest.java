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
}

