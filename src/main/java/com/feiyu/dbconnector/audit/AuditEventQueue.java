package com.feiyu.dbconnector.audit;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
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