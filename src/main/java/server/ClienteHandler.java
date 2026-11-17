package server;

import model.Mensagem;
import persistence.MensagemRepository.Perfil;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClienteHandler implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private static final DateTimeFormatter HORA_FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Set<String> AVATARES = Set.of("initials", "😊", "🎧", "🚀", "🌻", "🐱");

    public ClienteHandler(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream saida = socket.getOutputStream()) {

            String linha = leitor.readLine();
            if (linha == null) return;

            String[] partes = linha.split(" ");
            if (partes.length < 2) return;
            String metodo = partes[0];
            String rota = partes[1];
            if ("POST".equals(metodo) && "/api/perfil".equals(rota)) {
                salvarPerfil(leitor, saida);
                return;
            }
            if (!"GET".equals(metodo)) return;

            if (rota.startsWith("/api/perfil?")) {
                buscarPerfil(rota.substring("/api/perfil?".length()), saida);
                return;
            }

            //  API: Buscar mensagens do MySQL
            if (rota.equals("/api/mensagens")) {
                List<Mensagem> mensagens = servidor.getRepositorio().buscarUltimas(50);
                String json = converterParaJson(mensagens);
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", json.getBytes(StandardCharsets.UTF_8));
                return;
            }

            //  API: Enviar mensagem
            if (rota.startsWith("/api/enviar")) {
                processarEnvio(rota);
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Rota para cadastrar quem acabou de fazer login
            if (rota.startsWith("/api/registrar-usuario")) {
                try {
                    String query = rota.split("\\?")[1];
                    String nome = "";
                    String tel = "";
                    for (String p : query.split("&")) {
                        String[] kv = p.split("=", 2);
                        if (kv[0].equals("nome")) nome = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        if (kv[0].equals("telefone")) tel = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                    if (!tel.isEmpty()) servidor.getRepositorio().registrarUsuario(nome, tel);
                } catch (Exception ignored) {}
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Rota para checar se o número que você quer adicionar já existe
            if (rota.startsWith("/api/verificar-numero")) {
                String tel = "";
                if (rota.contains("telefone=")) {
                    tel = URLDecoder.decode(rota.split("telefone=")[1].split("&")[0], StandardCharsets.UTF_8);
                }
                String nomeEncontrado = servidor.getRepositorio().buscarNomePorTelefone(tel);
                String respostaJson = (nomeEncontrado != null) 
                        ? "{\"existe\":true,\"nome\":\"" + escaparTexto(nomeEncontrado) + "\"}" 
                        : "{\"existe\":false}";

                enviarRespostaHttp(saida, "application/json; charset=UTF-8", respostaJson.getBytes(StandardCharsets.UTF_8));
                return;
            }

            //  Rota para apagar mensagem individual
            if (rota.startsWith("/api/apagar-mensagem")) {
                try {
                    long id = Long.parseLong(rota.split("id=")[1].split("&")[0]);
                    servidor.getRepositorio().apagarMensagem(id);
                } catch (Exception ignored) {}
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Rota para limpar conversa inteira
            if (rota.startsWith("/api/limpar-conversa")) {
                try {
                    String query = rota.split("\\?")[1];
                    String conversa = "GERAL";
                    String usuario = "";
                    for (String p : query.split("&")) {
                        String[] kv = p.split("=", 2);
                        if (kv[0].equals("conversa")) conversa = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        if (kv[0].equals("usuario")) usuario = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                    servidor.getRepositorio().limparConversa(conversa, usuario);
                } catch (Exception ignored) {}
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Servir arquivos estáticos (HTML, CSS, JS)
            String caminhoArquivo = rota.equals("/") ? "/index.html" : rota;
            File arquivo = localizarArquivo(caminhoArquivo);

            if (arquivo != null && arquivo.exists() && !arquivo.isDirectory()) {
                String tipo = definirTipoConteudo(caminhoArquivo);
                byte[] conteudo = Files.readAllBytes(arquivo.toPath());
                enviarRespostaHttp(saida, tipo, conteudo);
            } else {
                enviarRespostaHttp(saida, "text/plain; charset=UTF-8", "Arquivo nao encontrado".getBytes(StandardCharsets.UTF_8));
            }

        } catch (IOException e) {
            // Conexão finalizada normalmente
        } finally {
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }

    }

    private void buscarPerfil(String query, OutputStream saida) throws IOException {
        try {
            String telefone = lerParametros(query).getOrDefault("telefone", "");
            if (!telefone.matches("[0-9]{1,32}")) {
                responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
                return;
            }
            Perfil perfil = servidor.getRepositorio().buscarPerfil(telefone);
            if (perfil == null) {
                responderPerfil(saida, "404 Not Found", "{\"status\":\"inexistente\"}");
                return;
            }
            String json = "{\"nome\":\"" + escaparTexto(perfil.nome())
                    + "\",\"avatar\":\"" + escaparTexto(perfil.avatar())
                    + "\",\"bio\":\"" + escaparTexto(perfil.bio()) + "\"}";
            responderPerfil(saida, "200 OK", json);
        } catch (IllegalArgumentException e) {
            responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
        } catch (SQLException e) {
            System.err.println("Erro ao buscar perfil: " + e.getMessage());
            responderPerfil(saida, "503 Service Unavailable", "{\"status\":\"indisponivel\"}");
        }
    }

    private void salvarPerfil(BufferedReader leitor, OutputStream saida) throws IOException {
        try {
            int tamanho = -1;
            String cabecalho;
            while ((cabecalho = leitor.readLine()) != null && !cabecalho.isEmpty()) {
                if (cabecalho.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    tamanho = Integer.parseInt(cabecalho.substring(15).trim());
                }
            }
            // URLSearchParams envia um formulário ASCII com valores codificados.
            if (cabecalho == null || tamanho < 1 || tamanho > 4096) {
                responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
                return;
            }
            char[] corpo = new char[tamanho];
            int recebidos = 0;
            while (recebidos < tamanho) {
                int lidos = leitor.read(corpo, recebidos, tamanho - recebidos);
                if (lidos == -1) break;
                recebidos += lidos;
            }
            if (recebidos != tamanho) {
                responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
                return;
            }

            Map<String, String> campos = lerParametros(new String(corpo));
            String telefone = campos.getOrDefault("telefone", "");
            String avatar = campos.getOrDefault("avatar", "");
            String bio = campos.getOrDefault("bio", "").strip();
            String chave = campos.getOrDefault("chave", "");
            if (!telefone.matches("[0-9]{1,32}") || !AVATARES.contains(avatar)
                    || bio.length() > 140 || !chave.matches("[0-9a-f]{64}")) {
                responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
                return;
            }

            if (servidor.getRepositorio().salvarPerfil(telefone, avatar, bio, chave)) {
                responderPerfil(saida, "200 OK", "{\"status\":\"ok\"}");
            } else {
                responderPerfil(saida, "403 Forbidden", "{\"status\":\"negado\"}");
            }
        } catch (IllegalArgumentException e) {
            responderPerfil(saida, "400 Bad Request", "{\"status\":\"invalido\"}");
        } catch (SQLException e) {
            System.err.println("Erro ao salvar perfil: " + e.getMessage());
            responderPerfil(saida, "503 Service Unavailable", "{\"status\":\"indisponivel\"}");
        }
    }

    private Map<String, String> lerParametros(String query) {
        Map<String, String> campos = new HashMap<>();
        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            if (par.length == 2) {
                campos.put(par[0], URLDecoder.decode(par[1], StandardCharsets.UTF_8));
            }
        }
        return campos;
    }

    private void responderPerfil(OutputStream saida, String status, String json) throws IOException {
        byte[] dados = json.getBytes(StandardCharsets.UTF_8);
        String cabecalho = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + dados.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        saida.write(cabecalho.getBytes(StandardCharsets.UTF_8));
        saida.write(dados);
        saida.flush();
    }

    private File localizarArquivo(String caminhoRelativo) {
        String[] tentativas = {
            "web" + caminhoRelativo,
            "../web" + caminhoRelativo,
            "../../web" + caminhoRelativo
        };
        for (String tentativa : tentativas) {
            File f = new File(tentativa);
            if (f.exists() && !f.isDirectory()) return f;
        }
        return null;
    }

    private void processarEnvio(String rota) {
        try {
            if (!rota.contains("?")) return;

            String query = rota.substring(rota.indexOf("?") + 1);
            String remetente = "";
            String destinatario = "GERAL";
            String conteudo = "";

            for (String param : query.split("&")) {
                String[] par = param.split("=", 2);
                if (par.length == 2) {
                    String chave = par[0];
                    String valor = URLDecoder.decode(par[1], StandardCharsets.UTF_8);
                    if (chave.equals("remetente")) remetente = valor;
                    if (chave.equals("destinatario")) destinatario = valor;
                    if (chave.equals("conteudo")) conteudo = valor;
                }
            }

            if (!remetente.trim().isEmpty() && !conteudo.trim().isEmpty()) {
                Mensagem msg = new Mensagem(remetente, conteudo);
                msg.setDestinatario(destinatario);
                servidor.getRepositorio().salvar(msg);
                System.out.println("-> [" + destinatario + "] " + remetente + ": " + conteudo);
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar envio: " + e.getMessage());
        }
    }

    private String converterParaJson(List<Mensagem> mensagens) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < mensagens.size(); i++) {
            Mensagem m = mensagens.get(i);
            String hora = m.getHorario() != null ? m.getHorario().format(HORA_FORMATO) : LocalDateTime.now().format(HORA_FORMATO);
            sb.append("{")
              .append("\"id\":").append(m.getId() != null ? m.getId() : 0).append(",")
              .append("\"remetente\":\"").append(escaparTexto(m.getRemetente())).append("\",")
              .append("\"destinatario\":\"").append(escaparTexto(m.getDestinatario())).append("\",")
              .append("\"conteudo\":\"").append(escaparTexto(m.getConteudo())).append("\",")
              .append("\"horario\":\"").append(hora).append("\"")
              .append("}");
            if (i < mensagens.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escaparTexto(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replaceAll("[\\x00-\\x1F]", " ");
    }

    private String definirTipoConteudo(String caminho) {
        if (caminho.endsWith(".html")) return "text/html; charset=UTF-8";
        if (caminho.endsWith(".css")) return "text/css; charset=UTF-8";
        if (caminho.endsWith(".js")) return "application/javascript; charset=UTF-8";
        return "application/octet-stream";
    }

    // Envio seguro em bytes diretos sem misturar streams
    private void enviarRespostaHttp(OutputStream saida, String tipo, byte[] dados) throws IOException {
        String cabecalho = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + tipo + "\r\n"
                + "Content-Length: " + dados.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";

        saida.write(cabecalho.getBytes(StandardCharsets.UTF_8));
        saida.write(dados);
        saida.flush();
    }
}