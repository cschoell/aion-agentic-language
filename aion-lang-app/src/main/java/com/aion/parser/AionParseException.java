package com.aion.parser;

import com.aion.ast.Node.Pos;

/** Thrown when the AST builder encounters an unexpected parse tree shape. */
public class AionParseException extends RuntimeException {

    private final Pos pos;

    public AionParseException(String message, Pos pos) {
        super("[%d:%d] %s".formatted(pos.line(), pos.col(), message));
        this.pos = pos;
    }

    public Pos pos() { return pos; }
}

