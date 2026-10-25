package persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoFactory {
    private static final String URL_PADRAO =
            "jdbc:mysql://localhost:3306/chat_db?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true";

    private ConexaoFactory() { }

    public static Connection getConexao() throws SQLException {
        String url = valorAmbiente("CHAT_DB_URL", URL_PADRAO);
        String usuario = valorAmbiente("CHAT_DB_USER", "root");
        String senha = System.getenv("CHAT_DB_PASSWORD");
        if (senha == null || senha.isBlank()) {
            throw new SQLException("Configure a variável de ambiente CHAT_DB_PASSWORD antes de iniciar o servidor.");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver do MySQL não encontrado no classpath.", e);
        }
        return DriverManager.getConnection(url, usuario, senha);
    }

    private static String valorAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
