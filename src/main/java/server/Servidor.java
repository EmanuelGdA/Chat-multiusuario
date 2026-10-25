package server;

import model.ArquivoPrivado;
import model.ConversaPrivada;
import model.Mensagem;
import model.MensagemPrivada;
import persistence.ArquivoPrivadoRepository;
import persistence.MensagemPrivadaRepository;
import persistence.MensagemRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Servidor {
    private static final int PORTA = 12345;
    private static final String ID_SALA_GERAL = "sala-geral";

    public record ResultadoArquivo(boolean enviado, String aviso) { }

    private final List<ClienteHandler> clientes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CopyOnWriteArrayList<ClienteHandler>> clientesPorId = new ConcurrentHashMap<>();
    private final MensagemRepository repositorio = new MensagemRepository();
    private final MensagemPrivadaRepository repositorioPrivado = new MensagemPrivadaRepository();
    private final ArquivoPrivadoRepository repositorioArquivos = new ArquivoPrivadoRepository();
    private boolean rodando = true;

    public static void main(String[] args) {
        new Servidor().iniciar();
    }

    public void iniciar() {
        System.out.println("=== SERVIDOR CONNECTCHAT INICIADO NA PORTA " + PORTA + " ===");
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (rodando) {
                Socket socketCliente = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + socketCliente.getInetAddress());
                ClienteHandler handler = new ClienteHandler(socketCliente, this);
                clientes.add(handler);
                new Thread(handler, "connectchat-cliente-" + socketCliente.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("Erro na execução do servidor: " + e.getMessage());
        }
    }

    public void registrarUsuario(ClienteHandler cliente, String id) {
        clientesPorId.computeIfAbsent(normalizarId(id), ignorado -> new CopyOnWriteArrayList<>())
                .addIfAbsent(cliente);
        transmitirPresenca(id, true);
    }

    public void removerCliente(ClienteHandler handler) {
        clientes.remove(handler);
        String id = handler.getUsuarioId();
        if (id != null) {
            String chave = normalizarId(id);
            CopyOnWriteArrayList<ClienteHandler> conectados = clientesPorId.get(chave);
            if (conectados != null) {
                conectados.remove(handler);
                if (conectados.isEmpty() && clientesPorId.remove(chave, conectados)) {
                    transmitirPresenca(id, false);
                }
            }
        }
        System.out.println("Cliente " + handler.getApelido() + " desconectado.");
    }

    public void enviarMensagemPrivada(ClienteHandler remetente, String destinatarioId, String texto) {
        String idDestino = destinatarioId.trim();
        model.MensagemPrivada mensagem = new model.MensagemPrivada(
                remetente.getUsuarioId(), remetente.getApelido(), idDestino,
                texto, java.time.LocalDateTime.now());

        repositorioPrivado.salvar(mensagem);
        remetente.enviarEventoMensagem(idDestino, mensagem);

        CopyOnWriteArrayList<ClienteHandler> destinos = clientesPorId.get(normalizarId(idDestino));
        if (destinos != null) {
            for (ClienteHandler destino : destinos) {
                if (destino != remetente) {
                    destino.enviarEventoMensagem(remetente.getUsuarioId(), mensagem);
                }
            }
        } else if (!normalizarId(idDestino).equals(normalizarId(remetente.getUsuarioId()))) {
            remetente.enviarStatus("Contato offline. A mensagem ficará no histórico quando o MySQL estiver disponível.");
        }
    }

    public void enviarHistoricoPrivado(ClienteHandler cliente, String idContato) {
        List<MensagemPrivada> historico = repositorioPrivado.buscarConversa(cliente.getUsuarioId(), idContato);
        cliente.enviarHistorico(idContato, historico);
        for (ArquivoPrivado arquivo : repositorioArquivos.buscarConversa(
                cliente.getUsuarioId(), idContato)) {
            cliente.enviarEventoArquivo(idContato, arquivo);
        }
    }

    public ResultadoArquivo enviarArquivoPrivado(ClienteHandler remetente, String destinatarioId,
                                                  String arquivoId, String nome, String tipoMime,
                                                  byte[] conteudo) {
        boolean salaGeral = normalizarId(destinatarioId).equals(normalizarId(ID_SALA_GERAL));
        String destinatarioReal = salaGeral ? ID_SALA_GERAL : destinatarioId;
        ArquivoPrivado arquivo = new ArquivoPrivado(arquivoId, remetente.getUsuarioId(),
                remetente.getApelido(), destinatarioReal, nome, tipoMime, conteudo,
                java.time.LocalDateTime.now());
        boolean salvo = repositorioArquivos.salvar(arquivo);

        if (salaGeral) {
            synchronized (clientes) {
                for (ClienteHandler cliente : clientes) {
                    cliente.enviarEventoArquivo(ID_SALA_GERAL, arquivo);
                }
            }
            String aviso = salvo
                    ? "Arquivo enviado para a Sala geral."
                    : "Arquivo entregue ao vivo, mas não ficou salvo. Confirme MySQL 3306 e a tabela arquivos_chat.";
            return new ResultadoArquivo(true, aviso);
        }

        CopyOnWriteArrayList<ClienteHandler> destinos = clientesPorId.get(normalizarId(destinatarioReal));
        if (destinos != null && !destinos.isEmpty()) {
            remetente.enviarEventoArquivo(destinatarioReal, arquivo);
            for (ClienteHandler destino : destinos) {
                if (destino != remetente) {
                    destino.enviarEventoArquivo(remetente.getUsuarioId(), arquivo);
                }
            }
            String aviso = salvo
                    ? "Arquivo enviado."
                    : "Arquivo entregue ao contato online, mas não ficou salvo no MySQL.";
            return new ResultadoArquivo(true, aviso);
        }

        if (salvo) {
            remetente.enviarEventoArquivo(destinatarioReal, arquivo);
            return new ResultadoArquivo(true,
                    "Contato offline; o arquivo foi salvo para entrega quando ele conectar.");
        }
        return new ResultadoArquivo(false,
                "Arquivo não enviado: contato offline e falha ao salvar. Verifique MySQL 3306, arquivos_chat e CHAT_DB_PASSWORD.");
    }

    public void consultarPresenca(ClienteHandler solicitante, String idUsuario) {
        CopyOnWriteArrayList<ClienteHandler> conectados = clientesPorId.get(normalizarId(idUsuario));
        solicitante.enviarPresenca(idUsuario, conectados != null && !conectados.isEmpty());
    }

    private void transmitirPresenca(String idUsuario, boolean online) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarPresenca(idUsuario, online);
            }
        }
    }

    public void processarNovaMensagem(ClienteHandler remetente, String texto) {
        Mensagem mensagem = new Mensagem(remetente.getApelido(), texto);
        if (!repositorio.salvar(mensagem)) {
            remetente.enviarStatus("Sala geral sem envio: confirme MySQL ativo em localhost:3306, banco/tabela criados e CHAT_DB_PASSWORD configurada.");
            return;
        }
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarEventoSala(mensagem, remetente.getUsuarioId());
            }
        }
    }

    public void enviarHistoricoSala(ClienteHandler cliente) {
        cliente.enviarHistoricoSala(repositorio.buscarUltimas(50));
        for (ArquivoPrivado arquivo : repositorioArquivos.buscarSalaGeral()) {
            cliente.enviarEventoArquivo(ID_SALA_GERAL, arquivo);
        }
    }

    public void transmitirMensagemSistema(String aviso) {
        transmitirParaTodos("[SISTEMA] " + aviso);
    }

    private void transmitirParaTodos(String texto) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarMensagem(texto);
            }
        }
    }

    public MensagemRepository getRepositorio() {
        return repositorio;
    }

    public static String codificar(String valor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodificar(String valor) {
        return new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
    }

    public static String normalizarId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}