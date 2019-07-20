package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.PeerInfo;
import unimelb.bitbox.Protocol;
import unimelb.bitbox.ProtocolParsingException;
import unimelb.bitbox.util.Document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * A job to register incoming requests
 * It waiting for hand shake request and response with:
 *      1. handshake response
 *      2. invalid protocol response
 *      3. connection refused response
 */
public class RegisterTCPIn extends Job{
    private SocketChannel channel;
    private String message;

    public RegisterTCPIn(SocketChannel channel) {
        this.channel = channel;
    }

    public JobStatus execute() {
        String incomingMessage;
        BlockingTimeoutJob blockingTimeoutJob = new BlockingTimeoutJob(Thread.currentThread(), BlockingTimeoutJob.DEFAULT_DELAY_TIME);
        try {
            // add timeout
            ServerMain.getServerMain().submitTimeoutJob(blockingTimeoutJob);

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int buf = channel.read(byteBuffer);
            incomingMessage = new String(byteBuffer.array(), 0, buf);

        } catch (IOException e) {
            log.info(e.toString()
                    + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.CONNECTION_FAILED;
        } finally {
            ServerMain.getServerMain().removeTimeoutJob(blockingTimeoutJob);  // remove that job
            Thread.interrupted();   // clear interrupt flag
        }

        // parse protocols
        Map<String, Object> map;
        try {
            map = Protocol.parse(incomingMessage, Protocol.HANDSHAKE_REQUEST);
        } catch (ProtocolParsingException e) {
            nextJob = new InvalidRequest(channel, e.getMessage());
            return JobStatus.FAIL_WITH_ACTION;
        }

        Map hostPort = (Map)map.get("hostPort");
        String host = (String)hostPort.get("host");
        int port = (int)hostPort.get("port");

        Collection<PeerInfo> peerInfos = ServerMain.getServerMain().registerChannel(channel, new PeerInfo(host, port));

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
                ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));

                // perform synchronize
                ServerMain.getServerMain().syncWith(channel);
            } catch (Exception e) {
                ServerMain.getServerMain().closeChannel(channel);
                e.printStackTrace();
            }
            log.info("handshake response to " + channel.socket().getInetAddress() + ":" + channel.socket().getPort());

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
                channel.write(ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
            } catch (Exception e) {
                e.printStackTrace();
                ServerMain.getServerMain().closeChannel(channel);
            }

            log.info("handshake refuse to " + channel.socket().getInetAddress().getHostAddress());
            ServerMain.getServerMain().closeChannel(channel);
        }

        return JobStatus.SUCCESS;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }
}
