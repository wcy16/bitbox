package unimelb.bitbox.jobs;

import unimelb.bitbox.Protocol;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;

/**
 * Send request for file bytes.
 */
public class FileBytesRequest extends Job {
    private FileSystemManager manager;
    private String pathName;
    private String md5;
    private long lastModified;
    private long fileSize;
    private long position;
    private Object channel;
    private String message;

    public static int generateId(String pathName, long position, long length) {
        return pathName.hashCode() + Long.hashCode(position) + Long.hashCode(length);
    }

    /**
     * Constructor
     * @param channel channel to send
     * @param manager the file system manager
     * @param pathName path name to file
     * @param md5 md5 of the file
     * @param lastModified last modified time
     * @param fileSize the file size
     * @param position the position of the required bytes
     */
    public FileBytesRequest(Object channel, FileSystemManager manager,
                            String pathName, String md5, long lastModified,
                            long fileSize, long position) {
        this.channel = channel;
        this.manager = manager;
        this.pathName = pathName;
        this.md5 = md5;
        this.lastModified = lastModified;
        this.fileSize = fileSize;
        this.position = position;
    }

    @Override
    public JobStatus execute() {
//        log.info("FileByteRequest===================");
        // check if the file should completed
        try {
            try {
                if (position >= fileSize) {
                    boolean checkComplete = manager.checkWriteComplete(pathName);
                    if (checkComplete)
                        return JobStatus.SUCCESS;
                    else {
                        log.severe("position out of bound for file: " + pathName);
                        manager.cancelFileLoader(pathName);
                        return JobStatus.FAIL;
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                log.severe("the MD5 hash algorithm is not available!");
                manager.cancelFileLoader(pathName);
                return JobStatus.FAIL;
            } catch (IOException e) {
                log.info("IO exception in check write complete at :"
                        + ServerMain.displayChannel(channel));
                manager.cancelFileLoader(pathName);
                return JobStatus.FAIL;
            }
        } catch (IOException e) {
            log.info("cannot cancel file loader: " + pathName);
        }

        long blockSize = ServerMain.getServerMain().getBlockSize();
        long remain = fileSize - position;
        long length = blockSize < remain ? blockSize : remain;

        Document document = new Document();
        Document fileDeescriptor = new Document();
        fileDeescriptor.append("md5", md5);
        fileDeescriptor.append("lastModified", lastModified);
        fileDeescriptor.append("fileSize", fileSize);

        document.append("command", Protocol.FILE_BYTES_REQUEST.toString());
        document.append("pathName", pathName);
        document.append("fileDescriptor", fileDeescriptor);
        document.append("position", position);
        document.append("length", length);

        // send request
        String response = document.toJson();
//        log.info(response);
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
            // we request a response here
            // first generate id for this message
            int id = generateId(pathName, position, length);
            ServerMain.getServerMain().submitNoResponseTimeoutJob(id, this, NoResponseTimeoutJob.DEFAULT_DELAY_TIME);
        } catch (IOException|InterruptedException e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.FAIL;
        }

//        nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, position + length);
//
//        return JobStatus.SUCCESS_WITH_ACTION;
        return JobStatus.SUCCESS;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }
}
