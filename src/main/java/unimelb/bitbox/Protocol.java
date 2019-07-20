package unimelb.bitbox;

import unimelb.bitbox.util.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * All of the protocols supported
 */
public enum Protocol {
    INVALID_PROTOCOL(invalidProtocol()),
    CONNECTION_REFUSED(connectionRefused()),
    HANDSHAKE_REQUEST(handshakeRequest()),
    HANDSHAKE_RESPONSE(handshakeResponse()),
    FILE_CREATE_REQUEST(fileCreateRequest()),
    FILE_CREATE_RESPONSE(fileCreateResponse()),
    FILE_DELETE_REQUEST(fileDeleteRequest()),
    FILE_DELETE_RESPONSE(fileDeleteResponse()),
    FILE_MODIFY_REQUEST(fileModifyRequest()),
    FILE_MODIFY_RESPONSE(fileModifyResponse()),
    DIRECTORY_CREATE_REQUEST(directoryCreateRequest()),
    DIRECTORY_CREATE_RESPONSE(directoryCreateResponse()),
    DIRECTORY_DELETE_REQUEST(directoryDeleteRequest()),
    DIRECTORY_DELETE_RESPONSE(directoryDeleteResponse()),
    FILE_BYTES_REQUEST(fileBytesRequest()),
    FILE_BYTES_RESPONSE(fileBytesResponse());

    // ===================================================
    // the protocol structure definitions start
    // ===================================================

    private static Map<String, Object> invalidProtocol() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("command", String.class);
        map.put("message", String.class);
        return map;
    }

    private static Map<String, Object> connectionRefused() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> peers = new HashMap<>();
        peers.put("host", String.class);
        peers.put("port", int.class);

        Map[] arr = {peers};

        map.put("command", String.class);
        map.put("message", String.class);
        map.put("peers", arr);

        return map;
    }

    private static Map<String, Object> handshakeRequest() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> hostPort = new HashMap<>();
        hostPort.put("host", String.class);
        hostPort.put("port", int.class);
        map.put("hostPort", hostPort);
        map.put("command", String.class);
        return map;
    }

    private static Map<String, Object> handshakeResponse() {
        return handshakeRequest();   // the same as handshake request
    }

    private static Map<String, Object> fileCreateRequest() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> fileDescriptor = new HashMap<>();
        fileDescriptor.put("md5", String.class);
        fileDescriptor.put("lastModified", long.class);
        fileDescriptor.put("fileSize", long.class);
        map.put("fileDescriptor", fileDescriptor);
        map.put("pathName", String.class);
        map.put("command", String.class);
        return map;
    }

    private static Map<String, Object> fileCreateResponse() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> fileDescriptor = new HashMap<>();
        fileDescriptor.put("md5", String.class);
        fileDescriptor.put("lastModified", long.class);
        fileDescriptor.put("fileSize", long.class);
        map.put("fileDescriptor", fileDescriptor);
        map.put("pathName", String.class);
        map.put("command", String.class);
        map.put("message", String.class);
        map.put("status", boolean.class);
        return map;
    }

    private static Map<String, Object> fileDeleteRequest() {
    	return fileCreateRequest(); // the same as file create request
    }

    private static Map<String, Object> fileDeleteResponse() {
        return fileCreateResponse(); // the same as file create response
    }

    private static Map<String, Object> fileModifyRequest() {
        return fileCreateRequest(); // the same as file create request
    }

    private static Map<String, Object> fileModifyResponse() {
        return fileCreateResponse(); // the same as file create response
    }

    private static Map<String, Object> directoryCreateRequest() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("pathName", String.class);
        map.put("command", String.class);
        return map;
    }

    private static Map<String, Object> directoryCreateResponse() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("pathName", String.class);
        map.put("command", String.class);
        map.put("message", String.class);
        map.put("status", boolean.class);
        return map;
    }

    private static Map<String, Object> directoryDeleteRequest() {
        return directoryCreateRequest();
    }

    private static Map<String, Object> directoryDeleteResponse() {
        return directoryCreateResponse();
    }

    private static Map<String, Object> fileBytesRequest() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> fileDescriptor = new HashMap<>();
        fileDescriptor.put("md5", String.class);
        fileDescriptor.put("lastModified", long.class);
        fileDescriptor.put("fileSize", long.class);
        map.put("fileDescriptor", fileDescriptor);
        map.put("pathName", String.class);
        map.put("position", long.class);
        map.put("length", long.class);
        map.put("command", String.class);
        return map;
    }

    private static Map<String, Object> fileBytesResponse() {
        HashMap<String, Object> map = new HashMap<>();
        HashMap<String, Object> fileDescriptor = new HashMap<>();
        fileDescriptor.put("md5", String.class);
        fileDescriptor.put("lastModified", long.class);
        fileDescriptor.put("fileSize", long.class);
        map.put("fileDescriptor", fileDescriptor);
        map.put("pathName", String.class);
        map.put("position", long.class);
        map.put("length", long.class);
        map.put("content", String.class);
        map.put("message", String.class);
        map.put("status", boolean.class);
        map.put("command", String.class);
        return map;
    }

    // ===================================================
    // the protocol structure definitions stop
    // ===================================================

    /**
     * The structure of a protocol. The value should be
     *      1. int.class: for a integer
     *      2. String.class: for a string
     *      3. Map : for a JSON map
     *      4. Map[]: for a JSON array
     */
    private Map<String, Object> structure;

    /**
     * define the structure of a protocol
     * @param structure the structure of a protocol
     */
    private Protocol(Map<String, Object> structure) {
        this.structure = structure;
    }

    /**
     * Parse a given JSOn string under given protocol
     * @param json the JSON string to parse
     * @param protocol the protocol needed
     * @return the content of the JSON string
     * @throws ProtocolParsingException if cannot parse that protocol
     */
    public static Map<String, Object> parse(String json, Protocol protocol) throws ProtocolParsingException {
        Document document = Document.parse(json);
        String command;
        try {
            command = document.getString("command");
        } catch (ClassCastException e) {
            throw new ProtocolParsingException("message must contain a command field as String");
        }

        if (command == null)
            throw new ProtocolParsingException("message must contain a command field as String");

        try {
            if (Protocol.valueOf(command) != protocol)
                throw new ProtocolParsingException(String.format("protocol %s invalid", command));
        } catch (IllegalArgumentException e) {
            throw new ProtocolParsingException("invalid protocol name " + command);
        }

        return generate(document, protocol.structure);
    }

    /**
     * Parse a given JSOn string under the protocol in that string
     * @param json the JSON string to parse
     * @return the content of the JSON string
     * @throws ProtocolParsingException if cannot parse that protocol
     */
    public static Map<String, Object> parse(String json) throws ProtocolParsingException {
        Document document = Document.parse(json);
        String command;
        try {
            command = document.getString("command");
        } catch (ClassCastException e) {
            throw new ProtocolParsingException("message must contain a command field as String");
        }
        if (command == null) {
            throw new ProtocolParsingException("message must contain a command field as String");
        }
        try {
            Protocol protocol = Protocol.valueOf(command);
            return generate(document, protocol.structure);
        } catch (IllegalArgumentException e) {
            throw new ProtocolParsingException("invalid protocol name " + command);
        }
    }

    /**
     * generate the map content from JSON document under certain structure
     * @param document the JSON document
     * @param structure the structure to generate
     * @return a map generated by the document and structure
     * @throws ProtocolParsingException if cannot parse that protocol
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> generate(Document document, Map<String, Object> structure) throws ProtocolParsingException {
        Map<String, Object> map = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : structure.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            try {
                if (value == int.class)
                    map.put(key, ((Long)document.get(key)).intValue());
                    //map.put(key, document.getInteger(key));  // this line will cause exception:
                    // java.lang.ClassCastException: java.base/java.lang.Long cannot be cast to java.base/java.lang.Integer
                else if (value == long.class)
                    map.put(key, (Long)document.get(key));
                else if (value == boolean.class)
                    map.put(key, (boolean)document.get(key));
                else if (value == String.class)
                    map.put(key, document.getString(key));
                else if (value instanceof Map) {
                    Document wrappedDocument = (Document)document.get(key);
                    if (wrappedDocument == null)
                        throw new NullPointerException();
                    map.put(key, generate(wrappedDocument, (Map<String, Object>)value));
                }
                else if (value instanceof Map[]) {
                    ArrayList<Object> arr = (ArrayList<Object>)document.get(key);
                    ArrayList<Object> arrayDocument = new ArrayList<>();

                    for (Object o: arr) {
                        arrayDocument.add(generate((Document)o, ((Map[])value)[0]));
                    }
                    map.put(key, arrayDocument);
                }
                else
                    throw new NullPointerException();

            } catch (NullPointerException | ClassCastException e) {   // if the key does not exists
                                                                      // or cannot cast value to desired type
                String type;
                if (value instanceof Map)
                    type = "JSON";
                else
                    type = value.toString();
                throw new ProtocolParsingException(String.format("message must contain a %s field as %s", key, type));
            }
        }

        return map;
    }
}


