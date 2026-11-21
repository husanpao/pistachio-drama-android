package com.hongguo.core;

import java.util.ArrayList;
import java.util.List;

/** One page of a list endpoint plus its server cursors. */
public final class ListPage {
    public final List<Drama> items = new ArrayList<>();
    public boolean hasMore;
    public int nextOffset;
    public String sessionId = "";
    public String searchId = "";
    public String passback = "";
    public final List<FilterGroup> filters = new ArrayList<>();
    public final List<FilterOption> rankOptions = new ArrayList<>();
}
