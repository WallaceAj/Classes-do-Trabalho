# BCP — Blockchain Copy Protocol

Implementação em Java de uma blockchain simplificada distribuída em rede P2P.
Cada nó é um **Servent**: servidor (escuta conexões TCP) e cliente (conecta em outros peers) ao mesmo tempo.
As mensagens são objetos `NetworkMessage` serializados com `ObjectOutputStream`/`ObjectInputStream`.

## Requisitos

- Java 17 ou superior (testado com OpenJDK 21)

## Compilar

```bash
javac -d out *.java
```

> Se o seu Java não tiver o `javac` (só o JRE), use:
> `java --module jdk.compiler/com.sun.tools.javac.Main -d out *.java`

## Testes

Os testes usam JUnit 5. O jar fica em `lib/`, então funciona sem internet:

```bash
javac -cp lib/junit-platform-console-standalone-1.11.4.jar -d out *.java test/*.java
java -jar lib/junit-platform-console-standalone-1.11.4.jar execute -cp out --scan-classpath
```

| Classe de teste | O que cobre |
|---|---|
| `BlockTest` | Hash SHA-256 e mineração |
| `BlockchainTest` | `isChainValid` e a regra da maior cadeia válida |
| `NetworkMessageTest` | Serialização com `ArrayList<Block>` |
| `NoTest` | Validação do payload recebido e lista de peers |
| `ServidorRotasTest` | Cada rota do servidor, sobre sockets reais em portas livres |
| `FluxosTest` | Fluxos A (descoberta) e B (sincronização) entre vários nós |

Levam cerca de 20 s por causa da mineração (PoW). Nas IDEs (IntelliJ, VS Code, Eclipse), adicione o jar de `lib/` ao classpath e rode a pasta `test/`.

## Executar

```bash
java -cp out No [porta] [seed1 IP:Porta] [seed2 IP:Porta] ...
```

- `porta`: porta TCP em que o nó escuta (padrão `8333`).
- `seeds`: nós semente usados no bootstrap. Se nenhum for informado, usa `SEED_NODES` em `No.java` (`127.0.0.1:8333`).
- `-Dbcp.host=IP` (opcional): força o IP com que o nó se identifica, caso a detecção automática pegue a interface errada.

### Em um único computador (5 nós)

Abra 5 terminais:

```bash
java -cp out No 8333
java -cp out No 8334
java -cp out No 8335
java -cp out No 8336
java -cp out No 8337
```

O nó `8333` é a seed; os outros se conectam a ele e descobrem uns aos outros.

### Em vários computadores (rede local)

1. Descubra o IP do computador que será a seed (`ip a` no Linux, `ipconfig` no Windows), por exemplo `192.168.1.10`.
2. Na seed: `java -cp out No 8333`
3. Nos demais: `java -cp out No 8333 192.168.1.10:8333`
4. Libere a porta no firewall, se necessário (ex.: `sudo ufw allow 8333/tcp`).

Você também pode trocar o endereço fixo em `SEED_NODES` (`No.java`) pelo IP da seed e recompilar.

## Menu do nó

| Opção | Ação |
|---|---|
| 1 | Minerar um novo bloco (PoW) e anunciar para todos os peers (Fluxo C) |
| 2 | Mostrar a blockchain local |
| 3 | Mostrar os peers conhecidos |
| 4 | Descobrir peers: `HELLO` + `GET_PEERS` (Fluxo A) |
| 5 | Sincronizar: `GET_BLOCKCHAIN` em todos os peers (Fluxo B) |
| 6 | Adicionar um peer manualmente |
| 0 | Sair |

A cada 30 s o nó repete a descoberta e a sincronização sozinho, para incluir nós que entraram depois.

## Rotas do protocolo

### Servidor (`Servidor.registrarRotas()`)

| Recebe | Handler | Responde |
|---|---|---|
| `HELLO("V1.0-<ip>:<porta>")` | `onHello` | `ACK("V1.0-<id>")` ou `REJECT` |
| `GET_PEERS(null)` | `onGetPeers` | `PEERS_LIST(List<String>)` |
| `GET_BLOCKCHAIN(null)` | `onGetBlockchain` | `BLOCKCHAIN_COPY(ArrayList<Block>)` |
| `BLOCKCHAIN_COPY(ArrayList<Block>)` | `onBlockchainCopy` | `ACK` ou `REJECT(motivo)` |
| `ACK` / `REJECT` | `onAck` / `onReject` | — |

Qualquer mensagem enviada antes do `HELLO` recebe `REJECT`.
Para criar uma rota nova: adicione o tipo em `MessageType`, escreva o handler `onXxx` e registre-o em `registrarRotas()`.

### Cliente (`Cliente`)

| Método | Envia | Espera |
|---|---|---|
| `new Cliente(endereco, id)` | `HELLO` | `ACK` |
| `requisitarPeers()` | `GET_PEERS` | `PEERS_LIST` |
| `requisitarBlockchain()` | `GET_BLOCKCHAIN` | `BLOCKCHAIN_COPY` |
| `confirmarCadeia()` / `rejeitarCadeia(motivo)` | `ACK` / `REJECT` | — |
| `anunciarBlockchain(cadeia)` | `BLOCKCHAIN_COPY` | `ACK` ou `REJECT` |

## Consenso

Uma cadeia recebida só substitui a local se `isChainValid()` aprovar (hashes, encadeamento `previousHash`, PoW com dificuldade 4 e mesmo bloco gênesis) **e** ela for maior que a local. Caso contrário, é descartada e o nó responde `REJECT`.

## Estrutura

| Arquivo | Responsabilidade |
|---|---|
| `No.java` | `main`, menu e fluxos A, B e C |
| `Servidor.java` | Papel de servidor e tabela de rotas |
| `Cliente.java` | Papel de cliente, com um método por requisição |
| `NetworkMessage.java` | Pacote do protocolo (`type` + `payload`) |
| `MessageType.java` | Tipos de mensagem |
| `Block.java` | Bloco, hash SHA-256 e mineração |
| `Blockchain.java` | Cadeia local, validação e regra da maior cadeia |
