package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.PeerInfo;
import unimelb.bitbox.Protocol;
import unimelb.bitbox.ProtocolParsingException;
import unimelb.bitbox.util.Document;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

/**
 * A job register outgoing requests
 * It will first send a handshake request to the target host and wait for response.
 *      1. if the response is HANDSHAKE_RESPONSE, the channel will be registered
 *      2. otherwise, it will response invalid protocol.
 */
public class RegisterTCPOut extends Job {
    private SocketChannel channel;
    private String message;

    public RegisterTCPOut(InetSocketAddress hostAddress, String host, int port) {
        this.host = host;
        this.port = port;
        this.hostAddress = hostAddress;
        possibles = new LinkedList<>();
        //tries = 10;
    }

    public RegisterTCPOut(InetSocketAddress hostAddress, String host, int port,
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
            nextJob = new RegisterTCPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
        }

        try {
            // open channel
            channel = SocketChannel.open(hostAddress);
            // send handshake request
            Document document = new Document();
            document.append("command", Protocol.HANDSHAKE_REQUEST.toString());
            Document hostPort = new Document();
            hostPort.append("host", host);
            hostPort.append("port", port);
            document.append("hostPort", hostPort);
            String response = document.toJson();
            Thread.sleep(100);
            channel.write(ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            log.info(e.toString());
            ServerMain.getServerMain().closeChannel(channel);

            nextJob = new RegisterTCPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
        }

        // wait for handshake response
        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
        int buf;
        BlockingTimeoutJob blockingTimeoutJob = new BlockingTimeoutJob(Thread.currentThread(), BlockingTimeoutJob.DEFAULT_DELAY_TIME);
        try {
            // add timeout
            ServerMain.getServerMain().submitTimeoutJob(blockingTimeoutJob);

            buf = channel.read(byteBuffer);
            if (buf == -1) {   // channel closed
                log.info("channel closed by: " + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
                ServerMain.getServerMain().closeChannel(channel);

                nextJob = new RegisterTCPOut(possibles.poll(), host, port, possibles);
                nextJob.tries = tries;
                return JobStatus.FAIL_WITH_ACTION;
            }
        } catch (IOException e) {
            log.info(e.toString()
                    + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
            ServerMain.getServerMain().closeChannel(channel);

            nextJob = new RegisterTCPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
        } finally {
            ServerMain.getServerMain().removeTimeoutJob(blockingTimeoutJob);   // remove timeout job
            Thread.interrupted();   // clear interrupt flag
        }

        // parse contents before \n
        int pos;
        byte[] buffer = byteBuffer.array();
        for (pos = 0; pos != buf; pos++) {
            if (buffer[pos] == '\n')
                break;
        }
        String message = new String(buffer, 0, pos);

        // will add rest to channel buffer

        // parse handshake response
        Map<String, Object> map;
        try {
            map = Protocol.parse(message);
        } catch (ProtocolParsingException e) {
            nextJob = new InvalidRequest(channel, e.getMessage());
            return JobStatus.FAIL_WITH_ACTION;
        }

        Protocol recvProtocol = Protocol.valueOf((String)map.get("command"));
        if (recvProtocol == Protocol.CONNECTION_REFUSED) {  // try to find other peers
            ServerMain.getServerMain().closeChannel(channel);
            log.info("connection refused from "
                    + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
            log.info("possible peers: " + printPeers((ArrayList<Map>)map.get("peers")));

            for (Map m: (ArrayList<Map>)map.get("peers")) {
                String host = (String)m.get("host");
                int port = (int)m.get("port");
                possibles.add(new InetSocketAddress(host, port));
            }
            nextJob = new RegisterTCPOut(possibles.poll(), host, port, possibles);
            nextJob.tries = tries;
            return JobStatus.FAIL_WITH_ACTION;
            //return JobStatus.FAIL;
        }
        else if (recvProtocol == Protocol.INVALID_PROTOCOL) {
            ServerMain.getServerMain().closeChannel(channel);
            log.info("invalid prtocol: " + message);
            return JobStatus.FAIL;
        }
        else if (recvProtocol == Protocol.HANDSHAKE_RESPONSE) {
            Map peerHostPort = (Map)map.get("hostPort");

            PeerInfo peerInfo = new PeerInfo((String)peerHostPort.get("host"), (int)peerHostPort.get("port"));
//            PeerInfo peerInfo = new PeerInfo(channel.socket().getInetAddress().getHostAddress(), (int)peerHostPort.get("port"));
            peerInfo.incoming = false;
            peerInfo.buffer = Arrays.copyOfRange(buffer, pos, buf);   // copy buffer
            ServerMain.getServerMain().registerChannel(channel, peerInfo);
            log.info("handshake response from " + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
//            try {
//                channel.socket().setReceiveBufferSize(2 * (int)ServerMain.getServerMain().getBlockSize());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            // perform synchronize
            ServerMain.getServerMain().syncWith(channel);

            return JobStatus.SUCCESS;
        }
        else {   // invalid protocol
            nextJob = new InvalidRequest(channel, "protocol " + recvProtocol.toString() + " invalid");
            return JobStatus.FAIL_WITH_ACTION;
        }
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
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
