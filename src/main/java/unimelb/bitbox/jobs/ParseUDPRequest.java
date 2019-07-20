package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.FileSystemManager;

import java.net.SocketAddress;

/**
 * parse UDP request. Since UDP request is sent in a single package, we do not need to assemble it like in
 * parseTCPRequest.
 */
public class ParseUDPRequest extends Job {
    private String request;
    private FileSystemManager manager;
    private SocketAddress addr;
    public ParseUDPRequest(SocketAddress addr, FileSystemManager manager, String request) {
        this.request = request;
        this.manager = manager;
        this.addr = addr;
    }

    @Override
    public JobStatus execute() {

        ServerMain.getServerMain().updateTimeoutJob(new Pulse.ID(addr),
                new Pulse(addr, Pulse.DEFAULT_DELAY_TIME));

        String json = request.split("\n")[0];
//        log.info(json);
        // check if registered
        if (ServerMain.getServerMain().checkRegister(addr)) {
            nextJob = new Response(addr, manager, json);
        }
        else {
            nextJob = new RegisterUDPIn(json, addr);
        }
        return JobStatus.SUCCESS_WITH_ACTION;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(addr);
    }
}
