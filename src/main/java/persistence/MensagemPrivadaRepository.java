package persistence;

import model.ConversaPrivada;
import model.MensagemPrivada;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MensagemPrivadaRepository {
    public void salvar(MensagemPrivada mensagem) {
        String sql = "INSERT INTO mensagens_privadas " +
                "(chave_conversa, remetente_id, remetente, destinatario_id, conteudo, horario) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ConversaPrivada.gerarChave(
                    mensagem.getRemetenteId(), mensagem.getDestinatarioId()));
            stmt.setString(2, mensagem.getRemetenteId());
            stmt.setString(3, mensagem.getRemetenteNome());
            stmt.setString(4, mensagem.getDestinatarioId());
            stmt.setString(5, mensagem.getConteudo());
            stmt.setTimestamp(6, Timestamp.valueOf(mensagem.getHorario()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Não foi possível salvar a mensagem privada no MySQL: " + e.getMessage());
        }
    }

    public List<MensagemPrivada> buscarConversa(String idA, String idB) {
        String sql = "SELECT remetente_id, remetente, destinatario_id, conteudo, horario " +
                "FROM mensagens_privadas WHERE chave_conversa = ? ORDER BY horario ASC, id ASC";
        List<MensagemPrivada> mensagens = new ArrayList<>();
        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ConversaPrivada.gerarChave(idA, idB));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp horario = rs.getTimestamp("horario");
                    mensagens.add(new MensagemPrivada(
                            rs.getString("remetente_id"),
                            rs.getString("remetente"),
                            rs.getString("destinatario_id"),
                            rs.getString("conteudo"),
                            horario == null ? LocalDateTime.now() : horario.toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            System.err.println("Não foi possível carregar a conversa privada do MySQL: " + e.getMessage());
        }
        return mensagens;
    }
}