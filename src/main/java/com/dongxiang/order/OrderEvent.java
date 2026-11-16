package com.dongxiang.order;
public record OrderEvent(String orderId, long voucherId, long userId) {}
