package com.aion.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lexical scope — linked list of frames, innermost first.
 */
public final class Environment {

    private final Map<String, AionValue> bindings = new HashMap<>();
    private final Environment parent;

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public static Environment global() { return new Environment(null); }

    public Environment child() { return new Environment(this); }

    /** Define a new binding in THIS scope. */
    public void define(String name, AionValue value) {
        bindings.put(name, value);
    }

    /** Reassign an existing binding anywhere in the chain. */
    public void assign(String name, AionValue value) {
        if (bindings.containsKey(name)) {
            bindings.put(name, value);
        } else if (parent != null) {
            parent.assign(name, value);
        } else {
            throw new AionRuntimeException("Undefined variable: " + name);
        }
    }

    /** Look up a binding in the chain. */
    public AionValue lookup(String name) {
        if (bindings.containsKey(name)) return bindings.get(name);
        if (parent != null) return parent.lookup(name);
        throw new AionRuntimeException("Undefined variable: " + name);
    }

    public Optional<AionValue> tryLookup(String name) {
        if (bindings.containsKey(name)) return Optional.of(bindings.get(name));
        if (parent != null) return parent.tryLookup(name);
        return Optional.empty();
    }
}

