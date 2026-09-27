package com.feiyu.dbconnector.audit;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界审计队列：满了直接丢弃并计数，绝不反压查询路径。
 * ponytail: 丢弃 + 1s 批量刷盘，上限单节点 ~百 QPS；上量后审计外送或换独立存储
 */
@Component
public class AuditEventQueue {

    private final BlockingQueue<AuditEvent> queue = new ArrayBlockingQueue<>(1000);
    private final AtomicLong dropped = new AtomicLong();

    public void offer(AuditEvent event) {
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
        }
    }

    public List<AuditEvent> drain() {
        List<AuditEvent> out = new ArrayList<>();
        queue.drainTo(out);
        return out;
    }

    public long droppedCount() {
        return dropped.get();
    }
}
