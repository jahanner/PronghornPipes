package com.ociweb.json.encode;

import com.ociweb.json.encode.function.*;
import com.ociweb.json.JSONType;
import com.ociweb.json.template.StringTemplateBuilder;

public class JSONArray<T, P extends JSONComplete, N> implements JSONComplete {
    private final JSONBuilder<T> builder;
    private final ArrayIteratorFunction<T, N> iterator;
    private final P owner;
    private final int depth;

    JSONArray(StringTemplateBuilder<T> scripts, JSONKeywords keywords, ArrayIteratorFunction<T, N> iterator, P owner, int depth) {
        this.iterator = iterator;
        this.depth = depth;
        this.builder = new JSONBuilder<>(scripts, keywords, depth);
        this.owner = owner;
    }

    @Override
    public void complete() {
        builder.endArray();
        owner.complete();
    }

    // TODO: all other element types
    // TODO create an Array hosted version of JSONArray and JSONObject to pass index int[]

    public P constantNull() {
        builder.addNull(iterator);
        this.complete();
        return owner;
    }

    public P integer(IterLongFunction<T, N> func) {
        builder.addInteger(iterator, func);
        this.complete();
        return owner;
    }

    public P integer(IterLongFunction<T, N> func, JSONType encode) {
        builder.addInteger(iterator, func, encode);
        this.complete();
        return owner;
    }

    public P integerNull(IterNullableLongFunction<T, N> func) {
        builder.addInteger(iterator, func);
        this.complete();
        return owner;
    }

    public P integerNull(IterNullableLongFunction<T, N> func, JSONType encode) {
        builder.addInteger(iterator, func, encode);
        this.complete();
        return owner;
    }
}