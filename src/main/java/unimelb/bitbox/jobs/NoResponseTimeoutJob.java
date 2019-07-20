package unimelb.bitbox.jobs;

import java.util.concurrent.BlockingQueue;

/**
 * If we execute a job in udp mode but get no response, we
 * execute it again
 */
public class NoResponseTimeoutJob extends TimeoutJob {
    private Job job;
    private BlockingQueue<Job> queue;
    private String name;
    private int id;

    public static long DEFAULT_DELAY_TIME = 1000;

    public NoResponseTimeoutJob(int id, Job job, long delay, BlockingQueue<Job> queue) {
        this.job = job;
        time = System.currentTimeMillis() + delay;
        this.queue = queue;
        this.id = id;
    }

    @Override
    public void execute() {
        queue.add(job);
    }

    @Override
    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || this.getClass() != o.getClass())
//            return false;
//
//        NoResponseTimeoutJob job = (NoResponseTimeoutJob)o;
//        return this.name.equals(job.name);
        if (o instanceof ID) {
            ID id = (ID)o;
            return this.id == id.id;
        }

        return Integer.valueOf(id).equals(o);
    }

    public static class ID {
        private int id;
        public ID(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NoResponseTimeoutJob) {
                NoResponseTimeoutJob job = (NoResponseTimeoutJob)obj;
                return id == job.id;
            }
            return false;
        }
    }
}
