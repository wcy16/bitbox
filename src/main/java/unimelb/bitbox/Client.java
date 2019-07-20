package unimelb.bitbox;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openssl.PEMKeyPair;
import java.util.Base64;

import unimelb.bitbox.util.Document;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

import org.bouncycastle.openssl.PEMParser;

/**
 * bitbox client for execution
 */
public class Client {
    private String command;
    private String server;
    private String client;
    private String identity;

    private RSAEngine encrypt;
    private RSAEngine decrypt;

    public Client(String command, String server, String client, String identity) {
        this.command = command;
        this.server = server;
        this.client = client;
        this.identity = identity;
        try {
            File file = new File("bitboxclient_rsa");
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            PEMParser parser = new PEMParser(reader);
            PEMKeyPair keyPair = (PEMKeyPair) parser.readObject();
            AsymmetricKeyParameter key = PrivateKeyFactory.createKey(keyPair.getPrivateKeyInfo());
            encrypt = new RSAEngine();
            encrypt.init(true, key);
            decrypt = new RSAEngine();
            decrypt.init(false, key);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * execute the command
     * @throws Exception
     */
    public void executeCommand() throws Exception {
        // check commands
        if (command == null || server == null || identity == null) {
            System.err.println("not enough arguments");
            return;
        }

        // connect
        Socket socket = null;
        DataInputStream input = null;
        DataOutputStream output = null;

        socket = createSocket(server);
        input = new DataInputStream(socket.
                getInputStream());
        output = new DataOutputStream(socket.
                getOutputStream());

        // auth
        Document request = new Document();
        request.append("command", "AUTH_REQUEST");
        request.append("identity", identity);
        output.writeUTF(request.toJson());
        output.flush();
        String message = input.readUTF();
        //System.out.println(message);
        Document response = Document.parse(message);
        boolean status = response.getBoolean("status");
        if (!status) {    // if status false
            //System.out.println(response.getString("message"));
            return;
        }

        // get aes key
        String encrypted = response.getString("AES128");
        byte[] key = decryptMsg(encrypted);
//        for (byte b: key) {
//            System.out.format("%02x ", b);
//        }
        //System.out.println("\n" + key.length);

        String msg;
        switch (command) {
            case "list_peers": msg = listPeers(); break;
            case "connect_peer": msg = connectDisconnectPeer(true); break;
            case "disconnect_peer": msg = connectDisconnectPeer(false); break;
            default:
                System.err.println("wrong command!");
                return;
        }
        //System.out.println(msg);

        // encrypt msg using aes128
        BufferedBlockCipher encrypt = new BufferedBlockCipher(new AESEngine());
        KeyParameter kp = new KeyParameter(key);
        encrypt.init(true, kp);

        byte[] raw = msg.getBytes("UTF-8");
        int pad = 16 - raw.length % 16;   // pad with 0
        pad = pad == 16 ? 0 : pad;
        byte[] padded = new byte[raw.length + pad];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        byte[] aesEncrypted = new byte[padded.length];
        int len = encrypt.processBytes(padded, 0, padded.length, aesEncrypted, 0);
        len += encrypt.doFinal(aesEncrypted, len);

        // send to server
        Document aesRequest = new Document();
        aesRequest.append("payload", Base64.getEncoder().withoutPadding().encodeToString(aesEncrypted));
        output.writeUTF(aesRequest.toJson());
        output.flush();

        // parse server response
        String aesMessage = input.readUTF();
        //System.out.println(aesMessage);
        Document aesResponse = Document.parse(aesMessage.split("\n")[0]);
        String payload = aesResponse.getString("payload");
        //System.out.println(payload);
        aesEncrypted = Base64.getMimeDecoder().decode(payload);
        BufferedBlockCipher decrypt = new BufferedBlockCipher(new AESEngine());
        decrypt.init(false, kp);

        byte[] aesDecrypted = new byte[aesEncrypted.length];
        len = decrypt.processBytes(aesEncrypted, 0, aesEncrypted.length, aesDecrypted, 0);
        len += decrypt.doFinal(aesDecrypted, len);
        String s = new String(aesDecrypted, "UTF-8");
        System.out.println(s);
    }

    private String listPeers() {
        Document document = new Document();
        document.append("command", "LIST_PEERS_REQUEST");
        return document.toJson();
    }

    private String connectDisconnectPeer(boolean b) {
        Document document = new Document();
        String[] info = client.split(":");
        String host = info[0];
        int port = Integer.parseInt(info[1]);
        if (b)
            document.append("command", "CONNECT_PEER_REQUEST");
        else
            document.append("command", "DISCONNECT_PEER_REQUEST");
        document.append("host", host);
        document.append("port", port);
        return document.toJson();
    }

    private Socket createSocket(String addr) throws UnknownHostException, IOException {
        String[] info = addr.split(":");
        String ip = info[0];
        int port = Integer.parseInt(info[1]);
        Socket socket = new Socket(ip, port);
        return socket;
    }

    private byte[] decryptMsg(String msg) throws UnsupportedEncodingException {

        byte[] block = Base64.getMimeDecoder().decode(msg);

        //System.out.println(decrypt.getInputBlockSize());
        byte[] decrypted= decrypt.processBlock(block, 0, block.length);
//        for (byte b: decrypted)
//            System.out.format("%02x ", b);
        return decrypted;
    }

    private String encryptMsg(String msg) throws UnsupportedEncodingException {
        byte[] block = msg.getBytes("UTF-8");
        byte[] encrypted = encrypt.processBlock(block, 0, block.length);
        return Base64.getEncoder().withoutPadding().encodeToString(encrypted);

    }

    public static void main(String[] args) throws Exception {
        int pos = 0;
        String command = null;
        String server = null;
        String client = null;
        String identity = null;
        try {
            while (pos != args.length) {
                switch (args[pos++]) {
                    case "-c":
                        command = args[pos++];
                        break;
                    case "-s":
                        server = args[pos++];
                        break;
                    case "-p":
                        client = args[pos++];
                        break;
                    case "-i":
                        identity = args[pos++];
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("parse arguments error");
            System.exit(1);
        }

        //System.out.format("%s %s %s", command, server, client);

        Client clientPeer = new Client(command, server, client, identity);
        clientPeer.executeCommand();
    }

}
