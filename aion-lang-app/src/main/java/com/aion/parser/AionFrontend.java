package com.aion.parser;

import com.aion.ast.Node;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public static ParseResult parseString(String source, String sourceName) {
        return parse(CharStreams.fromString(source), sourceName);
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

