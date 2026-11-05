import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class No {
    public static final String VERSAO = "V1.0";
    public static final int PORTA_PADRAO = 8333;
    private static final int INTERVALO_MANUTENCAO_S = 30;

    // Seeds gravadas estaticamente
    private static final String[] SEED_NODES = { "127.0.0.1:8333" };

    private final int porta;
    private final String id;
    private final Set<String> seeds;
    private final Set<String> peers = ConcurrentHashMap.newKeySet();
    private final Set<String> enderecosLocais = new HashSet<>();
    private final Blockchain blockchain = new Blockchain();

    public No(int porta, Set<String> seeds) {
        this.porta = porta;
        this.seeds = seeds;
        this.id = descobrirIpLocal() + ":" + porta;
        carregarEnderecosLocais();
    }

    public static void main(String[] args) {
        int porta = PORTA_PADRAO;
        Set<String> seeds = new LinkedHashSet<>();
        try {
            if (args.length > 0) porta = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida: " + args[0]);
            System.exit(1);
        }
        for (int i = 1; i < args.length; i++) seeds.add(args[i]);
        if (seeds.isEmpty()) Collections.addAll(seeds, SEED_NODES);

        new No(porta, seeds).iniciar();
    }

    // ciclo de vida

    private void iniciar() {
        log("No BCP " + VERSAO + " | id " + id + " | seeds " + seeds);

        Thread servidor = new Thread(new Servidor(this, porta), "servidor");
        servidor.start();

        descobrirPeers(true);
        sincronizar(true);

        Thread manutencao = new Thread(this::manutencaoPeriodica, "manutencao");
        manutencao.setDaemon(true);
        manutencao.start();

        menu();

        try {
            servidor.join();
        } catch (InterruptedException ignored) {
        }
    }

    private void manutencaoPeriodica() {
        while (true) {
            try {
                Thread.sleep(INTERVALO_MANUTENCAO_S * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            descobrirPeers(false);
            sincronizar(false);
        }
    }

    private void menu() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("==== No " + id + " | blocos: " + blockchain.tamanho() + " | peers: " + peers.size() + " ====");
            System.out.println("1) Minerar novo bloco e anunciar (Fluxo C)");
            System.out.println("2) Mostrar blockchain");
            System.out.println("3) Mostrar peers conhecidos");
            System.out.println("4) Descobrir peers (Fluxo A)");
            System.out.println("5) Sincronizar blockchain (Fluxo B)");
            System.out.println("6) Adicionar peer manualmente");
            System.out.println("0) Sair");
            System.out.print("> ");
            if (!sc.hasNextLine()) return;
            String opcao = sc.nextLine().trim();

            switch (opcao) {
                case "1" -> {
                    System.out.print("Dados do bloco: ");
                    if (!sc.hasNextLine()) return;
                    minerarEAnunciar(sc.nextLine().trim());
                }
                case "2" -> mostrarBlockchain();
                case "3" -> mostrarPeers();
                case "4" -> descobrirPeers(true);
                case "5" -> sincronizar(true);
                case "6" -> {
                    System.out.print("Endereco (IP:Porta): ");
                    if (!sc.hasNextLine()) return;
                    String end = sc.nextLine().trim();
                    if (end.matches(".+:\\d{1,5}")) adicionarPeer(end);
                    else System.out.println("Formato invalido.");
                }
                case "0" -> {
                    log("Encerrando.");
                    System.exit(0);
                }
                default -> System.out.println("Opcao invalida.");
            }
        }
    }

    // Fluxo A: bootstrap e descoberta

    public void descobrirPeers(boolean verbose) {
        Deque<String> fila = new ArrayDeque<>(seeds);
        fila.addAll(peers);
        Set<String> visitados = new HashSet<>();

        while (!fila.isEmpty()) {
            String endereco = fila.poll();
            if (isSelf(endereco) || !visitados.add(endereco)) continue;

            try (Cliente c = new Cliente(endereco, id)) {
                adicionarPeer(endereco);
                List<String> recebidos = c.requisitarPeers();
                if (verbose) log("PEERS_LIST de " + endereco + ": " + recebidos);
                for (String p : recebidos) {
                    if (!isSelf(p)) {
                        adicionarPeer(p);
                        fila.add(p);
                    }
                }
            } catch (IOException | RuntimeException e) {
                falhaDeConexao(endereco, e, verbose);
            }
        }
        if (verbose) log("Descoberta concluida. Peers conhecidos: " + peers.size());
    }

    // Fluxo B: sincronizacao

    public void sincronizar(boolean verbose) {
        for (String peer : new ArrayList<>(peers)) {
            try (Cliente c = new Cliente(peer, id)) {
                Object payload = c.requisitarBlockchain();
                String motivo = receberCadeia(payload, peer);
                if (motivo == null) {
                    c.confirmarCadeia();
                } else {
                    if (verbose) log("Cadeia de " + peer + " descartada: " + motivo);
                    c.rejeitarCadeia(motivo);
                }
            } catch (IOException | RuntimeException e) {
                falhaDeConexao(peer, e, verbose);
            }
        }
        if (verbose) log("Sincronizacao concluida. Blocos locais: " + blockchain.tamanho());
    }

    // Fluxo C: mineracao e broadcast

    private void minerarEAnunciar(String dados) {
        log("Minerando bloco (dificuldade " + Blockchain.DIFICULDADE + ")...");
        long inicio = System.currentTimeMillis();
        Block bloco = blockchain.minerar(dados);
        if (bloco == null) {
            log("A cadeia local mudou durante a mineracao; bloco descartado. Tente novamente.");
            return;
        }
        log("Bloco #" + bloco.getIndex() + " minerado em " + (System.currentTimeMillis() - inicio)
                + " ms (nonce " + bloco.getNonce() + "). Anunciando para " + peers.size() + " peer(s)...");
        broadcast();
    }

    private void broadcast() {
        for (String peer : new ArrayList<>(peers)) {
            try (Cliente c = new Cliente(peer, id)) {
                NetworkMessage r = c.anunciarBlockchain(blockchain.getCopia());
                log("  " + peer + " -> " + r.getType() + (r.getPayload() != null ? " (" + r.getPayload() + ")" : ""));
            } catch (IOException | RuntimeException e) {
                falhaDeConexao(peer, e, true);
            }
        }
    }

    // estado compartilhado

    public String receberCadeia(Object payload, String origem) {
        if (!(payload instanceof List<?> lista)) {
            return "Payload invalido: esperado ArrayList<Block>";
        }
        List<Block> cadeia = new ArrayList<>();
        for (Object o : lista) {
            if (!(o instanceof Block b)) return "Payload invalido: elemento nao e Block";
            cadeia.add(b);
        }
        String motivo = blockchain.tentarSubstituir(cadeia);
        if (motivo == null) {
            log("Cadeia local substituida pela de " + origem + " (" + cadeia.size() + " blocos).");
        }
        return motivo;
    }

    public void adicionarPeer(String endereco) {
        if (endereco != null && !isSelf(endereco) && peers.add(endereco)) {
            log("Novo peer: " + endereco);
        }
    }

    private void falhaDeConexao(String endereco, Exception e, boolean verbose) {
        if (verbose) log("Falha ao contatar " + endereco + ": " + e.getMessage());
        if (!seeds.contains(endereco) && peers.remove(endereco)) {
            log("Peer " + endereco + " inacessivel; removido da lista.");
        }
    }

    public Set<String> getPeers() {
        return new TreeSet<>(peers);
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public String getId() {
        return id;
    }

    private void mostrarBlockchain() {
        List<Block> copia = blockchain.getCopia();
        System.out.println("Blockchain (" + copia.size() + " blocos, valida=" + Blockchain.isChainValid(copia) + "):");
        for (Block b : copia) System.out.println("  " + b);
    }

    private void mostrarPeers() {
        System.out.println("Peers conhecidos (" + peers.size() + "):");
        for (String p : getPeers()) System.out.println("  " + p);
    }

    // utilitarios de rede

    private boolean isSelf(String endereco) {
        try {
            int i = endereco.lastIndexOf(':');
            int p = Integer.parseInt(endereco.substring(i + 1));
            if (p != porta) return false;
            String ip = InetAddress.getByName(endereco.substring(0, i)).getHostAddress();
            return enderecosLocais.contains(ip);
        } catch (Exception e) {
            return false;
        }
    }

    private void carregarEnderecosLocais() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    String ip = a.getHostAddress();
                    int pct = ip.indexOf('%');
                    enderecosLocais.add(pct >= 0 ? ip.substring(0, pct) : ip);
                }
            }
        } catch (IOException ignored) {
        }
        enderecosLocais.add("127.0.0.1");
        enderecosLocais.add("0.0.0.0");
    }

    private static String descobrirIpLocal() {
        String forcado = System.getProperty("bcp.host");
        if (forcado != null && !forcado.isBlank()) return forcado;
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = s.getLocalAddress().getHostAddress();
            if (!ip.equals("0.0.0.0")) return ip;
        } catch (IOException ignored) {
        }
        return "127.0.0.1";
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static synchronized void log(String mensagem) {
        System.out.println("[" + LocalTime.now().format(HORA) + "] " + mensagem);
    }
}
