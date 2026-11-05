import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TestUtil {
    private TestUtil() {
    }

    static ArrayList<Block> cadeia(int tamanho) {
        Blockchain bc = new Blockchain();
        for (int i = 1; i < tamanho; i++) bc.minerar("bloco " + i);
        return bc.getCopia();
    }

    static List<Block> converter(List<?> lista) {
        List<Block> r = new ArrayList<>();
        for (Object o : lista) r.add((Block) o);
        return r;
    }

    static int portaLivre() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int porta(No no) {
        String id = no.getId();
        return Integer.parseInt(id.substring(id.lastIndexOf(':') + 1));
    }

    static String endereco(No no) {
        return "127.0.0.1:" + porta(no);
    }

    static No iniciarNo(String... seeds) throws InterruptedException {
        int porta = portaLivre();
        No no = new No(porta, new LinkedHashSet<>(Set.of(seeds)));
        Thread t = new Thread(new Servidor(no, porta));
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", porta)) {
                s.getInputStream();
                return no;
            } catch (IOException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("Servidor nao subiu na porta " + porta);
    }

    static Cliente conectar(No servidor) throws IOException {
        return new Cliente(endereco(servidor), "127.0.0.1:5555");
    }

    /** Envia objetos sem o handshake automatico do Cliente e le uma resposta para cada. */
    static List<NetworkMessage> enviarCru(No servidor, Serializable... mensagens) throws Exception {
        List<NetworkMessage> respostas = new ArrayList<>();
        try (Socket s = new Socket("127.0.0.1", porta(servidor));
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            s.setSoTimeout(5000);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            for (Serializable m : mensagens) {
                out.writeObject(m);
                out.flush();
                respostas.add((NetworkMessage) in.readObject());
            }
        }
        return respostas;
    }
}
