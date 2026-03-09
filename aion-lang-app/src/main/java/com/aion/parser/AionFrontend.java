package com.aion.parser;

import com.aion.ast.Node;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Entry point for parsing Aion source text into an AST.
 *
 * <pre>{@code
 *   ParseResult result = AionFrontend.parseFile(path);
 *   if (result.hasErrors()) { ... }
 *   Node.Module module = result.module();
 * }</pre>
 */
public final class AionFrontend {

    private AionFrontend() {}

    public record ParseResult(Node.Module module, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    public static ParseResult parseFile(Path path) throws IOException {
        CharStream stream = CharStreams.fromPath(path);
        return parse(stream, path.getFileName().toString());
    }

    /**
     * Parse {@code path} and recursively resolve all {@code import} declarations,
     * merging every imported module's declarations (in import order, depth-first)
     * into a single flat {@link Node.Module}.  Cycles are detected and reported
     * as parse errors rather than causing infinite recursion.
     */
    public static ParseResult parseFileWithImports(Path path) throws IOException {
        List<String> errors = new ArrayList<>();
        List<Node> allDecls = new ArrayList<>();
        Set<Path> visited = new LinkedHashSet<>();
        loadRecursive(path.toAbsolutePath().normalize(), errors, allDecls, visited);
        if (!errors.isEmpty()) return new ParseResult(null, errors);
        Node.Module merged = new Node.Module(allDecls, "<merged>");
        return new ParseResult(merged, errors);
    }

    private static void loadRecursive(Path path, List<String> errors,
                                      List<Node> allDecls, Set<Path> visited) throws IOException {
        if (visited.contains(path)) {
            errors.add("Cyclic import detected: " + path);
            return;
        }
        visited.add(path);

        ParseResult result = parseFile(path);
        if (result.hasErrors()) { errors.addAll(result.errors()); return; }

        Path dir = path.getParent();
        for (Node decl : result.module().decls()) {
            if (decl instanceof Node.ImportDecl imp) {
                // Resolve  import a.b.c  →  <dir>/a/b/c.aion
                String rel = String.join("/", imp.path()) + ".aion";
                Path imported = (dir != null ? dir.resolve(rel) : Path.of(rel)).normalize();
                if (!imported.toFile().exists()) {
                    errors.add("Cannot find imported module '" + String.join(".", imp.path())
                            + "' at " + imported);
                    return;
                }
                loadRecursive(imported, errors, allDecls, visited);
            } else {
                allDecls.add(decl);
            }
        }
    }

    public static ParseResult parseString(String source, String sourceName) {
        return parse(CharStreams.fromString(source), sourceName);
    }

    /**
     * Parse a single expression (used for string interpolation holes).
     * Wraps the expression in a minimal function to reuse the full parser.
     */
    public static Node.Expr parseExprString(String exprSrc, int lineHint) {
        // Wrap in a throwaway module so we can reuse the full parser/builder
        String wrapped = "@pure fn __expr__() -> Unit { let __v__ = " + exprSrc + " }";
        ParseResult r = parseString(wrapped, "<interp>");
        if (r.hasErrors() || r.module() == null)
            throw new AionParseException("Invalid interpolation expression: " + exprSrc,
                    new Node.Pos(lineHint, 0));
        // Extract the let-binding value
        Node.FnDecl fn = (Node.FnDecl) r.module().decls().getFirst();
        return ((Node.Stmt.Let) fn.body().stmts().getFirst()).value();
    }

    private static ParseResult parse(CharStream stream, String sourceName) {
        List<String> errors = new ArrayList<>();

        AionLexer lexer = new AionLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?,?> r, Object o, int line, int col,
                                    String msg, RecognitionException e) {
                errors.add("[%d:%d] Lexer error: %s".formatted(line, col, msg));
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        AionParser parser = new AionParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?,?> r, Object o, int line, int col,
                                    String msg, RecognitionException e) {
                errors.add("[%d:%d] Parse error: %s".formatted(line, col, msg));
            }
        });

        ParseTree tree = parser.module();
        if (!errors.isEmpty()) {
            return new ParseResult(null, errors);
        }

        try {
            AstBuilder builder = new AstBuilder(sourceName);
            Node.Module module = (Node.Module) builder.visit(tree);
            return new ParseResult(module, errors);
        } catch (AionParseException ex) {
            errors.add(ex.getMessage());
            return new ParseResult(null, errors);
        }
    }
}

