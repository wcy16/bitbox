package unimelb.bitbox.jobs;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * detect if a thread is blocking for a given amount of time
 */
public class BlockingTimeoutJob extends TimeoutJob {
    public static long DEFAULT_DELAY_TIME = 10000;

    public BlockingTimeoutJob(Thread thread, long delay) {
        this.thread = thread;
        time = System.currentTimeMillis() + delay;
    }

    @Override
    public void execute() {
        thread.interrupt();
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(time - System.currentTimeMillis(),TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass())
            return false;

        BlockingTimeoutJob job = (BlockingTimeoutJob)o;
        return this.thread == job.thread;
    }

    public Thread thread;
}
