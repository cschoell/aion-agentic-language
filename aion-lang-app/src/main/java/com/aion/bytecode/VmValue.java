package com.aion.bytecode;

import java.util.List;
import java.util.Map;

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
        VmValue.ListVal, VmValue.MapVal {

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
}

