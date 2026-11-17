package persistence;

import model.Mensagem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            stmt.setTimestamp(4, Timestamp.valueOf(mensagem.getHorario()));
            stmt.executeUpdate();
        }
    }

    public List<Mensagem> buscarUltimas(int limite) throws SQLException {
        List<Mensagem> historico = new ArrayList<>();
        String sql = "SELECT id, remetente, destinatario, conteudo, horario FROM mensagens ORDER BY id DESC LIMIT ?";
        Connection conn = ConexaoFactory.getConexao();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String remetente = rs.getString("remetente");
                    String destinatario = rs.getString("destinatario");
                    String conteudo = rs.getString("conteudo");
                    LocalDateTime horario = rs.getTimestamp("horario").toLocalDateTime();

                    historico.add(new Mensagem(id, remetente, destinatario, conteudo, horario));
                }
            }
        }

        Collections.reverse(historico);
        return historico;
    }

    // Apaga uma mensagem individual do banco pelo ID
    public void apagarMensagem(long id) {
        String sql = "DELETE FROM mensagens WHERE id = ?";
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
            sql = "DELETE FROM mensagens WHERE (remetente LIKE ? AND destinatario LIKE ?) OR (remetente LIKE ? AND destinatario LIKE ?)";
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