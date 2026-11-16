package com.dongxiang.auth;

public final class CurrentUser {
    private static final ThreadLocal<Long> USER = new ThreadLocal<>();
    private CurrentUser() {}
    public static void set(long id) { USER.set(id); }
    public static Long get() { return USER.get(); }
    public static long require() { Long id = USER.get(); if (id == null) throw new IllegalStateException("请先登录"); return id; }
    public static void clear() { USER.remove(); }
}
