package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.Protocol;
import unimelb.bitbox.util.Document;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;

/**
 * A job handle invalid request
 * It will send a message to the channel and close it.
 */
public class InvalidRequest extends Job{
    private Object channel;
    private String message;

    public InvalidRequest(Object channel, String message) {
        this.channel = channel;
        this.message = message;
    }

    @Override
    public JobStatus execute() {
        log.info("invalid request at: "
                + ServerMain.displayChannel(channel) + " message: " + message);
        try {
            Document document = new Document();
            document.append("command", Protocol.INVALID_PROTOCOL.toString());
            document.append("message", message);
            String response = document.toJson();
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            log.info(e.toString()
                    + ServerMain.displayChannel(channel));
        } finally {
            if (ServerMain.getServerMain().isTCPMode())
                ServerMain.getServerMain().closeChannel(channel);
        }

        return JobStatus.SUCCESS;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }
}
