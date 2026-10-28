package server;

import persistence.MensagemRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {

    // Porta 8080 (padrao para navegadores web e celulares)
    private static final int PORTA = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
    private final MensagemRepository repositorio = new MensagemRepository();

    public static void main(String[] args) {
        new Servidor().iniciar();
    }

    public void iniciar() {
        System.out.println("==================================================");
        System.out.println("  SERVIDOR DO APP INICIADO NA PORTA " + PORTA);
        System.out.println("  Acesse no computador: http://localhost:" + PORTA);
        System.out.println("==================================================");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                // Cada nova conexao do navegador ou celular ganha uma Thread propria
                Socket socket = serverSocket.accept();
                new Thread(new ClienteHandler(socket, this)).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    public MensagemRepository getRepositorio() {
        return repositorio;
    }
}