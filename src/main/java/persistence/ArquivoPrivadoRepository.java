package persistence;

import model.ArquivoPrivado;
import model.ConversaPrivada;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ArquivoPrivadoRepository {
    public boolean salvar(ArquivoPrivado arquivo) {
        String sql = "INSERT INTO arquivos_chat " +
                "(arquivo_id, chave_conversa, remetente_id, remetente, destinatario_id, " +
                "nome, tipo_mime, conteudo, horario) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, arquivo.getId());
            stmt.setString(2, chaveConversa(
                    arquivo.getRemetenteId(), arquivo.getDestinatarioId()));
            stmt.setString(3, arquivo.getRemetenteId());
            stmt.setString(4, arquivo.getRemetenteNome());
            stmt.setString(5, arquivo.getDestinatarioId());
            stmt.setString(6, arquivo.getNome());
            stmt.setString(7, arquivo.getTipoMime());
            stmt.setBytes(8, arquivo.getConteudo());
            stmt.setTimestamp(9, Timestamp.valueOf(arquivo.getHorario()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Não foi possível salvar o arquivo no MySQL: " + e.getMessage());
            return false;
        }
    }

    public List<ArquivoPrivado> buscarSalaGeral() {
        return buscarConversa("sala-geral", "sala-geral");
    }

    private String chaveConversa(String remetenteId, String destinatarioId) {
        if ("sala-geral".equalsIgnoreCase(destinatarioId)) {
            return ConversaPrivada.gerarChave("sala-geral", "sala-geral");
        }
        return ConversaPrivada.gerarChave(remetenteId, destinatarioId);
    }

    public List<ArquivoPrivado> buscarConversa(String idA, String idB) {
        String sql = "SELECT arquivo_id, remetente_id, remetente, destinatario_id, nome, " +
                "tipo_mime, conteudo, horario FROM arquivos_chat " +
                "WHERE chave_conversa = ? ORDER BY horario ASC, arquivo_id ASC";
        List<ArquivoPrivado> arquivos = new ArrayList<>();
        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ConversaPrivada.gerarChave(idA, idB));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp horario = rs.getTimestamp("horario");
                    arquivos.add(new ArquivoPrivado(
                            rs.getString("arquivo_id"),
                            rs.getString("remetente_id"),
                            rs.getString("remetente"),
                            rs.getString("destinatario_id"),
                            rs.getString("nome"),
                            rs.getString("tipo_mime"),
                            rs.getBytes("conteudo"),
                            horario == null ? LocalDateTime.now() : horario.toLocalDateTime()));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("Não foi possível carregar arquivos da conversa: " + e.getMessage());
        }
        return arquivos;
    }
}
