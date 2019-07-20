package unimelb.bitbox;

import unimelb.bitbox.jobs.BlockingTimeoutJob;
import unimelb.bitbox.jobs.TimeoutJob;

import java.util.concurrent.DelayQueue;

/**
 * This class monitor timeout jobs using a delay queue. Once a thread is
 * timeout, it interrupt that thread.
 * @see TimeoutJob
 */
public class TimeoutMonitor implements Runnable {
    public TimeoutMonitor(DelayQueue<TimeoutJob> queue) {
        delayQueue = queue;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                TimeoutJob job = delayQueue.take();
                job.execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DelayQueue<TimeoutJob> delayQueue;
}
