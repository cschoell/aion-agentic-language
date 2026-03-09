package com.aion.bytecode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime value types for the Aion bytecode VM.
 *
 * Primitive Java types are used for Int (Long), Float (Double), Bool (Boolean),
 * Str (String), and None (null).  These sealed types cover the composite cases.
 */
public sealed interface VmValue permits
        VmValue.SomeVal, VmValue.NoneVal,
        VmValue.OkVal, VmValue.ErrVal,
        VmValue.EnumVal, VmValue.RecordVal,
        VmValue.ListVal, VmValue.MapVal,
        VmValue.TupleVal,
        VmValue.LambdaVal,
        VmValue.FutureVal {

    /** Option::Some(inner) */
    record SomeVal(Object inner) implements VmValue {}
    /** Option::None */
    record NoneVal()             implements VmValue {}
    /** Result::Ok(inner) */
    record OkVal(Object inner)   implements VmValue {}
    /** Result::Err(inner) */
    record ErrVal(Object inner)  implements VmValue {}
    /** Enum variant with optional payload: typeName::variant(payload…) */
    record EnumVal(String typeName, String variant, List<Object> payload) implements VmValue {}
    /** Record instance: typeName { field → value } */
    record RecordVal(String typeName, Map<String, Object> fields) implements VmValue {}
    /** List value (mutable). */
    record ListVal(java.util.ArrayList<Object> elements) implements VmValue {}
    /** Map value (mutable). */
    record MapVal(java.util.LinkedHashMap<Object, Object> entries) implements VmValue {}
    /** Tuple value: fixed-size, heterogeneous, immutable sequence. */
    record TupleVal(java.util.List<Object> elements) implements VmValue {}
    /**
     * A first-class function / lambda value.
     * {@code address} is the VM instruction index of the first instruction of the body.
     * {@code params} lists parameter names in order for named-argument binding.
     */
    record LambdaVal(int address, java.util.List<String> params) implements VmValue {}
    /** A Future[T] value — result of an async function call. */
    record FutureVal(CompletableFuture<Object> future) implements VmValue {}
}

