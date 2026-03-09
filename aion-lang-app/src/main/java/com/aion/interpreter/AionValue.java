package com.aion.interpreter;

import com.aion.ast.Node;
import com.aion.ast.Node.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Aion runtime value — a sealed type so the interpreter's pattern matches
 * are exhaustive and the compiler catches missing cases.
 */
public sealed interface AionValue permits
        AionValue.IntVal,
        AionValue.FloatVal,
        AionValue.BoolVal,
        AionValue.StrVal,
        AionValue.UnitVal,
        AionValue.NoneVal,
        AionValue.SomeVal,
        AionValue.OkVal,
        AionValue.ErrVal,
        AionValue.ListVal,
        AionValue.MapVal,
        AionValue.RecordVal,
        AionValue.EnumVal,
        AionValue.TupleVal,
        AionValue.FnVal,
        AionValue.FutureVal {

    record IntVal(long value)           implements AionValue { public String toString() { return String.valueOf(value); } }
    record FloatVal(double value)       implements AionValue { public String toString() { return String.valueOf(value); } }
    record BoolVal(boolean value)       implements AionValue { public String toString() { return String.valueOf(value); } }
    record StrVal(String value)         implements AionValue { public String toString() { return value; } }
    record UnitVal()                    implements AionValue { public String toString() { return "()"; } }
    record NoneVal()                    implements AionValue { public String toString() { return "none"; } }
    record SomeVal(AionValue inner)     implements AionValue { public String toString() { return "some(" + inner + ")"; } }
    record OkVal(AionValue inner)       implements AionValue { public String toString() { return "ok(" + inner + ")"; } }
    record ErrVal(AionValue inner)      implements AionValue { public String toString() { return "err(" + inner + ")"; } }
    record ListVal(List<AionValue> elements) implements AionValue {
        public String toString() { return elements.toString(); }
    }
    record MapVal(Map<AionValue, AionValue> entries) implements AionValue {
        public String toString() { return entries.toString(); }
    }
    record RecordVal(String typeName, Map<String, AionValue> fields) implements AionValue {
        public String toString() {
            return typeName + " { " + fields.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue()).reduce((a,b)->a+", "+b).orElse("") + " }";
        }
    }
    record EnumVal(String typeName, String variant, List<AionValue> payload) implements AionValue {
        public String toString() {
            return payload.isEmpty() ? typeName + "::" + variant
                    : typeName + "::" + variant + "(" + payload + ")";
        }
    }
    /** Tuple value: an ordered, fixed-size collection of heterogeneous values. */
    record TupleVal(List<AionValue> elements) implements AionValue {
        public String toString() {
            return "(" + elements.stream().map(Object::toString).reduce((a,b)->a+", "+b).orElse("") + ")";
        }
    }
    record FnVal(FnDecl decl, Environment closure) implements AionValue {
        public String toString() { return "<fn " + decl.name() + ">"; }
    }
    /** A Future[T] value backed by a Java CompletableFuture — result of an async fn call. */
    record FutureVal(CompletableFuture<AionValue> future) implements AionValue {
        public String toString() { return future.isDone() ? "Future(" + future.join() + ")" : "Future(<pending>)"; }
    }
}

