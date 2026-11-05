import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/** Papel de Servidor do Servent: recebe NetworkMessage e encaminha para a rota do seu MessageType. */
public class Servidor implements Runnable {

    @FunctionalInterface
    private interface Handler {
        NetworkMessage tratar(Sessao sessao, NetworkMessage msg) throws IOException;
    }

    private static final class Sessao {
        final String ipRemoto;
        String peerRemoto;

        Sessao(String ipRemoto) {
            this.ipRemoto = ipRemoto;
        }

        boolean autenticada() {
            return peerRemoto != null;
        }
    }

    private final No no;
    private final int porta;
    private final Map<MessageType, Handler> rotas = new EnumMap<>(MessageType.class);

    public Servidor(No no, int porta) {
        this.no = no;
        this.porta = porta;
        registrarRotas();
    }

    // Rotas: MessageType recebido -> handler (resposta)
    private void registrarRotas() {
        rotas.put(MessageType.HELLO,           this::onHello);          // -> ACK | REJECT
        rotas.put(MessageType.GET_PEERS,       this::onGetPeers);       // -> PEERS_LIST
        rotas.put(MessageType.GET_BLOCKCHAIN,  this::onGetBlockchain);  // -> BLOCKCHAIN_COPY
        rotas.put(MessageType.BLOCKCHAIN_COPY, this::onBlockchainCopy); // -> ACK | REJECT
        rotas.put(MessageType.ACK,             this::onAck);            // sem resposta
        rotas.put(MessageType.REJECT,          this::onReject);         // sem resposta
    }

    private NetworkMessage onHello(Sessao sessao, NetworkMessage msg) {
        String portaRemota = extrairPortaDoHello(msg.getPayload());
        if (portaRemota == null) {
            return new NetworkMessage(MessageType.REJECT,
                    "Versao de protocolo incompativel (esperado " + No.VERSAO + "-<host>:<porta>)");
        }
        sessao.peerRemoto = sessao.ipRemoto + ":" + portaRemota;
        no.adicionarPeer(sessao.peerRemoto);
        return new NetworkMessage(MessageType.ACK, No.VERSAO + "-" + no.getId());
    }

    private NetworkMessage onGetPeers(Sessao sessao, NetworkMessage msg) {
        ArrayList<String> lista = new ArrayList<>(no.getPeers());
        lista.remove(sessao.peerRemoto);
        return new NetworkMessage(MessageType.PEERS_LIST, lista);
    }

    private NetworkMessage onGetBlockchain(Sessao sessao, NetworkMessage msg) {
        return new NetworkMessage(MessageType.BLOCKCHAIN_COPY, no.getBlockchain().getCopia());
    }

    private NetworkMessage onBlockchainCopy(Sessao sessao, NetworkMessage msg) {
        String motivo = no.receberCadeia(msg.getPayload(), sessao.peerRemoto);
        return motivo == null
                ? new NetworkMessage(MessageType.ACK, "Cadeia aceita")
                : new NetworkMessage(MessageType.REJECT, motivo);
    }

    private NetworkMessage onAck(Sessao sessao, NetworkMessage msg) {
        return null;
    }

    private NetworkMessage onReject(Sessao sessao, NetworkMessage msg) {
        String motivo = String.valueOf(msg.getPayload());
        if (!motivo.startsWith(Blockchain.MOTIVO_TAMANHO)) {
            No.log(sessao.peerRemoto + " rejeitou nossa cadeia: " + motivo);
        }
        return null;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            No.log("Servidor BCP escutando na porta " + porta);
            while (true) {
                Socket socket = serverSocket.accept();
                Thread t = new Thread(() -> atenderConexao(socket), "conexao-" + socket.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            No.log("ERRO: nao foi possivel abrir a porta " + porta + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private void atenderConexao(Socket socket) {
        Sessao sessao = new Sessao(socket.getInetAddress().getHostAddress());

        try (socket; ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Object recebido;
                try {
                    recebido = in.readObject();
                } catch (EOFException | SocketException fim) {
                    break;
                }
                NetworkMessage resposta = despachar(sessao, recebido);
                if (resposta != null) {
                    out.writeObject(resposta);
                    out.flush();
                    out.reset();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            No.log("Conexao com " + sessao.ipRemoto + " encerrada com erro: " + e.getMessage());
        }
    }

    private NetworkMessage despachar(Sessao sessao, Object recebido) throws IOException {
        if (!(recebido instanceof NetworkMessage msg) || msg.getType() == null) {
            return new NetworkMessage(MessageType.REJECT, "Mensagem fora do formato NetworkMessage");
        }
        if (msg.getType() != MessageType.HELLO && !sessao.autenticada()) {
            return new NetworkMessage(MessageType.REJECT, "Handshake HELLO obrigatorio antes de " + msg.getType());
        }
        Handler handler = rotas.get(msg.getType());
        if (handler == null) {
            return new NetworkMessage(MessageType.REJECT, "Tipo de mensagem inesperado: " + msg.getType());
        }
        return handler.tratar(sessao, msg);
    }

    private static String extrairPortaDoHello(Object payload) {
        if (!(payload instanceof String s) || !s.startsWith(No.VERSAO + "-")) {
            return null;
        }
        int i = s.lastIndexOf(':');
        if (i < 0) return null;
        try {
            int p = Integer.parseInt(s.substring(i + 1));
            return (p > 0 && p <= 65535) ? String.valueOf(p) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
