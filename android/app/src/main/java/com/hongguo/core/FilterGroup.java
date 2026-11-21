package com.hongguo.core;

import java.util.ArrayList;
import java.util.List;

/** A filter row returned by the category selector panel. */
public final class FilterGroup {
    public final String type;
    public final String label;
    public final List<FilterOption> options = new ArrayList<>();

    public FilterGroup(String type, String label) {
        this.type = type;
        this.label = label;
    }
}
