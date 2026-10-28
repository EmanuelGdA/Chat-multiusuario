package server;

import model.Mensagem;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ClienteHandler implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private static final DateTimeFormatter HORA_FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClienteHandler(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream saida = socket.getOutputStream()) {

            String linha = leitor.readLine();
            if (linha == null || !linha.startsWith("GET")) {
                return;
            }

            String[] partes = linha.split(" ");
            if (partes.length < 2) return;
            String rota = partes[1];

            // 1. API: Buscar mensagens do MySQL
            if (rota.equals("/api/mensagens")) {
                List<Mensagem> mensagens = servidor.getRepositorio().buscarUltimas(50);
                String json = converterParaJson(mensagens);
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", json.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 2. API: Enviar mensagem
            if (rota.startsWith("/api/enviar")) {
                processarEnvio(rota);
                enviarRespostaHttp(saida, "application/json; charset=UTF-8", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 3. Servir arquivos estáticos (HTML, CSS, JS)
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
            String conteudo = "";

            for (String param : query.split("&")) {
                String[] par = param.split("=", 2);
                if (par.length == 2) {
                    String chave = par[0];
                    String valor = URLDecoder.decode(par[1], StandardCharsets.UTF_8);
                    if (chave.equals("remetente")) remetente = valor;
                    if (chave.equals("conteudo")) conteudo = valor;
                }
            }

            if (!remetente.trim().isEmpty() && !conteudo.trim().isEmpty()) {
                Mensagem msg = new Mensagem(remetente, conteudo);
                servidor.getRepositorio().salvar(msg);
                System.out.println("-> [MENSAGEM GRAVADA NO BANCO] " + remetente + ": " + conteudo);
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
              .append("\"remetente\":\"").append(escaparTexto(m.getRemetente())).append("\",")
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
                    .replace("\r", "")
                    .replace("\n", " ");
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