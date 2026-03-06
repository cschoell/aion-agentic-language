package com.aion;

import com.aion.bytecode.Bytecode;
import com.aion.bytecode.BytecodeCompiler;
import com.aion.bytecode.BytecodeVM;
import com.aion.bytecode.Instruction;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end tests for the bytecode compiler + VM pipeline:
 *   Aion source  →  parse  →  BytecodeCompiler  →  BytecodeVM  →  captured stdout
 */
class BytecodeCompilerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Compile + run with no stdin; return printed lines. */
    private List<String> run(String source) {
        return runWithInput(source, "");
    }

    /** Compile + run; feed {@code stdinLines} (newline-separated) as stdin. */
    private List<String> runWithInput(String source, String stdinInput) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();

        Bytecode bytecode = new BytecodeCompiler().compile(result.module());

        // Capture stdout
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream captured = new PrintStream(baos, true, StandardCharsets.UTF_8);
        PrintStream original = System.out;
        System.setOut(captured);

        // Provide stdin
        InputStream stdinStream = new ByteArrayInputStream(
                stdinInput.getBytes(StandardCharsets.UTF_8));
        InputStream originalIn = System.in;
        System.setIn(stdinStream);

        try {
            new BytecodeVM().run(bytecode);
        } finally {
            System.setOut(original);
            System.setIn(originalIn);
            captured.flush();
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        // Split on newlines, drop trailing empty entry
        String[] lines = output.split("\r?\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            return List.of(lines).subList(0, lines.length - 1);
        }
        return List.of(lines);
    }

    /** Compile source and return the raw instruction list. */
    private List<Instruction> compile(String source) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        return new BytecodeCompiler().compile(result.module()).instructions;
    }

    // ── Instruction emission tests ────────────────────────────────────────────


    @Test
    void emits_push_int_and_print_and_halt() {
        var instrs = compile("@pure fn main() -> Unit { print(42) }");
        // Layout: [Jump→main, main: PushInt, Print, Halt]
        assertThat(instrs.get(0)).isInstanceOf(Instruction.Jump.class);
        assertThat(instrs.get(1)).isInstanceOf(Instruction.PushInt.class);
        assertThat(((Instruction.PushInt) instrs.get(1)).value()).isEqualTo(42L);
        assertThat(instrs.get(2)).isInstanceOf(Instruction.Print.class);
        assertThat(instrs.get(3)).isInstanceOf(Instruction.Halt.class);
    }

    @Test
    void emits_push_str_and_print_and_halt() {
        var instrs = compile("@pure fn main() -> Unit { print(\"hello\") }");
        // Layout: [Jump→main, main: PushStr, Print, Halt]
        assertThat(instrs.get(1)).isInstanceOf(Instruction.PushStr.class);
        assertThat(((Instruction.PushStr) instrs.get(1)).value()).isEqualTo("hello");
    }

    @Test
    void emits_store_and_load_for_let() {
        var instrs = compile("@pure fn main() -> Unit { let x = 7 }");
        assertThat(instrs).anySatisfy(i ->
                assertThat(i).isInstanceOf(Instruction.Store.class));
    }

    @Test
    void emits_eq_for_comparison() {
        var instrs = compile("@pure fn main() -> Unit { let b = 1 == 1 }");
        assertThat(instrs).anySatisfy(i ->
                assertThat(i).isInstanceOf(Instruction.Eq.class));
    }

    @Test
    void emits_jump_if_false_for_if() {
        var instrs = compile("""
                @pure fn main() -> Unit {
                    if 1 == 1 {
                        print("yes")
                    }
                }
                """);
        assertThat(instrs).anySatisfy(i ->
                assertThat(i).isInstanceOf(Instruction.JumpIfFalse.class));
        assertThat(instrs).anySatisfy(i ->
                assertThat(i).isInstanceOf(Instruction.Jump.class));
    }

    // ── VM output tests ───────────────────────────────────────────────────────

    @Test
    void prints_integer_literal() {
        var out = run("@pure fn main() -> Unit { print(42) }");
        assertThat(out).containsExactly("42");
    }

    @Test
    void prints_string_literal() {
        var out = run("@pure fn main() -> Unit { print(\"hello world\") }");
        assertThat(out).containsExactly("hello world");
    }

    @Test
    void prints_float_literal() {
        var out = run("@pure fn main() -> Unit { print(3.14) }");
        assertThat(out).containsExactly("3.14");
    }

    @Test
    void prints_bool_literal() {
        var out = run("@pure fn main() -> Unit { print(true) }");
        assertThat(out).containsExactly("true");
    }

    @Test
    void prints_multiple_lines() {
        var out = run("""
                @pure fn main() -> Unit {
                    print("a")
                    print("b")
                    print("c")
                }
                """);
        assertThat(out).containsExactly("a", "b", "c");
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    @Test
    void arithmetic_add() {
        var out = run("@pure fn main() -> Unit { print(3 + 4) }");
        assertThat(out).containsExactly("7");
    }

    @Test
    void arithmetic_sub() {
        var out = run("@pure fn main() -> Unit { print(10 - 3) }");
        assertThat(out).containsExactly("7");
    }

    @Test
    void arithmetic_mul() {
        var out = run("@pure fn main() -> Unit { print(6 * 7) }");
        assertThat(out).containsExactly("42");
    }

    @Test
    void arithmetic_div() {
        var out = run("@pure fn main() -> Unit { print(84 / 2) }");
        assertThat(out).containsExactly("42");
    }

    @Test
    void arithmetic_mod() {
        var out = run("@pure fn main() -> Unit { print(10 % 3) }");
        assertThat(out).containsExactly("1");
    }

    @Test
    void unary_neg() {
        var out = run("@pure fn main() -> Unit { print(-5) }");
        assertThat(out).containsExactly("-5");
    }

    // ── Variables ─────────────────────────────────────────────────────────────

    @Test
    void let_binding_and_print() {
        var out = run("@pure fn main() -> Unit { let x = 99  print(x) }");
        assertThat(out).containsExactly("99");
    }

    @Test
    void mut_binding_then_reassign() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut score = 0
                    score = score + 1
                    score = score + 1
                    print(score)
                }
                """);
        assertThat(out).containsExactly("2");
    }

    // ── Conditionals ─────────────────────────────────────────────────────────

    @Test
    void if_true_branch_taken() {
        var out = run("""
                @pure fn main() -> Unit {
                    if 1 == 1 {
                        print("yes")
                    } else {
                        print("no")
                    }
                }
                """);
        assertThat(out).containsExactly("yes");
    }

    @Test
    void if_false_branch_taken() {
        var out = run("""
                @pure fn main() -> Unit {
                    if 1 == 2 {
                        print("yes")
                    } else {
                        print("no")
                    }
                }
                """);
        assertThat(out).containsExactly("no");
    }

    @Test
    void nested_if_else() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut x = 2
                    if x == 1 {
                        print("one")
                    } else {
                        if x == 2 {
                            print("two")
                        } else {
                            print("other")
                        }5
                    }
                }
                """);
        assertThat(out).containsExactly("two");
    }

    // ── While loop ────────────────────────────────────────────────────────────

    @Test
    void while_loop_counts_up() {
        var out = run("""
                @pure fn main() -> Unit {
                    mut i = 0
                    while i < 3 {
                        print(i)
                        i = i + 1
                    }
                }
                """);
        assertThat(out).containsExactly("0", "1", "2");
    }

    // ── Input / output ────────────────────────────────────────────────────────

    @Test
    void input_returns_typed_line() {
        var out = runWithInput("""
                @io fn main() -> Unit {
                    let answer = input()
                    print(answer)
                }
                """, "hello from stdin\n");
        assertThat(out).containsExactly("hello from stdin");
    }

    // ── Method calls ──────────────────────────────────────────────────────────

    @Test
    void str_trim() {
        var out = run("@pure fn main() -> Unit { let s = \"  hello  \"  print(s.trim()) }");
        assertThat(out).containsExactly("hello");
    }

    @Test
    void str_upper_and_lower() {
        var out = run("""
                @pure fn main() -> Unit {
                    print("hello".upper())
                    print("WORLD".lower())
                }
                """);
        assertThat(out).containsExactly("HELLO", "world");
    }

    @Test
    void str_len() {
        var out = run("@pure fn main() -> Unit { print(\"hello\".len()) }");
        assertThat(out).containsExactly("5");
    }

    @Test
    void str_contains() {
        var out = run("""
                @pure fn main() -> Unit {
                    if "hello world".contains("world") {
                        print("yes")
                    } else {
                        print("no")
                    }
                }
                """);
        assertThat(out).containsExactly("yes");
    }

    // ── User-defined function calls ───────────────────────────────────────────

    @Test
    void user_fn_call_returns_value() {
        var out = run("""
                @pure fn double(x: Int) -> Int { return x * 2 }
                @pure fn main() -> Unit { print(double(21)) }
                """);
        assertThat(out).containsExactly("42");
    }

    @Test
    void user_fn_multiple_args() {
        var out = run("""
                @pure fn add(a: Int, b: Int) -> Int { return a + b }
                @pure fn main() -> Unit { print(add(10, 32)) }
                """);
        assertThat(out).containsExactly("42");
    }

    @Test
    void user_fn_called_multiple_times() {
        var out = run("""
                @pure fn square(n: Int) -> Int { return n * n }
                @pure fn main() -> Unit {
                    print(square(3))
                    print(square(4))
                    print(square(5))
                }
                """);
        assertThat(out).containsExactly("9", "16", "25");
    }

    // ── Agent-safety features ─────────────────────────────────────────────────

    @Test
    void assert_passes_when_condition_true() {
        var out = run("""
                @pure fn main() -> Unit {
                    assert 1 == 1, "should not fail"
                    print("ok")
                }
                """);
        assertThat(out).containsExactly("ok");
    }

    @Test
    void assert_throws_when_condition_false() {
        assertThatThrownBy(() -> run("""
                @pure fn main() -> Unit {
                    assert 1 == 2, "one is not two"
                }
                """))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("one is not two");
    }

    // ── agent-tools.aion scenario ─────────────────────────────────────────────

    @Test
    void user_fn_with_requires_style_assert() {
        // Models the safe_div tool: assert b != 0 before dividing
        var out = run("""
                @pure fn safe_div(a: Int, b: Int) -> Int {
                    assert b != 0, "divisor must not be zero"
                    return a / b
                }
                @pure fn main() -> Unit {
                    print(safe_div(10, 2))
                }
                """);
        assertThat(out).containsExactly("5");
    }

    @Test
    void user_fn_with_bool_param_branching() {
        var out = run("""
                @pure fn award(score: Int, correct: Bool) -> Int {
                    if correct { return score + 1 }
                    else       { return score }
                }
                @pure fn main() -> Unit {
                    mut s = 0
                    s = award(s, true)
                    s = award(s, false)
                    s = award(s, true)
                    print(s)
                }
                """);
        assertThat(out).containsExactly("2");
    }



    @Test
    void qa_correct_answer_prints_correct() {
        var out = runWithInput("""
                @io fn main() -> Unit {
                    print("Q: 2 + 2?")
                    let a = input()
                    if a == "4" {
                        print("Correct!")
                    } else {
                        print("Wrong.")
                    }
                }
                """, "4\n");
        assertThat(out).containsExactly("Q: 2 + 2?", "Correct!");
    }

    @Test
    void qa_wrong_answer_prints_wrong() {
        var out = runWithInput("""
                @io fn main() -> Unit {
                    print("Q: 2 + 2?")
                    let a = input()
                    if a == "4" {
                        print("Correct!")
                    } else {
                        print("Wrong.")
                    }
                }
                """, "5\n");
        assertThat(out).containsExactly("Q: 2 + 2?", "Wrong.");
    }

    @Test
    void qa_full_quiz_all_correct() {
        String source = """
                @io fn main() -> Unit {
                    mut score = 0
                    print("Q1: 2 + 2?")
                    let a1 = input()
                    if a1 == "4" { print("[CORRECT]")  score = score + 1 }
                    else         { print("[WRONG]") }
                    print("Q2: Capital of France?")
                    let a2 = input()
                    if a2 == "Paris" { print("[CORRECT]")  score = score + 1 }
                    else             { print("[WRONG]") }
                    print("Q3: 7 * 6?")
                    let a3 = input()
                    if a3 == "42" { print("[CORRECT]")  score = score + 1 }
                    else          { print("[WRONG]") }
                    if score == 3 { print("PERFECT") }
                    else          { print("NOT PERFECT") }
                }
                """;
        var out = runWithInput(source, "4\nParis\n42\n");
        assertThat(out).containsExactly(
                "Q1: 2 + 2?",    "[CORRECT]",
                "Q2: Capital of France?", "[CORRECT]",
                "Q3: 7 * 6?",    "[CORRECT]",
                "PERFECT");
    }

    @Test
    void qa_full_quiz_all_wrong() {
        String source = """
                @io fn main() -> Unit {
                    mut score = 0
                    print("Q1: 2 + 2?")
                    let a1 = input()
                    if a1 == "4" { print("[CORRECT]")  score = score + 1 }
                    else         { print("[WRONG]") }
                    print("Q2: Capital of France?")
                    let a2 = input()
                    if a2 == "Paris" { print("[CORRECT]")  score = score + 1 }
                    else             { print("[WRONG]") }
                    print("Q3: 7 * 6?")
                    let a3 = input()
                    if a3 == "42" { print("[CORRECT]")  score = score + 1 }
                    else          { print("[WRONG]") }
                    if score == 3 { print("PERFECT") }
                    else          { print("NOT PERFECT") }
                }
                """;
        var out = runWithInput(source, "1\nLondon\n7\n");
        assertThat(out).containsExactly(
                "Q1: 2 + 2?",    "[WRONG]",
                "Q2: Capital of France?", "[WRONG]",
                "Q3: 7 * 6?",    "[WRONG]",
                "NOT PERFECT");
    }
}

