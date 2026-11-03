package persistence;

import model.Mensagem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MensagemRepository {

    public void salvar(Mensagem mensagem) {
        String sql = "INSERT INTO mensagens (remetente, destinatario, conteudo, horario) VALUES (?, ?, ?, ?)";

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mensagem.getRemetente());
                stmt.setString(2, mensagem.getDestinatario());
                stmt.setString(3, mensagem.getConteudo());
                stmt.setTimestamp(4, Timestamp.valueOf(mensagem.getHorario()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao salvar mensagem no banco: " + e.getMessage());
        }
    }

    public List<Mensagem> buscarUltimas(int limite) {
        List<Mensagem> historico = new ArrayList<>();
        String sql = "SELECT id, remetente, destinatario, conteudo, horario FROM mensagens ORDER BY id DESC LIMIT ?";

        try {
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
        } catch (SQLException e) {
            System.err.println("Erro ao carregar historico do banco: " + e.getMessage());
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
}