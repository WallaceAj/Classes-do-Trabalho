import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Block implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int index;
    private final long timestamp;
    private final String data;
    private final String previousHash;
    private long nonce;
    private String hash;

    public Block(int index, long timestamp, String data, String previousHash) {
        this.index = index;
        this.timestamp = timestamp;
        this.data = data;
        this.previousHash = previousHash;
        this.nonce = 0;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        String conteudo = index + "|" + timestamp + "|" + previousHash + "|" + nonce + "|" + data;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(conteudo.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    public void mineBlock(int dificuldade) {
        String alvo = "0".repeat(dificuldade);
        while (!hash.startsWith(alvo)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public int getIndex() { return index; }
    public long getTimestamp() { return timestamp; }
    public String getData() { return data; }
    public String getPreviousHash() { return previousHash; }
    public long getNonce() { return nonce; }
    public String getHash() { return hash; }

    @Override
    public String toString() {
        return String.format("#%d | dados=\"%s\" | nonce=%d%n     hash=%s%n     prev=%s",
                index, data, nonce, hash, previousHash);
    }
}
