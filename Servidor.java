import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    private static final int PORTA = 8080;

    public static void main(String[] args) {
        System.out.println("Iniciando o servidor na porta " + PORTA + "...");
        
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            System.out.println("Servidor aguardando conexoes...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Conexao estabelecida com: " + socket.getInetAddress().getHostAddress());

                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);

                String mensagemRecebida = entrada.readLine();
                System.out.println("Mensagem recebida da rede: " + mensagemRecebida);

                saida.println("SUCESSO: Dados recebidos pelo servidor.");

                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }
}