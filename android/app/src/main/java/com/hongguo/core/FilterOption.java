package com.hongguo.core;

/** One selectable value in a filter row (or a rank tab). */
public final class FilterOption {
    public final String id;
    public final String label;

    public FilterOption(String id, String label) {
        this.id = id;
        this.label = label;
    }
}
