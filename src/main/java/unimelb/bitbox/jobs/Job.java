package unimelb.bitbox.jobs;

import java.util.logging.Logger;

/**
 * Job that can be done by workers.
 */
public abstract class Job {
    protected static Logger log = Logger.getLogger(Job.class.getName());

    public static int DEFAULT_TRIES = -1;

    public Job nextJob = null;

    /**
     * for execute the job
     * @return
     */
    public abstract JobStatus execute();

    /**
     * execute when job tries == 0
     */
    public abstract void fail();

    public int tries = DEFAULT_TRIES;
}

