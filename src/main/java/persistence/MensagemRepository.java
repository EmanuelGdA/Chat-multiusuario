package persistence;

import model.Mensagem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MensagemRepository {

    public void salvar(Mensagem mensagem) {
        String sql = "INSERT INTO mensagens (remetente, conteudo, horario) VALUES (?, ?, ?)";

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mensagem.getRemetente());
                stmt.setString(2, mensagem.getConteudo());
                stmt.setTimestamp(3, Timestamp.valueOf(mensagem.getHorario()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao salvar mensagem no banco: " + e.getMessage());
        }
    }

    public List<Mensagem> buscarUltimas(int limite) {
        List<Mensagem> historico = new ArrayList<>();
        String sql = "SELECT id, remetente, conteudo, horario FROM mensagens ORDER BY id DESC LIMIT ?";

        try {
            Connection conn = ConexaoFactory.getConexao();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limite);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Long id = rs.getLong("id");
                        String remetente = rs.getString("remetente");
                        String conteudo = rs.getString("conteudo");
                        LocalDateTime horario = rs.getTimestamp("horario").toLocalDateTime();

                        historico.add(new Mensagem(id, remetente, conteudo, horario));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao carregar historico do banco: " + e.getMessage());
        }

        Collections.reverse(historico);
        return historico;
    }
}