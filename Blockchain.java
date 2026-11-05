import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    public static final int DIFICULDADE = 4;
    public static final String MOTIVO_TAMANHO = "Tamanho menor ou igual ao da cadeia local";

    private ArrayList<Block> chain = new ArrayList<>();

    public Blockchain() {
        chain.add(criarGenesis());
    }

    // Genesis fixo: identico em todos os nos
    private static Block criarGenesis() {
        return new Block(0, 0L, "Bloco Genesis BCP", "0");
    }

    public synchronized ArrayList<Block> getCopia() {
        return new ArrayList<>(chain);
    }

    public synchronized int tamanho() {
        return chain.size();
    }

    public synchronized Block ultimo() {
        return chain.get(chain.size() - 1);
    }

    // Retorna null se a cadeia mudou durante a mineracao
    public Block minerar(String dados) {
        Block anterior = ultimo();
        Block novo = new Block(anterior.getIndex() + 1, System.currentTimeMillis(), dados, anterior.getHash());
        novo.mineBlock(DIFICULDADE);
        synchronized (this) {
            if (!ultimo().getHash().equals(anterior.getHash())) {
                return null;
            }
            chain.add(novo);
            return novo;
        }
    }

    public static boolean isChainValid(List<Block> cadeia) {
        if (cadeia == null || cadeia.isEmpty()) {
            return false;
        }
        Block genesis = cadeia.get(0);
        if (genesis == null || !criarGenesis().getHash().equals(genesis.getHash())) {
            return false;
        }
        String alvo = "0".repeat(DIFICULDADE);
        for (int i = 1; i < cadeia.size(); i++) {
            Block atual = cadeia.get(i);
            Block anterior = cadeia.get(i - 1);
            if (atual == null || atual.getHash() == null) return false;
            if (!atual.getHash().equals(atual.calculateHash())) return false;
            if (!atual.getHash().startsWith(alvo)) return false;
            if (!anterior.getHash().equals(atual.getPreviousHash())) return false;
            if (atual.getIndex() != anterior.getIndex() + 1) return false;
        }
        return true;
    }

    // Regra da maior cadeia valida: null se aceitou, senao o motivo
    public synchronized String tentarSubstituir(List<Block> recebida) {
        if (!isChainValid(recebida)) {
            return "Cadeia invalida";
        }
        if (recebida.size() <= chain.size()) {
            return MOTIVO_TAMANHO + " (" + recebida.size() + " <= " + chain.size() + ")";
        }
        chain = new ArrayList<>(recebida);
        return null;
    }
}
