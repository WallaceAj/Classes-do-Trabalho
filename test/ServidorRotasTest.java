import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServidorRotasTest {

    @Test
    @DisplayName("HELLO com versao correta -> ACK com id do servidor")
    void helloAck() throws Exception {
        No servidor = TestUtil.iniciarNo();
        List<NetworkMessage> r = TestUtil.enviarCru(servidor,
                new NetworkMessage(MessageType.HELLO, "V1.0-127.0.0.1:9999"));
        assertEquals(MessageType.ACK, r.get(0).getType());
        assertEquals("V1.0-" + servidor.getId(), r.get(0).getPayload());
    }

    @Test
    @DisplayName("HELLO com versao ou formato errado -> REJECT")
    void helloVersaoErrada() throws Exception {
        No servidor = TestUtil.iniciarNo();
        List<NetworkMessage> r = TestUtil.enviarCru(servidor,
                new NetworkMessage(MessageType.HELLO, "V9.9-127.0.0.1:9999"),
                new NetworkMessage(MessageType.HELLO, "sem formato"),
                new NetworkMessage(MessageType.HELLO, null));
        for (NetworkMessage m : r) assertEquals(MessageType.REJECT, m.getType());
        assertEquals(0, servidor.getPeers().size());
    }

    @Test
    @DisplayName("Cliente com id sem porta e recusado no handshake")
    void clienteHelloInvalido() throws Exception {
        No servidor = TestUtil.iniciarNo();
        assertThrows(IOException.class, () -> new Cliente(TestUtil.endereco(servidor), "sem-porta"));
    }

    @Test
    @DisplayName("HELLO registra o peer remoto com o IP da conexao e a porta informada")
    void helloRegistraPeer() throws Exception {
        No servidor = TestUtil.iniciarNo();
        TestUtil.enviarCru(servidor, new NetworkMessage(MessageType.HELLO, "V1.0-10.0.0.5:7777"));
        assertTrue(servidor.getPeers().contains("127.0.0.1:7777"));
    }

    @Test
    @DisplayName("mensagem antes do HELLO -> REJECT")
    void semHandshake() throws Exception {
        No servidor = TestUtil.iniciarNo();
        List<NetworkMessage> r = TestUtil.enviarCru(servidor,
                new NetworkMessage(MessageType.GET_PEERS, null),
                new NetworkMessage(MessageType.GET_BLOCKCHAIN, null),
                new NetworkMessage(MessageType.BLOCKCHAIN_COPY, TestUtil.cadeia(3)));
        for (NetworkMessage m : r) {
            assertEquals(MessageType.REJECT, m.getType());
            assertTrue(String.valueOf(m.getPayload()).startsWith("Handshake HELLO obrigatorio"));
        }
        assertEquals(1, servidor.getBlockchain().tamanho());
    }

    @Test
    @DisplayName("objeto que nao e NetworkMessage -> REJECT")
    void objetoInvalido() throws Exception {
        No servidor = TestUtil.iniciarNo();
        assertEquals(MessageType.REJECT, TestUtil.enviarCru(servidor, "uma string qualquer").get(0).getType());
    }

    @Test
    @DisplayName("tipo sem rota (PEERS_LIST) -> REJECT")
    void tipoSemRota() throws Exception {
        No servidor = TestUtil.iniciarNo();
        List<NetworkMessage> r = TestUtil.enviarCru(servidor,
                new NetworkMessage(MessageType.HELLO, "V1.0-127.0.0.1:9999"),
                new NetworkMessage(MessageType.PEERS_LIST, new ArrayList<String>()));
        assertEquals(MessageType.REJECT, r.get(1).getType());
    }

    @Test
    @DisplayName("GET_PEERS devolve peers sem o solicitante")
    void getPeers() throws Exception {
        No servidor = TestUtil.iniciarNo();
        servidor.adicionarPeer("10.0.0.1:8333");
        servidor.adicionarPeer("10.0.0.2:8333");
        try (Cliente c = TestUtil.conectar(servidor)) {
            List<String> peers = c.requisitarPeers();
            assertEquals(2, peers.size());
            assertTrue(peers.containsAll(List.of("10.0.0.1:8333", "10.0.0.2:8333")));
            assertFalse(peers.contains("127.0.0.1:5555"));
        }
        assertTrue(servidor.getPeers().contains("127.0.0.1:5555"));
    }

    @Test
    @DisplayName("GET_BLOCKCHAIN devolve a cadeia do servidor")
    void getBlockchain() throws Exception {
        No servidor = TestUtil.iniciarNo();
        servidor.getBlockchain().minerar("bloco do servidor");
        try (Cliente c = TestUtil.conectar(servidor)) {
            List<Block> recebida = TestUtil.converter((List<?>) c.requisitarBlockchain());
            assertEquals(2, recebida.size());
            assertEquals(servidor.getBlockchain().ultimo().getHash(), recebida.get(1).getHash());
            assertTrue(Blockchain.isChainValid(recebida));
        }
    }

    @Test
    @DisplayName("BLOCKCHAIN_COPY com cadeia maior e valida -> ACK e substitui")
    void copyAceita() throws Exception {
        No servidor = TestUtil.iniciarNo();
        ArrayList<Block> maior = TestUtil.cadeia(4);
        try (Cliente c = TestUtil.conectar(servidor)) {
            assertEquals(MessageType.ACK, c.anunciarBlockchain(maior).getType());
        }
        assertEquals(4, servidor.getBlockchain().tamanho());
        assertEquals(maior.get(3).getHash(), servidor.getBlockchain().ultimo().getHash());
    }

    @Test
    @DisplayName("BLOCKCHAIN_COPY com cadeia invalida -> REJECT")
    void copyInvalida() throws Exception {
        No servidor = TestUtil.iniciarNo();
        ArrayList<Block> c = TestUtil.cadeia(2);
        c.add(new Block(2, System.currentTimeMillis(), "sem pow", c.get(1).getHash()));
        try (Cliente cli = TestUtil.conectar(servidor)) {
            NetworkMessage r = cli.anunciarBlockchain(c);
            assertEquals(MessageType.REJECT, r.getType());
            assertEquals("Cadeia invalida", r.getPayload());
        }
        assertEquals(1, servidor.getBlockchain().tamanho());
    }

    @Test
    @DisplayName("BLOCKCHAIN_COPY de mesmo tamanho -> REJECT")
    void copyMesmoTamanho() throws Exception {
        No servidor = TestUtil.iniciarNo();
        servidor.getBlockchain().minerar("servidor");
        try (Cliente c = TestUtil.conectar(servidor)) {
            assertEquals(MessageType.REJECT, c.anunciarBlockchain(TestUtil.cadeia(2)).getType());
        }
        assertEquals("servidor", servidor.getBlockchain().ultimo().getData());
    }

    @Test
    @DisplayName("varias mensagens na mesma conexao")
    void variasMensagens() throws Exception {
        No servidor = TestUtil.iniciarNo();
        servidor.adicionarPeer("10.0.0.1:8333");
        try (Cliente c = TestUtil.conectar(servidor)) {
            assertEquals(1, c.requisitarPeers().size());
            assertEquals(1, ((List<?>) c.requisitarBlockchain()).size());
            assertEquals(MessageType.ACK, c.anunciarBlockchain(TestUtil.cadeia(2)).getType());
            assertEquals(2, ((List<?>) c.requisitarBlockchain()).size());
        }
    }
}
