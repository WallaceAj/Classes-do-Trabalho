import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Cliente {
    public static void main(String[] args) {
        String ipServidor = "127.0.0.1";
        int porta = 8080;

        if (args.length > 0) {
            ipServidor = args[0];
        }

        System.out.println("Conectando ao servidor em " + ipServidor + ":" + porta + "...");

        try (Socket socket = new Socket(ipServidor, porta)) {
            PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String dadosParaEnviar = "{\"bloco\": 1, \"dado\": \"Teste de conexao P2P no laboratorio\"}";
            
            System.out.println("Enviando dados: " + dadosParaEnviar);
            saida.println(dadosParaEnviar);

            String resposta = entrada.readLine();
            System.out.println("Resposta do servidor: " + resposta);

        } catch (IOException e) {
            System.err.println("Erro ao conectar com o servidor: " + e.getMessage());
        }
    }
}