package com.aion.interpreter;

/** Thrown on runtime errors in Aion programs. */
public class AionRuntimeException extends RuntimeException {
    public AionRuntimeException(String message) { super(message); }
    public AionRuntimeException(String message, Throwable cause) { super(message, cause); }
}

