package com.yupi.springbootinit.job;

import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RetryQueueService {
    private final Queue<Long> retryQueue = new ConcurrentLinkedQueue<>();

    public void offer(Long id) {
        retryQueue.offer(id);
    }

    public Long poll() {
        return retryQueue.poll();
    }

    public boolean isEmpty() {
        return retryQueue.isEmpty();
    }
}