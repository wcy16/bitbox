package unimelb.bitbox;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import unimelb.bitbox.jobs.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;

/**
 * ServerMain client for bitbox.
 */
public class ServerMain implements FileSystemObserver {
    /**
     * display channel like 0.0.0.0:1234
     * @param c
     * @return
     */
    public static String displayChannel(Object c) {
        if (c instanceof SocketChannel) {
            SocketChannel channel = (SocketChannel)c;
            return channel.socket().getInetAddress() + ":" + channel.socket().getPort();
        }

        else if (c instanceof DatagramChannel) {
            DatagramChannel channel = (DatagramChannel)c;
            return channel.socket().getInetAddress() + ":" + channel.socket().getPort();
        }

        else if (c instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress)c;
            return addr.getAddress() + ":" + addr.getPort();
        }

        else {
            return "not a channel!";
        }
    }

    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int WORKER_COUNT = 10;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
    }
    private static ServerMain serverMain = new ServerMain();
    public static ServerMain getServerMain() {
        return serverMain;
    }
    private ServerMain() {
        log.info("BitBox ServerMain starting...");

        // load properties
        port = Integer.parseInt(Configuration.getConfigurationValue("port"));
        path = Configuration.getConfigurationValue("path");
        advertisedName = Configuration.getConfigurationValue("advertisedName");
        peers = Configuration.getConfigurationValue("peers");
        maximumIncommingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
        clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
        authorizedKeys = Configuration.getConfigurationValue("authorized_keys");
        mode = Configuration.getConfigurationValue("mode");
        maxTries =  Integer.parseInt(Configuration.getConfigurationValue("maxTries"));
        maxResponseTime = Integer.parseInt(Configuration.getConfigurationValue("maxResponseTime"));
        maxPulseInterval = Integer.parseInt(Configuration.getConfigurationValue("maxPulseInterval"));
        maxBlockTime = Integer.parseInt(Configuration.getConfigurationValue("maxBlockTime"));

        // configure default values
        Job.DEFAULT_TRIES = maxTries;
        Pulse.DEFAULT_DELAY_TIME = maxPulseInterval * 1000;
        NoResponseTimeoutJob.DEFAULT_DELAY_TIME = maxResponseTime * 1000;
        BlockingTimeoutJob.DEFAULT_DELAY_TIME = maxBlockTime * 1000;

        try {
            fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
        } catch (IOException e) {
            log.severe("an initial scan of the share directory fails!");
            System.exit(1);
        } catch (NoSuchAlgorithmException e) {
            log.severe("the MD5 hash algorithm is not available!");
        }

        // initialize the queue
        queue = new LinkedBlockingQueue<Job>();
        delayQueue = new DelayQueue<>();

        if (mode.equals("tcp")) {
            // initialize the map
            connectedPeers = new HashMap<>();
            incomingPeerCount = 0;
        }
        else if (mode.equals("udp")) {
            remeberedPeers = new HashMap<>();
            incomingPeerCount = 0;
            // setting block size
            blockSize = blockSize > 8192 ? 8192 : blockSize;
            log.info("UDP block size: " + blockSize);
        }
        else {
            log.severe("unrecognizable mode: " + mode);
            System.exit(0);
        }
    }

    public void startServer() {
        if (mode.equals("tcp")) {
            startTCPServer();
        }
        else if (mode.equals("udp")) {
            startUDPServer();
        }
    }

    /**
     * Start TCP server. The following events will be processed
     *      1. open listening port
     *      2. connecting to other peers
     *      3. start worker threads
     *      4. start synchronize thread
     *      5. start timeout monitor thread
     *      6. start client listening thread
     *      7. start polling
     */
    public void startTCPServer() {
        // initialize channels
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            // set to non blocking
            serverSocketChannel.configureBlocking(false);
            // bind port
            serverSocketChannel.bind(new InetSocketAddress(port));

            // selector
            selector = Selector.open();
            // register selector
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            log.info(String.format("server socket channel registered at port %d", port));

        } catch(Exception e) {
            e.printStackTrace();
        }
        // connect to other peers
        for (String peer: peers.split(", *")) {
            String[] hostPort = peer.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            InetSocketAddress hostAddress = new InetSocketAddress(host, port);

            queue.add(new RegisterTCPOut(hostAddress, getHost(), getPort()));
            log.info("register connection to " + host + ":" + port);
        }

        // start workers
        initializeWorker(WORKER_COUNT);

        // start synchronize thread
        Thread sync = new Thread(new SyncEvent(fileSystemManager, syncInterval));
        sync.setDaemon(true);
        sync.start();

        // start timeout monitor
        Thread timeout = new Thread(new TimeoutMonitor(delayQueue));
        timeout.setDaemon(true);
        timeout.start();

        // start client listening thread
        Thread clientListenThread = new Thread(new ClientListenThread(clientPort, authorizedKeys));
        clientListenThread.start();

        // start polling
        try {
            polling();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    public void startUDPServer() {
        Thread udp = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerMain.this.pollingUDP();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        });
        udp.start();

        // connect to other peers
        for (String peer: peers.split(", *")) {
            String[] hostPort = peer.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            InetSocketAddress hostAddress = new InetSocketAddress(host, port);

            queue.add(new RegisterUDPOut(hostAddress, getHost(), getPort()));
            log.info("register connection to " + host + ":" + port);
        }

        // start workers
        initializeWorker(WORKER_COUNT);

        // start synchronize thread
        Thread sync = new Thread(new SyncEventUDP(fileSystemManager, syncInterval));
        sync.setDaemon(true);
        sync.start();

        // start timeout monitor
        Thread timeout = new Thread(new TimeoutMonitor(delayQueue));
        timeout.setDaemon(true);
        timeout.start();

        // start client listening thread
        Thread clientListenThread = new Thread(new ClientListenThread(clientPort, authorizedKeys));
        clientListenThread.start();

    }

    private void initializeWorker(int workerCount) {
        workers = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i != workerCount; i++) {
            workers.execute(new Worker(queue));
        }
    }

    private void polling() throws IOException{
        while (true) {
            if (selector.select() > 0) {
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey selectionKey = it.next();
                    it.remove();
                    if (selectionKey.isValid() && selectionKey.isAcceptable()) {   // listener
                        ServerSocketChannel server = (ServerSocketChannel)selectionKey.channel();

                        // get channel
                        SocketChannel socketChannel = server.accept();
                        //socketChannel.configureBlocking(false);
                        // we do not register here
                        //socketChannel.register(selector, SelectionKey.OP_READ);

                        queue.add(new RegisterTCPIn(socketChannel));
                        log.info("register queued from " + socketChannel.socket().getInetAddress() + ":" + socketChannel.socket().getPort());
                    }
                    else if (selectionKey.isValid() && selectionKey.isReadable()) {   // message received
                        SocketChannel channel = (SocketChannel)selectionKey.channel();
                        if (!acquireChannel(channel))    // some worker is working on this channel
                            continue;

                        queue.add(new ParseTCPRequest(channel, fileSystemManager));
                    }
                }
            }

            selectorLock.lock();
            selectorLock.unlock();
        }
    }

    private void pollingUDP() throws IOException {
        udpChannel = DatagramChannel.open();
        InetSocketAddress addr = new InetSocketAddress(port);
        udpChannel.socket().bind(addr);
        udpChannel.configureBlocking(true);
        ByteBuffer buffer = ByteBuffer.allocate(2 * Long.valueOf(blockSize).intValue());
        while (true) {
            SocketAddress recvAddr = udpChannel.receive(buffer);

            // create a channel to handle the msg
//            DatagramChannel channel1 = DatagramChannel.open();
//            channel1.connect(recvAddr);
            String recv = new String(buffer.array());
            buffer.clear();
            //log.info("=============" + recv);
            queue.add(new ParseUDPRequest(recvAddr, fileSystemManager, recv));
//            queue.add(new Response(channel, fileSystemManager, StandardCharsets.UTF_8.decode(buffer).toString()));
        }

    }

    /**
     * delete disconnected channels from our map
     * @param channel the channel to delete
     */
    public void closeChannel(Object channel) {
        if (channel instanceof SocketChannel) {
            closeTCPChannel((SocketChannel)channel);
        }
        else if (channel instanceof DatagramChannel) {
            return;  // we do not close udp channel
            //closeUDPChannel((DatagramChannel)channel);
        } else if (channel instanceof SocketAddress) {
            closeUDPChannel((SocketAddress)channel);
        }
    }

    private void closeTCPChannel(SocketChannel channel) {
        try {
            log.info("closing channel at "
                    + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
        } catch (Exception e) {   // if two threads close that channel in the same time
            ;                     // a null pointer exception may be thrown
        }
        mapLock.writeLock().lock();
        try {
            if (connectedPeers.containsKey(channel)) {
                if (connectedPeers.get(channel).incoming)   // an incoming serverMain
                    incomingPeerCount--;
                connectedPeers.remove(channel);
            }

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            mapLock.writeLock().unlock();
        }

        selectorLock.lock();
        try {
            channel.close();
        } catch (Exception e) {
            log.info(e.toString());
        } finally {
            selectorLock.unlock();
        }
    }

    private void closeUDPChannel(SocketAddress channel) {
        try {
            log.info("closing channel at "
                    + displayChannel(channel));
        } catch (Exception e) {   // if two threads close that channel in the same time
            ;                     // a null pointer exception may be thrown
        }
        mapLock.writeLock().lock();
        try {
            if (remeberedPeers.containsKey(channel)) {
                if (remeberedPeers.get(channel).incoming)   // an incoming serverMain
                    incomingPeerCount--;
                remeberedPeers.remove(channel);
            }

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            mapLock.writeLock().unlock();
        }
    }

    public boolean isTCPMode() {
        return mode.equals("tcp");
    }

    /**
     * register a channel for listening after handshake. this register will check if the incoming peers
     * reach its limit and return all connected peers if the limit is reached.
     * @param channel the channel to register
     * @param peerInfo the serverMain info for that channel
     * @return null if successfully registered and a collection of all connected peers if fail
     */
    public Collection<PeerInfo> registerChannel(Object channel, PeerInfo peerInfo) {
        if (channel instanceof SocketChannel)
            return registerTCPChannel((SocketChannel)channel, peerInfo);
        else if (channel instanceof SocketAddress)
            return registerUDPChannel((SocketAddress)channel, peerInfo);
        else
            throw new RuntimeException("not a valid channel!");
    }

    public Collection<PeerInfo> registerTCPChannel(SocketChannel channel, PeerInfo peerInfo) {
        mapLock.writeLock().lock();
        selectorLock.lock();
        try {
            if (incomingPeerCount < maximumIncommingConnections || !peerInfo.incoming) {
                channel.configureBlocking(false);
                selector.wakeup();
                channel.register(selector, SelectionKey.OP_READ);
                connectedPeers.put(channel, peerInfo);
                if (peerInfo.incoming)
                    incomingPeerCount++;
                log.info("channel registered");
            }
            else {
                return connectedPeers.values();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            selectorLock.unlock();
            mapLock.writeLock().unlock();
        }

        return null;
    }

    public Collection<PeerInfo> registerUDPChannel(SocketAddress channel, PeerInfo peerInfo) {
        mapLock.writeLock().lock();
        try {
            if (incomingPeerCount < maximumIncommingConnections || !peerInfo.incoming) {
                remeberedPeers.put(channel, peerInfo);
                if (peerInfo.incoming)
                    incomingPeerCount++;
                log.info("channel registered");
            }
            else {
                return remeberedPeers.values();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mapLock.writeLock().unlock();
        }
        return null;
    }

    public Collection<PeerInfo> listPeers() {
        if (mode.equals("tcp"))
            return connectedPeers.values();
        else if (mode.equals("udp"))
            return remeberedPeers.values();

        throw new UnsupportedOperationException();
    }

    public boolean connectTo(String host, int port) {
        InetSocketAddress hostAddress = new InetSocketAddress(host, port);
        Job job;
        if (mode.equals("tcp")) {
            job = new RegisterTCPOut(hostAddress, getHost(), getPort());
            JobStatus status = job.execute();
            if (status == JobStatus.SUCCESS) {
                return true;
            }
            else {
                return false;
            }
        }
        else if (mode.equals("udp")) {
            if (remeberedPeers.containsKey(hostAddress))
                return false;
            job = new RegisterUDPOut(hostAddress, getHost(), getPort());
            JobStatus status = job.execute();
            if (status == JobStatus.SUCCESS) {
                // todo wait for connection
                return true;
            }
            else {
                return false;
            }
        }
        else {
            throw new UnsupportedOperationException();
        }

    }

    public boolean disconnectTo(String host, int port) {
        InetSocketAddress hostAddress = new InetSocketAddress(host, port);
        Job job;
        if (mode.equals("tcp")) {
            Collection<SocketChannel> channels = connectedPeers.keySet();
            for (SocketChannel channel: channels) {
                if (channel.socket().getInetAddress().equals(hostAddress)) {
                    closeChannel(channel);
                    return true;
                }
            }
            return false;
        }
        else if (mode.equals("udp")) {
            PeerInfo peer = remeberedPeers.remove(hostAddress);
            if (peer == null)
                return false;
            return true;
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public boolean checkRegister(SocketAddress addr) {
        return remeberedPeers.containsKey(addr);
    }


    /**
     * Check if the channel is free to listening again. When selector observe a readable channel,
     * it will try to acquire it. If successful, the selector will add it into a job.
     * @param channel the channel to acquire
     * @return true if successfully acquired and false if fail to acquire
     */
    private boolean acquireChannel(SocketChannel channel) {
        mapLock.readLock().lock();
        boolean flag = false;
        try {
            if (connectedPeers.get(channel).read.tryAcquire())
                return true;
        } catch (Exception e) {
            flag = true;
            log.info(e.toString());
        } finally {
            mapLock.readLock().unlock();
        }

        // here we do not close channel at the catch block because that will
        // cause a dead lock.
        // this thread has a read lock tries to get a write lock in closeChannel(),
        // and other threads also tries to get a write lock before this thread.
        // so we set a flag to decide if close that channel
        if (flag)
            closeChannel(channel);

        return false;
    }

    /**
     * It release a channel when a job finish processing it.
     * @param channel the channel to release
     * @return true if release successful and false if fail
     */
    public boolean releaseChannel(SocketChannel channel) {
        mapLock.readLock().lock();
        try {
            connectedPeers.get(channel).read.release();
        } catch (Exception e) {
            closeChannel(channel);
            return false;
        } finally {
            mapLock.readLock().unlock();
        }

        return true;
    }

    /**
     * write here
     * @param obj the channel/address to write
     * @param buffer the content to write
     * @throws IOException
     * @throws InterruptedException
     */
    public void write(Object obj, ByteBuffer buffer) throws IOException, InterruptedException {
        if (obj instanceof ByteChannel)
            writeChannel((ByteChannel)obj, buffer);
        else if (obj instanceof SocketAddress)
            writeAddr((SocketAddress)obj, buffer);
        else
            throw new UnsupportedOperationException();

    }

    public void writeChannel(ByteChannel channel, ByteBuffer buffer) throws IOException, InterruptedException {
        mapLock.readLock().lock();
        try {
            connectedPeers.get(channel).write.acquire();
            //channel.write(buffer);
            while(buffer.hasRemaining()) {
                int buf = channel.write(buffer);
                if (buf < 0)
                    throw new EOFException();
            }
            connectedPeers.get(channel).write.release();
        } finally {
            mapLock.readLock().unlock();
        }
    }

    public void writeAddr(SocketAddress addr, ByteBuffer buffer) {
        try {
            udpChannel.send(buffer, addr);
        } catch (Exception e) {
            // todo currently do nothing
        }

    }

    /**
     * read channel's buffer
     * @param channel the channel to read
     * @return the channel's buffer
     */
    public byte[] readBuffer(ByteChannel channel) {
        mapLock.readLock().lock();
        try {
            return connectedPeers.get(channel).buffer;
        } catch (Exception e) {
            closeChannel(channel);
            return null;
        } finally {
            mapLock.readLock().unlock();
        }
    }

    /**
     * write channel buffer
     * @param channel the channel to write
     * @param buffer content to write
     */
    public void writeBuffer(SocketChannel channel, byte[] buffer) {
        mapLock.readLock().lock();  // or we can add another lock
        try {
            connectedPeers.get(channel).buffer = buffer;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mapLock.readLock().unlock();
        }
    }

    public String getHost() {
        return advertisedName;
    }

    public int getPort() {
        return port;
    }

    public long getBlockSize() {
        return blockSize;
    }

    public void syncWith(SocketChannel channel) {
        log.info("synchronizing with "
                + channel.socket().getInetAddress() + ":" + channel.socket().getPort());
        Collection<FileSystemManager.FileSystemEvent> events
                = fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event : events) {
            queue.add(new FileEventRequest(channel, event));
        }
    }

    public void syncWith(SocketAddress addr) {
        log.info("synchronizing with "
                + displayChannel(addr));
        Collection<FileSystemManager.FileSystemEvent> events
                = fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event : events) {
            queue.add(new FileEventRequest(addr, event));
        }
    }

    /**
     * Submit a timeout job for a timeout thread monitoring the results. The
     * job will be interrupted if timeout
     * @param job the timeout job to monitor
     */
    public void submitTimeoutJob(TimeoutJob job) {
        delayQueue.add(job);
    }

    public void submitNoResponseTimeoutJob(int id, Job job, long delay) {
        delayQueue.add(new NoResponseTimeoutJob(id, job, delay, queue));
    }

    /**
     * When finish a timeout job without exceed the given time, we should
     * remove that job from the delay queue
     * @param job the timeout job to monitor
     */
    public void removeTimeoutJob(Object job) {
        delayQueue.remove(job);
    }

    public void updateTimeoutJob(Object job1, TimeoutJob job2) {
        queueLock.lock();
        try {
            removeTimeoutJob(job1);
            submitTimeoutJob(job2);
        } finally {
            queueLock.unlock();
        }
    }

    @Override
    public void processFileSystemEvent(FileSystemManager.FileSystemEvent fileSystemEvent) {
        // send the event to every channel
        mapLock.readLock().lock();
        try {
            if (mode.equals("tcp")) {
                for (Map.Entry<SocketChannel, PeerInfo> entry : connectedPeers.entrySet()) {
                    queue.add(new FileEventRequest(entry.getKey(), fileSystemEvent));
                }
            }
            else if (mode.equals("udp")) {
                for (Map.Entry<SocketAddress, PeerInfo> entry : remeberedPeers.entrySet()) {
                    queue.add(new FileEventRequest(entry.getKey(), fileSystemEvent));
                }
            }
        } finally {
            mapLock.readLock().unlock();
        }
    }

    /**
     * for synchronizing events between peers
     */
    private class SyncEvent implements Runnable{

        public SyncEvent(FileSystemManager manager, int syncInterval) {
            this.manager = manager;
            this.syncInterval = syncInterval * 1000;   // change from second to millisecond
        }

        @Override
        public void run() {
            log.info("Run synchronizing event, interval " + syncInterval + "ms");
            while (true) {
                log.info("Start synchronizing...");
                Collection<FileSystemManager.FileSystemEvent> events = manager.generateSyncEvents();
                mapLock.readLock().lock();
                try {
                    for (Map.Entry<SocketChannel, PeerInfo> entry : connectedPeers.entrySet()) {
                        for (FileSystemManager.FileSystemEvent event : events) {
                            queue.add(new FileEventRequest(entry.getKey(), event));
                        }
                    }
                } finally {
                    mapLock.readLock().unlock();
                }

                try {
                    Thread.sleep(syncInterval);
                } catch (InterruptedException e) {
                    log.warning(e.getMessage());
                }
            }
        }

        private FileSystemManager manager;
        private int syncInterval;
    }

    /**
     * for synchronizing events between peers
     */
    private class SyncEventUDP implements Runnable{

        public SyncEventUDP(FileSystemManager manager, int syncInterval) {
            this.manager = manager;
            this.syncInterval = syncInterval * 1000;   // change from second to millisecond
        }

        @Override
        public void run() {
            log.info("Run synchronizing event, interval " + syncInterval + "ms");
            while (true) {
                log.info("Start synchronizing...");
                Collection<FileSystemManager.FileSystemEvent> events = manager.generateSyncEvents();
                mapLock.readLock().lock();
                try {
                    for (Map.Entry<SocketAddress, PeerInfo> entry : remeberedPeers.entrySet()) {
                        for (FileSystemManager.FileSystemEvent event : events) {
                            queue.add(new FileEventRequest(entry.getKey(), event));
                        }
                    }
                } finally {
                    mapLock.readLock().unlock();
                }

                try {
                    Thread.sleep(syncInterval);
                } catch (InterruptedException e) {
                    log.warning(e.getMessage());
                }
            }
        }

        private FileSystemManager manager;
        private int syncInterval;
    }

    // properties
    private final int port;
    private final String path;
    private final String advertisedName;
    private final String peers;
    private final int maximumIncommingConnections;
    private long blockSize;
    private final int syncInterval;
    private final int clientPort;
    private final String authorizedKeys;
    private final int maxTries;
    private final int maxResponseTime;
    private final int maxPulseInterval;
    private final int maxBlockTime;

    private final String mode;
    private DatagramChannel udpChannel;

    protected FileSystemManager fileSystemManager;

    private BlockingQueue<Job> queue;
    private DelayQueue<TimeoutJob> delayQueue;

    // workers
    private ExecutorService workers;

    // ====================================================
    // objects that require a lock to operate start
    // ====================================================

    private Selector selector;

    // a list of connected peers, we do not use concurrent map because we want to have some
    // customized operations to be atomic
    private Map<SocketChannel, PeerInfo> connectedPeers;   // for TCP
    private Map<SocketAddress, PeerInfo> remeberedPeers;   // for UDP

    // locks
    private final ReentrantLock selectorLock = new ReentrantLock();
    private final ReentrantReadWriteLock mapLock = new ReentrantReadWriteLock();
    private final ReentrantLock queueLock = new ReentrantLock();

    // ====================================================
    // objects that require a lock to operate end
    // ====================================================

    // record how many incoming peers now
    private int incomingPeerCount;
}


