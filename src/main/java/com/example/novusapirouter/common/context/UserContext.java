package com.example.novusapirouter.common.context;

public class UserContext {
    private static final ThreadLocal<Long> CURRENT_UID = new ThreadLocal<>();

    public static void setUid(Long uid) {
        CURRENT_UID.set(uid);
    }

    public static Long getUid() {
        return CURRENT_UID.get();
    }

    public static void clear() {
        CURRENT_UID.remove();
    }
}
