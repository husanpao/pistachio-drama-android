package com.hongguo.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Persist the anonymous device identity used for search. */
public final class DeviceSessionStore {
    private static final String PREFS = "hongguo_device";
    private static final String DEVICE_ID = "device_id";
    private static final String INSTALL_ID = "install_id";
    private static final String CDID = "cdid";
    private static final String OPENUDID = "openudid";

    private DeviceSessionStore() {
    }

    public static DeviceSession load(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(DEVICE_ID, "");
        String installId = prefs.getString(INSTALL_ID, "");
        if (deviceId == null || deviceId.isEmpty() || installId == null || installId.isEmpty()) {
            return null;
        }
        return new DeviceSession(deviceId, installId,
                prefs.getString(CDID, ""), prefs.getString(OPENUDID, ""));
    }

    public static void save(Context context, DeviceSession session) {
        if (context == null || session == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(DEVICE_ID, session.deviceId)
                .putString(INSTALL_ID, session.installId)
                .putString(CDID, session.cdid == null ? "" : session.cdid)
                .putString(OPENUDID, session.openudid == null ? "" : session.openudid)
                .apply();
    }
}
