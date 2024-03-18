package eu.ows.util;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class FetchItemQueue {

    final BlockingDeque<FetchItem> queue;

    private final AtomicInteger inProgress = new AtomicInteger();

    private final int maxThreads;

    public FetchItemQueue(final int maxThreads, final int maxQueueSize) {
        this.maxThreads = maxThreads;
        this.queue = new LinkedBlockingDeque<>(maxQueueSize);
    }

    public int size() {
        return queue.size();
    }

    public boolean addFetchItem(final FetchItem fetchItem) {
        return queue.offer(fetchItem);
    }

    public FetchItem getFetchItem() {
        if (inProgress.get() >= maxThreads) {
            return null;
        }

        final FetchItem fetchItem = queue.pollFirst();
        if (fetchItem != null) {
            inProgress.incrementAndGet();
        }

        return fetchItem;
    }

    public void finishFetchItem(final FetchItem fetchItem) {
        if (fetchItem != null) {
            inProgress.decrementAndGet();
        }
    }
}
