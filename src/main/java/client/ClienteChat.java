package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteChat extends JFrame {

    private static final String HOST = "localhost";
    private static final int PORTA = 12345;

    private Socket socket;
    private PrintWriter escritor;
    private BufferedReader leitor;

    private String meuNome;
    private String meuNumero;
    private String meuIdentificador;

    private JPanel painelMensagens;
    private JScrollPane scrollMensagens;
    private JTextField campoTexto;
    private DefaultListModel<String> modeloContatos;
    private JList<String> listaContatos;

    // Paleta de cores moderna e profissional (Tons de Azul e Ardósia)
    private final Color COR_PRIMARIA = new Color(37, 99, 235);       // Azul Royal
    private final Color COR_BARRA_TOPO = new Color(248, 250, 252);     // Branco gelo
    private final Color COR_FUNDO_CHAT = new Color(241, 245, 249);     // Cinza ardósia suave
    private final Color COR_MEU_BALAO = new Color(224, 231, 255);      // Azul bem suave para minhas mensagens
    private final Color COR_OUTRO_BALAO = Color.WHITE;                 // Branco para outros usuários
    private final Color COR_BORDA = new Color(226, 232, 240);          // Linha divisória sutil

    public ClienteChat() {
        super("ConnectChat");

        solicitarDadosUsuario();
        configurarInterface();
        conectarAoServidor();
    }

    private void solicitarDadosUsuario() {
        JPanel painelLogin = new JPanel(new GridLayout(4, 1, 5, 5));
        JTextField txtNome = new JTextField();
        JTextField txtNumero = new JTextField();

        painelLogin.add(new JLabel("Nome Completo ou Apelido:"));
        painelLogin.add(txtNome);
        painelLogin.add(new JLabel("Telefone / Identificador:"));
        painelLogin.add(txtNumero);

        int opcao = JOptionPane.showConfirmDialog(
                null,
                painelLogin,
                "ConnectChat - Identificação",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (opcao != JOptionPane.OK_OPTION 
                || txtNome.getText().trim().isEmpty() 
                || txtNumero.getText().trim().isEmpty()) {
            System.exit(0);
        }

        this.meuNome = txtNome.getText().trim();
        this.meuNumero = txtNumero.getText().trim();
        this.meuIdentificador = this.meuNome + " (" + this.meuNumero + ")";
    }

    private void configurarInterface() {
        setSize(920, 640);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- PAINEL LATERAL (CONTATOS E SALAS) ---
        JPanel painelEsquerda = new JPanel(new BorderLayout());
        painelEsquerda.setPreferredSize(new Dimension(280, 0));
        painelEsquerda.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, COR_BORDA));
        painelEsquerda.setBackground(Color.WHITE);

        // Perfil do Usuário
        JPanel topoEsquerda = new JPanel(new BorderLayout());
        topoEsquerda.setBackground(COR_BARRA_TOPO);
        topoEsquerda.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel lblPerfil = new JLabel("● " + this.meuNome);
        lblPerfil.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPerfil.setForeground(new Color(15, 23, 42));

        JLabel lblMeuNumero = new JLabel(this.meuNumero);
        lblMeuNumero.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMeuNumero.setForeground(new Color(100, 116, 139));

        JPanel infoPerfil = new JPanel(new GridLayout(2, 1));
        infoPerfil.setOpaque(false);
        infoPerfil.add(lblPerfil);
        infoPerfil.add(lblMeuNumero);

        JButton btnNovoContato = new JButton("+");
        btnNovoContato.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnNovoContato.setFocusPainted(false);
        btnNovoContato.setBackground(COR_PRIMARIA);
        btnNovoContato.setForeground(Color.WHITE);
        btnNovoContato.setToolTipText("Adicionar número");
        btnNovoContato.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovoContato.addActionListener(e -> adicionarNovoContato());

        topoEsquerda.add(infoPerfil, BorderLayout.CENTER);
        topoEsquerda.add(btnNovoContato, BorderLayout.EAST);
        painelEsquerda.add(topoEsquerda, BorderLayout.NORTH);

        // Lista de Canais/Contatos
        modeloContatos = new DefaultListModel<>();
        modeloContatos.addElement("💬 Sala Principal");

        listaContatos = new JList<>(modeloContatos);
        listaContatos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        listaContatos.setFixedCellHeight(45);
        listaContatos.setSelectedIndex(0);
        listaContatos.setSelectionBackground(new Color(241, 245, 249));
        listaContatos.setSelectionForeground(new Color(15, 23, 42));

        JScrollPane scrollContatos = new JScrollPane(listaContatos);
        scrollContatos.setBorder(null);
        painelEsquerda.add(scrollContatos, BorderLayout.CENTER);

        add(painelEsquerda, BorderLayout.WEST);

        // --- PAINEL PRINCIPAL (CHAT) ---
        JPanel painelDireita = new JPanel(new BorderLayout());

        // Topo da Conversa
        JPanel topoChat = new JPanel(new BorderLayout());
        topoChat.setBackground(COR_BARRA_TOPO);
        topoChat.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel lblConversaAtiva = new JLabel("💬 Sala Principal");
        lblConversaAtiva.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblConversaAtiva.setForeground(new Color(15, 23, 42));

        JLabel lblOnline = new JLabel("Conectado");
        lblOnline.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblOnline.setForeground(new Color(22, 163, 74));

        JPanel infoConversa = new JPanel(new GridLayout(2, 1));
        infoConversa.setOpaque(false);
        infoConversa.add(lblConversaAtiva);
        infoConversa.add(lblOnline);
        topoChat.add(infoConversa, BorderLayout.WEST);

        painelDireita.add(topoChat, BorderLayout.NORTH);

        // Área dos Balões de Mensagem
        painelMensagens = new JPanel();
        painelMensagens.setLayout(new BoxLayout(painelMensagens, BoxLayout.Y_AXIS));
        painelMensagens.setBackground(COR_FUNDO_CHAT);
        painelMensagens.setBorder(new EmptyBorder(15, 20, 15, 20));

        scrollMensagens = new JScrollPane(painelMensagens);
        scrollMensagens.setBorder(null);
        scrollMensagens.getVerticalScrollBar().setUnitIncrement(16);
        painelDireita.add(scrollMensagens, BorderLayout.CENTER);

        // Rodapé de Entrada
        JPanel rodape = new JPanel(new BorderLayout(10, 0));
        rodape.setBackground(Color.WHITE);
        rodape.setBorder(new EmptyBorder(12, 16, 12, 16));

        campoTexto = new JTextField();
        campoTexto.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        campoTexto.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COR_BORDA, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        campoTexto.addActionListener(e -> enviarMensagem());

        JButton btnEnviar = new JButton("Enviar");
        btnEnviar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnEnviar.setBackground(COR_PRIMARIA);
        btnEnviar.setForeground(Color.WHITE);
        btnEnviar.setFocusPainted(false);
        btnEnviar.setBorder(new EmptyBorder(8, 18, 8, 18));
        btnEnviar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEnviar.addActionListener(e -> enviarMensagem());

        rodape.add(campoTexto, BorderLayout.CENTER);
        rodape.add(btnEnviar, BorderLayout.EAST);
        painelDireita.add(rodape, BorderLayout.SOUTH);

        add(painelDireita, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });
    }

    private void adicionarNovoContato() {
        String novoNumero = JOptionPane.showInputDialog(
                this,
                "Digite o número de telefone do contato:\nEx: (41) 98888-0000",
                "Adicionar Número",
                JOptionPane.PLAIN_MESSAGE
        );
        if (novoNumero != null && !novoNumero.trim().isEmpty()) {
            modeloContatos.addElement("📞 " + novoNumero.trim());
        }
    }

    private void conectarAoServidor() {
        try {
            socket = new Socket(HOST, PORTA);
            leitor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            escritor = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> {
                try {
                    String linha;
                    while ((linha = leitor.readLine()) != null) {
                        if (linha.startsWith("BEM-VINDO!")) {
                            escritor.println(this.meuIdentificador);
                            continue;
                        }

                        String msg = linha;
                        SwingUtilities.invokeLater(() -> renderizarMensagem(msg));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> adicionarAvisoCentral("Conexão com o servidor encerrada."));
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível conectar ao servidor.\nVerifique se o Servidor está em execução.",
                    "Falha de Rede",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void renderizarMensagem(String textoCompleto) {
        if (textoCompleto.startsWith("---") || textoCompleto.startsWith("====")) {
            return;
        }

        if (textoCompleto.startsWith("[SISTEMA]")) {
            adicionarAvisoCentral(textoCompleto.replace("[SISTEMA]", "").trim());
            return;
        }

        boolean fuiEu = textoCompleto.contains("] " + this.meuIdentificador + ":");
        adicionarBalaoMensagem(textoCompleto, fuiEu);
    }

    private void adicionarBalaoMensagem(String texto, boolean fuiEu) {
        JPanel linha = new JPanel(new FlowLayout(fuiEu ? FlowLayout.RIGHT : FlowLayout.LEFT));
        linha.setOpaque(false);

        JPanel balao = new JPanel();
        balao.setLayout(new BoxLayout(balao, BoxLayout.Y_AXIS));
        balao.setBackground(fuiEu ? COR_MEU_BALAO : COR_OUTRO_BALAO);
        balao.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(fuiEu ? new Color(199, 210, 254) : COR_BORDA, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblTexto = new JLabel("<html><body style='width: 320px;'>" + texto + "</body></html>");
        lblTexto.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTexto.setForeground(new Color(30, 41, 59));

        balao.add(lblTexto);
        linha.add(balao);

        painelMensagens.add(linha);
        painelMensagens.add(Box.createVerticalStrut(6));
        painelMensagens.revalidate();
        painelMensagens.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar v = scrollMensagens.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    private void adicionarAvisoCentral(String aviso) {
        JPanel linha = new JPanel(new FlowLayout(FlowLayout.CENTER));
        linha.setOpaque(false);

        JPanel cardAviso = new JPanel();
        cardAviso.setBackground(new Color(241, 245, 249));
        cardAviso.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COR_BORDA, 1, true),
                new EmptyBorder(3, 10, 3, 10)
        ));

        JLabel lbl = new JLabel(aviso);
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lbl.setForeground(new Color(100, 116, 139));

        cardAviso.add(lbl);
        linha.add(cardAviso);

        painelMensagens.add(linha);
        painelMensagens.add(Box.createVerticalStrut(6));
        painelMensagens.revalidate();
        painelMensagens.repaint();
    }

    private void enviarMensagem() {
        String texto = campoTexto.getText().trim();
        if (!texto.isEmpty() && escritor != null) {
            escritor.println(texto);
            campoTexto.setText("");
            campoTexto.requestFocus();
        }
    }

    private void desconectar() {
        if (escritor != null) {
            escritor.println("/sair");
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Socket finalizado
        }
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteChat().setVisible(true));
    }
}