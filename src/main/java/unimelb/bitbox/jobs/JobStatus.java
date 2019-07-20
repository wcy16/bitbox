package unimelb.bitbox.jobs;

/**
 * Return status of a job.
 */
public enum JobStatus {
    SUCCESS,
    SUCCESS_WITH_ACTION,
    CONNECTION_FAILED,
    FAIL,
    FAIL_WITH_ACTION,
    EXIT,
}