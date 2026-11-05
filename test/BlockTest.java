import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockTest {

    @Test
    @DisplayName("hash e deterministico")
    void hashDeterministico() {
        Block a = new Block(1, 1000L, "dados", "abc");
        Block b = new Block(1, 1000L, "dados", "abc");
        assertEquals(a.getHash(), b.getHash());
        assertEquals(a.getHash(), a.calculateHash());
        assertEquals(64, a.getHash().length());
    }

    @Test
    @DisplayName("hash muda quando os dados mudam")
    void hashMudaComDados() {
        Block a = new Block(1, 1000L, "dados", "abc");
        Block b = new Block(1, 1000L, "outros", "abc");
        assertNotEquals(a.getHash(), b.getHash());
    }

    @Test
    @DisplayName("mineBlock gera hash com prefixo de zeros")
    void mineracaoGeraPrefixo() {
        Block b = new Block(1, 1000L, "dados", "abc");
        b.mineBlock(3);
        assertTrue(b.getHash().startsWith("000"));
        assertEquals(b.getHash(), b.calculateHash());
    }
}
