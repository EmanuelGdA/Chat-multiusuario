package server;

import persistence.MensagemRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public class Servidor {

    // Porta 8080 (padrao para navegadores web e celulares)
    private static final int PORTA = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
    private final MensagemRepository repositorio = new MensagemRepository();
    private final Presenca presenca = new Presenca();

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

    Presenca getPresenca() {
        return presenca;
    }

    public MensagemRepository getRepositorio() {
        return repositorio;
    }
}

final class Presenca {
    private static final long LIMITE_ONLINE = Duration.ofSeconds(20).toNanos();
    private static final long LIMITE_DIGITANDO = Duration.ofSeconds(5).toNanos();

    enum Estado { OFFLINE, ONLINE, DIGITANDO }

    private record Aba(String telefone, String digitandoPara, long atualizadaEm) {}

    private final Map<String, Aba> abas = new HashMap<>();
    private final LongSupplier relogio;

    Presenca() {
        this(System::nanoTime);
    }

    Presenca(LongSupplier relogio) {
        this.relogio = relogio;
    }

    synchronized Estado atualizar(String sessao, String telefone, String contato, boolean digitando) {
        long agora = relogio.getAsLong();
        abas.values().removeIf(aba -> agora - aba.atualizadaEm() >= LIMITE_ONLINE);
        abas.put(sessao, new Aba(telefone, digitando ? contato : "", agora));

        if (contato.isEmpty()) return Estado.OFFLINE;
        Estado estado = Estado.OFFLINE;
        for (Aba aba : abas.values()) {
            if (!contato.equals(aba.telefone())) continue;

            estado = Estado.ONLINE;
            if (telefone.equals(aba.digitandoPara()) && agora - aba.atualizadaEm() < LIMITE_DIGITANDO) {
                return Estado.DIGITANDO;
            }
        }
        return estado;
    }
}
