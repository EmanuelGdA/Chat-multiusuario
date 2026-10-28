package persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoFactory {

    // Corrigido com a letra 'l' minúscula: btzv6bk32l2je6l7roid
    private static final String HOST = "btzv6bk32l2je6l7roid-mysql.services.clever-cloud.com";
    private static final String BANCO = "btzv6bk32l2je6l7roid";
    private static final String PORTA = "3306";

    private static final String URL = "jdbc:mysql://" + HOST + ":" + PORTA + "/" + BANCO
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo";

    private static final String USUARIO = "ukmq8r8odlei6z4h";
    private static final String SENHA = "jcp337adwp0dhA0pOIxY";

    private static Connection conexaoUnica = null;

    public static synchronized Connection getConexao() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver do MySQL não encontrado!");
        }

        if (conexaoUnica == null || conexaoUnica.isClosed()) {
            conexaoUnica = DriverManager.getConnection(URL, USUARIO, SENHA);
        }

        return conexaoUnica;
    }
}