package unimelb.bitbox;

import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

/**
 * record connected peer infos
 */
public class PeerInfo {
    public PeerInfo(String name, int addr) {
        this.name = name;
        this.addr = addr;
    }
    public PeerInfo(DatagramChannel channel, int port) {
        this.name = channel.socket().getInetAddress().toString();
        this.addr = port;
    }
    public PeerInfo(SocketChannel channel, int port) {
        this.name = channel.socket().getInetAddress().toString();
        this.addr = port;
    }
    public String name;
    public int addr;
    public boolean incoming = true;

    public Semaphore read = new Semaphore(1);
    public Semaphore write = new Semaphore(1);

    // buffer for the received data
    public byte[] buffer = new byte[0];

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PeerInfo p = (PeerInfo) obj;
            return name.equals(p.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + Integer.hashCode(addr);
    }
}