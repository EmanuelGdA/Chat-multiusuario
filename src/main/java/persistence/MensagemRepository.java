package persistence;

import model.Mensagem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MensagemRepository {

    // Salva uma mensagem no MySQL
    public boolean salvar(Mensagem mensagem) {
        String sql = "INSERT INTO mensagens (remetente, conteudo, horario) VALUES (?, ?, ?)";

        // Try-with-resources: fecha a conexao automaticamente apos o uso
        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // PreparedStatement evita ataques de SQL Injection
            stmt.setString(1, mensagem.getRemetente());
            stmt.setString(2, mensagem.getConteudo());
            stmt.setTimestamp(3, Timestamp.valueOf(mensagem.getHorario()));

            stmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("Erro ao salvar mensagem no banco: " + e.getMessage());
            return false;
        }
    }

    // Busca as ultimas N mensagens para mostrar no historico ao entrar no chat
    public List<Mensagem> buscarUltimas(int limite) {
        List<Mensagem> historico = new ArrayList<>();
        // Pega as mais recentes ordenando pelo ID do maior para o menor
        String sql = "SELECT id, remetente, conteudo, horario FROM mensagens ORDER BY id DESC LIMIT ?";

        try (Connection conn = ConexaoFactory.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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

        } catch (SQLException e) {
            System.err.println("Erro ao carregar historico do banco: " + e.getMessage());
        }

        // Inverte a lista para exibir na tela na ordem cronologica (da mais antiga para a mais nova)
        Collections.reverse(historico);
        return historico;
    }
}