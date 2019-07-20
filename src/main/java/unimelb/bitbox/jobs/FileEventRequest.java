package unimelb.bitbox.jobs;

import unimelb.bitbox.Protocol;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * send the following protocols to a given channel
 *      FILE_CREATE_REQUEST
 *      FILE_DELETE_REQUEST
 *      FILE_MODIFY_REQUEST
 *      DIRECTORY_CREATE_REQUEST
 *      DIRECTORY_DELETE_REQUEST
 */
public class FileEventRequest extends Job {
    private FileSystemManager.FileSystemEvent event;
    private Object channel;
    private String message;
    
    public FileEventRequest(Object channel, FileSystemManager.FileSystemEvent event) {
        this.channel = channel;
        this.event = event;
    }

    @Override
    public JobStatus execute() {
        Document document = new Document();
        document.append("pathName", event.pathName);
        switch (event.event) {
            case FILE_CREATE:
                document.append("command", Protocol.FILE_CREATE_REQUEST.toString());
                document.append("fileDescriptor", event.fileDescriptor.toDoc());
                break;
            case FILE_DELETE:
                document.append("command", Protocol.FILE_DELETE_REQUEST.toString());
                document.append("fileDescriptor", event.fileDescriptor.toDoc());
                break;
            case FILE_MODIFY:
                document.append("command", Protocol.FILE_MODIFY_REQUEST.toString());
                document.append("fileDescriptor", event.fileDescriptor.toDoc());
                break;
            case DIRECTORY_CREATE:
                document.append("command", Protocol.DIRECTORY_CREATE_REQUEST.toString());
                break;
            case DIRECTORY_DELETE:
                document.append("command", Protocol.DIRECTORY_DELETE_REQUEST.toString());
        }

        String response = document.toJson();
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8")));
        if (channel instanceof SocketChannel) {
            try {
                ServerMain.getServerMain().write((SocketChannel)channel, buffer);
            } catch (Exception e) {
                e.printStackTrace();
                ServerMain.getServerMain().closeChannel((SocketChannel)channel);
                return JobStatus.FAIL;
            }
        }
        else if (channel instanceof SocketAddress) {
            try {
                ServerMain.getServerMain().write((SocketAddress) channel, buffer);
            } catch (Exception e) {
                return JobStatus.EXIT;
            }
        }

        return JobStatus.SUCCESS;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }
}
