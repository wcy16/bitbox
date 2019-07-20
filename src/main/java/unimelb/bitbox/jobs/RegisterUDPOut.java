package unimelb.bitbox.jobs;

import unimelb.bitbox.Protocol;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;

/**
 * register udp out
 */
public class RegisterUDPOut extends Job {
    public static int generateId(SocketAddress addr) {
        return addr.hashCode();
    }

    public RegisterUDPOut(InetSocketAddress hostAddress, String host, int port) {
        this.host = host;
        this.port = port;
        this.hostAddress = hostAddress;
        possibles = new LinkedList<>();
        //tries = 10;
    }

    public RegisterUDPOut(InetSocketAddress hostAddress, String host, int port,
                          LinkedList<InetSocketAddress> possibles) {
        this.host = host;
        this.port = port;
        this.hostAddress = hostAddress;
        this.possibles = possibles;
    }

    private LinkedList<InetSocketAddress> possibles;

    @Override
    @SuppressWarnings("unchecked")
    public JobStatus execute() {
        if (hostAddress == null) {
            log.info("no more servers to connect to.");
            return JobStatus.FAIL;
        }

        // not to connect to self
        if (hostAddress.getAddress().isLoopbackAddress()
                && ServerMain.getServerMain().getPort() == hostAddress.getPort()) {
            log.info("do not connect to self");
            nextJob = new RegisterUDPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
        }

        try {
            // send handshake request
            Document document = new Document();
            document.append("command", Protocol.HANDSHAKE_REQUEST.toString());
            Document hostPort = new Document();
            hostPort.append("host", host);
            hostPort.append("port", port);
            document.append("hostPort", hostPort);
            String response = document.toJson();
            Thread.sleep(100);
            ServerMain.getServerMain().write(hostAddress, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            log.info(e.toString());

            nextJob = new RegisterUDPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
        }

        // do not need to wait for handshake response
        // add a timeout job
        ServerMain.getServerMain().submitNoResponseTimeoutJob(generateId(hostAddress), this, NoResponseTimeoutJob.DEFAULT_DELAY_TIME);
        return JobStatus.SUCCESS;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(hostAddress);
    }

    private String printPeers(ArrayList<Map> peers) {
        StringBuilder builder = new StringBuilder();
        for (Map peer: peers) {
            builder.append(" ");
            builder.append(peer.get("host"));
            builder.append(":");
            builder.append(peer.get("port"));
        }
        return builder.toString();
    }

    private String host;
    private int port;
    private InetSocketAddress hostAddress;


}
