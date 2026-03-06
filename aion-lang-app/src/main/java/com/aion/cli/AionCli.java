package com.aion.cli;

import com.aion.interpreter.AionRuntimeException;
import com.aion.interpreter.Interpreter;
import com.aion.parser.AionFrontend;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name        = "aion",
    description = "Aion language runner",
    mixinStandardHelpOptions = true,
    version     = "0.1.0",
    subcommands = { AionCli.Run.class, AionCli.Check.class, AionCli.Repl.class, AionCli.Compile.class }
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
}

