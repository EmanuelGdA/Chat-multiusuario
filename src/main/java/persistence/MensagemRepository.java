package persistence;

import model.Mensagem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MensagemRepository {
    public record Perfil(String nome, String avatar, String bio) {}

    private boolean tabelaPerfisPronta;

    private synchronized void prepararTabelaPerfis(Connection conn) throws SQLException {
        if (tabelaPerfisPronta) return;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS perfis_contato (
                    telefone VARCHAR(32) PRIMARY KEY,
                    avatar VARCHAR(16) NOT NULL,
                    bio VARCHAR(140) NOT NULL,
                    chave CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                ) CHARACTER SET utf8mb4
                """);
            tabelaPerfisPronta = true;
        }
    }

    public Perfil buscarPerfil(String telefone) throws SQLException {
        Connection conn = ConexaoFactory.getConexao();
        prepararTabelaPerfis(conn);
        String sql = """
            SELECT u.nome, p.avatar, p.bio
            FROM usuarios u LEFT JOIN perfis_contato p ON p.telefone = ?
            WHERE u.telefone = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, telefone);
            stmt.setString(2, telefone);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? new Perfil(rs.getString("nome"), rs.getString("avatar"), rs.getString("bio")) : null;
            }
        }
    }

    public boolean salvarPerfil(String telefone, String avatar, String bio, String chave) throws SQLException {
        Connection conn = ConexaoFactory.getConexao();
        prepararTabelaPerfis(conn);
        String sql = """
            INSERT INTO perfis_contato (telefone, avatar, bio, chave)
            SELECT ?, ?, ?, ? FROM usuarios WHERE telefone = ?
            ON DUPLICATE KEY UPDATE avatar = IF(chave = ?, ?, avatar), bio = IF(chave = ?, ?, bio)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, telefone);
            stmt.setString(2, avatar);
            stmt.setString(3, bio);
            stmt.setString(4, chave);
            stmt.setString(5, telefone);
            stmt.setString(6, chave);
            stmt.setString(7, avatar);
            stmt.setString(8, chave);
            stmt.setString(9, bio);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM perfis_contato WHERE telefone = ? AND chave = ?")) {
            stmt.setString(1, telefone);
            stmt.setString(2, chave);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }


    public void salvar(Mensagem mensagem) throws SQLException {
        String sql = "INSERT INTO mensagens (remetente, destinatario, conteudo, horario) VALUES (?, ?, ?, ?)";
        Connection conn = ConexaoFactory.getConexao();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mensagem.getRemetente());
            stmt.setString(2, mensagem.getDestinatario());
            stmt.setString(3, mensagem.getConteudo());
            stmt.setObject(4, mensagem.getHorario());
            stmt.executeUpdate();
        }
    }

    public record Grupo(long id, String nome, int totalMembros) {}
    private boolean tabelaGruposPronta;

    private synchronized void prepararTabelaGrupos(Connection conn) throws SQLException {
        if (tabelaGruposPronta) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS grupos_chat (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    nome VARCHAR(60) NOT NULL,
                    membros JSON NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            tabelaGruposPronta = true;
        }
    }

    public Grupo criarGrupo(String nome, Set<String> membros) throws SQLException {
        Connection conn = ConexaoFactory.getConexao();
        prepararTabelaGrupos(conn);
        // Os números chegam normalizados e validados na API.
        String membrosJson = "[\"" + String.join("\",\"", membros) + "\"]";

        String parametros = String.join(",", Collections.nCopies(membros.size(), "?"));
        String verificar = "SELECT COUNT(DISTINCT telefone) FROM usuarios WHERE telefone IN (" + parametros + ")";
        try (PreparedStatement stmt = conn.prepareStatement(verificar)) {
            int i = 1;
            for (String telefone : membros) stmt.setString(i++, telefone);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != membros.size()) {
                    throw new IllegalArgumentException("Há um participante sem cadastro.");
                }
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO grupos_chat (nome, membros) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nome);
            stmt.setString(2, membrosJson);
            stmt.executeUpdate();
            try (ResultSet chaves = stmt.getGeneratedKeys()) {
                if (chaves.next()) return new Grupo(chaves.getLong(1), nome, membros.size());
                throw new SQLException("Grupo sem identificador.");
            }
        }
    }

    public List<Grupo> listarGrupos(String telefone) throws SQLException {
        Connection conn = ConexaoFactory.getConexao();
        prepararTabelaGrupos(conn);
        String sql = """
            SELECT id, nome, JSON_LENGTH(membros) AS total
            FROM grupos_chat WHERE JSON_CONTAINS(membros, JSON_QUOTE(?))
            ORDER BY id DESC
            """;
        List<Grupo> grupos = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, telefone);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    grupos.add(new Grupo(rs.getLong("id"), rs.getString("nome"), rs.getInt("total")));
                }
            }
        }
        return grupos;
    }

    public boolean participaDoGrupo(long grupo, String telefone) throws SQLException {
        Connection conn = ConexaoFactory.getConexao();
        prepararTabelaGrupos(conn);
        String sql = "SELECT 1 FROM grupos_chat WHERE id = ? AND JSON_CONTAINS(membros, JSON_QUOTE(?))";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, grupo);
            stmt.setString(2, telefone);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Mensagem> buscarUltimas(int limite) throws SQLException {
        String sql = """
            SELECT id, remetente, destinatario, conteudo, horario FROM mensagens
            WHERE destinatario IS NULL OR UPPER(destinatario) NOT LIKE 'G:%'
            ORDER BY id DESC LIMIT ?
            """;
        return buscarMensagens(sql, limite, null);
    }

    public List<Mensagem> buscarMensagensDeGrupos(String telefone, int limite) throws SQLException {
        prepararTabelaGrupos(ConexaoFactory.getConexao());
        String sql = """
            SELECT m.id, m.remetente, m.destinatario, m.conteudo, m.horario
            FROM mensagens m JOIN grupos_chat g ON m.destinatario = CONCAT('G:', g.id)
            WHERE JSON_CONTAINS(g.membros, JSON_QUOTE(?))
            ORDER BY m.id DESC LIMIT ?
            """;
        return buscarMensagens(sql, limite, telefone);
    }

    private List<Mensagem> buscarMensagens(String sql, int limite, String telefone) throws SQLException {
        List<Mensagem> historico = new ArrayList<>();
        Connection conn = ConexaoFactory.getConexao();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int parametro = 1;
            if (telefone != null) stmt.setString(parametro++, telefone);
            stmt.setInt(parametro, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String remetente = rs.getString("remetente");
                    String destinatario = rs.getString("destinatario");
                    String conteudo = rs.getString("conteudo");
                    LocalDateTime horario = rs.getObject("horario", LocalDateTime.class);

                    historico.add(new Mensagem(id, remetente, destinatario, conteudo, horario));
                }
            }
        }

        Collections.reverse(historico);
        return historico;
    }

    // Apaga uma mensagem individual do banco pelo ID
    public void apagarMensagem(long id) {
        String sql = "DELETE FROM mensagens WHERE id = ? AND (destinatario IS NULL OR UPPER(destinatario) NOT LIKE 'G:%')";
        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao apagar mensagem: " + e.getMessage());
        }
    }

    // Limpa todas as mensagens da conversa atual
    public void limparConversa(String conversa, String meuNum) {
        String sql;
        if ("GERAL".equalsIgnoreCase(conversa)) {
            sql = "DELETE FROM mensagens WHERE destinatario = 'GERAL' OR destinatario IS NULL";
        } else {
            sql = "DELETE FROM mensagens WHERE UPPER(destinatario) NOT LIKE 'G:%' AND ((remetente LIKE ? AND destinatario LIKE ?) OR (remetente LIKE ? AND destinatario LIKE ?))";
        }

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (!"GERAL".equalsIgnoreCase(conversa)) {
                    stmt.setString(1, "%" + meuNum + "%");
                    stmt.setString(2, "%" + conversa + "%");
                    stmt.setString(3, "%" + conversa + "%");
                    stmt.setString(4, "%" + meuNum + "%");
                }
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao limpar conversa: " + e.getMessage());
        }
    }

    // Salva ou atualiza o cadastro do usuário pelo número puro
    public void registrarUsuario(String nome, String telefone) {
        String telLimpo = telefone.replaceAll("[^0-9]", "");
        String sql = "INSERT INTO usuarios (telefone, nome) VALUES (?, ?) ON DUPLICATE KEY UPDATE nome = ?";

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, telLimpo);
                stmt.setString(2, nome);
                stmt.setString(3, nome);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao registrar usuario: " + e.getMessage());
        }
    }

    // Busca um usuário cadastrado pelo telefone
    public String buscarNomePorTelefone(String telefone) {
        String telLimpo = telefone.replaceAll("[^0-9]", "");
        String sql = "SELECT nome FROM usuarios WHERE telefone = ?";

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, telLimpo);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("nome");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao verificar usuario: " + e.getMessage());
        }
        return null; // Retorna null se o número não existir
    }
}