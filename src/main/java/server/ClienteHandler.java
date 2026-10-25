package server;

import model.Mensagem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

// Implementa Runnable para que cada cliente rode em sua propria Thread sem travar os outros
public class ClienteHandler implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private PrintWriter escritor;
    private BufferedReader leitor;
    private String apelido;

    public ClienteHandler(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try {
            // Streams para ler o que o cliente digita e escrever respostas para ele
            leitor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            escritor = new PrintWriter(socket.getOutputStream(), true);

            // 1. Primeiro passo ao conectar: solicitar o apelido
            escritor.println("BEM-VINDO! Digite seu apelido:");
            this.apelido = leitor.readLine();

            if (this.apelido == null || this.apelido.trim().isEmpty()) {
                this.apelido = "Anonimo_" + socket.getPort();
            }

            // 2. Envia o historico de mensagens salvas no banco para o novo cliente
            enviarHistorico();

            // 3. Avisa a todos que este usuario entrou na sala
            servidor.transmitirMensagemSistema(this.apelido + " entrou no chat!");

            // 4. Laco principal: fica ouvindo novas mensagens digitadas por esse cliente
            String texto;
            while ((texto = leitor.readLine()) != null) {
                if (texto.equalsIgnoreCase("/sair")) {
                    break;
                }
                // Cria a mensagem, salva no banco e faz o broadcast para todos
                servidor.processarNovaMensagem(this.apelido, texto);
            }

        } catch (IOException e) {
            // Conexao perdida ou cliente fechou a janela abruptamente
            System.out.println("Conexao com " + apelido + " foi encerrada.");
        } finally {
            // Garante que o cliente seja removido da lista e os recursos sejam fechados
            encerrarConexao();
        }
    }

    // Envia o historico recente do MySQL apenas para este cliente que acabou de se conectar
    private void enviarHistorico() {
        List<Mensagem> ultimasMensagens = servidor.getRepositorio().buscarUltimas(10);
        escritor.println("--- HISTORICO DE MENSAGENS RECENTES ---");
        for (Mensagem msg : ultimasMensagens) {
            escritor.println(msg.formatarParaExibicao());
        }
        escritor.println("----------------------------------------");
    }

    // Metodo chamado pelo Servidor para entregar uma mensagem na tela deste cliente
    public void enviarMensagem(String mensagemFormatada) {
        if (escritor != null) {
            escritor.println(mensagemFormatada);
        }
    }

    // Fecha os canais e notifica o servidor que este cliente saiu
    private void encerrarConexao() {
        servidor.removerCliente(this);
        if (apelido != null) {
            servidor.transmitirMensagemSistema(this.apelido + " saiu do chat.");
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar o socket: " + e.getMessage());
        }
    }

    public String getApelido() {
        return apelido;
    }
}