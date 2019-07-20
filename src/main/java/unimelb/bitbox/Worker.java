package unimelb.bitbox;

import unimelb.bitbox.jobs.Job;
import unimelb.bitbox.jobs.JobStatus;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * Worker processing jobs in the queue. The worker will execute the execute() method in Job object.
 * If the return value is FAIL_WITH_ACTION, the nextJob Job will be added to queue.
 */
public class Worker implements Runnable {
    private static Logger log = Logger.getLogger(Worker.class.getName());
    public Worker(BlockingQueue<Job> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                Job job = queue.take();
                // we have maximum tries for a job
                if (job.tries == 0) {
                    log.info(job.getClass().toString() + " maximum tries reached");
                    job.fail();
                    continue;
                }
                else if (job.tries > 0)
                    job.tries--;
                JobStatus status = job.execute();
                if (status == JobStatus.FAIL_WITH_ACTION || status == JobStatus.SUCCESS_WITH_ACTION)
                    queue.add(job.nextJob);
                else if (status == JobStatus.FAIL) {
                    queue.add(job);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BlockingQueue<Job> queue;

}
