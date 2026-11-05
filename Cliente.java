import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Papel de Cliente do Servent: conecta em um peer, faz HELLO e expoe uma requisicao por metodo. */
public final class Cliente implements Closeable {
    private static final int TIMEOUT_CONEXAO_MS = 3000;
    private static final int TIMEOUT_LEITURA_MS = 15000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public Cliente(String endereco, String meuId) throws IOException {
        int i = endereco.lastIndexOf(':');
        String host = endereco.substring(0, i);
        int porta = Integer.parseInt(endereco.substring(i + 1));

        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, porta), TIMEOUT_CONEXAO_MS);
            socket.setSoTimeout(TIMEOUT_LEITURA_MS);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            enviar(MessageType.HELLO, No.VERSAO + "-" + meuId);
            NetworkMessage resposta = receber();
            if (resposta.getType() != MessageType.ACK) {
                throw new IOException("Handshake recusado: " + resposta.getPayload());
            }
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    // Rotas: requisicao enviada -> resposta esperada

    /** GET_PEERS -> PEERS_LIST */
    public List<String> requisitarPeers() throws IOException {
        NetworkMessage r = requisitar(MessageType.GET_PEERS, null, MessageType.PEERS_LIST);
        List<String> peers = new ArrayList<>();
        if (r.getPayload() instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof String s && s.matches(".+:\\d{1,5}")) {
                    peers.add(s);
                }
            }
        }
        return peers;
    }

    /** GET_BLOCKCHAIN -> BLOCKCHAIN_COPY */
    public Object requisitarBlockchain() throws IOException {
        return requisitar(MessageType.GET_BLOCKCHAIN, null, MessageType.BLOCKCHAIN_COPY).getPayload();
    }

    /** ACK (cadeia recebida aceita) */
    public void confirmarCadeia() throws IOException {
        enviar(MessageType.ACK, "Cadeia aceita");
    }

    /** REJECT (cadeia recebida descartada) */
    public void rejeitarCadeia(String motivo) throws IOException {
        enviar(MessageType.REJECT, motivo);
    }

    /** BLOCKCHAIN_COPY (broadcast) -> ACK | REJECT */
    public NetworkMessage anunciarBlockchain(ArrayList<Block> cadeia) throws IOException {
        enviar(MessageType.BLOCKCHAIN_COPY, cadeia);
        return receber();
    }

    private NetworkMessage requisitar(MessageType tipo, Serializable payload, MessageType esperado) throws IOException {
        enviar(tipo, payload);
        NetworkMessage r = receber();
        if (r.getType() != esperado) {
            throw new IOException(tipo + " respondido com " + r + " (esperado " + esperado + ")");
        }
        return r;
    }

    private void enviar(MessageType tipo, Serializable payload) throws IOException {
        out.writeObject(new NetworkMessage(tipo, payload));
        out.flush();
        out.reset();
    }

    private NetworkMessage receber() throws IOException {
        try {
            Object obj = in.readObject();
            if (obj instanceof NetworkMessage msg) {
                return msg;
            }
            throw new IOException("Resposta fora do formato NetworkMessage");
        } catch (ClassNotFoundException e) {
            throw new IOException("Classe desconhecida na resposta: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
