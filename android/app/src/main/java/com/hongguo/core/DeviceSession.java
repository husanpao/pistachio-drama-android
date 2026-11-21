package com.hongguo.core;

/** Plain anonymous device identity; no Android dependency. */
public final class DeviceSession {
    public final String deviceId;
    public final String installId;
    public final String cdid;
    public final String openudid;

    public DeviceSession(String deviceId, String installId, String cdid, String openudid) {
        this.deviceId = deviceId;
        this.installId = installId;
        this.cdid = cdid;
        this.openudid = openudid;
    }

    public boolean valid() {
        return deviceId != null && !deviceId.isEmpty()
                && installId != null && !installId.isEmpty();
    }
}
