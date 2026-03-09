package com.aion;

import com.aion.interpreter.Interpreter;
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
 * Tests for the tree-walking interpreter's Q&A / input() support.
 */
class InterpreterQaTest {

    private List<String> run(String source, String stdinInput) {
        var result = AionFrontend.parseString(source, "<test>");
        assertThat(result.errors()).as("parse errors").isEmpty();

        // Capture stdout
        var baos = new ByteArrayOutputStream();
        var captured = new PrintStream(baos, true, StandardCharsets.UTF_8);
        var origOut = System.out;
        System.setOut(captured);

        // Provide stdin
        InputStream stdinStream = new ByteArrayInputStream(
                stdinInput.getBytes(StandardCharsets.UTF_8));
        var origIn = System.in;
        System.setIn(stdinStream);

        try {
            Interpreter interp = new Interpreter();
            interp.loadModule(result.module());
            interp.callFunction("main", List.of());
        } finally {
            System.setOut(origOut);
            System.setIn(origIn);
            captured.flush();
        }

        String output = baos.toString(StandardCharsets.UTF_8);
        String[] lines = output.split("\r?\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty())
            return List.of(lines).subList(0, lines.length - 1);
        return List.of(lines);
    }

    @Test
    void interpreter_input_returns_typed_line() {
        var out = run("""
                @io fn main() -> Unit {
                    let answer = input()
                    print(answer)
                }
                """, "hello from stdin\n");
        assertThat(out).containsExactly("hello from stdin");
    }

    @Test
    void interpreter_qa_correct_answer() {
        var out = run("""
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
    void interpreter_qa_wrong_answer() {
        var out = run("""
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
    void interpreter_qa_full_quiz_all_correct() {
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
        var out = run(source, "4\nParis\n42\n");
        assertThat(out).containsExactly(
                "Q1: 2 + 2?",             "[CORRECT]",
                "Q2: Capital of France?", "[CORRECT]",
                "Q3: 7 * 6?",             "[CORRECT]",
                "PERFECT");
    }

    // ── input() must be called exactly once even when passed as an arg ──────

    @Test
    void input_called_once_when_passed_to_user_fn() {
        // Regression: the old tryCallBuiltin path evaluated args speculatively
        // before deciding whether the callee was a builtin, causing input() to
        // consume an extra line from stdin. The second call would read "" and
        // fail any @requires(raw != "") guard.
        var out = run("""
                @pure fn echo(s: Str) -> Str { return s }
                @io fn main() -> Unit {
                    let a = echo(input())
                    print(a)
                }
                """, "hello\n");
        assertThat(out).containsExactly("hello");
    }

    @Test
    void input_called_once_per_question_across_three_questions() {
        // Each call to sanitise(input()) must consume exactly one stdin line.
        var out = run("""
                @pure fn id(s: Str) -> Str { return s }
                @io fn main() -> Unit {
                    let a1 = id(input())
                    let a2 = id(input())
                    let a3 = id(input())
                    print(a1)
                    print(a2)
                    print(a3)
                }
                """, "first\nsecond\nthird\n");
        assertThat(out).containsExactly("first", "second", "third");
    }

    // ── @on_fail wraps @requires violations ──────────────────────────────────

    @Test
    void on_fail_wraps_requires_violation() {
        var out = run("""
                @tool
                @pure
                @requires(raw_input != "")
                @on_fail("Input was empty.")
                fn sanitise(raw_input: Str) -> Str {
                    return raw_input.trim()
                }
                @io fn main() -> Unit {
                    let r = sanitise("")
                    match r {
                        err(e) => print("caught"),
                        ok(v)  => print("ok"),
                    }
                }
                """, "");
        assertThat(out).containsExactly("caught");
    }

    @Test
    void on_fail_passes_through_on_success() {
        var out = run("""
                @tool
                @pure
                @requires(raw_input != "")
                @on_fail("Input was empty.")
                fn sanitise(raw_input: Str) -> Str {
                    return raw_input.trim()
                }
                @io fn main() -> Unit {
                    let r = sanitise("  hello  ")
                    print(r)
                }
                """, "");
        assertThat(out).containsExactly("hello");
    }

    // ── Lambda pipeline (list of lambdas called in a for-loop) ───────────────

    @Test
    void lambda_list_called_in_for_loop() {
        var out = run("""
                @pure fn normalise(answer: Str) -> Str {
                    let steps = [
                        fn(s: Str) -> Str { return s.trim() },
                        fn(s: Str) -> Str { return s.lower() },
                    ]
                    mut out = answer
                    for step in steps {
                        out = step(out)
                    }
                    return out
                }
                @io fn main() -> Unit {
                    print(normalise("  HELLO  "))
                    print(normalise("Paris"))
                    print(normalise("  42  "))
                }
                """, "");
        assertThat(out).containsExactly("hello", "paris", "42");
    }

    // ── Combined: sanitise(input()) + normalise pipeline ──────────────────────

    @Test
    void sanitise_input_then_normalise_reads_stdin_once() {
        // Full regression test for the agent-tools.aion pattern:
        //   let a = sanitise(input())
        //   if normalise(a) == "5" { ... }
        // input() must be called exactly once; normalise must receive "5", not "".
        var out = run("""
                @tool
                @pure
                @requires(raw_input != "")
                @on_fail("Input was empty.")
                fn sanitise(raw_input: Str) -> Str {
                    let trimmed = raw_input.trim()
                    return trimmed
                }
                @pure fn normalise(answer: Str) -> Str {
                    let steps = [
                        fn(s: Str) -> Str { return s.trim() },
                        fn(s: Str) -> Str { return s.lower() },
                    ]
                    mut out = answer
                    for step in steps {
                        out = step(out)
                    }
                    return out
                }
                @io fn main() -> Unit {
                    let a1 = sanitise(input())
                    if normalise(a1) == "5" {
                        print("correct")
                    } else {
                        print("wrong: ${a1}")
                    }
                }
                """, "5\n");
        assertThat(out).containsExactly("correct");
    }

    @Test
    void interpreter_qa_full_quiz_all_wrong() {
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
        var out = run(source, "1\nLondon\n7\n");
        assertThat(out).containsExactly(
                "Q1: 2 + 2?",             "[WRONG]",
                "Q2: Capital of France?", "[WRONG]",
                "Q3: 7 * 6?",             "[WRONG]",
                "NOT PERFECT");
    }
}

