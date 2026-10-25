package server;

import model.ArquivoPrivado;
import model.MensagemPrivada;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class ClienteHandler implements Runnable {
    private static final DateTimeFormatter FORMATADOR = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_ARQUIVO_BYTES = 5 * 1024 * 1024;

    private final Socket socket;
    private final Servidor servidor;
    private PrintWriter escritor;
    private BufferedReader leitor;
    private String apelido;
    private String usuarioId;

    public ClienteHandler(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try {
            leitor = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            escritor = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            enviarMensagem("BEM-VINDO! ConnectChat");

            String primeiraLinha = leitor.readLine();
            if (primeiraLinha == null) return;
            boolean clienteNovo = registrar(primeiraLinha);
            if (usuarioId == null) return;

            if (clienteNovo) {
                servidor.enviarHistoricoSala(this);
            } else {
                servidor.transmitirMensagemSistema(apelido + " entrou no chat!");
                enviarHistoricoAntigo();
            }

            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.equalsIgnoreCase("/sair")) break;
                if (linha.startsWith("/dm\t")) {
                    processarMensagemPrivada(linha);
                } else if (linha.startsWith("/history\t")) {
                    processarPedidoHistorico(linha);
                } else if (linha.startsWith("/presence\t")) {
                    processarPedidoPresenca(linha);
                } else if (linha.startsWith("/profile\t")) {
                    processarPerfil(linha);
                } else if (linha.startsWith("/file\t")) {
                    processarArquivoPrivado(linha);
                } else if (linha.equals("/room_history")) {
                    servidor.enviarHistoricoSala(this);
                } else if (linha.startsWith("/room\t")) {
                    processarMensagemSala(linha);
                } else {
                    servidor.processarNovaMensagem(this, linha);
                }
            }
        } catch (IOException e) {
            System.out.println("Conexão com " + apelido + " foi encerrada.");
        } finally {
            encerrarConexao();
        }
    }

    private boolean registrar(String linha) {
        if (!linha.startsWith("/registrar\t")) {
            apelido = linha.trim().isEmpty() ? "Anônimo_" + socket.getPort() : linha.trim();
            usuarioId = apelido;
            return false;
        }

        try {
            String[] partes = linha.split("\t", -1);
            if (partes.length != 3) throw new IllegalArgumentException();
            usuarioId = Servidor.decodificar(partes[1]).trim();
            apelido = Servidor.decodificar(partes[2]).trim();
            if (usuarioId.isEmpty() || apelido.isEmpty()) throw new IllegalArgumentException();
            servidor.registrarUsuario(this, usuarioId);
            enviarMensagem("REGISTRADO");
            return true;
        } catch (IllegalArgumentException e) {
            usuarioId = null;
            apelido = null;
            enviarStatus("Identificador ou nome inválido.");
            return true;
        }
    }

    private void processarMensagemPrivada(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 3 || partes[1].isBlank() || partes[2].isBlank()) {
            enviarStatus("Contato e mensagem são obrigatórios.");
            return;
        }
        try {
            String destinatario = Servidor.decodificar(partes[1]).trim();
            String texto = Servidor.decodificar(partes[2]).trim();
            if (destinatario.isEmpty() || texto.isEmpty()) {
                enviarStatus("Contato e mensagem são obrigatórios.");
                return;
            }
            servidor.enviarMensagemPrivada(this, destinatario, texto);
        } catch (IllegalArgumentException e) {
            enviarStatus("Não foi possível enviar a mensagem.");
        }
    }

    private void processarMensagemSala(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 2 || partes[1].isBlank()) {
            enviarStatus("A mensagem não pode ficar vazia.");
            return;
        }
        try {
            String texto = Servidor.decodificar(partes[1]).trim();
            if (texto.isEmpty()) {
                enviarStatus("A mensagem não pode ficar vazia.");
                return;
            }
            servidor.processarNovaMensagem(this, texto);
        } catch (IllegalArgumentException e) {
            enviarStatus("Não foi possível enviar a mensagem para a sala.");
        }
    }

    private void processarPedidoHistorico(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 2 || partes[1].isBlank()) {
            enviarStatus("Não foi possível abrir o histórico desta conversa.");
            return;
        }
        try {
            servidor.enviarHistoricoPrivado(this, Servidor.decodificar(partes[1]).trim());
        } catch (IllegalArgumentException e) {
            enviarStatus("Identificador do contato inválido.");
        }
    }

    private void processarPedidoPresenca(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 2 || partes[1].isBlank()) {
            enviarStatus("Não foi possível consultar a presença.");
            return;
        }
        try {
            servidor.consultarPresenca(this, Servidor.decodificar(partes[1]).trim());
        } catch (IllegalArgumentException e) {
            enviarStatus("Identificador do contato inválido.");
        }
    }

    private void processarPerfil(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 2) {
            enviarStatus("Não foi possível atualizar seu perfil.");
            return;
        }
        try {
            String novoNome = Servidor.decodificar(partes[1]).trim();
            if (novoNome.isEmpty() || novoNome.length() > 120) {
                enviarStatus("O nome deve ter entre 1 e 120 caracteres.");
                return;
            }
            apelido = novoNome;
            enviarStatus("Nome do perfil atualizado.");
        } catch (IllegalArgumentException e) {
            enviarStatus("Não foi possível atualizar seu perfil.");
        }
    }

    private void processarArquivoPrivado(String linha) {
        String[] partes = linha.split("\t", -1);
        String arquivoId = null;
        if (partes.length > 1) {
            try {
                arquivoId = UUID.fromString(partes[1]).toString();
            } catch (IllegalArgumentException ignored) {
                // Sem identificador válido, não há como correlacionar a resposta.
            }
        }
        if (partes.length != 6 || linha.length() > 7_100_000) {
            enviarResultadoArquivo(arquivoId, false, "Arquivo inválido ou maior que o limite de 5 MB.");
            return;
        }
        try {
            String destinatario = Servidor.decodificar(partes[2]).trim();
            String nome = Servidor.decodificar(partes[3]).trim()
                    .replace('\\', '/');
            nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
            String tipoMime = Servidor.decodificar(partes[4]).trim();
            byte[] conteudo = java.util.Base64.getUrlDecoder().decode(partes[5]);
            if (destinatario.isEmpty() || nome.isEmpty() || conteudo.length == 0
                    || conteudo.length > MAX_ARQUIVO_BYTES) {
                enviarResultadoArquivo(arquivoId, false,
                        "Arquivo inválido; envie um arquivo entre 1 byte e 5 MB.");
                return;
            }
            if (nome.length() > 255) nome = nome.substring(nome.length() - 255);
            if (tipoMime.length() > 150) tipoMime = "application/octet-stream";
            Servidor.ResultadoArquivo resultado = servidor.enviarArquivoPrivado(
                    this, destinatario, arquivoId, nome, tipoMime, conteudo);
            enviarResultadoArquivo(arquivoId, resultado.enviado(), resultado.aviso());
        } catch (IllegalArgumentException e) {
            enviarResultadoArquivo(arquivoId, false, "Não foi possível processar o arquivo.");
        }
    }

    private void enviarResultadoArquivo(String arquivoId, boolean enviado, String aviso) {
        if (arquivoId == null) {
            enviarStatus(aviso);
            return;
        }
        enviarMensagem("FILE_RESULT\t" + arquivoId + "\t" + enviado + "\t"
                + Servidor.codificar(aviso));
    }

    public synchronized void enviarEventoSala(model.Mensagem mensagem) {
        enviarEventoSala(mensagem, mensagem.getRemetente());
    }

    public synchronized void enviarEventoSala(model.Mensagem mensagem, String remetenteId) {
        enviarMensagem("ROOM_MSG\t"
                + Servidor.codificar(remetenteId) + "\t"
                + Servidor.codificar(mensagem.getRemetente()) + "\t"
                + mensagem.getHorario().format(FORMATADOR) + "\t"
                + Servidor.codificar(mensagem.getConteudo()));
    }

    public synchronized void enviarHistoricoSala(List<model.Mensagem> historico) {
        enviarMensagem("ROOM_HISTORY_BEGIN");
        for (model.Mensagem mensagem : historico) {
            enviarEventoSala(mensagem);
        }
        enviarMensagem("ROOM_HISTORY_END");
    }

    public synchronized void enviarEventoMensagem(String idContato, MensagemPrivada mensagem) {
        enviarMensagem("MSG\t"
                + Servidor.codificar(idContato) + "\t"
                + Servidor.codificar(mensagem.getRemetenteId()) + "\t"
                + Servidor.codificar(mensagem.getRemetenteNome()) + "\t"
                + mensagem.getHorario().format(FORMATADOR) + "\t"
                + Servidor.codificar(mensagem.getConteudo()));
    }

    public synchronized void enviarHistorico(String idContato, List<MensagemPrivada> historico) {
        String idCodificado = Servidor.codificar(idContato);
        enviarMensagem("HISTORY_BEGIN\t" + idCodificado);
        for (MensagemPrivada mensagem : historico) {
            enviarEventoMensagem(idContato, mensagem);
        }
        enviarMensagem("HISTORY_END\t" + idCodificado);
    }

    public synchronized void enviarStatus(String aviso) {
        enviarMensagem("STATUS\t" + Servidor.codificar(aviso));
    }

    public synchronized void enviarPresenca(String idUsuario, boolean online) {
        enviarMensagem("PRESENCE\t" + Servidor.codificar(idUsuario) + "\t" + online);
    }

    public synchronized void enviarEventoArquivo(String idConversa, ArquivoPrivado arquivo) {
        String conteudo = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(arquivo.getConteudo());
        enviarMensagem("FILE\t"
                + Servidor.codificar(idConversa) + "\t"
                + Servidor.codificar(arquivo.getRemetenteId()) + "\t"
                + Servidor.codificar(arquivo.getRemetenteNome()) + "\t"
                + arquivo.getHorario().format(FORMATADOR) + "\t"
                + arquivo.getId() + "\t"
                + Servidor.codificar(arquivo.getNome()) + "\t"
                + Servidor.codificar(arquivo.getTipoMime()) + "\t"
                + conteudo);
    }

    public synchronized void enviarMensagem(String mensagem) {
        if (escritor != null) {
            escritor.println(mensagem);
            escritor.flush();
        }
    }

    private void enviarHistoricoAntigo() {
        List<model.Mensagem> ultimas = servidor.getRepositorio().buscarUltimas(10);
        enviarMensagem("--- HISTÓRICO DE MENSAGENS RECENTES ---");
        for (model.Mensagem mensagem : ultimas) {
            enviarMensagem(mensagem.formatarParaExibicao());
        }
        enviarMensagem("----------------------------------------");
    }

    private void encerrarConexao() {
        servidor.removerCliente(this);
        if (apelido != null) {
            servidor.transmitirMensagemSistema(apelido + " saiu do chat.");
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public String getApelido() { return apelido == null ? "sem nome" : apelido; }
    public String getUsuarioId() { return usuarioId; }
}