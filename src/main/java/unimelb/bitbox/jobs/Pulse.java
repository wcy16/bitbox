package unimelb.bitbox.jobs;

import unimelb.bitbox.ServerMain;

import java.net.SocketAddress;

/**
 * pulse is used in udp to check if a peer is still alive.
 * the server will automatically disconnect the peer if it receive
 * no packages in a certain amount of time.
 */
public class Pulse extends TimeoutJob {
    public static long DEFAULT_DELAY_TIME = 10000;
    private SocketAddress addr;

    public static class ID {
        private SocketAddress addr;
        public ID(SocketAddress addr) {
            this.addr = addr;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Pulse) {
                Pulse p = (Pulse)obj;
                return this.addr.equals(p.addr);
            }
            else if (obj instanceof ID) {
                ID id = (ID)obj;
                return this.addr.equals(id.addr);
            }
            return false;
        }
    }

    public Pulse(SocketAddress addr, long delay) {
        this.addr = addr;
        time = System.currentTimeMillis() + delay;
    }


    @Override
    public void execute() {
        ServerMain.getServerMain().closeChannel(addr);
        log.info("No pulse from " + ServerMain.displayChannel(addr));
    }
}
