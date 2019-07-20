package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * parse TCP request. TCP requests may be split into many packages, we assemble them into a single tcp request
 */
public class ParseTCPRequest extends Job {
    private static final int BUFFER_LENGTH = 1024;
    private FileSystemManager manager;
    private SocketChannel channel;
    private String message;

    public ParseTCPRequest(SocketChannel channel, FileSystemManager manager) {
        this.channel = channel;
        this.channel = channel;
        this.manager = manager;
    }

    @Override
    public JobStatus execute() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_LENGTH);
        int buf;
        StringBuilder recv = new StringBuilder();

        BlockingTimeoutJob blockingTimeoutJob = new BlockingTimeoutJob(Thread.currentThread(), 1000000);  // 100 s
        try {
            // add timeout
            ServerMain.getServerMain().submitTimeoutJob(blockingTimeoutJob);
            // read the remaining buffer
            byte[] buffer = ServerMain.getServerMain().readBuffer(channel);


            buf = buffer.length;
            int braces = 0;
            //buffer = byteBuffer.array();
            int[] arr = parseJson(buffer, buf, braces);
            ArrayList<byte[]> parseBuf = new ArrayList<>();
            if (buf != 0) {
                if (arr[0] == buf)
                    parseBuf.add(Arrays.copyOfRange(buffer, 0, arr[0]));
                else
                    parseBuf.add(Arrays.copyOfRange(buffer, 0, arr[0] + 1));
            }
            while (arr[0] == buf) {   // parsing not complete
                braces = arr[1];
                byteBuffer.clear();
                buf = channel.read(byteBuffer);

                if (buf == -1) {
                    log.info("channel closed by : " + channel.toString());
                    ServerMain.getServerMain().closeChannel(channel);
                    return JobStatus.FAIL;
                }

                if (buf == 0) {  // read finish
                    arr[0] = 0;
                    continue;
                }

                buffer = byteBuffer.array();
                arr = parseJson(buffer, buf, braces);
                if (arr[0] == buf)
                    parseBuf.add(Arrays.copyOfRange(buffer, 0, arr[0]));
                else
                    parseBuf.add(Arrays.copyOfRange(buffer, 0, arr[0] + 1));
            }


            for (byte[] b: parseBuf) {
                //recv.append(new String(b));
                recv.append(new String(b, StandardCharsets.UTF_8));
            }

            // put the remaining into buffer
            if (buf != 0)
                ServerMain.getServerMain().writeBuffer(channel, Arrays.copyOfRange(buffer, arr[0] + 1, buf));
            else
                ServerMain.getServerMain().writeBuffer(channel, new byte[0]);

        } catch (IOException e) {
            //e.printStackTrace();
            log.info(e.toString());
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.FAIL;
        } finally {
            ServerMain.getServerMain().removeTimeoutJob(blockingTimeoutJob);   // remove timeout job
            Thread.interrupted();   // clear interrupt flag
        }

        // MUST release the channel!
        ServerMain.getServerMain().releaseChannel(channel);

        nextJob = new Response(channel, manager, recv.toString());
//        nextJob.execute();

        return JobStatus.SUCCESS_WITH_ACTION;
//        return nextJob.execute();
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }

    private int[] parseJson(byte[] buffer, int len, int braces) {
        int i;
        for (i = 0; i != len; i++) {
            if (buffer[i] == '{') {
                braces++;
            }
            else if (buffer[i] == '}') {
                if (braces == 1) {
                    break;
                }
                braces--;
            }
        }

//        if (i == buffer.length)
//            i = -1;

        int[] ret = new int[2];
        ret[0] = i;
        ret[1] = braces;
        return ret;
    }

}
