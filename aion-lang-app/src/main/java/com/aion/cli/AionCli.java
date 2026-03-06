package com.aion.cli;

import com.aion.ast.Node;
import com.aion.interpreter.AionRuntimeException;
import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name        = "aion",
    description = "Aion language runner",
    mixinStandardHelpOptions = true,
    version     = "0.1.0",
    subcommands = { AionCli.Run.class, AionCli.Check.class, AionCli.Repl.class,
                    AionCli.Compile.class, AionCli.Describe.class }
)
public class AionCli implements Callable<Integer> {

    public static void main(String[] args) {
        System.exit(new CommandLine(new AionCli()).execute(args));
    }

    @Override public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    // ── aion run <file> [fn] ─────────────────────────────────────────────────

    @Command(name = "run", description = "Run an Aion source file")
    static class Run implements Callable<Integer> {

        @Parameters(index = "0", description = "Source file (.aion)")
        Path file;

        @Parameters(index = "1", defaultValue = "main",
                    description = "Entry-point function (default: main)")
        String entryPoint;

        @Override public Integer call() {
            try {
                var result = AionFrontend.parseFile(file);
                if (result.hasErrors()) {
                    result.errors().forEach(System.err::println);
                    return 1;
                }
                Interpreter interp = new Interpreter();
                interp.loadModule(result.module());
                interp.callFunction(entryPoint, java.util.List.of());
                return 0;
            } catch (AionRuntimeException e) {
                System.err.println("Runtime error: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── aion check <file> ────────────────────────────────────────────────────

    @Command(name = "check", description = "Parse and type-check an Aion source file without running it")
    static class Check implements Callable<Integer> {

        @Parameters(index = "0", description = "Source file (.aion)")
        Path file;

        @Override public Integer call() {
            try {
                var result = AionFrontend.parseFile(file);
                if (result.hasErrors()) {
                    result.errors().forEach(System.err::println);
                    return 1;
                }
                System.out.println("OK — " + file.getFileName());
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── aion repl ────────────────────────────────────────────────────────────

    @Command(name = "repl", description = "Start an interactive Aion REPL")
    static class Repl implements Callable<Integer> {

        @Override public Integer call() {
            System.out.println("Aion REPL v0.1.0  (type :quit to exit)");
            Interpreter interp = new Interpreter();
            java.util.Scanner sc = new java.util.Scanner(System.in);
            StringBuilder buf = new StringBuilder();
            int depth = 0;

            while (true) {
                System.out.print(depth > 0 ? "... " : ">>> ");
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine();
                if (line.equals(":quit") || line.equals(":q")) break;

                for (char c : line.toCharArray()) {
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                }
                buf.append(line).append("\n");

                if (depth <= 0) {
                    String src = buf.toString().trim();
                    buf.setLength(0);
                    depth = 0;
                    if (src.isBlank()) continue;

                    // Wrap in a throwaway module
                    String wrapped = "@pure fn _repl() -> Unit {\n" + src + "\n}";
                    var res = AionFrontend.parseString(wrapped, "<repl>");
                    if (res.hasErrors()) {
                        res.errors().forEach(System.err::println);
                    } else {
                        try {
                            interp.loadModule(res.module());
                            interp.callFunction("_repl", java.util.List.of());
                        } catch (AionRuntimeException e) {
                            System.err.println("Runtime error: " + e.getMessage());
                        }
                    }
                }
            }
            return 0;
        }
    }

    // ── aion compile <file> ─────────────────────────────────────────────────

    @Command(name = "compile", description = "Compile an Aion source file to bytecode and run it")
    static class Compile implements Callable<Integer> {

        @Parameters(index = "0", description = "Source file (.aion)")
        Path file;

        @Override public Integer call() {
            try {
                var result = com.aion.parser.AionFrontend.parseFile(file);
                if (result.hasErrors()) {
                    result.errors().forEach(System.err::println);
                    return 1;
                }
                var compiler = new com.aion.bytecode.BytecodeCompiler();
                var bytecode = compiler.compile(result.module());
                var vm = new com.aion.bytecode.BytecodeVM();
                vm.run(bytecode);
                System.out.flush();
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e);
                e.printStackTrace(System.err);
                return 1;
            }
        }
    }

    // ── aion describe <file> ─────────────────────────────────────────────────

    @Command(name = "describe",
             description = "Emit JSON tool descriptors for all @tool-annotated functions")
    static class Describe implements Callable<Integer> {

        @Parameters(index = "0", description = "Source file (.aion)")
        Path file;

        @Override public Integer call() {
            try {
                var result = AionFrontend.parseFile(file);
                if (result.hasErrors()) {
                    result.errors().forEach(System.err::println);
                    return 1;
                }
                System.out.println(buildToolDescriptors(result.module()));
                System.out.flush();
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private String buildToolDescriptors(Node.Module module) {
            var sb = new StringBuilder("[\n");
            boolean first = true;
            for (Node decl : module.decls()) {
                if (!(decl instanceof Node.FnDecl fn)) continue;
                boolean isTool = fn.annotations().stream()
                        .anyMatch(a -> a instanceof Node.Annotation.Tool);
                if (!isTool) continue;

                if (!first) sb.append(",\n");
                first = false;

                // Extract describe text from first stmt in body
                String docString = fn.body().stmts().stream()
                        .filter(s -> s instanceof Node.Stmt.Describe)
                        .map(s -> ((Node.Stmt.Describe) s).text())
                        .findFirst().orElse("");

                // Extract @requires / @ensures / @timeout
                List<String> requires = fn.annotations().stream()
                        .filter(a -> a instanceof Node.Annotation.Requires)
                        .map(a -> exprText(((Node.Annotation.Requires) a).condition()))
                        .toList();
                List<String> ensures = fn.annotations().stream()
                        .filter(a -> a instanceof Node.Annotation.Ensures)
                        .map(a -> exprText(((Node.Annotation.Ensures) a).condition()))
                        .toList();
                long timeout = fn.annotations().stream()
                        .filter(a -> a instanceof Node.Annotation.Timeout)
                        .mapToLong(a -> ((Node.Annotation.Timeout) a).millis())
                        .findFirst().orElse(-1L);
                boolean trusted   = fn.annotations().stream().anyMatch(a -> a instanceof Node.Annotation.TrustedAnn);
                boolean untrusted = fn.annotations().stream().anyMatch(a -> a instanceof Node.Annotation.UntrustedAnn);

                sb.append("  {\n");
                sb.append("    \"name\": \"").append(fn.name()).append("\",\n");
                sb.append("    \"description\": \"").append(escape(docString)).append("\",\n");
                sb.append("    \"parameters\": [\n");
                for (int i = 0; i < fn.params().size(); i++) {
                    var p = fn.params().get(i);
                    sb.append("      { \"name\": \"").append(p.name())
                      .append("\", \"type\": \"").append(typeStr(p.type())).append("\" }");
                    if (i < fn.params().size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("    ],\n");
                sb.append("    \"returns\": \"").append(typeStr(fn.returnType())).append("\",\n");
                sb.append("    \"requires\": ").append(jsonStringArray(requires)).append(",\n");
                sb.append("    \"ensures\": ").append(jsonStringArray(ensures)).append(",\n");
                sb.append("    \"timeout_ms\": ").append(timeout < 0 ? "null" : timeout).append(",\n");
                sb.append("    \"input_trust\": \"")
                  .append(trusted ? "trusted" : untrusted ? "untrusted" : "unspecified")
                  .append("\"\n");
                sb.append("  }");
            }
            sb.append("\n]");
            return sb.toString();
        }

        private String exprText(Node.Expr e) { return exprStr(e); }

        /** Render an expression as clean source-like text. */
        private String exprStr(Node.Expr e) {
            return switch (e) {
                case Node.Expr.BinOp b  -> exprStr(b.left()) + " " + opStr(b.op()) + " " + exprStr(b.right());
                case Node.Expr.UnaryOp u -> opStr(u.op()) + exprStr(u.operand());
                case Node.Expr.VarRef v  -> v.name();
                case Node.Expr.IntLit i  -> String.valueOf(i.value());
                case Node.Expr.FloatLit f -> String.valueOf(f.value());
                case Node.Expr.StrLit s  -> "\"" + s.value() + "\"";
                case Node.Expr.BoolLit b -> String.valueOf(b.value());
                case Node.Expr.NoneLit ignored -> "none";
                case Node.Expr.FnCall fc -> fc.name() + "(...)";
                default -> e.toString();
            };
        }

        private String opStr(Node.BinOpKind op) {
            return switch (op) {
                case ADD -> "+"; case SUB -> "-"; case MUL -> "*";
                case DIV -> "/"; case MOD -> "%";
                case EQ  -> "=="; case NEQ -> "!=";
                case LT  -> "<";  case LE  -> "<=";
                case GT  -> ">";  case GE  -> ">=";
                case AND -> "and"; case OR -> "or";
            };
        }

        private String opStr(Node.UnaryOpKind op) {
            return switch (op) { case NEG -> "-"; case NOT -> "not "; };
        }

        /** Render a TypeRef as clean Aion type syntax. */
        private String typeStr(Node.TypeRef t) {
            return switch (t) {
                case Node.TypeRef.IntT    ignored -> "Int";
                case Node.TypeRef.FloatT  ignored -> "Float";
                case Node.TypeRef.BoolT   ignored -> "Bool";
                case Node.TypeRef.StrT    ignored -> "Str";
                case Node.TypeRef.UnitT   ignored -> "Unit";
                case Node.TypeRef.OptionT o -> "Option[" + typeStr(o.inner()) + "]";
                case Node.TypeRef.ResultT r -> "Result[" + typeStr(r.ok()) + ", " + typeStr(r.err()) + "]";
                case Node.TypeRef.ListT   l -> "List[" + typeStr(l.element()) + "]";
                case Node.TypeRef.MapT    m -> "Map[" + typeStr(m.key()) + ", " + typeStr(m.value()) + "]";
                case Node.TypeRef.Named   n -> n.name();
                case Node.TypeRef.FnT     f -> "Fn";
            };
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "");
        }

        private String jsonStringArray(List<String> items) {
            if (items.isEmpty()) return "[]";
            var sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escape(items.get(i))).append("\"");
            }
            return sb.append("]").toString();
        }
    }
}

