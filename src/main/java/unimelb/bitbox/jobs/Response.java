package unimelb.bitbox.jobs;

import unimelb.bitbox.PeerInfo;
import unimelb.bitbox.Protocol;
import unimelb.bitbox.ProtocolParsingException;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * this job parse client requests
 */
public class Response extends Job {
    private static final int BUFFER_LENGTH = 1024;
    private FileSystemManager manager;
    String recv;
    private Object channel;
    private String message;

    public Response(Object channel, FileSystemManager manager, String recv) {
        this.channel = channel;
        this.manager = manager;
        this.recv = recv;
    }

    @Override
    public JobStatus execute() {
//        log.info(recv);

        Map<String, Object> request;
        try {
            request = Protocol.parse(recv);
        } catch (ProtocolParsingException e) {
            log.info(recv);
            nextJob = new InvalidRequest(channel, e.getMessage());
            return JobStatus.FAIL_WITH_ACTION;
        }

        JobStatus status;
        switch (Protocol.valueOf((String)request.get("command"))) {
            case FILE_CREATE_REQUEST: status = fileCreateRequest(request); break;
            case FILE_DELETE_REQUEST: status = fileDeleteRequest(request); break;
            case FILE_MODIFY_REQUEST: status = fileModifyRequest(request); break;
            case DIRECTORY_CREATE_REQUEST: status = directoryCreateRequest(request); break;
            case DIRECTORY_DELETE_REQUEST: status = directoryDeleteRequest(request); break;

            case FILE_CREATE_RESPONSE:
            case FILE_DELETE_RESPONSE:
            case FILE_MODIFY_RESPONSE:
            case DIRECTORY_CREATE_RESPONSE:
            case DIRECTORY_DELETE_RESPONSE: status = checkResponse(request); break;

            case FILE_BYTES_REQUEST: status = fileBytesRequest(request); break;
            case FILE_BYTES_RESPONSE: status = fileBytesResponse(request); break;

            case HANDSHAKE_REQUEST: status = handshakeRequest(request); break;

            // if invalid protocol from TCP, the opposite peer will disconnect
            // if from udp, we just ignore it.
            case INVALID_PROTOCOL: status = invalidRequest(request); break;

            default:
                nextJob = new InvalidRequest(channel, "invalid protocol " + request.get("command"));
                status = JobStatus.FAIL_WITH_ACTION;
        }

        return status;
    }

    @Override
    public void fail() {
        ServerMain.getServerMain().closeChannel(channel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFileDescriptor(Map<String, Object> request) {
        return (Map<String, Object>)request.get("fileDescriptor");
    }

    // ==============================================================
    // specific jobs for execution
    // ==============================================================

    private JobStatus invalidRequest(Map<String, Object> request) {
        if (ServerMain.getServerMain().isTCPMode())
            ServerMain.getServerMain().closeChannel(channel);
        return JobStatus.EXIT;
    }

    private JobStatus fileCreateRequest(Map<String, Object> request) {
        String pathName = (String)request.get("pathName");
        Map<String, Object> fileDescriptor = getFileDescriptor(request);
        String md5 = (String)fileDescriptor.get("md5");
        long lastModified = (long)fileDescriptor.get("lastModified");
        long fileSize = (long)fileDescriptor.get("fileSize");
        boolean status;
        String message = null;

        boolean check = false;

        if (!manager.isSafePathName(pathName)) {
            status = false;
            message = "unsafe pathname given";
        }
        else if (manager.fileNameExists(pathName)) {
            status = false;
            message = "pathname already exists";
        }
        else {

            try {
                status = manager.createFileLoader(pathName, md5, fileSize, lastModified);
                check = manager.checkShortcut(pathName);
                if (status)
                    message = "file loader ready";
                else
                    message = "there was a problem creating the file";
            } catch (IOException e) {
                status = false;
                message = "triggered an IO exception";
            } catch (NoSuchAlgorithmException e) {
                status = false;
                message = "no such algorithm on the server";
            }
        }

        request.put("command", Protocol.FILE_CREATE_RESPONSE.toString());
        request.put("message", message);
        request.put("status", status);

        String response = (new Document(request)).toJson();
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        if (status && !check) {
            log.info("=======================");
            nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, 0);
            return JobStatus.SUCCESS_WITH_ACTION;
        }
        else {
            return JobStatus.EXIT;
        }

    }


    private JobStatus fileDeleteRequest(Map<String, Object> request) {
    	
    	String pathName = (String)request.get("pathName");
        Map<String, Object> fileDescriptor = getFileDescriptor(request);
        String md5 = (String)fileDescriptor.get("md5");
        long lastModified = (long)fileDescriptor.get("lastModified");
        long fileSize = (long)fileDescriptor.get("fileSize");
        boolean status;
        String message = null;
    	
        boolean check = false;
        
        if (!manager.isSafePathName(pathName)) {
            status = false;
            message = "unsafe pathname given";
        }
        
        
        else if (!manager.fileNameExists(pathName)) {
            status = false;
            message = "pathname does not exist";
        }
        else {
          
            status = manager.deleteFile(pathName, lastModified, md5);
            
            if (status)
                message = "file deleted";
            else
                message = "there was a problem deleting the file";
           
        }

        request.put("command", Protocol.FILE_DELETE_RESPONSE.toString());
        request.put("message", message);
        request.put("status", status);

        String response = (new Document(request)).toJson();
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        if (status) {
            return JobStatus.SUCCESS;
        }
        else
            return JobStatus.EXIT;

    }

    private JobStatus fileModifyRequest(Map<String, Object> request) {
        String pathName = (String)request.get("pathName");
        Map<String, Object> fileDescriptor = getFileDescriptor(request);
        String md5 = (String)fileDescriptor.get("md5");
        long lastModified = (long)fileDescriptor.get("lastModified");
        long fileSize = (long)fileDescriptor.get("fileSize");
        boolean status;
        String message = null;

        boolean check = false;

        if (!manager.isSafePathName(pathName)) {
            status = false;
            message = "unsafe pathname given";
        }
        else if (!manager.fileNameExists(pathName)) {
            status = false;
            message = "pathname does not exist";
        }
        else if (manager.fileNameExists(pathName,md5)) {
            status = false;
            message = "file already exits with matching content";
        }
        else {

            try {
                status = manager.modifyFileLoader(pathName, md5, lastModified);
                check = manager.checkShortcut(pathName);
                if (status)
                    message = "file loader ready";
                else
                    message = "there was a problem modifying the file";
            } catch (IOException e) {
                status = false;
                message = "triggered an IO exception";
            } catch (NoSuchAlgorithmException e) {
                status = false;
                message = "no such algorithm on the server";
            }
        }

        request.put("command", Protocol.FILE_MODIFY_RESPONSE.toString());
        request.put("message", message);
        request.put("status", status);

        String response = (new Document(request)).toJson();
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        if (status && !check) {
            nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, 0);
            return JobStatus.SUCCESS_WITH_ACTION;
        }
        else
            return JobStatus.EXIT;
    }

     private JobStatus directoryCreateRequest(Map<String, Object> request) {
        String pathName = (String)request.get("pathName");
        String message = null;
        boolean status;
        if (!manager.isSafePathName(pathName)) {
            status = false;
            message = "unsafe pathname given";
        }
        else if (manager.dirNameExists(pathName)) {
            status = false;
            message = "pathname already exists";
        }
        else{
            status = manager.makeDirectory(pathName);
            if (status)
                message = "directory created";
            else
                message = "there was a problem creating the directory";
        }

        request.put("command", Protocol.DIRECTORY_CREATE_RESPONSE.toString());
        request.put("message", message);
        request.put("status", status);

        String response = (new Document(request)).toJson();
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        if (status) {
            //nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, 0);
            return JobStatus.SUCCESS;
        }
        else
            return JobStatus.EXIT;
    }

    private JobStatus directoryDeleteRequest(Map<String, Object> request) {
        String pathName = (String)request.get("pathName");
        String message = null;
        boolean status;
        if (!manager.isSafePathName(pathName)) {
            status = false;
            message = "unsafe pathname given";
        }
        else if (!manager.dirNameExists(pathName)) {
            status = false;
            message = "pathname does not exist";
        }
        else{
            status = manager.deleteDirectory(pathName);
            if (status)
                message = "directory deleted";
            else
                message = "there was a problem deleting the directory";
        }

        request.put("command", Protocol.DIRECTORY_DELETE_RESPONSE.toString());
        request.put("message", message);
        request.put("status", status);

        String response = (new Document(request)).toJson();
        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        if (status) {
            //nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, 0);
            return JobStatus.SUCCESS;
        }
        else
            return JobStatus.EXIT;
    }

    private JobStatus fileBytesRequest(Map<String, Object> request) {
        //String pathName = (String)request.get("pathName");
        Map<String, Object> fileDescriptor = getFileDescriptor(request);
        String md5 = (String)fileDescriptor.get("md5");
        //long lastModified = (long)fileDescriptor.get("lastModified");
        //long fileSize = (long)fileDescriptor.get("fileSize");
        long position = (long)request.get("position");
        long length = (long)request.get("length");
        boolean status;
        String message = "successful read";
        ByteBuffer buffer;
        String content = "";
        try {
            buffer = manager.readFile(md5, position, length);
            status = true;
            content = Base64.getEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (Exception e) {
            status = false;
            message = "unsuccessful read";
        }

        request.put("message", message);
        request.put("status", status);
        request.put("content", content);
        request.put("command", Protocol.FILE_BYTES_RESPONSE.toString());
        String response = new Document(request).toJson();

        try {
            ServerMain.getServerMain().write(channel, ByteBuffer.wrap(response.getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            ServerMain.getServerMain().closeChannel(channel);
            return JobStatus.EXIT;
        }

        return JobStatus.SUCCESS;

    }

    private JobStatus fileBytesResponse(Map<String, Object> request) {
        // prepare for the next time request
        String pathName = (String)request.get("pathName");
        Map<String, Object> fileDescriptor = getFileDescriptor(request);
        String md5 = (String)fileDescriptor.get("md5");
        long lastModified = (long)fileDescriptor.get("lastModified");
        long fileSize = (long)fileDescriptor.get("fileSize");
        long position = (long)request.get("position");
        long length = (long)request.get("length");
        boolean status = (boolean)request.get("status");
        String message = (String)request.get("message");
        String content = (String)request.get("content");

        int id = FileBytesRequest.generateId(pathName, position, length);
        ServerMain.getServerMain().removeTimeoutJob(new NoResponseTimeoutJob.ID(id));
        // todo if the job not exist we send back invalid request

        if (!status) {  // not a successful status
            log.info("file byte response status false at: "
                    + ServerMain.displayChannel(channel)
                    + ", message: " + message);
            try {
                manager.cancelFileLoader(pathName);
            } catch (Exception e) {
                log.info("exception when closing file loader: " + e.toString());
            }
            return JobStatus.EXIT;
        }

        ByteBuffer src = ByteBuffer.wrap(Base64.getMimeDecoder().decode(content));

        try {
            boolean flag = manager.writeFile(pathName, src, position);
            if (flag) {
                // do not check if the file has been completed loaded here
                nextJob = new FileBytesRequest(channel, manager, pathName, md5, lastModified, fileSize, position + length);
                return JobStatus.SUCCESS_WITH_ACTION;
            }
        } catch (IOException e) {
            log.info("error occurs when write file from: "
                    + ServerMain.displayChannel(channel));
            return JobStatus.EXIT;
        }

        return JobStatus.EXIT;
    }

    private JobStatus handshakeRequest(Map<String, Object> request) {
        // if in tcp mode we return with invalid protocol
        if (ServerMain.getServerMain().isTCPMode()) {
            nextJob = new InvalidRequest(channel, "invalid protocol " + request.get("command"));
            return JobStatus.FAIL_WITH_ACTION;
        }

        // in udp mode
        Map hostPort = (Map)request.get("hostPort");
        String host = (String)hostPort.get("host");
        int port = (int)hostPort.get("port");

        // first we close it and after that we register again
        ServerMain.getServerMain().closeChannel(channel);
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
                ServerMain.getServerMain().syncWith((SocketAddress)channel);
            } catch (Exception e) {
                ServerMain.getServerMain().closeChannel(channel);
                e.printStackTrace();
            }
            log.info("handshake response to " + ServerMain.displayChannel(channel));
        }
        return JobStatus.SUCCESS;
    }

    /**
     * We do not check response in our project. (TODO should we?)
     * @param request the request map
     * @return job status
     */
    private JobStatus checkResponse(Map<String, Object> request) {
        return JobStatus.SUCCESS;
    }

    // ==============================================================
    // specific jobs for execution end
    // ==============================================================

}
