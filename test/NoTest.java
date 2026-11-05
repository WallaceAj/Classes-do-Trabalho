import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoTest {

    @Test
    @DisplayName("receberCadeia rejeita payload que nao e lista")
    void payloadNaoLista() {
        No no = new No(TestUtil.portaLivre(), Set.of());
        assertTrue(no.receberCadeia("texto", "x").startsWith("Payload invalido"));
        assertTrue(no.receberCadeia(null, "x").startsWith("Payload invalido"));
    }

    @Test
    @DisplayName("receberCadeia rejeita lista com elemento que nao e Block")
    void payloadElementoErrado() {
        No no = new No(TestUtil.portaLivre(), Set.of());
        ArrayList<Object> lista = new ArrayList<>(TestUtil.cadeia(2));
        lista.add("nao sou um bloco");
        assertTrue(no.receberCadeia(lista, "x").startsWith("Payload invalido"));
        assertEquals(1, no.getBlockchain().tamanho());
    }

    @Test
    @DisplayName("adicionarPeer ignora o proprio endereco")
    void ignoraProprioEndereco() {
        int porta = TestUtil.portaLivre();
        No no = new No(porta, Set.of());
        no.adicionarPeer("127.0.0.1:" + porta);
        no.adicionarPeer(no.getId());
        assertEquals(0, no.getPeers().size());
        no.adicionarPeer("127.0.0.1:" + (porta + 1));
        assertEquals(1, no.getPeers().size());
    }
}
