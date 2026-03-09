package com.aion;

import com.aion.bytecode.Bytecode;
import com.aion.bytecode.BytecodeCompiler;
import com.aion.bytecode.BytecodeVM;
import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the module import system (feature #1).
 * Uses @TempDir to write real .aion files and verifies that
 * import declarations are resolved and merged correctly.
 */
class ModuleImportTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String runInterp(Path mainFile) throws IOException {
        var result = AionFrontend.parseFileWithImports(mainFile);
        assertThat(result.errors()).as("parse/import errors").isEmpty();
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        var orig = System.out;
        System.setOut(ps);
        try {
            var interp = new Interpreter();
            interp.loadModule(result.module());
            interp.callFunction("main", java.util.List.of());
        } finally {
            System.setOut(orig);
            ps.flush();
        }
        return baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n").strip();
    }

    private String runBytecode(Path mainFile) throws IOException {
        var result = AionFrontend.parseFileWithImports(mainFile);
        assertThat(result.errors()).as("parse/import errors").isEmpty();
        Bytecode bc = new BytecodeCompiler().compile(result.module());
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        var orig = System.out;
        System.setOut(ps);
        try { new BytecodeVM().run(bc); } finally { System.setOut(orig); ps.flush(); }
        return baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n").strip();
    }

    // ── Interpreter tests ─────────────────────────────────────────────────────

    @Test void import_function_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("math.aion"), """
                @pure fn double(x: Int) -> Int { return x * 2 }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import math
                @pure fn main() -> Unit { print(double(21)) }
                """);
        assertThat(runInterp(main)).isEqualTo("42");
    }

    @Test void import_const_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("constants.aion"), """
                const PI: Int = 3
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import constants
                @pure fn main() -> Unit { print(PI) }
                """);
        assertThat(runInterp(main)).isEqualTo("3");
    }

    @Test void import_transitive_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("base.aion"), """
                @pure fn greet(name: Str) -> Str { return "Hello, " + name }
                """);
        Files.writeString(dir.resolve("mid.aion"), """
                import base
                @pure fn shout(name: Str) -> Str { return greet(name) + "!" }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import mid
                @pure fn main() -> Unit { print(shout("Aion")) }
                """);
        assertThat(runInterp(main)).isEqualTo("Hello, Aion!");
    }

    @Test void import_missing_reports_error(@TempDir Path dir) throws IOException {
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import nonexistent
                @pure fn main() -> Unit { print(42) }
                """);
        var result = AionFrontend.parseFileWithImports(main);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors().getFirst()).contains("nonexistent");
    }

    // ── Bytecode tests ────────────────────────────────────────────────────────

    @Test void import_function_bytecode(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("math.aion"), """
                @pure fn triple(x: Int) -> Int { return x * 3 }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import math
                @pure fn main() -> Unit { print(triple(7)) }
                """);
        assertThat(runBytecode(main)).isEqualTo("21");
    }

    @Test void import_const_bytecode(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("limits.aion"), """
                const MAX: Int = 100
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import limits
                @pure fn main() -> Unit { print(MAX) }
                """);
        assertThat(runBytecode(main)).isEqualTo("100");
    }

    // ── Selective import tests ────────────────────────────────────────────────

    @Test void selective_import_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("utils.aion"), """
                @pure fn square(x: Int) -> Int { return x * x }
                @pure fn cube(x: Int) -> Int { return x * x * x }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import utils { square }
                @pure fn main() -> Unit { print(square(5)) }
                """);
        assertThat(runInterp(main)).isEqualTo("25");
    }

    @Test void selective_import_excludes_others_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("utils.aion"), """
                @pure fn square(x: Int) -> Int { return x * x }
                @pure fn cube(x: Int) -> Int { return x * x * x }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import utils { square }
                @pure fn main() -> Unit { print(square(4)) }
                """);
        // only square is imported; cube is not available but we don't call it
        assertThat(runInterp(main)).isEqualTo("16");
    }

    @Test void selective_import_multiple_names_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("math.aion"), """
                @pure fn add(a: Int, b: Int) -> Int { return a + b }
                @pure fn mul(a: Int, b: Int) -> Int { return a * b }
                @pure fn sub(a: Int, b: Int) -> Int { return a - b }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import math { add, mul }
                @pure fn main() -> Unit { print(add(3, mul(2, 4))) }
                """);
        assertThat(runInterp(main)).isEqualTo("11");
    }

    @Test void selective_import_const_interp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("consts.aion"), """
                const A: Int = 10
                const B: Int = 20
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import consts { A }
                @pure fn main() -> Unit { print(A) }
                """);
        assertThat(runInterp(main)).isEqualTo("10");
    }

    @Test void selective_import_bytecode(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("utils.aion"), """
                @pure fn double(x: Int) -> Int { return x * 2 }
                @pure fn triple(x: Int) -> Int { return x * 3 }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import utils { double }
                @pure fn main() -> Unit { print(double(9)) }
                """);
        assertThat(runBytecode(main)).isEqualTo("18");
    }

    @Test void import_transitive_bytecode(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("base.aion"), """
                @pure fn add(a: Int, b: Int) -> Int { return a + b }
                """);
        Files.writeString(dir.resolve("mid.aion"), """
                import base
                @pure fn add3(a: Int, b: Int, c: Int) -> Int { return add(add(a, b), c) }
                """);
        Path main = dir.resolve("main.aion");
        Files.writeString(main, """
                import mid
                @pure fn main() -> Unit { print(add3(1, 2, 3)) }
                """);
        assertThat(runBytecode(main)).isEqualTo("6");
    }
}
