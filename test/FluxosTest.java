import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FluxosTest {

    @Test
    @DisplayName("Fluxo A: descoberta transitiva de peers")
    void descoberta() throws Exception {
        No a = TestUtil.iniciarNo();
        No b = TestUtil.iniciarNo(TestUtil.endereco(a));
        No c = TestUtil.iniciarNo(TestUtil.endereco(a));

        b.descobrirPeers(false);
        c.descobrirPeers(false);

        assertTrue(c.getPeers().containsAll(Set.of(TestUtil.endereco(a), TestUtil.endereco(b))));
        assertTrue(a.getPeers().containsAll(Set.of(TestUtil.endereco(b), TestUtil.endereco(c))));
        assertTrue(b.getPeers().contains(TestUtil.endereco(c)));
    }

    @Test
    @DisplayName("Fluxo B: sincronizar adota a maior cadeia da rede")
    void sincronizacaoAdotaMaior() throws Exception {
        No a = TestUtil.iniciarNo();
        a.getBlockchain().minerar("1");
        a.getBlockchain().minerar("2");
        No b = TestUtil.iniciarNo(TestUtil.endereco(a));
        b.descobrirPeers(false);
        b.sincronizar(false);
        assertEquals(3, b.getBlockchain().tamanho());
        assertEquals(a.getBlockchain().ultimo().getHash(), b.getBlockchain().ultimo().getHash());
    }

    @Test
    @DisplayName("Fluxo B: sincronizar mantem a cadeia local quando ela e maior")
    void sincronizacaoMantemLocal() throws Exception {
        No a = TestUtil.iniciarNo();
        a.getBlockchain().minerar("a1");
        No b = TestUtil.iniciarNo(TestUtil.endereco(a));
        b.getBlockchain().minerar("b1");
        b.getBlockchain().minerar("b2");
        b.descobrirPeers(false);
        b.sincronizar(false);
        assertEquals(3, b.getBlockchain().tamanho());
        assertEquals("b2", b.getBlockchain().ultimo().getData());
        assertEquals(2, a.getBlockchain().tamanho());
    }
}
