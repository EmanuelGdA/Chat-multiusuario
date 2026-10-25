package persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoFactory {

    private static final String URL = "jdbc:mysql://localhost:3306/chat_db?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String SENHA = "E1manuel@g"; 

    public static Connection getConexao() throws SQLException {
        try {
            // Forca o Java a carregar o conector do MySQL na memoria
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver do MySQL nao encontrado!");
        }
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }
}