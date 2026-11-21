package com.hongguo.core;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bottom-sheet category filter with wrapping single-select chip rows. */
public final class FilterSheet {
    public interface Listener {
        void onApply(Map<String, String> selection);
    }

    private static final class Binding {
        final String type;
        final List<TextView> chips = new ArrayList<>();
        final List<String> ids = new ArrayList<>();

        Binding(String type) {
            this.type = type;
        }

        void sync(String selected) {
            for (int i = 0; i < chips.size(); i++) {
                Ui.selectChip(chips.get(i), ids.get(i).equals(selected));
            }
        }
    }

    private FilterSheet() {
    }

    public static void show(Context context, Map<String, String> current,
                            List<FilterGroup> groups, Listener listener) {
        if (groups == null || groups.isEmpty()) {
            Ui.toast(context, "筛选条件加载中，请稍后再试");
            return;
        }
        Dialog dialog = new Dialog(context, R.style.Theme_Hongguo_Sheet);
        LinearLayout root = Ui.column(context);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(Theme.dp(context, 18), Theme.dp(context, 14),
                Theme.dp(context, 18), Theme.dp(context, 14));

        LinearLayout bar = Ui.row(context);
        bar.addView(Ui.medium(context, "筛选", 17, Theme.TEXT));
        View spacer = new View(context);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = Ui.medium(context, "关闭", 13, Theme.TEXT_TERTIARY);
        close.setPadding(Theme.dp(context, 10), Theme.dp(context, 4),
                Theme.dp(context, 2), Theme.dp(context, 4));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close);
        root.addView(bar);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.column(context);
        content.setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 8));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Map<String, String> draft = new HashMap<>();
        if (current != null) {
            for (Map.Entry<String, String> entry : current.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    draft.put(entry.getKey(), entry.getValue());
                }
            }
        }
        List<Binding> bindings = new ArrayList<>();
        for (FilterGroup group : groups) {
            TextView label = Ui.medium(context, group.label, 14, Theme.TEXT);
            label.setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 8));
            content.addView(label);

            FlowLayout flow = new FlowLayout(context);
            Binding binding = new Binding(group.type);
            String selected = draft.get(group.type);
            addChip(context, flow, binding, "", "全部", selected == null || selected.isEmpty(),
                    value -> {
                        draft.remove(group.type);
                        binding.sync("");
                    });
            for (FilterOption option : group.options) {
                boolean active = option.id.equals(selected);
                addChip(context, flow, binding, option.id, option.label, active, value -> {
                    draft.put(group.type, value);
                    binding.sync(value);
                });
            }
            content.addView(flow);
            bindings.add(binding);
        }

        LinearLayout footer = Ui.row(context);
        footer.setPadding(0, Theme.dp(context, 12), 0, 0);
        TextView reset = Ui.medium(context, "重置", 14, Theme.TEXT_SECONDARY);
        reset.setGravity(Gravity.CENTER);
        reset.setBackgroundResource(R.drawable.bg_chip_normal);
        reset.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12));
        footer.addView(reset, new LinearLayout.LayoutParams(0,
                Theme.dp(context, 46), 1f));
        TextView apply = Ui.medium(context, "确定", 14, Color.WHITE);
        apply.setGravity(Gravity.CENTER);
        apply.setBackground(Theme.round(Theme.BRAND, Theme.dp(context, 23)));
        apply.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0,
                Theme.dp(context, 46), 2f);
        applyParams.leftMargin = Theme.dp(context, 10);
        footer.addView(apply, applyParams);
        root.addView(footer);

        reset.setOnClickListener(v -> {
            draft.clear();
            for (Binding binding : bindings) binding.sync("");
        });
        apply.setOnClickListener(v -> {
            if (listener != null) listener.onApply(draft);
            dialog.dismiss();
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (Ui.screenHeight(context) * 0.78));
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (Ui.screenHeight(context) * 0.78));
        }
    }

    private static void addChip(Context context, FlowLayout flow, Binding binding,
                                String id, String label, boolean selected,
                                final ValueCallback callback) {
        TextView chip = Ui.chip(context, label, selected);
        chip.setOnClickListener(v -> {
            Ui.selectChip(chip, true);
            callback.onValue(id);
        });
        flow.addView(chip);
        binding.chips.add(chip);
        binding.ids.add(id);
    }

    private interface ValueCallback {
        void onValue(String value);
    }
}
