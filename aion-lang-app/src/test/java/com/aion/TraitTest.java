package com.aion;

import com.aion.bytecode.Bytecode;
import com.aion.bytecode.BytecodeCompiler;
import com.aion.bytecode.BytecodeVM;
import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the trait / impl system.
 *
 *   trait Describable { fn describe(self: Self) -> Str }
 *   impl  Describable for Point { fn describe(self: Point) -> Str { ... } }
 *   point.describe()
 */
class TraitTest {

    // ── Interpreter helpers ───────────────────────────────────────────────────

    private List<String> interp(String source) {
        var result = AionFrontend.parseString(source, "<trait-test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        var baos = new ByteArrayOutputStream();
        var ps   = new PrintStream(baos, true, StandardCharsets.UTF_8);
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

    // ── Bytecode helpers ──────────────────────────────────────────────────────

    private String bc(String source) {
        var result = AionFrontend.parseString(source, "<trait-bc-test>");
        assertThat(result.errors()).as("parse errors").isEmpty();
        Bytecode bytecode = new BytecodeCompiler().compile(result.module());
        var bos   = new ByteArrayOutputStream();
        var saved = System.out;
        System.setOut(new PrintStream(bos));
        try { new BytecodeVM().run(bytecode); } finally { System.setOut(saved); }
        return bos.toString().replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    // ── Trait declaration + impl + method call (interpreter) ─────────────────

    static final String POINT_PROGRAM = """
            trait Describable {
                fn to_str(self: Str) -> Str
            }

            type Point = { x: Int, y: Int }

            impl Describable for Point {
                @pure fn to_str(self: Point) -> Str {
                    return "Point"
                }
            }

            @pure fn main() -> Unit {
                let p = Point { x: 3, y: 4 }
                print(p.to_str())
            }
            """;

    @Test void trait_method_call_interpreter() {
        assertThat(interp(POINT_PROGRAM)).containsExactly("Point");
    }

    @Test void trait_method_call_bytecode() {
        assertThat(bc(POINT_PROGRAM)).isEqualTo("Point");
    }

    // ── Impl method receives self fields (interpreter) ────────────────────────

    static final String AREA_PROGRAM = """
            trait HasArea {
                fn area(self: Str) -> Int
            }

            type Rect = { width: Int, height: Int }

            impl HasArea for Rect {
                @pure fn area(self: Rect) -> Int {
                    return self.width * self.height
                }
            }

            @pure fn main() -> Unit {
                let r = Rect { width: 6, height: 7 }
                print(r.area())
            }
            """;

    @Test void impl_method_accesses_self_fields_interpreter() {
        assertThat(interp(AREA_PROGRAM)).containsExactly("42");
    }

    @Test void impl_method_accesses_self_fields_bytecode() {
        assertThat(bc(AREA_PROGRAM)).isEqualTo("42");
    }

    // ── Multiple impl methods on same type ────────────────────────────────────

    static final String MULTI_METHOD_PROGRAM = """
            trait Shape {
                fn area(self: Str) -> Int
                fn perimeter(self: Str) -> Int
            }

            type Square = { side: Int }

            impl Shape for Square {
                @pure fn area(self: Square) -> Int {
                    return self.side * self.side
                }
                @pure fn perimeter(self: Square) -> Int {
                    return 4 * self.side
                }
            }

            @pure fn main() -> Unit {
                let s = Square { side: 5 }
                print(s.area())
                print(s.perimeter())
            }
            """;

    @Test void multiple_impl_methods_interpreter() {
        assertThat(interp(MULTI_METHOD_PROGRAM)).containsExactly("25", "20");
    }

    @Test void multiple_impl_methods_bytecode() {
        assertThat(bc(MULTI_METHOD_PROGRAM)).isEqualTo("25\n20");
    }

    // ── Two types implementing the same trait ─────────────────────────────────

    static final String TWO_TYPES_PROGRAM = """
            trait Greet {
                fn greet(self: Str) -> Str
            }

            type Dog = { name: Str }
            type Cat = { name: Str }

            impl Greet for Dog {
                @pure fn greet(self: Dog) -> Str {
                    return "Woof! I am a dog"
                }
            }

            impl Greet for Cat {
                @pure fn greet(self: Cat) -> Str {
                    return "Meow! I am a cat"
                }
            }

            @pure fn main() -> Unit {
                let d = Dog { name: "Rex" }
                let c = Cat { name: "Whiskers" }
                print(d.greet())
                print(c.greet())
            }
            """;

    @Test void two_types_same_trait_interpreter() {
        assertThat(interp(TWO_TYPES_PROGRAM)).containsExactly("Woof! I am a dog", "Meow! I am a cat");
    }

    @Test void two_types_same_trait_bytecode() {
        assertThat(bc(TWO_TYPES_PROGRAM)).isEqualTo("Woof! I am a dog\nMeow! I am a cat");
    }

    // ── Impl method with extra parameters ────────────────────────────────────

    static final String EXTRA_PARAM_PROGRAM = """
            trait Scalable {
                fn scale(self: Str, factor: Int) -> Int
            }

            type Box = { value: Int }

            impl Scalable for Box {
                @pure fn scale(self: Box, factor: Int) -> Int {
                    return self.value * factor
                }
            }

            @pure fn main() -> Unit {
                let b = Box { value: 10 }
                print(b.scale(factor: 3))
            }
            """;

    @Test void impl_method_extra_param_interpreter() {
        assertThat(interp(EXTRA_PARAM_PROGRAM)).containsExactly("30");
    }

    @Test void impl_method_extra_param_bytecode() {
        assertThat(bc(EXTRA_PARAM_PROGRAM)).isEqualTo("30");
    }
}
