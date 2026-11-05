import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkMessageTest {

    @Test
    @DisplayName("serializa e desserializa com ArrayList<Block>")
    void serializacao() throws Exception {
        ArrayList<Block> c = TestUtil.cadeia(3);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new NetworkMessage(MessageType.BLOCKCHAIN_COPY, c));
        }
        NetworkMessage lida;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            lida = (NetworkMessage) in.readObject();
        }
        assertEquals(MessageType.BLOCKCHAIN_COPY, lida.getType());
        List<Block> recebida = TestUtil.converter((List<?>) lida.getPayload());
        assertEquals(3, recebida.size());
        assertEquals(c.get(2).getHash(), recebida.get(2).getHash());
        assertTrue(Blockchain.isChainValid(recebida));
    }
}
