package com.aion;

import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AionLanguageTest {

    private Interpreter run(String source) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        Interpreter interp = new Interpreter();
        interp.loadModule(result.module());
        return interp;
    }

    private com.aion.interpreter.AionValue eval(String fnBody) {
        String src = "@pure fn _t() -> Unit {\n" + fnBody + "\n}";
        return run(src).callFunction("_t", List.of());
    }

    // ── Parsing smoke tests ───────────────────────────────────────────────────

    @Test void parses_empty_module() {
        var result = AionFrontend.parseString("", "<empty>");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.module().decls()).isEmpty();
    }

    @Test void parses_fn_declaration() {
        var result = AionFrontend.parseString(
                "@pure fn add(a: Int, b: Int) -> Int { return a + b }", "<test>");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.module().decls()).hasSize(1);
    }

    @Test void parses_type_declaration() {
        var result = AionFrontend.parseString(
                "type Point = { x: Float, y: Float }", "<test>");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test void parses_enum_declaration() {
        var result = AionFrontend.parseString(
                "enum Color { Red, Green, Blue }", "<test>");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test void parse_error_produces_messages() {
        var result = AionFrontend.parseString("fn broken {{{", "<test>");
        assertThat(result.hasErrors()).isTrue();
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    @Test void integer_addition() {
        Interpreter i = run("@pure fn add(a: Int, b: Int) -> Int { return a + b }");
        var v = i.callFunction("add", List.of(
                new com.aion.interpreter.AionValue.IntVal(3),
                new com.aion.interpreter.AionValue.IntVal(4)));
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(7);
    }

    @Test void float_multiplication() {
        Interpreter i = run("@pure fn mul(a: Float, b: Float) -> Float { return a * b }");
        var v = i.callFunction("mul", List.of(
                new com.aion.interpreter.AionValue.FloatVal(2.5),
                new com.aion.interpreter.AionValue.FloatVal(4.0)));
        assertThat(((com.aion.interpreter.AionValue.FloatVal) v).value()).isEqualTo(10.0);
    }

    @Test void string_concatenation() {
        Interpreter i = run("@pure fn cat(a: Str, b: Str) -> Str { return a + b }");
        var v = i.callFunction("cat", List.of(
                new com.aion.interpreter.AionValue.StrVal("Hello"),
                new com.aion.interpreter.AionValue.StrVal(" World")));
        assertThat(((com.aion.interpreter.AionValue.StrVal) v).value()).isEqualTo("Hello World");
    }

    // ── Let / mut bindings ────────────────────────────────────────────────────

    @Test void let_binding_immutable() {
        Interpreter i = run("@pure fn val() -> Int { let x = 42 return x }");
        var v = i.callFunction("val", List.of());
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(42);
    }

    @Test void mut_binding_reassignable() {
        Interpreter i = run("@pure fn counter() -> Int { mut x = 0 x = x + 1 x = x + 1 return x }");
        var v = i.callFunction("counter", List.of());
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(2);
    }

    // ── Conditionals ─────────────────────────────────────────────────────────

    @Test void if_else_true_branch() {
        Interpreter i = run("@pure fn sign(n: Int) -> Str { if n > 0 { return \"pos\" } else { return \"neg\" } }");
        var v = i.callFunction("sign", List.of(new com.aion.interpreter.AionValue.IntVal(5)));
        assertThat(((com.aion.interpreter.AionValue.StrVal) v).value()).isEqualTo("pos");
    }

    @Test void if_else_false_branch() {
        Interpreter i = run("@pure fn sign(n: Int) -> Str { if n > 0 { return \"pos\" } else { return \"neg\" } }");
        var v = i.callFunction("sign", List.of(new com.aion.interpreter.AionValue.IntVal(-3)));
        assertThat(((com.aion.interpreter.AionValue.StrVal) v).value()).isEqualTo("neg");
    }

    // ── Match / patterns ──────────────────────────────────────────────────────

    @Test void match_int_literal() {
        Interpreter i = run("""
            @pure fn label_of(n: Int) -> Str {
                return match n {
                    0 => "zero",
                    1 => "one",
                    _ => "other",
                }
            }
            """);
        assertThat(((com.aion.interpreter.AionValue.StrVal)
                i.callFunction("label_of", List.of(new com.aion.interpreter.AionValue.IntVal(0)))).value())
                .isEqualTo("zero");
        assertThat(((com.aion.interpreter.AionValue.StrVal)
                i.callFunction("label_of", List.of(new com.aion.interpreter.AionValue.IntVal(99)))).value())
                .isEqualTo("other");
    }

    @Test void match_option() {
        Interpreter i = run("""
            @pure fn unwrap_or_zero(v: Option[Int]) -> Int {
                return match v {
                    some(x) => x,
                    none    => 0,
                }
            }
            """);
        var some = i.callFunction("unwrap_or_zero",
                List.of(new com.aion.interpreter.AionValue.SomeVal(new com.aion.interpreter.AionValue.IntVal(7))));
        assertThat(((com.aion.interpreter.AionValue.IntVal) some).value()).isEqualTo(7);

        var none = i.callFunction("unwrap_or_zero",
                List.of(new com.aion.interpreter.AionValue.NoneVal()));
        assertThat(((com.aion.interpreter.AionValue.IntVal) none).value()).isEqualTo(0);
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    @Test void list_creation_and_len() {
        Interpreter i = run("""
            @pure fn list_len() -> Int {
                let xs = [10, 20, 30]
                return xs.len()
            }
            """);
        var v = i.callFunction("list_len", List.of());
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(3);
    }

    @Test void for_loop_accumulates() {
        Interpreter i = run("""
            @pure fn sum() -> Int {
                let xs = [1, 2, 3, 4, 5]
                mut acc = 0
                for x in xs {
                    acc = acc + x
                }
                return acc
            }
            """);
        var v = i.callFunction("sum", List.of());
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(15);
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────

    @Test void pipeline_operator() {
        Interpreter i = run("""
            @pure fn double(x: Int) -> Int { return x * 2 }
            @pure fn inc(x: Int)    -> Int { return x + 1 }
            @pure fn piped()        -> Int { return 3 |> double |> inc }
            """);
        var v = i.callFunction("piped", List.of());
        assertThat(((com.aion.interpreter.AionValue.IntVal) v).value()).isEqualTo(7);
    }

    // ── Result propagation ────────────────────────────────────────────────────

    @Test void result_ok_propagates() {
        Interpreter i = run("""
            @pure fn divide(a: Float, b: Float) -> Result[Float, Str] {
                if b == 0.0 { return err("zero") }
                return ok(a / b)
            }
            @pure fn safe_half(n: Float) -> Result[Float, Str] {
                let v = divide(a: n, b: 2.0)?
                return ok(v)
            }
            """);
        var v = i.callFunction("safe_half",
                List.of(new com.aion.interpreter.AionValue.FloatVal(10.0)));
        assertThat(v).isInstanceOf(com.aion.interpreter.AionValue.OkVal.class);
        assertThat(((com.aion.interpreter.AionValue.FloatVal)
                ((com.aion.interpreter.AionValue.OkVal) v).inner()).value()).isEqualTo(5.0);
    }
}

