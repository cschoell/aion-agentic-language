package com.aion;

import com.aion.bytecode.Bytecode;
import com.aion.bytecode.BytecodeCompiler;
import com.aion.bytecode.BytecodeVM;
import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests that load every {@code .aion} script from
 * {@code src/main/resources} and run it through <em>both</em> backends
 * (tree-walking interpreter and bytecode VM), supplying a canned stdin
 * where the script calls {@code input()}.
 *
 * <p>Expected output lines are derived from actually running the scripts;
 * any change to a script that alters its output must be reflected here.
 */
class ResourceScriptE2ETest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Read a classpath resource to a UTF-8 string. */
    private static String resource(String name) throws Exception {
        try (InputStream is = ResourceScriptE2ETest.class
                .getClassLoader().getResourceAsStream(name)) {
            Objects.requireNonNull(is, "Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Run {@code source} through the tree-walking interpreter with the given
     * stdin lines joined by {@code \n}.  Returns trimmed stdout lines.
     */
    private List<String> runInterpreter(String source, String stdin) {
        var parsed = AionFrontend.parseString(source, "<e2e-interp>");
        assertThat(parsed.errors()).as("parse errors").isEmpty();

        var baos    = new ByteArrayOutputStream();
        var origOut = System.out;
        var origIn  = System.in;
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        try {
            var interp = new Interpreter();
            interp.loadModule(parsed.module());
            interp.callFunction("main", List.of());
        } finally {
            System.setOut(origOut);
            System.setIn(origIn);
        }
        return splitLines(baos.toString(StandardCharsets.UTF_8));
    }

    /**
     * Compile {@code source} with the bytecode compiler and run in the VM
     * with the given stdin.  Returns trimmed stdout lines.
     */
    private List<String> runBytecode(String source, String stdin) {
        var parsed = AionFrontend.parseString(source, "<e2e-bc>");
        assertThat(parsed.errors()).as("parse errors").isEmpty();

        Bytecode bc = new BytecodeCompiler().compile(parsed.module());

        var baos    = new ByteArrayOutputStream();
        var origOut = System.out;
        var origIn  = System.in;
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        try {
            new BytecodeVM().run(bc);
        } finally {
            System.setOut(origOut);
            System.setIn(origIn);
        }
        return splitLines(baos.toString(StandardCharsets.UTF_8));
    }

    /** Split captured output into lines, stripping the trailing empty entry. */
    private static List<String> splitLines(String raw) {
        // Normalise line endings
        raw = raw.replace("\r\n", "\n").replace("\r", "\n");
        if (raw.endsWith("\n")) raw = raw.substring(0, raw.length() - 1);
        if (raw.isEmpty()) return List.of();
        return List.of(raw.split("\n", -1));
    }

    // ── hello.aion ────────────────────────────────────────────────────────────

    private static final List<String> HELLO_EXPECTED = List.of(
            "Hello from Aion v1!",
            "",
            "Positive: 7",
            "Tag:      hello-aion",
            "",
            "divide(20, 4) = 5",
            "",
            "double_evens([1..6]) = [4, 8, 12]",
            "",
            "1",
            "2",
            "Fizz",
            "4",
            "Buzz",
            "Fizz",
            "7",
            "8",
            "Fizz",
            "Buzz",
            "11",
            "Fizz",
            "13",
            "14",
            "FizzBuzz",
            "range: min=1, max=9",
            "",
            "scale_score(8, 10) = 80",
            "",
            "Vec2 after set: (1.5, 2.5)"
    );

    @Test void hello_interpreter() throws Exception {
        assertThat(runInterpreter(resource("hello.aion"), "")).isEqualTo(HELLO_EXPECTED);
    }

    @Test void hello_bytecode() throws Exception {
        assertThat(runBytecode(resource("hello.aion"), "")).isEqualTo(HELLO_EXPECTED);
    }

    // ── bytecode-demo.aion ────────────────────────────────────────────────────

    private static final List<String> BYTECODE_DEMO_EXPECTED = List.of(
            "Hello from Aion bytecode!",
            "Broke out at i = 4",
            "odd: 1",
            "odd: 3",
            "odd: 5",
            "odd: 7",
            "5! = 120",
            "6 * 7 = 42",
            "Squares: [1, 4, 9, 16, 25]",
            "Evens:   [2, 4]",
            "Tripled: [3, 6, 9, 12, 15]",
            "safe_div(20, 4) = 5",
            "split_at([1..6], 3): lo=3, hi=3",
            "abs_clamp(-7) = 7",
            "abs_clamp(4)  = 4",
            "counter 'hits' = 42",
            "MAX_BYTE 0xFF        = 255",
            "FLAGS    0b1010_1010 = 170",
            "UNIX_RWX 0o755       = 493",
            "MILLION  1_000_000   = 1000000",
            "0xDEAD = 57005, 0xDEAD / 0x10 = 3562",
            "p + q = 7",
            "sum(1..5) = 15",
            "sum(1..=10) = 55",
            "digits 0..5 = [0, 1, 2, 3, 4]",
            "even sum 0..10 = 20",
            "Vec2 destructure: x=3, y=4",
            "tuple destructure: cx=10, cy=20",
            "pair_sum = 606"
    );

    @Test void bytecode_demo_interpreter() throws Exception {
        assertThat(runInterpreter(resource("bytecode-demo.aion"), ""))
                .isEqualTo(BYTECODE_DEMO_EXPECTED);
    }

    @Test void bytecode_demo_bytecode() throws Exception {
        assertThat(runBytecode(resource("bytecode-demo.aion"), ""))
                .isEqualTo(BYTECODE_DEMO_EXPECTED);
    }

    // ── sample.aion ───────────────────────────────────────────────────────────

    private static final List<String> SAMPLE_EXPECTED = List.of(
            "3 + 4 = 7",
            "Hello, Aion!",
            "Age 10 is a child",
            "Age 35 is an adult",
            "",
            "Circle area:    78.53975",
            "Rectangle area: 24.0",
            "Colour: red",
            "",
            "10.0 / 3.0 = 3.3333333333333335",
            "Caught: division by zero",
            "",
            "PositiveInt p = 42",
            "ShortStr tag  = aion-agent-v1",
            "",
            "Squares:   [1, 4, 9, 16, 25]",
            "Above 3:   [4, 5]",
            "Tripled:   [3, 6, 9, 12, 15]",
            "",
            "min = 1, max = 9",
            "coord len = 2",
            "",
            "award_point: 5 + correct = 6, then wrong = 6",
            "",
            "point after mutation: x=3.0, y=4.0",
            "",
            "clamp_score(7, 10) = 7",
            "",
            "Even sum (2+4, skip 3, stop at 6): 6",
            "Pipeline 3 >> double >> inc = 7",
            "",
            "Found Alice, who is a adult",
            "Eve not found"
    );

    @Test void sample_interpreter() throws Exception {
        assertThat(runInterpreter(resource("sample.aion"), "")).isEqualTo(SAMPLE_EXPECTED);
    }

    @Test void sample_bytecode() throws Exception {
        assertThat(runBytecode(resource("sample.aion"), "")).isEqualTo(SAMPLE_EXPECTED);
    }

    // ── qa-demo.aion ─────────────────────────────────────────────────────────

    private static final String QA_BORDER = "+======================================+";

    private static final List<String> QA_HEADER = List.of(
            QA_BORDER,
            "|        Aion Quiz  --  3 Questions    |",
            QA_BORDER,
            "",
            "Type 'quit' at any question to exit early.",
            ""
    );

    @Test void qa_demo_all_correct_interpreter() throws Exception {
        var out = runInterpreter(resource("qa-demo.aion"), "4\nParis\n42\n");
        assertThat(out).isEqualTo(List.of(
                QA_BORDER,
                "|        Aion Quiz  --  3 Questions    |",
                QA_BORDER,
                "",
                "Type 'quit' at any question to exit early.",
                "",
                "Q1: What is 2 + 2?",
                "  [CORRECT]",
                "",
                "Q2: What is the capital of France?",
                "  [CORRECT]",
                "",
                "Q3: What is 7 * 6?",
                "  [CORRECT]",
                "",
                QA_BORDER,
                "  PERFECT SCORE! You got 3 / 3.",
                QA_BORDER
        ));
    }

    @Test void qa_demo_all_correct_bytecode() throws Exception {
        var out = runBytecode(resource("qa-demo.aion"), "4\nParis\n42\n");
        assertThat(out).isEqualTo(List.of(
                QA_BORDER,
                "|        Aion Quiz  --  3 Questions    |",
                QA_BORDER,
                "",
                "Type 'quit' at any question to exit early.",
                "",
                "Q1: What is 2 + 2?",
                "  [CORRECT]",
                "",
                "Q2: What is the capital of France?",
                "  [CORRECT]",
                "",
                "Q3: What is 7 * 6?",
                "  [CORRECT]",
                "",
                QA_BORDER,
                "  PERFECT SCORE! You got 3 / 3.",
                QA_BORDER
        ));
    }

    @Test void qa_demo_mixed_answers_interpreter() throws Exception {
        // Q1 correct (4), Q2 wrong (Berlin), Q3 correct (42) → "Good job! 2/3"
        var out = runInterpreter(resource("qa-demo.aion"), "4\nBerlin\n42\n");
        assertThat(out).contains(
                "  [CORRECT]",
                "  [WRONG]  The answer is Paris.",
                "  [CORRECT]",
                "  Good job! You got 2 / 3."
        );
        assertThat(out.getLast()).isEqualTo(QA_BORDER);
    }

    @Test void qa_demo_mixed_answers_bytecode() throws Exception {
        var out = runBytecode(resource("qa-demo.aion"), "4\nBerlin\n42\n");
        assertThat(out).contains(
                "  [CORRECT]",
                "  [WRONG]  The answer is Paris.",
                "  [CORRECT]",
                "  Good job! You got 2 / 3."
        );
        assertThat(out.getLast()).isEqualTo(QA_BORDER);
    }

    @Test void qa_demo_quit_early_interpreter() throws Exception {
        // Typing "quit" at Q1 skips remaining questions and prints 0/3
        var out = runInterpreter(resource("qa-demo.aion"), "quit\n");
        assertThat(out).contains("Q1: What is 2 + 2?");
        assertThat(out).doesNotContain("Q2: What is the capital of France?");
        assertThat(out).noneMatch(l -> l.contains("[CORRECT]") || l.contains("[WRONG]"));
    }

    @Test void qa_demo_quit_early_bytecode() throws Exception {
        var out = runBytecode(resource("qa-demo.aion"), "quit\n");
        assertThat(out).contains("Q1: What is 2 + 2?");
        assertThat(out).doesNotContain("Q2: What is the capital of France?");
        assertThat(out).noneMatch(l -> l.contains("[CORRECT]") || l.contains("[WRONG]"));
    }

    @Test void qa_demo_all_wrong_interpreter() throws Exception {
        var out = runInterpreter(resource("qa-demo.aion"), "1\nLondon\n7\n");
        assertThat(out.stream().filter(l -> l.contains("[WRONG]")).count()).isEqualTo(3);
        assertThat(out).anyMatch(l -> l.contains("0 / 3"));
    }

    @Test void qa_demo_all_wrong_bytecode() throws Exception {
        var out = runBytecode(resource("qa-demo.aion"), "1\nLondon\n7\n");
        assertThat(out.stream().filter(l -> l.contains("[WRONG]")).count()).isEqualTo(3);
        assertThat(out).anyMatch(l -> l.contains("0 / 3"));
    }

    // ── agent-tools.aion ─────────────────────────────────────────────────────

    private static final String AGENT_BORDER = "+======================================+";

    @Test void agent_tools_all_correct_interpreter() throws Exception {
        var out = runInterpreter(resource("agent-tools.aion"), "5\nParis\n42\n");
        assertThat(out).isEqualTo(List.of(
                AGENT_BORDER,
                "|   Aion Agent-Safe Quiz  (mode: interp)|",
                AGENT_BORDER,
                "",
                "Type 'quit' at any question to exit early.",
                "",
                "Q1: What is 10 / 2?",
                "  [CORRECT]  (verified via safe_div: 5)",
                "",
                "Q2: What is the capital of France?",
                "  [CORRECT]",
                "",
                "Q3: What is 7 * 6?",
                "  [CORRECT]",
                "",
                AGENT_BORDER,
                "  PERFECT SCORE!  You got 3 / 3.",
                AGENT_BORDER
        ));
    }

    @Test void agent_tools_all_correct_bytecode() throws Exception {
        var out = runBytecode(resource("agent-tools.aion"), "5\nParis\n42\n");
        assertThat(out).isEqualTo(List.of(
                AGENT_BORDER,
                "|   Aion Agent-Safe Quiz  (mode: interp)|",
                AGENT_BORDER,
                "",
                "Type 'quit' at any question to exit early.",
                "",
                "Q1: What is 10 / 2?",
                "  [CORRECT]  (verified via safe_div: 5)",
                "",
                "Q2: What is the capital of France?",
                "  [CORRECT]",
                "",
                "Q3: What is 7 * 6?",
                "  [CORRECT]",
                "",
                AGENT_BORDER,
                "  PERFECT SCORE!  You got 3 / 3.",
                AGENT_BORDER
        ));
    }

    @Test void agent_tools_all_wrong_interpreter() throws Exception {
        var out = runInterpreter(resource("agent-tools.aion"), "3\nLondon\n6\n");
        assertThat(out.stream().filter(l -> l.contains("[WRONG]")).count()).isEqualTo(3);
        assertThat(out).anyMatch(l -> l.contains("0 / 3"));
    }

    @Test void agent_tools_all_wrong_bytecode() throws Exception {
        var out = runBytecode(resource("agent-tools.aion"), "3\nLondon\n6\n");
        assertThat(out.stream().filter(l -> l.contains("[WRONG]")).count()).isEqualTo(3);
        assertThat(out).anyMatch(l -> l.contains("0 / 3"));
    }

    @Test void agent_tools_quit_early_interpreter() throws Exception {
        var out = runInterpreter(resource("agent-tools.aion"), "quit\n");
        assertThat(out).contains("Q1: What is 10 / 2?");
        assertThat(out).doesNotContain("Q2: What is the capital of France?");
        assertThat(out).noneMatch(l -> l.contains("[CORRECT]") || l.contains("[WRONG]"));
    }

    @Test void agent_tools_quit_early_bytecode() throws Exception {
        var out = runBytecode(resource("agent-tools.aion"), "quit\n");
        assertThat(out).contains("Q1: What is 10 / 2?");
        assertThat(out).doesNotContain("Q2: What is the capital of France?");
        assertThat(out).noneMatch(l -> l.contains("[CORRECT]") || l.contains("[WRONG]"));
    }
}

