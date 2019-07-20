package unimelb.bitbox.jobs;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * base class for timeout jobs
 */
public abstract class TimeoutJob implements Delayed {
    protected static Logger log = Logger.getLogger(TimeoutJob.class.getName());

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(time - System.currentTimeMillis(),TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    public abstract void execute();

    protected long time;
    public static final long DEFAULT_DELAY_TIME = 10000;
}
