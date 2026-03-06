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

