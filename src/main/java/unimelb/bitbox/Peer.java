package unimelb.bitbox;

import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

/**
 * Program Main
 */
public class Peer
{
    private static Logger log = Logger.getLogger(Peer.class.getName());
    public static void main( String[] args ) throws NumberFormatException
    {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        Configuration.getConfiguration();

        ServerMain.getServerMain().startServer();

    }
}
