package com.dongxiang.order;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic="dongxiang-orders", consumerGroup="dongxiang-order-consumers")
public class OrderConsumer implements RocketMQListener<OrderEvent> {
    private final OrderTransactions transactions; public OrderConsumer(OrderTransactions transactions) { this.transactions=transactions; }
    @Override public void onMessage(OrderEvent event) { transactions.create(event); }
}
