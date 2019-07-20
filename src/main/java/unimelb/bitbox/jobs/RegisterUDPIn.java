package unimelb.bitbox.jobs;

import unimelb.bitbox.PeerInfo;
import unimelb.bitbox.Protocol;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * register udp in
 */
public class RegisterUDPIn extends Job {
    private String msg;
    private SocketAddress addr;
    public RegisterUDPIn(String msg, SocketAddress addr) {
        this.msg = msg;
        this.addr = addr;
    }

    @Override
    public JobStatus execute() {
        // parse protocols
        Map<String, Object> map;
        try {
            map = Protocol.parse(msg);
            Protocol type = Protocol.valueOf((String) map.get("command"));
            if (type == Protocol.HANDSHAKE_REQUEST)
                return handshakeRequest(map);
            else if (type == Protocol.HANDSHAKE_RESPONSE)
                return handshakeResponse(map);
            else if (type == Protocol.CONNECTION_REFUSED)
                return connectionRefused(map);
        } catch (Exception e) {
//            nextJob = new InvalidRequest(channel, e.getMessage());
//            return JobStatus.FAIL_WITH_ACTION;
            e.printStackTrace();
            return JobStatus.EXIT;
        }

        return JobStatus.EXIT;
    }

    private JobStatus handshakeRequest(Map<String, Object> map) {
        Map hostPort = (Map)map.get("hostPort");
        String host = (String)hostPort.get("host");
        int port = (int)hostPort.get("port");

        Collection<PeerInfo> peerInfos = ServerMain.getServerMain().registerChannel(addr, new PeerInfo(host, port));

        if (peerInfos == null) {
            // add handshake response here
            try {
                Document document = new Document();
                document.append("command", Protocol.HANDSHAKE_RESPONSE.toString());
                Document selfHostPort = new Document();
                selfHostPort.append("host", ServerMain.getServerMain().getHost());
                selfHostPort.append("port", ServerMain.getServerMain().getPort());
                document.append("hostPort", selfHostPort);
                String response = document.toJson();
                ServerMain.getServerMain().write(addr, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));

                // perform synchronize
                ServerMain.getServerMain().syncWith(addr);
            } catch (Exception e) {
                ServerMain.getServerMain().closeChannel(addr);
                e.printStackTrace();
            }
            log.info("handshake response to " + ServerMain.displayChannel(addr));

        }
        else {
            // add connection refused
            Document document = new Document();
            document.append("command", Protocol.CONNECTION_REFUSED.toString());
            document.append("message", "connection limit reached");

            ArrayList<Document> peers = new ArrayList<>();
            for (PeerInfo peerInfo: peerInfos) {
                Document peer = new Document();
                peer.append("host", peerInfo.name);
                peer.append("port", peerInfo.addr);
                peers.add(peer);
            }
            document.append("peers", peers);
            String response = document.toJson();

            try {
                ServerMain.getServerMain().write(addr, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
            } catch (Exception e) {
                e.printStackTrace();
            }

            log.info("handshake refuse to " + ServerMain.displayChannel(addr));
        }

        return JobStatus.SUCCESS;
    }

    private JobStatus handshakeResponse(Map<String, Object> map) {
        int id = RegisterUDPOut.generateId(addr);
        ServerMain.getServerMain().removeTimeoutJob(new NoResponseTimeoutJob.ID(id));
        Map peerHostPort = (Map)map.get("hostPort");

        PeerInfo peerInfo = new PeerInfo((String)peerHostPort.get("host"), (int)peerHostPort.get("port"));
        peerInfo.incoming = false;
        ServerMain.getServerMain().registerChannel(addr, peerInfo);
        log.info("handshake response from " + ServerMain.displayChannel(addr));
        ServerMain.getServerMain().syncWith(addr);

        return JobStatus.SUCCESS;
    }

    private JobStatus connectionRefused(Map<String, Object> map) {
        // extract job
        // todo
        return JobStatus.EXIT;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(addr);
    }
}
