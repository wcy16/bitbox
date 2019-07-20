package unimelb.bitbox;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.util.encoders.Hex;
import unimelb.bitbox.util.Document;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * listen to the port for client connecting
 */
public class ClientListenThread implements Runnable {

    private static Logger log = Logger.getLogger(ClientListenThread.class.getName());

    private int port;
    private Map<String, String> identities;

    public ClientListenThread(int port, String publicKeys) {
        this.port = port;
        identities = new ConcurrentHashMap<>();
        for (String publicKey: publicKeys.split(", *")) {
            // content:   ssh-rsa key xxx@yyy
            String[] content = publicKey.split(" ");
            identities.put(content[2], content[1]);
        }
    }

    @Override
    public void run() {
        try {
            // establish a port for listening
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket client = serverSocket.accept();
                log.info("client connection accepted from: " + client.getInetAddress() + ":" + client.getPort());
                Thread thread = new Thread(new Response(client));
                thread.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * response class
     */
    private class Response implements Runnable {

        private static final String AUTH_REQUEST = "AUTH_REQUEST";
        private static final String AUTH_RESPONSE = "AUTH_RESPONSE";
        private static final String PAYLOAD = "PAYLOAD";

        private static final String LIST_PEERS_REQUEST = "LIST_PEERS_REQUEST";
        private static final String CONNECT_PEER_REQUEST = "CONNECT_PEER_REQUEST";
        private static final String DISCONNECT_PEER_REQUEST = "DISCONNECT_PEER_REQUEST";
        private static final String LIST_PEERS_RESPONSE = "LIST_PEERS_RESPONSE";
        private static final String CONNECT_PEER_RESPONSE = "CONNECT_PEER_RESPONSE";
        private static final String DISCONNECT_PEER_RESPONSE = "DISCONNECT_PEER_RESPONSE";


        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        public Response(Socket socket) {
            this.socket = socket;

        }

        private String recv() throws IOException {
            return input.readUTF();
        }

        private void send(String msg) throws IOException {
            output.writeUTF(msg);
            output.flush();
        }

        @Override
        public void run() {
            try {
                input = new DataInputStream(socket.
                        getInputStream());
                output = new DataOutputStream(socket.
                        getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            try {
                // get client request
                String received = recv();
                Document request = Document.parse(received);

                if (!AUTH_REQUEST.equals(request.getString("command")))
                    return;

                String identity = request.getString("identity");

                // if not contain key
                if (!ClientListenThread.this.identities.containsKey(identity)) {
                    Document response = new Document();
                    response.append("command", AUTH_RESPONSE);
                    response.append("status", false);
                    response.append("message", "public key not found");
                    send(response.toJson());
                    socket.close();
                    return;
                }

                // send back aes key
                // generate AES key
                byte[] aeskey = new byte[16];
                new Random().nextBytes(aeskey);
//                aeskey = Hex.decode("0123456789abcdeffedcba9876543210");
                //System.out.println(aeskey.length);

                // encrypt AES key
                String pk = ClientListenThread.this.identities.get(identity);
                AsymmetricKeyParameter pubkey = OpenSSHPublicKeyUtil.parsePublicKey(java.util.Base64.getMimeDecoder().decode(pk));

                RSAEngine encrypt = new RSAEngine();
                encrypt.init(true, pubkey);
                byte[] encryptedAESKey = encrypt.processBlock(aeskey, 0, aeskey.length);
                //System.out.println(encryptedAESKey.length);
                Document response = new Document();
                response.append("command", AUTH_RESPONSE);
                response.append("AES128", Base64.getEncoder().withoutPadding().encodeToString(encryptedAESKey));
                response.append("status", true);
                response.append("message", "public key found");
                send(response.toJson());

                // waiting for encrypted request
                request = Document.parse(recv());
                //System.out.println(request.toJson());
                byte[] encryptedRequest = Base64.getMimeDecoder().decode(request.getString("payload"));

                // AES descrypt
                KeyParameter kp = new KeyParameter(aeskey);
                BufferedBlockCipher decrypt = new BufferedBlockCipher(new AESEngine());
                decrypt.init(false, kp);

                int len;
                byte[] aesDecrypted = new byte[encryptedRequest.length];
                len = decrypt.processBytes(encryptedRequest, 0, encryptedRequest.length, aesDecrypted, 0);
                len += decrypt.doFinal(aesDecrypted, len);

                //System.out.println(new String(aesDecrypted, "UTF-8"));

                request = Document.parse((new String(aesDecrypted, "UTF-8")).split("\n")[0]);
                //System.out.println(request.toJson());
                String command = request.getString("command");
                response = new Document();
                switch (command) {
                    case LIST_PEERS_REQUEST: listPeers(response); break;
                    case CONNECT_PEER_REQUEST:
                        connectPeer(response, request.getString("host"), ((Long)request.get("port")).intValue());
                        break;
                    case DISCONNECT_PEER_REQUEST:
                        disconnectPeer(response, request.getString("host"), ((Long)request.get("port")).intValue());
                        break;
                    default:
                        socket.close();
                        ClientListenThread.log.info("not a valid command");
                        return;    // todo may send some response
                }

                // AES encrypt
                BufferedBlockCipher aesEncrypt = new BufferedBlockCipher(new AESEngine());
                aesEncrypt.init(true, kp);
                //System.out.println(response.toJson());
                byte[] raw = response.toJson().getBytes("UTF-8");
                int cnt = 16 - raw.length % 16;
                cnt = cnt == 16 ? 0 : cnt;
                byte[] msg = new byte[raw.length + cnt];
                byte[] encrypted = new byte[msg.length];
                System.arraycopy(raw, 0, msg, 0, raw.length);

                len = aesEncrypt.processBytes(msg, 0, msg.length, encrypted, 0);
                //System.out.println(len);
                len += aesEncrypt.doFinal(encrypted, len);

                response = new Document();
                response.append("payload", Base64.getEncoder().withoutPadding().encodeToString(encrypted));

                send(response.toJson());
                socket.close();

            } catch (IOException | InvalidCipherTextException e) {
                e.printStackTrace();
                return;
            }

        }
        private void listPeers(Document document) {
            document.append("command", LIST_PEERS_RESPONSE);
            ArrayList<Document> arr = new ArrayList<>();
            Collection<PeerInfo> peers = ServerMain.getServerMain().listPeers();
            for (PeerInfo peer: peers) {
                Document peerDoc = new Document();
                peerDoc.append("host", peer.name);
                peerDoc.append("port", peer.addr);
                arr.add(peerDoc);
            }
            document.append("peers", arr);
        }

        private void connectPeer(Document document, String host, int port) {
            document.append("command", CONNECT_PEER_RESPONSE);
            document.append("host", host);
            document.append("port", port);

            boolean connect = ServerMain.getServerMain().connectTo(host, port);
            if (connect) {
                document.append("status", true);
                document.append("message", "connected to peer");
                }
            else {
                document.append("status", false);
                document.append("message", "connection failed");
            }
        }

        private void disconnectPeer(Document document, String host, int port) {
            document.append("command", DISCONNECT_PEER_RESPONSE);
            document.append("host", host);
            document.append("port", port);

            boolean status = ServerMain.getServerMain().disconnectTo(host, port);
            if (status) {
                document.append("status", true);
                document.append("message", "disconnected from peer");
            }
            else {
                document.append("status", false);
                document.append("message", "connection not active");
            }
        }
    }

    public static void main(String[] args) {
        int port = 3000;
        String pubkey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC+N1y1sovCtFw6X2XRWrAs5NfAhIV6UU/yDIyxuAwaWGxaJAeMJeiVv0Ax0imvpeN8pZsRqng6Wdo+mMd2gx8LrzDkjNHSZBDvhQ9VGejBbjMcTIt+UQqKU1QsyAd7oOLpeZrSnhRjN+HrDZc2k74ONMba21ufseiJD2dpl0+iu5u2o5xeR4sjuHxVVBJhJ0OeSzGSwIhYBk6VPfJLyZNX7COdKv4xrMts1Zg2TQ+VPHJAeODyyU2P1qd04dRouoQ6AihOxqqzI8Ye7H5+KRlS8GAw5HuLign8cnyLZ8CBHUPtehSix1ht928obEeafI8MOlwddBKmJiCp1SC+w3WB wcy@test";

        (new ClientListenThread(port, pubkey)).run();
    }

}
