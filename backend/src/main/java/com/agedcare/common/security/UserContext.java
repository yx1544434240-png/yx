package com.agedcare.common.security;

public class UserContext {
    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public static void setUser(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser getUser() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getId() : null;
    }

    public static String getUserRole() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getRole() : null;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
