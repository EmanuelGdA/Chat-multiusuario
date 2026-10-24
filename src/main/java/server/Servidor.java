package server;

import model.Mensagem;
import persistence.MensagemRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Servidor {

    private static final int PORTA = 12345;
    
    // Lista sincronizada para evitar Race Conditions (concorrencia entre threads)
    private final List<ClienteHandler> clientes = Collections.synchronizedList(new ArrayList<>());
    private final MensagemRepository repositorio = new MensagemRepository();
    private boolean rodando = true;

    public static void main(String[] args) {
        Servidor servidor = new Servidor();
        servidor.iniciar();
    }

    public void iniciar() {
        System.out.println("=== SERVIDOR DE CHAT INICIADO NA PORTA " + PORTA + " ===");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {

            // Laco infinito que aceita novas conexoes de clientes
            while (rodando) {
                // Aguarda um cliente se conectar (bloqueia ate receber conexao)
                Socket socketCliente = serverSocket.accept();
                System.out.println("Novo cliente conectado do endereco: " + socketCliente.getInetAddress());

                // Cria o atendente exclusivo do cliente
                ClienteHandler handler = new ClienteHandler(socketCliente, this);
                clientes.add(handler);

                // Executa o cliente em uma nova Thread para nao travar o servidor
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("Erro na execucao do servidor: " + e.getMessage());
        }
    }

    // Processa a mensagem digitada: salva no MySQL e retransmite a todos (Broadcast)
    public void processarNovaMensagem(String remetente, String texto) {
        Mensagem mensagem = new Mensagem(remetente, texto);

        // 1. Persiste a mensagem no banco de dados MySQL
        repositorio.salvar(mensagem);

        // 2. Retransmite (Broadcast) a mensagem formatada para todos os clientes conectados
        String formatada = mensagem.formatarParaExibicao();
        transmitirParaTodos(formatada);
    }

    // Envia mensagens de aviso do sistema (ex: Fulano entrou / saiu)
    public void transmitirMensagemSistema(String aviso) {
        transmitirParaTodos("[SISTEMA] " + aviso);
    }

    // Mecanismo de Broadcast seguro com sincronizacao
    private void transmitirParaTodos(String texto) {
        // Bloco sincronizado obrigatorio ao iterar sobre uma lista thread-safe
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarMensagem(texto);
            }
        }
    }

    // Remove o cliente da lista quando ele sai ou cai a conexao
    public void removerCliente(ClienteHandler handler) {
        clientes.remove(handler);
        System.out.println("Cliente " + handler.getApelido() + " desconectado.");
    }

    public MensagemRepository getRepositorio() {
        return repositorio;
    }
}