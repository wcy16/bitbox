package unimelb.bitbox;

/**
 * Exception for parsing protocol
 */
public class ProtocolParsingException extends Exception {
    ProtocolParsingException() {}
    ProtocolParsingException(String message) {
        super(message);
    }
}
