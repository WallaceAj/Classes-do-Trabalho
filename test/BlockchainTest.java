import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockchainTest {

    private static final String ZEROS = "0".repeat(Blockchain.DIFICULDADE);

    @Test
    @DisplayName("nova cadeia tem so o genesis e e valida")
    void cadeiaNovaValida() {
        Blockchain bc = new Blockchain();
        assertEquals(1, bc.tamanho());
        assertEquals(0, bc.ultimo().getIndex());
        assertTrue(Blockchain.isChainValid(bc.getCopia()));
    }

    @Test
    @DisplayName("genesis e igual em todas as instancias")
    void genesisIgual() {
        assertEquals(new Blockchain().ultimo().getHash(), new Blockchain().ultimo().getHash());
    }

    @Test
    @DisplayName("minerar encadeia o bloco no topo")
    void minerarEncadeia() {
        Blockchain bc = new Blockchain();
        String hashGenesis = bc.ultimo().getHash();
        Block b = bc.minerar("tx");
        assertNotNull(b);
        assertEquals(2, bc.tamanho());
        assertEquals(1, b.getIndex());
        assertEquals(hashGenesis, b.getPreviousHash());
        assertTrue(b.getHash().startsWith(ZEROS));
        assertTrue(Blockchain.isChainValid(bc.getCopia()));
    }

    @Test
    @DisplayName("getCopia nao expoe a lista interna")
    void copiaIsolada() {
        Blockchain bc = new Blockchain();
        bc.getCopia().clear();
        assertEquals(1, bc.tamanho());
    }

    @Test
    @DisplayName("isChainValid rejeita null e vazia")
    void invalidaNullVazia() {
        assertFalse(Blockchain.isChainValid(null));
        assertFalse(Blockchain.isChainValid(new ArrayList<>()));
    }

    @Test
    @DisplayName("isChainValid rejeita dados adulterados")
    void invalidaDadosAdulterados() throws Exception {
        List<Block> c = TestUtil.cadeia(3);
        Field data = Block.class.getDeclaredField("data");
        data.setAccessible(true);
        data.set(c.get(1), "Transacao adulterada");
        assertFalse(Blockchain.isChainValid(c));
    }

    @Test
    @DisplayName("isChainValid rejeita previousHash quebrado")
    void invalidaPreviousHash() {
        List<Block> c = TestUtil.cadeia(2);
        Block falso = new Block(2, System.currentTimeMillis(), "x", "hash-que-nao-existe");
        falso.mineBlock(Blockchain.DIFICULDADE);
        c.add(falso);
        assertFalse(Blockchain.isChainValid(c));
    }

    @Test
    @DisplayName("isChainValid rejeita genesis diferente")
    void invalidaGenesis() {
        List<Block> c = new ArrayList<>(List.of(new Block(0, 123L, "Outro genesis", "0")));
        assertFalse(Blockchain.isChainValid(c));
    }

    @Test
    @DisplayName("isChainValid rejeita bloco sem Proof of Work")
    void invalidaSemPoW() {
        List<Block> c = TestUtil.cadeia(1);
        c.add(new Block(1, System.currentTimeMillis(), "sem pow", c.get(0).getHash()));
        assertFalse(c.get(1).getHash().startsWith(ZEROS));
        assertFalse(Blockchain.isChainValid(c));
    }

    @Test
    @DisplayName("isChainValid rejeita indice fora de sequencia")
    void invalidaIndice() {
        List<Block> c = TestUtil.cadeia(1);
        Block b = new Block(5, System.currentTimeMillis(), "indice errado", c.get(0).getHash());
        b.mineBlock(Blockchain.DIFICULDADE);
        c.add(b);
        assertFalse(Blockchain.isChainValid(c));
    }

    @Test
    @DisplayName("consenso aceita cadeia valida maior")
    void consensoAceitaMaior() {
        Blockchain local = new Blockchain();
        List<Block> maior = TestUtil.cadeia(3);
        assertNull(local.tentarSubstituir(maior));
        assertEquals(3, local.tamanho());
        assertEquals(maior.get(2).getHash(), local.ultimo().getHash());
        maior.clear();
        assertEquals(3, local.tamanho());
    }

    @Test
    @DisplayName("consenso rejeita cadeia de mesmo tamanho")
    void consensoRejeitaIgual() {
        Blockchain local = new Blockchain();
        local.minerar("local");
        String motivo = local.tentarSubstituir(TestUtil.cadeia(2));
        assertNotNull(motivo);
        assertTrue(motivo.startsWith(Blockchain.MOTIVO_TAMANHO));
        assertEquals("local", local.ultimo().getData());
    }

    @Test
    @DisplayName("consenso rejeita cadeia menor")
    void consensoRejeitaMenor() {
        Blockchain local = new Blockchain();
        local.minerar("a");
        local.minerar("b");
        assertNotNull(local.tentarSubstituir(TestUtil.cadeia(2)));
        assertEquals(3, local.tamanho());
    }

    @Test
    @DisplayName("consenso rejeita cadeia maior porem invalida")
    void consensoRejeitaMaiorInvalida() {
        Blockchain local = new Blockchain();
        List<Block> c = TestUtil.cadeia(2);
        c.add(new Block(2, System.currentTimeMillis(), "sem pow", c.get(1).getHash()));
        assertEquals("Cadeia invalida", local.tentarSubstituir(c));
        assertEquals(1, local.tamanho());
    }
}
