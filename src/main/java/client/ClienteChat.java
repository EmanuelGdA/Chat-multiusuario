package client;

import model.ConversaPrivada;
import model.MensagemPrivada;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class ClienteChat extends JFrame {
    private static final String HOST = "localhost";
    private static final int PORTA = 12345;
    private static final String ID_SALA_GERAL = "sala-geral";
    private static Color BG = new Color(10, 13, 18);
    private static Color RAIL = new Color(8, 11, 16);
    private static Color CANVAS = new Color(13, 18, 25);
    private static Color SURFACE = new Color(17, 24, 34);
    private static Color HOVER = new Color(25, 35, 48);
    private static Color BORDER = new Color(37, 49, 64);
    private static Color TEXT = new Color(234, 241, 248);
    private static Color MUTED = new Color(139, 154, 171);
    private static Color CYAN = new Color(137, 211, 247);
    private static Color CYAN_SOFT = new Color(27, 47, 64);
    private static Color INCOMING = new Color(20, 29, 40);
    private static Color OUTGOING = new Color(25, 43, 59);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, Conversa> conversas = new LinkedHashMap<>();
    private final Path arquivoContatos;
    private final Path arquivoPreferencias;
    private final Path pastaArquivos = Path.of("data", "arquivos-recebidos");
    private final Map<String, String> arquivosPendentes = new LinkedHashMap<>();
    private Socket socket;
    private PrintWriter escritor;
    private BufferedReader leitor;
    private String meuNome;
    private String meuId;
    private boolean registrado;
    private boolean servidorDisponivel;
    private boolean aguardandoConfirmacaoSala;
    private String textoSalaPendente;
    private boolean encerrando;
    private boolean placeholder = true;
    private boolean temaClaro;
    private Color fundoConversa = CANVAS;
    private boolean fundoPersonalizado;
    private Conversa conversaAtual;

    private JPanel listaConversas;
    private JPanel listaMensagens;
    private JScrollPane scrollMensagens;
    private JTextField campoTexto;
    private JLabel estadoConexao;
    private JLabel tituloConversa;
    private JLabel descricaoConversa;
    private JLabel avisoConversa;

    public ClienteChat() {
        super("ConnectChat");
        configurarTemaDialogos();
        solicitarDadosUsuario();
        arquivoContatos = Path.of("data",
                "contatos-" + ConversaPrivada.gerarChave(meuId, meuId) + ".txt");
        arquivoPreferencias = Path.of("data",
                "configuracoes-" + ConversaPrivada.gerarChave(meuId, meuId) + ".properties");
        carregarPreferencias();
        configurarTemaDialogos();
        Conversa salaGeral = criarConversa(ID_SALA_GERAL, "Sala geral", false, false);
        salaGeral.salaGeral = true;
        criarConversa(meuId, "Meu espaço", true, false);
        carregarContatos();
        configurarInterface();
        conectarAoServidor();
        atualizarListaConversas();
        selecionarConversa(conversas.get(chaveLocal(meuId)));
    }

    private void configurarTemaDialogos() {
        UIManager.put("OptionPane.background", BG);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("Button.background", HOVER);
        UIManager.put("Button.foreground", TEXT);
    }

    private void solicitarDadosUsuario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(8, 4, 4, 4));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 5, 0);
        JLabel titulo = rotulo("Entre no ConnectChat");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        c.gridy = 0;
        form.add(titulo, c);
        JLabel dica = new JLabel("Use um identificador único para receber mensagens.");
        dica.setForeground(MUTED);
        c.gridy = 1;
        form.add(dica, c);
        JTextField nome = new JTextField(22);
        JTextField id = new JTextField(22);
        estilizarCampo(nome);
        estilizarCampo(id);
        c.gridy = 2;
        form.add(rotulo("Seu nome ou apelido"), c);
        c.gridy = 3;
        form.add(nome, c);
        c.gridy = 4;
        form.add(rotulo("Seu identificador (ex.: rafael123)"), c);
        c.gridy = 5;
        form.add(id, c);
        int escolha = JOptionPane.showConfirmDialog(null, form, "ConnectChat",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION || nome.getText().isBlank() || id.getText().isBlank()) {
            System.exit(0);
        }
        meuNome = nome.getText().trim();
        meuId = id.getText().trim();
    }

    private JLabel rotulo(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(MUTED);
        return label;
    }

    private void configurarInterface() {
        setSize(1280, 820);
        setMinimumSize(new Dimension(980, 650));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setContentPane(criarRaiz());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { desconectar(); }
        });
    }

    private JPanel criarRaiz() {
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(BG);
        raiz.add(criarNavegacao(), BorderLayout.WEST);
        raiz.add(criarPainelConversas(), BorderLayout.CENTER);
        return raiz;
    }

    private void reconstruirInterface() {
        Conversa selecionada = conversaAtual;
        setContentPane(criarRaiz());
        configurarTemaDialogos();
        atualizarListaConversas();
        if (selecionada != null) {
            conversaAtual = null;
            selecionarConversa(selecionada);
        }
        revalidate();
        repaint();
    }

    private JPanel criarNavegacao() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setPreferredSize(new Dimension(70, 0));
        rail.setBackground(RAIL);
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));
        JLabel logo = new JLabel("C", SwingConstants.CENTER);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logo.setForeground(CYAN);
        logo.setOpaque(true);
        logo.setBackground(CYAN_SOFT);
        logo.setBorder(new LineBorder(new Color(52, 83, 106), 1, true));
        logo.setPreferredSize(new Dimension(42, 42));
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 18));
        topo.setOpaque(false);
        topo.add(logo);
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 12));
        rodape.setOpaque(false);
        JButton perfil = new JButton();
        perfil.setLayout(new BorderLayout());
        perfil.add(new SettingsAvatarBadge(), BorderLayout.CENTER);
        perfil.setToolTipText("Abrir configurações da conta");
        perfil.setPreferredSize(new Dimension(48, 48));
        perfil.setContentAreaFilled(false);
        perfil.setBorderPainted(false);
        perfil.setFocusPainted(false);
        perfil.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        perfil.getAccessibleContext().setAccessibleName("Configurações da conta");
        perfil.addActionListener(e -> abrirConfiguracoes());
        rodape.add(perfil);
        rail.add(topo, BorderLayout.NORTH);
        rail.add(rodape, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel criarPainelConversas() {
        JPanel corpo = new JPanel(new BorderLayout());
        corpo.setBackground(CANVAS);
        corpo.add(criarBarraConversas(), BorderLayout.WEST);
        corpo.add(criarAreaConversa(), BorderLayout.CENTER);
        return corpo;
    }
    private JPanel criarBarraConversas() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBackground(SURFACE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));
        JPanel topo = new JPanel();
        topo.setBackground(SURFACE);
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        topo.setBorder(new EmptyBorder(22, 18, 14, 18));
        JLabel marca = new JLabel("CONNECTCHAT");
        marca.setFont(new Font("Segoe UI", Font.BOLD, 10));
        marca.setForeground(CYAN);
        JLabel titulo = new JLabel("Conversas");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(TEXT);
        JLabel subtitulo = new JLabel("Pessoas e seu espaço pessoal");
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitulo.setForeground(MUTED);
        topo.add(marca);
        topo.add(Box.createVerticalStrut(8));
        topo.add(titulo);
        topo.add(Box.createVerticalStrut(3));
        topo.add(subtitulo);
        topo.add(Box.createVerticalStrut(16));
        JButton adicionar = criarBotao("+  Adicionar contato", CYAN, new Color(10, 18, 25));
        adicionar.setAlignmentX(Component.LEFT_ALIGNMENT);
        adicionar.addActionListener(e -> adicionarContato());
        topo.add(adicionar);
        JLabel rotuloLista = new JLabel("  SUA LISTA");
        rotuloLista.setFont(new Font("Segoe UI", Font.BOLD, 10));
        rotuloLista.setForeground(MUTED);
        rotuloLista.setBorder(new EmptyBorder(18, 0, 8, 0));
        topo.add(rotuloLista);

        listaConversas = new JPanel();
        listaConversas.setLayout(new BoxLayout(listaConversas, BoxLayout.Y_AXIS));
        listaConversas.setBackground(SURFACE);
        listaConversas.setBorder(new EmptyBorder(4, 10, 16, 10));
        JScrollPane scroll = new JScrollPane(listaConversas);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SURFACE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(topo, BorderLayout.NORTH);
        sidebar.add(scroll, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel criarAreaConversa() {
        JPanel principal = new JPanel(new BorderLayout());
        principal.setBackground(CANVAS);
        JPanel cabecalho = new JPanel(new BorderLayout(16, 0));
        cabecalho.setBackground(SURFACE);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(17, 24, 17, 24)));
        JPanel tituloBox = new JPanel();
        tituloBox.setOpaque(false);
        tituloBox.setLayout(new BoxLayout(tituloBox, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("CONNECTCHAT  ·  CONVERSAS");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 10));
        eyebrow.setForeground(CYAN);
        tituloConversa = new JLabel("Escolha uma conversa");
        tituloConversa.setFont(new Font("Segoe UI", Font.BOLD, 21));
        tituloConversa.setForeground(TEXT);
        descricaoConversa = new JLabel("Converse com uma pessoa ou use seu espaço pessoal.");
        descricaoConversa.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descricaoConversa.setForeground(MUTED);
        tituloBox.add(eyebrow);
        tituloBox.add(Box.createVerticalStrut(3));
        tituloBox.add(tituloConversa);
        tituloBox.add(Box.createVerticalStrut(2));
        tituloBox.add(descricaoConversa);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        acoes.setOpaque(false);
        estadoConexao = new JLabel("●  Conectando");
        estadoConexao.setFont(new Font("Segoe UI", Font.BOLD, 11));
        estadoConexao.setForeground(MUTED);
        acoes.add(estadoConexao);
        cabecalho.add(tituloBox, BorderLayout.WEST);
        cabecalho.add(acoes, BorderLayout.EAST);

        avisoConversa = new JLabel(" ");
        avisoConversa.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        avisoConversa.setForeground(MUTED);
        avisoConversa.setBorder(new EmptyBorder(7, 25, 0, 10));
        listaMensagens = new JPanel();
        listaMensagens.setLayout(new BoxLayout(listaMensagens, BoxLayout.Y_AXIS));
        listaMensagens.setBackground(fundoConversa);
        listaMensagens.setBorder(new EmptyBorder(18, 24, 22, 24));
        JPanel painelMensagens = new JPanel(new BorderLayout());
        painelMensagens.setBackground(fundoConversa);
        painelMensagens.add(listaMensagens, BorderLayout.NORTH);
        scrollMensagens = new JScrollPane(painelMensagens);
        scrollMensagens.setBorder(null);
        scrollMensagens.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollMensagens.getViewport().setBackground(fundoConversa);
        scrollMensagens.getVerticalScrollBar().setUnitIncrement(18);
        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(fundoConversa);
        centro.add(avisoConversa, BorderLayout.NORTH);
        centro.add(scrollMensagens, BorderLayout.CENTER);
        principal.add(cabecalho, BorderLayout.NORTH);
        principal.add(centro, BorderLayout.CENTER);
        principal.add(criarCompositor(), BorderLayout.SOUTH);
        return principal;
    }
    private JPanel criarCompositor() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(SURFACE);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(13, 22, 15, 22)));
        JPanel caixa = new JPanel(new BorderLayout(12, 0));
        caixa.setBackground(CANVAS);
        caixa.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true), new EmptyBorder(7, 10, 7, 8)));
        campoTexto = new JTextField();
        campoTexto.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        campoTexto.setForeground(MUTED);
        campoTexto.setCaretColor(CYAN);
        campoTexto.setOpaque(false);
        campoTexto.setBorder(new EmptyBorder(7, 3, 7, 3));
        campoTexto.setText("Escreva uma mensagem...");
        campoTexto.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (placeholder) {
                    campoTexto.setText("");
                    campoTexto.setForeground(TEXT);
                    placeholder = false;
                }
            }
            @Override public void focusLost(FocusEvent e) { restaurarPlaceholder(); }
        });
        campoTexto.addActionListener(e -> enviarMensagem());
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        acoes.setOpaque(false);
        JButton anexar = criarBotao("＋", HOVER, TEXT);
        anexar.setToolTipText("Anexar foto, PDF ou outro arquivo (até 5 MB)");
        anexar.getAccessibleContext().setAccessibleName("Anexar arquivo");
        anexar.addActionListener(e -> anexarArquivo());
        JButton enviar = criarBotao("Enviar  ↑", CYAN, new Color(10, 18, 25));
        enviar.addActionListener(e -> enviarMensagem());
        acoes.add(anexar);
        acoes.add(enviar);
        caixa.add(campoTexto, BorderLayout.CENTER);
        caixa.add(acoes, BorderLayout.EAST);
        JLabel rodape = new JLabel("ENTER envia  ·  Anexos privados até 5 MB");
        rodape.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        rodape.setForeground(new Color(104, 119, 136));
        rodape.setBorder(new EmptyBorder(7, 3, 0, 0));
        area.add(caixa, BorderLayout.CENTER);
        area.add(rodape, BorderLayout.SOUTH);
        return area;
    }

    private JButton criarBotao(String texto, Color fundo, Color tinta) {
        JButton botao = new JButton(texto);
        botao.setFont(new Font("Segoe UI", Font.BOLD, 12));
        botao.setForeground(tinta);
        botao.setBackground(fundo);
        botao.setFocusPainted(false);
        botao.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true), new EmptyBorder(9, 13, 9, 13)));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return botao;
    }

    private void estilizarCampo(JTextField campo) {
        campo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        campo.setForeground(TEXT);
        campo.setCaretColor(CYAN);
        campo.setBackground(SURFACE);
        campo.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true), new EmptyBorder(9, 11, 9, 11)));
    }

    private void conectarAoServidor() {
        try {
            socket = new Socket(HOST, PORTA);
            leitor = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            escritor = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            setEstadoConexao("●  Conectando ao servidor", MUTED);
            Thread leitorThread = new Thread(() -> {
                try {
                    String linha;
                    while ((linha = leitor.readLine()) != null) processarLinhaServidor(linha);
                    if (!encerrando) SwingUtilities.invokeLater(() -> {
                        servidorDisponivel = false;
                        registrado = false;
                        marcarTodosOffline();
                        setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
                    });
                } catch (IOException e) {
                    if (!encerrando) SwingUtilities.invokeLater(() -> {
                        servidorDisponivel = false;
                        registrado = false;
                        marcarTodosOffline();
                        setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
                    });
                }
            }, "connectchat-leitor-" + meuId);
            leitorThread.setDaemon(true);
            leitorThread.start();
        } catch (IOException e) {
            servidorDisponivel = false;
            setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
            avisoConversa.setText("Inicie o MySQL na porta 3306 e depois o Servidor no VS Code.");
        }
    }

    private void processarLinhaServidor(String linha) {
        try {
            if (linha.startsWith("BEM-VINDO!")) {
                escritor.println("/registrar\t" + codificar(meuId) + "\t" + codificar(meuNome));
            } else if (linha.equals("REGISTRADO")) {
                SwingUtilities.invokeLater(() -> {
                    registrado = true;
                    servidorDisponivel = true;
                    atualizarEstadoConversa();
                    for (Conversa conversa : conversas.values()) {
                        if (!conversa.pessoal && !conversa.salaGeral) consultarPresenca(conversa);
                    }
                    if (conversaAtual != null) solicitarHistorico(conversaAtual);
                });
            } else if (linha.startsWith("PRESENCE\t")) {
                processarEventoPresenca(linha);
            } else if (linha.startsWith("FILE\t")) {
                processarEventoArquivo(linha);
            } else if (linha.startsWith("FILE_RESULT\t")) {
                processarResultadoArquivo(linha);
            } else if (linha.startsWith("MSG\t")) {
                processarEventoMensagem(linha);
            } else if (linha.startsWith("ROOM_MSG\t")) {
                processarEventoSala(linha);
            } else if (linha.equals("ROOM_HISTORY_BEGIN")) {
                SwingUtilities.invokeLater(() -> {
                    Conversa sala = conversas.get(chaveLocal(ID_SALA_GERAL));
                    if (sala != null) {
                        sala.mensagens.clear();
                        if (conversaAtual == sala) renderizarMensagens();
                    }
                });
            } else if (linha.equals("ROOM_HISTORY_END")) {
                SwingUtilities.invokeLater(() -> {
                    Conversa sala = conversas.get(chaveLocal(ID_SALA_GERAL));
                    if (sala != null) {
                        sala.historicoCarregado = true;
                        sala.mensagens.sort(Comparator.comparing(MensagemPrivada::getHorario));
                        atualizarListaConversas();
                        if (conversaAtual == sala) renderizarMensagens();
                    }
                });
            } else if (linha.startsWith("HISTORY_BEGIN\t")) {
                String id = decodificar(linha.split("\t", -1)[1]);
                SwingUtilities.invokeLater(() -> {
                    Conversa conversa = conversaPorId(id);
                    conversa.mensagens.clear();
                    if (conversaAtual == conversa) renderizarMensagens();
                });
            } else if (linha.startsWith("HISTORY_END\t")) {
                String id = decodificar(linha.split("\t", -1)[1]);
                SwingUtilities.invokeLater(() -> {
                    Conversa conversa = conversaPorId(id);
                    conversa.historicoCarregado = true;
                    conversa.mensagens.sort(Comparator.comparing(MensagemPrivada::getHorario));
                    atualizarListaConversas();
                    if (conversaAtual == conversa) renderizarMensagens();
                });
            } else if (linha.startsWith("STATUS\t")) {
                String aviso = decodificar(linha.substring("STATUS\t".length()));
                SwingUtilities.invokeLater(() -> {
                    finalizarEnvioSala(null);
                    avisoConversa.setText(aviso);
                    avisoConversa.setForeground(new Color(239, 190, 111));
                });
            } else if (linha.startsWith("[SISTEMA]")) {
                String aviso = linha.substring("[SISTEMA]".length()).trim();
                SwingUtilities.invokeLater(() -> {
                    avisoConversa.setText(aviso);
                    avisoConversa.setForeground(MUTED);
                });
            }
        } catch (RuntimeException e) {
            SwingUtilities.invokeLater(() -> avisoConversa.setText("Recebi uma mensagem em formato inválido."));
        }
    }

    private void processarEventoSala(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 5) return;
        String remetenteId = decodificar(partes[1]);
        String remetenteNome = decodificar(partes[2]);
        LocalDateTime horario = LocalDateTime.parse(partes[3]);
        String texto = decodificar(partes[4]);
        SwingUtilities.invokeLater(() -> {
            Conversa sala = conversas.get(chaveLocal(ID_SALA_GERAL));
            if (sala == null) return;
            sala.mensagens.add(new MensagemPrivada(
                    remetenteId, remetenteNome, ID_SALA_GERAL, texto, horario));
            if (aguardandoConfirmacaoSala
                    && normalizarId(remetenteId).equals(normalizarId(meuId))
                    && texto.equals(textoSalaPendente)) {
                String textoConfirmado = textoSalaPendente;
                finalizarEnvioSala(null);
                if (campoTexto != null && campoTexto.getText().trim().equals(textoConfirmado)) {
                    campoTexto.setText("");
                    campoTexto.setForeground(TEXT);
                    placeholder = false;
                }
                avisoConversa.setText("Mensagem enviada para a Sala geral.");
                avisoConversa.setForeground(MUTED);
            }
            atualizarListaConversas();
            if (conversaAtual == sala) renderizarMensagens();
        });
    }

    private void finalizarEnvioSala(String aviso) {
        if (!aguardandoConfirmacaoSala) return;
        aguardandoConfirmacaoSala = false;
        textoSalaPendente = null;
        if (aviso != null && avisoConversa != null) avisoConversa.setText(aviso);
    }

    private void processarEventoMensagem(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 6) return;
        String idConversa = decodificar(partes[1]);
        String remetenteId = decodificar(partes[2]);
        String remetenteNome = decodificar(partes[3]);
        LocalDateTime horario = LocalDateTime.parse(partes[4]);
        String texto = decodificar(partes[5]);
        SwingUtilities.invokeLater(() -> {
            Conversa conversa = conversaPorId(idConversa);
            boolean mensagemMinha = normalizarId(remetenteId).equals(normalizarId(meuId));
            if (!mensagemMinha && conversa.bloqueado) return;
            if (conversa.limpoAte != null && !horario.isAfter(conversa.limpoAte)) return;
            if (!mensagemMinha && conversa.nome.equals(conversa.idContato)) conversa.nome = remetenteNome;
            conversa.mensagens.add(new MensagemPrivada(
                    remetenteId, remetenteNome, idConversa, texto, horario));
            conversa.mensagens.sort(Comparator.comparing(MensagemPrivada::getHorario));
            atualizarListaConversas();
            if (conversaAtual == conversa) renderizarMensagens();
            else if (!mensagemMinha) notificarNovaMensagem(conversa, texto);
        });
    }

    private void processarEventoPresenca(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 3) return;
        String idUsuario = decodificar(partes[1]);
        boolean online = Boolean.parseBoolean(partes[2]);
        SwingUtilities.invokeLater(() -> {
            Conversa conversa = conversas.get(chaveLocal(idUsuario));
            if (conversa == null) return;
            conversa.online = online;
            atualizarListaConversas();
            if (conversaAtual == conversa) atualizarEstadoConversa();
        });
    }

    private void processarEventoArquivo(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 9) return;
        try {
            String idConversa = decodificar(partes[1]);
            String remetenteId = decodificar(partes[2]);
            String remetenteNome = decodificar(partes[3]);
            LocalDateTime horario = LocalDateTime.parse(partes[4]);
            String arquivoId = UUID.fromString(partes[5]).toString();
            String nome = nomeSeguroArquivo(decodificar(partes[6]));
            byte[] bytes = Base64.getUrlDecoder().decode(partes[8]);
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) return;
            String marcador = "@@ARQUIVO@@|" + arquivoId + "|" + codificar(nome) + "|" + bytes.length;
            SwingUtilities.invokeLater(() -> {
                Conversa conversa = conversaPorId(idConversa);
                boolean mensagemMinha = normalizarId(remetenteId).equals(normalizarId(meuId));
                if (!mensagemMinha && conversa.bloqueado) return;
                if (conversa.limpoAte != null && !horario.isAfter(conversa.limpoAte)) return;
                try {
                    Files.createDirectories(pastaArquivos);
                    Path destino = pastaArquivos.resolve(arquivoId + "_" + nome).normalize();
                    if (!destino.startsWith(pastaArquivos.normalize())) return;
                    if (!Files.exists(destino)) Files.write(destino, bytes);
                } catch (IOException e) {
                    avisoConversa.setText("O arquivo chegou, mas não pôde ser salvo neste computador.");
                    avisoConversa.setForeground(new Color(239, 133, 125));
                    return;
                }
                conversa.mensagens.add(new MensagemPrivada(
                        remetenteId, remetenteNome, idConversa, marcador, horario));
                conversa.mensagens.sort(Comparator.comparing(MensagemPrivada::getHorario));
                atualizarListaConversas();
                if (conversaAtual == conversa) renderizarMensagens();
                else if (!mensagemMinha) notificarNovaMensagem(conversa, "Enviou um arquivo: " + nome);
            });
        } catch (IllegalArgumentException e) {
            SwingUtilities.invokeLater(() -> {
                avisoConversa.setText("Não foi possível receber este arquivo.");
                avisoConversa.setForeground(new Color(239, 133, 125));
            });
        }
    }

    private void processarResultadoArquivo(String linha) {
        String[] partes = linha.split("\t", -1);
        if (partes.length != 4) return;
        try {
            String arquivoId = UUID.fromString(partes[1]).toString();
            boolean enviado = Boolean.parseBoolean(partes[2]);
            String aviso = decodificar(partes[3]);
            SwingUtilities.invokeLater(() -> {
                if (arquivosPendentes.remove(arquivoId) == null) return;
                avisoConversa.setText(aviso);
                avisoConversa.setForeground(enviado
                        ? new Color(83, 211, 155) : new Color(239, 133, 125));
            });
        } catch (IllegalArgumentException e) {
            SwingUtilities.invokeLater(() -> {
                avisoConversa.setText("Não foi possível confirmar o envio do arquivo.");
                avisoConversa.setForeground(new Color(239, 133, 125));
            });
        }
    }

    private void adicionarContato() {
        JTextField nome = new JTextField(20);
        JTextField id = new JTextField(20);
        estilizarCampo(nome);
        estilizarCampo(id);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(6, 6, 6, 6));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 5, 0);
        c.gridy = 0;
        form.add(rotulo("Nome ou apelido"), c);
        c.gridy = 1;
        form.add(nome, c);
        c.gridy = 2;
        form.add(rotulo("Identificador único da pessoa"), c);
        c.gridy = 3;
        form.add(id, c);
        int escolha = JOptionPane.showConfirmDialog(this, form, "Adicionar contato",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION) return;
        String nomeContato = nome.getText().trim();
        String idContato = id.getText().trim();
        if (nomeContato.isEmpty() || idContato.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha o nome e o identificador do contato.");
            return;
        }
        if (normalizarId(idContato).equals(normalizarId(meuId))) {
            JOptionPane.showMessageDialog(this, "Use Meu espaço para conversar sozinho.");
            return;
        }
        Conversa conversa = criarConversa(idContato, nomeContato, false, true);
        atualizarListaConversas();
        selecionarConversa(conversa);
        campoTexto.requestFocusInWindow();
    }

    private Conversa criarConversa(String id, String nome, boolean pessoal, boolean salvar) {
        String chave = chaveLocal(id);
        Conversa existente = conversas.get(chave);
        if (existente != null) {
            if (nome != null && !nome.isBlank()) existente.nome = nome.trim();
            if (pessoal) existente.pessoal = true;
            if (salvar && !existente.pessoal && !existente.salaGeral) salvarContato(existente);
            return existente;
        }
        Conversa conversa = new Conversa(id.trim(), nome.trim(), pessoal);
        conversas.put(chave, conversa);
        if (salvar && !pessoal) salvarContato(conversa);
        return conversa;
    }

    private Conversa conversaPorId(String id) {
        Conversa conversa = conversas.get(chaveLocal(id));
        if (conversa != null) return conversa;
        boolean pessoal = normalizarId(id).equals(normalizarId(meuId));
        return criarConversa(id, pessoal ? "Meu espaço" : id, pessoal, !pessoal);
    }
    private void atualizarListaConversas() {
        listaConversas.removeAll();
        for (Conversa conversa : conversas.values()) {
            Color fundoItem = conversa == conversaAtual ? CYAN_SOFT : SURFACE;
            JPanel item = new JPanel(new BorderLayout(3, 0));
            item.setBackground(fundoItem);
            item.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(conversa == conversaAtual ? new Color(49, 79, 101) : SURFACE, 1, true),
                    new EmptyBorder(5, 6, 5, 5)));

            JButton abrir = new JButton();
            abrir.setLayout(new BorderLayout(10, 0));
            abrir.setBackground(fundoItem);
            abrir.setOpaque(true);
            abrir.setBorderPainted(false);
            abrir.setFocusPainted(false);
            abrir.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            AvatarBadge avatar = new AvatarBadge(
                    conversa.salaGeral ? "SG" : conversa.pessoal ? "EU" : iniciais(conversa.nome),
                    conversa.pessoal || conversa.salaGeral ? CYAN_SOFT : HOVER, CYAN);
            JPanel textos = new JPanel();
            textos.setOpaque(false);
            textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
            JLabel nome = new JLabel(escaparHtml(conversa.nome));
            nome.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nome.setForeground(TEXT);
            String previa = conversa.silenciado ? "🔕 Silenciado · " + previa(conversa) : previa(conversa);
            if (conversa.online && !conversa.salaGeral && !conversa.pessoal
                    && conversa.mensagens.isEmpty() && !conversa.silenciado) {
                previa = "● Online";
            }
            JLabel preview = new JLabel(escaparHtml(previa));
            preview.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            preview.setForeground(conversa.online ? new Color(83, 211, 155) : MUTED);
            textos.add(nome);
            textos.add(Box.createVerticalStrut(4));
            textos.add(preview);
            abrir.add(avatar, BorderLayout.WEST);
            abrir.add(textos, BorderLayout.CENTER);
            abrir.addActionListener(e -> selecionarConversa(conversa));
            item.add(abrir, BorderLayout.CENTER);

            if (!conversa.pessoal && !conversa.salaGeral) {
                JButton opcoes = new JButton("⋮");
                opcoes.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16));
                opcoes.setForeground(MUTED);
                opcoes.setBackground(fundoItem);
                opcoes.setBorderPainted(false);
                opcoes.setFocusPainted(false);
                opcoes.setToolTipText("Configurações de " + conversa.nome);
                opcoes.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                opcoes.addActionListener(e -> mostrarOpcoesContato(conversa, opcoes));
                item.add(opcoes, BorderLayout.EAST);
            }

            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            listaConversas.add(item);
            listaConversas.add(Box.createVerticalStrut(5));
        }
        listaConversas.revalidate();
        listaConversas.repaint();
    }

    private void mostrarOpcoesContato(Conversa conversa, Component ancora) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem limpar = new JMenuItem("Limpar conversa");
        limpar.addActionListener(e -> limparConversa(conversa));
        JMenuItem bloquear = new JMenuItem(conversa.bloqueado ? "Desbloquear contato" : "Bloquear contato");
        bloquear.addActionListener(e -> alternarBloqueio(conversa));
        JMenuItem renomear = new JMenuItem("Alterar nome");
        renomear.addActionListener(e -> renomearContato(conversa));
        JMenuItem remover = new JMenuItem("Remover contato");
        remover.addActionListener(e -> removerContato(conversa));
        JMenuItem silenciar = new JMenuItem(conversa.silenciado ? "Ativar notificações" : "Silenciar");
        silenciar.addActionListener(e -> alternarSilencio(conversa));
        JMenuItem compartilhar = new JMenuItem("Compartilhar contato");
        compartilhar.addActionListener(e -> compartilharContato(conversa));
        menu.add(limpar);
        menu.add(bloquear);
        menu.add(renomear);
        menu.add(remover);
        menu.add(silenciar);
        menu.addSeparator();
        menu.add(compartilhar);
        menu.show(ancora, 0, ancora.getHeight());
    }

    private void limparConversa(Conversa conversa) {
        int escolha = JOptionPane.showConfirmDialog(this,
                "Apagar o histórico desta conversa somente neste computador?",
                "Limpar conversa", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (escolha != JOptionPane.YES_OPTION) return;
        conversa.limpoAte = LocalDateTime.now();
        conversa.mensagens.clear();
        conversa.historicoSolicitado = true;
        conversa.historicoCarregado = true;
        salvarTodosContatos();
        atualizarListaConversas();
        if (conversaAtual == conversa) renderizarMensagens();
    }

    private void alternarBloqueio(Conversa conversa) {
        if (!conversa.bloqueado) {
            int escolha = JOptionPane.showConfirmDialog(this,
                    "Bloquear " + conversa.nome + "? As mensagens recebidas serão ocultadas neste dispositivo.",
                    "Bloquear contato", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (escolha != JOptionPane.YES_OPTION) return;
        }
        conversa.bloqueado = !conversa.bloqueado;
        salvarTodosContatos();
        atualizarListaConversas();
        if (conversaAtual == conversa) {
            descricaoConversa.setText(conversa.bloqueado
                    ? "Contato bloqueado neste dispositivo" : "Conversa direta com " + conversa.idContato);
            renderizarMensagens();
        }
    }

    private void renomearContato(Conversa conversa) {
        String nome = JOptionPane.showInputDialog(this, "Novo nome para este contato:",
                conversa.nome);
        if (nome == null) return;
        nome = nome.trim();
        if (nome.isEmpty()) {
            JOptionPane.showMessageDialog(this, "O nome não pode ficar vazio.");
            return;
        }
        conversa.nome = nome;
        salvarTodosContatos();
        atualizarListaConversas();
        if (conversaAtual == conversa) tituloConversa.setText(conversa.nome);
    }

    private void removerContato(Conversa conversa) {
        int escolha = JOptionPane.showConfirmDialog(this,
                "Remover " + conversa.nome + " da sua lista? O histórico no servidor não será apagado.",
                "Remover contato", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (escolha != JOptionPane.YES_OPTION) return;
        conversas.remove(chaveLocal(conversa.idContato));
        salvarTodosContatos();
        atualizarListaConversas();
        if (conversaAtual == conversa) {
            selecionarConversa(conversas.get(chaveLocal(ID_SALA_GERAL)));
        }
    }

    private void alternarSilencio(Conversa conversa) {
        conversa.silenciado = !conversa.silenciado;
        salvarTodosContatos();
        atualizarListaConversas();
        if (conversaAtual == conversa) {
            avisoConversa.setText(conversa.silenciado
                    ? "Conversa silenciada neste dispositivo." : "Notificações desta conversa ativadas.");
            avisoConversa.setForeground(MUTED);
        }
    }

    private void compartilharContato(Conversa contato) {
        List<Conversa> destinos = conversas.values().stream()
                .filter(c -> c != contato && !c.pessoal && !c.salaGeral && !c.bloqueado)
                .toList();
        if (destinos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Adicione outra conversa para compartilhar este contato.",
                    "Compartilhar contato", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<String> escolhaDestino = new JComboBox<>(
                destinos.stream().map(c -> c.nome + " (" + c.idContato + ")").toArray(String[]::new));
        int escolha = JOptionPane.showConfirmDialog(this, escolhaDestino,
                "Enviar contato para", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION) return;
        int indice = escolhaDestino.getSelectedIndex();
        if (indice < 0 || indice >= destinos.size()) return;
        enviarTextoPara(destinos.get(indice),
                "Contato compartilhado:\nNome: " + contato.nome + "\nIdentificador: " + contato.idContato);
    }

    private String previa(Conversa conversa) {
        if (conversa.mensagens.isEmpty()) {
            if (conversa.salaGeral) return "Sala compartilhada";
            return conversa.pessoal ? "Suas notas privadas" : conversa.idContato;
        }
        String texto = conversa.mensagens.get(conversa.mensagens.size() - 1)
                .getConteudo().replaceAll("\\s+", " ");
        return texto.length() > 34 ? texto.substring(0, 31) + "..." : texto;
    }

    private void selecionarConversa(Conversa conversa) {
        if (conversa == null) return;
        conversaAtual = conversa;
        tituloConversa.setText(conversa.nome);
        descricaoConversa.setText(conversa.salaGeral
                ? "Mensagens visíveis para todas as pessoas conectadas"
                : conversa.pessoal
                    ? "Seu espaço privado neste computador"
                    : conversa.bloqueado
                        ? "Contato bloqueado neste dispositivo"
                        : "Conversa direta com " + conversa.idContato);
        avisoConversa.setText(" ");
        avisoConversa.setForeground(MUTED);
        atualizarListaConversas();
        atualizarEstadoConversa();
        renderizarMensagens();
        if (registrado) {
            consultarPresenca(conversa);
            solicitarHistorico(conversa);
        }
    }

    private void solicitarHistorico(Conversa conversa) {
        if (!conversa.historicoSolicitado && escritor != null && socket != null && !socket.isClosed()) {
            conversa.historicoSolicitado = true;
            if (conversa.salaGeral) {
                escritor.println("/room_history");
            } else {
                escritor.println("/history\t" + codificar(conversa.idContato));
            }
        }
    }

    private void renderizarMensagens() {
        listaMensagens.removeAll();
        if (conversaAtual == null || conversaAtual.mensagens.isEmpty()) {
            listaMensagens.add(criarEstadoVazio());
        } else {
            for (MensagemPrivada mensagem : conversaAtual.mensagens) {
                listaMensagens.add(criarBalao(mensagem));
                listaMensagens.add(Box.createVerticalStrut(12));
            }
        }
        listaMensagens.revalidate();
        listaMensagens.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar barra = scrollMensagens.getVerticalScrollBar();
            barra.setValue(barra.getMaximum());
        });
    }

    private JPanel criarEstadoVazio() {
        JPanel centro = new JPanel(new GridBagLayout());
        centro.setOpaque(false);
        JPanel bloco = new JPanel();
        bloco.setOpaque(false);
        bloco.setLayout(new BoxLayout(bloco, BoxLayout.Y_AXIS));
        JLabel icone = new JLabel("✦", SwingConstants.CENTER);
        icone.setAlignmentX(Component.CENTER_ALIGNMENT);
        icone.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 27));
        icone.setForeground(CYAN);
        JLabel titulo = new JLabel("Sua conversa começa aqui");
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titulo.setForeground(TEXT);
        JLabel dica = new JLabel("Envie uma mensagem para "
                + (conversaAtual == null ? "alguém" : escaparHtml(conversaAtual.nome)) + ".");
        dica.setAlignmentX(Component.CENTER_ALIGNMENT);
        dica.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dica.setForeground(MUTED);
        bloco.add(icone);
        bloco.add(Box.createVerticalStrut(12));
        bloco.add(titulo);
        bloco.add(Box.createVerticalStrut(7));
        bloco.add(dica);
        centro.add(bloco);
        centro.setPreferredSize(new Dimension(700, 380));
        return centro;
    }

    private JPanel criarBalao(MensagemPrivada mensagem) {
        boolean fuiEu = normalizarId(mensagem.getRemetenteId()).equals(normalizarId(meuId))
                || (conversaAtual != null && conversaAtual.salaGeral
                    && normalizarId(mensagem.getRemetenteNome()).equals(normalizarId(meuNome)));
        JPanel linha = new JPanel(new FlowLayout(fuiEu ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        linha.setOpaque(false);
        if (!fuiEu) {
            linha.add(new AvatarBadge(iniciais(mensagem.getRemetenteNome()), HOVER, CYAN));
            linha.add(Box.createHorizontalStrut(9));
        }
        RoundedPanel balao = new RoundedPanel(fuiEu ? OUTGOING : INCOMING, BORDER, 16);
        balao.setLayout(new BorderLayout(0, 7));
        balao.setBorder(new EmptyBorder(10, 13, 10, 13));
        balao.setMaximumSize(new Dimension(560, Integer.MAX_VALUE));
        JPanel meta = new JPanel(new BorderLayout(15, 0));
        meta.setOpaque(false);
        JLabel nome = new JLabel(fuiEu ? "Você" : escaparHtml(mensagem.getRemetenteNome()));
        nome.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nome.setForeground(fuiEu ? CYAN : new Color(190, 204, 218));
        JLabel hora = new JLabel(mensagem.getHorario().format(HORA));
        hora.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hora.setForeground(MUTED);
        meta.add(nome, BorderLayout.WEST);
        meta.add(hora, BorderLayout.EAST);
        balao.add(meta, BorderLayout.NORTH);
        if (ehMarcadorArquivo(mensagem.getConteudo())) {
            String[] partesArquivo = mensagem.getConteudo().split("\\|", -1);
            String arquivoId = partesArquivo[1];
            String nomeArquivo = decodificar(partesArquivo[2]);
            long tamanho = Long.parseLong(partesArquivo[3]);
            JButton abrir = new JButton("📎  " + escaparHtml(nomeArquivo)
                    + "  ·  " + formatarTamanho(tamanho));
            abrir.setFont(new Font("Segoe UI", Font.BOLD, 12));
            abrir.setForeground(TEXT);
            abrir.setBackground(HOVER);
            abrir.setFocusPainted(false);
            abrir.setBorder(new LineBorder(BORDER, 1, true));
            abrir.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            abrir.setToolTipText("Abrir ou visualizar " + nomeArquivo);
            abrir.addActionListener(e -> abrirArquivoRecebido(arquivoId, nomeArquivo));
            balao.add(abrir, BorderLayout.CENTER);
        } else {
            String textoSeguro = escaparHtml(mensagem.getConteudo()).replace("\n", "<br>");
            JLabel conteudo = new JLabel("<html><div style='width:420px;'>" + textoSeguro + "</div></html>");
            conteudo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            conteudo.setForeground(TEXT);
            balao.add(conteudo, BorderLayout.CENTER);
        }
        linha.add(balao);
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, linha.getPreferredSize().height));
        return linha;
    }

    private boolean ehMarcadorArquivo(String conteudo) {
        if (conteudo == null || !conteudo.startsWith("@@ARQUIVO@@|")) return false;
        String[] partes = conteudo.split("\\|", -1);
        if (partes.length != 4) return false;
        try {
            UUID.fromString(partes[1]);
            String nome = decodificar(partes[2]);
            long tamanho = Long.parseLong(partes[3]);
            return !nome.isBlank() && tamanho > 0 && tamanho <= 5L * 1024 * 1024;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void enviarMensagem() {
        if (placeholder || campoTexto == null) return;
        String texto = campoTexto.getText().trim();
        if (texto.isEmpty()) return;
        if (conversaAtual == null) {
            avisoConversa.setText("Escolha ou adicione uma conversa antes de enviar.");
            return;
        }
        if (conversaAtual.bloqueado) {
            avisoConversa.setText("Desbloqueie este contato para enviar mensagens.");
            avisoConversa.setForeground(new Color(239, 133, 125));
            return;
        }
        if (conversaAtual.salaGeral) {
            if (aguardandoConfirmacaoSala) {
                avisoConversa.setText("Aguarde a confirmação da mensagem anterior.");
                avisoConversa.setForeground(MUTED);
                return;
            }
            textoSalaPendente = texto;
            aguardandoConfirmacaoSala = true;
            if (!enviarTextoPara(conversaAtual, texto)) {
                finalizarEnvioSala(null);
                return;
            }
            avisoConversa.setText("Enviando mensagem para a Sala geral...");
            avisoConversa.setForeground(MUTED);
            return;
        }
        if (enviarTextoPara(conversaAtual, texto)) {
            campoTexto.setText("");
            campoTexto.setForeground(TEXT);
            placeholder = false;
            campoTexto.requestFocusInWindow();
        }
    }

    private boolean enviarTextoPara(Conversa destino, String texto) {
        if (destino == null || texto == null || texto.isBlank()) return false;
        if (!registrado || escritor == null || socket == null || socket.isClosed()) {
            avisoConversa.setText("Conecte o servidor antes de enviar mensagens.");
            avisoConversa.setForeground(new Color(239, 133, 125));
            return false;
        }
        if (destino.bloqueado) {
            avisoConversa.setText("Desbloqueie o contato de destino antes de enviar.");
            avisoConversa.setForeground(new Color(239, 133, 125));
            return false;
        }
        if (destino.salaGeral) {
            escritor.println("/room\t" + codificar(texto));
        } else {
            escritor.println("/dm\t" + codificar(destino.idContato) + "\t" + codificar(texto));
        }
        if (escritor.checkError()) {
            servidorDisponivel = false;
            registrado = false;
            setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
            avisoConversa.setText("Mensagem não enviada: a conexão com o servidor foi encerrada.");
            avisoConversa.setForeground(new Color(239, 133, 125));
            return false;
        }
        return true;
    }

    private void anexarArquivo() {
        if (conversaAtual == null) {
            avisoConversa.setText("Escolha uma conversa direta para anexar um arquivo.");
            return;
        }
        if (conversaAtual.bloqueado) {
            avisoConversa.setText("Desbloqueie este contato para enviar arquivos.");
            return;
        }
        if (!registrado || escritor == null || socket == null || socket.isClosed()) {
            avisoConversa.setText("Conecte o servidor antes de enviar arquivos.");
            avisoConversa.setForeground(new Color(239, 133, 125));
            return;
        }
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Escolha uma foto, PDF ou outro arquivo");
        seletor.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (seletor.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File arquivo = seletor.getSelectedFile();
        if (arquivo.length() == 0 || arquivo.length() > 5L * 1024 * 1024) {
            JOptionPane.showMessageDialog(this, "Escolha um arquivo entre 1 byte e 5 MB.",
                    "Tamanho não permitido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(arquivo.toPath());
            String tipoMime = Files.probeContentType(arquivo.toPath());
            if (tipoMime == null) tipoMime = "application/octet-stream";
            String dados = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String destinatario = conversaAtual.salaGeral ? "sala-geral" : conversaAtual.idContato;
            String arquivoId = UUID.randomUUID().toString();
            String linha = "/file\t" + arquivoId + "\t"
                    + codificar(destinatario) + "\t"
                    + codificar(arquivo.getName()) + "\t"
                    + codificar(tipoMime) + "\t" + dados;
            if (linha.length() > 7_100_000) {
                JOptionPane.showMessageDialog(this, "Este arquivo excede o limite de envio.",
                        "Arquivo muito grande", JOptionPane.WARNING_MESSAGE);
                return;
            }
            arquivosPendentes.put(arquivoId, arquivo.getName());
            escritor.println(linha);
            escritor.flush();
            if (escritor.checkError()) {
                arquivosPendentes.remove(arquivoId);
                servidorDisponivel = false;
                registrado = false;
                setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
                avisoConversa.setText("O arquivo não saiu: a conexão com o servidor foi encerrada.");
                avisoConversa.setForeground(new Color(239, 133, 125));
                return;
            }
            javax.swing.Timer timeout = new javax.swing.Timer(60000, e -> {
                if (arquivosPendentes.remove(arquivoId) != null) {
                    avisoConversa.setText("O servidor não confirmou o envio de " + arquivo.getName() + ".");
                    avisoConversa.setForeground(new Color(239, 133, 125));
                }
            });
            timeout.setRepeats(false);
            timeout.start();
            avisoConversa.setText("Enviando " + arquivo.getName() + "…");
            avisoConversa.setForeground(MUTED);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível ler o arquivo: " + e.getMessage(),
                    "Erro ao anexar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirArquivoRecebido(String id, String nome) {
        try {
            Path arquivo = pastaArquivos.resolve(UUID.fromString(id) + "_" + nomeSeguroArquivo(nome)).normalize();
            if (!arquivo.startsWith(pastaArquivos.normalize()) || !Files.exists(arquivo)) {
                throw new IOException("O arquivo recebido não está mais neste computador.");
            }
            if (!Desktop.isDesktopSupported()) throw new IOException("A abertura de arquivos não está disponível.");
            Desktop.getDesktop().open(arquivo.toFile());
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Não foi possível abrir o arquivo", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String nomeSeguroArquivo(String nome) {
        String seguro = nome == null ? "arquivo" : nome.replace('\\', '/');
        seguro = seguro.substring(seguro.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (seguro.isBlank() || seguro.equals(".") || seguro.equals("..")) return "arquivo";
        return seguro.length() > 255 ? seguro.substring(seguro.length() - 255) : seguro;
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void carregarContatos() {
        if (!Files.exists(arquivoContatos)) return;
        try {
            for (String linha : Files.readAllLines(arquivoContatos, StandardCharsets.UTF_8)) {
                String[] partes = linha.split("\t", -1);
                if (partes.length < 2) continue;
                String id = decodificar(partes[0]);
                String nome = decodificar(partes[1]);
                if (normalizarId(id).equals(normalizarId(meuId))) continue;
                Conversa conversa = criarConversa(id, nome, false, false);
                if (partes.length > 2) conversa.bloqueado = Boolean.parseBoolean(partes[2]);
                if (partes.length > 3) conversa.silenciado = Boolean.parseBoolean(partes[3]);
                if (partes.length > 4 && !partes[4].isBlank()) {
                    try { conversa.limpoAte = LocalDateTime.parse(partes[4]); }
                    catch (RuntimeException ignorado) { conversa.limpoAte = null; }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Não foi possível carregar contatos do arquivo local.");
        }
    }

    private void salvarContato(Conversa conversa) {
        salvarTodosContatos();
    }

    private void salvarTodosContatos() {
        try {
            Path pasta = arquivoContatos.getParent();
            if (pasta != null) Files.createDirectories(pasta);
            List<String> linhas = new ArrayList<>();
            for (Conversa conversa : conversas.values()) {
                if (conversa.pessoal || conversa.salaGeral) continue;
                String dataLimpeza = conversa.limpoAte == null ? "" : conversa.limpoAte.toString();
                linhas.add(codificar(conversa.idContato) + "\t" + codificar(conversa.nome)
                        + "\t" + conversa.bloqueado + "\t" + conversa.silenciado + "\t" + dataLimpeza);
            }
            Files.write(arquivoContatos, linhas, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (avisoConversa != null) avisoConversa.setText("Não foi possível salvar as configurações de contato.");
        }
    }

    private void carregarPreferencias() {
        Properties preferencias = new Properties();
        try {
            if (Files.exists(arquivoPreferencias)) {
                try (java.io.InputStream entrada = Files.newInputStream(arquivoPreferencias)) {
                    preferencias.load(entrada);
                }
            }
        } catch (IOException e) {
            System.err.println("Não foi possível ler as configurações locais.");
        }
        String nomeSalvo = preferencias.getProperty("nomePerfil");
        if (nomeSalvo != null && !nomeSalvo.isBlank()) meuNome = nomeSalvo;
        temaClaro = Boolean.parseBoolean(preferencias.getProperty("temaClaro", "false"));
        definirPaleta(temaClaro);
        String cor = preferencias.getProperty("fundoConversa");
        if (cor != null && cor.matches("[0-9A-Fa-f]{6}")) {
            fundoConversa = new Color(Integer.parseInt(cor, 16));
            fundoPersonalizado = true;
        } else {
            fundoConversa = CANVAS;
        }
    }

    private void salvarPreferencias() {
        Properties preferencias = new Properties();
        preferencias.setProperty("temaClaro", Boolean.toString(temaClaro));
        preferencias.setProperty("nomePerfil", meuNome);
        if (fundoPersonalizado) {
            preferencias.setProperty("fundoConversa",
                    String.format(Locale.ROOT, "%06X", fundoConversa.getRGB() & 0xFFFFFF));
        }
        try {
            Path pasta = arquivoPreferencias.getParent();
            if (pasta != null) Files.createDirectories(pasta);
            try (java.io.OutputStream saida = Files.newOutputStream(arquivoPreferencias)) {
                preferencias.store(saida, "Configurações locais do ConnectChat");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível salvar as configurações neste computador.",
                    "Configurações", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void definirPaleta(boolean claro) {
        if (claro) {
            BG = new Color(239, 243, 247);
            RAIL = new Color(250, 252, 254);
            CANVAS = new Color(246, 248, 251);
            SURFACE = Color.WHITE;
            HOVER = new Color(231, 238, 244);
            BORDER = new Color(213, 222, 231);
            TEXT = new Color(26, 38, 51);
            MUTED = new Color(96, 112, 129);
            CYAN = new Color(0, 126, 171);
            CYAN_SOFT = new Color(218, 239, 247);
            INCOMING = Color.WHITE;
            OUTGOING = new Color(220, 239, 247);
        } else {
            BG = new Color(10, 13, 18);
            RAIL = new Color(8, 11, 16);
            CANVAS = new Color(13, 18, 25);
            SURFACE = new Color(17, 24, 34);
            HOVER = new Color(25, 35, 48);
            BORDER = new Color(37, 49, 64);
            TEXT = new Color(234, 241, 248);
            MUTED = new Color(139, 154, 171);
            CYAN = new Color(137, 211, 247);
            CYAN_SOFT = new Color(27, 47, 64);
            INCOMING = new Color(20, 29, 40);
            OUTGOING = new Color(25, 43, 59);
        }
    }

    private void abrirConfiguracoes() {
        JDialog dialogo = new JDialog(this, "Minhas configurações", true);
        dialogo.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialogo.setSize(540, 430);
        dialogo.setLocationRelativeTo(this);

        JTabbedPane abas = new JTabbedPane();
        JPanel perfil = new JPanel(new GridBagLayout());
        perfil.setBackground(BG);
        perfil.setBorder(new EmptyBorder(18, 20, 18, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);
        JTextField nome = new JTextField(meuNome, 24);
        estilizarCampo(nome);
        c.gridy = 0; perfil.add(rotulo("Nome exibido"), c);
        c.gridy = 1; perfil.add(nome, c);
        c.gridy = 2; perfil.add(rotulo("Seu identificador"), c);
        c.gridy = 3;
        JLabel identificador = new JLabel(escaparHtml(meuId));
        identificador.setForeground(TEXT);
        perfil.add(identificador, c);
        JLabel dicaPerfil = new JLabel("O identificador permanece fixo para não perder suas conversas.");
        dicaPerfil.setForeground(MUTED);
        c.gridy = 4; perfil.add(dicaPerfil, c);

        JPanel aparencia = new JPanel(new GridBagLayout());
        aparencia.setBackground(BG);
        aparencia.setBorder(new EmptyBorder(18, 20, 18, 20));
        GridBagConstraints a = new GridBagConstraints();
        a.gridx = 0;
        a.anchor = GridBagConstraints.WEST;
        a.fill = GridBagConstraints.HORIZONTAL;
        a.weightx = 1;
        a.insets = new Insets(8, 0, 8, 0);
        JComboBox<String> tema = new JComboBox<>(new String[]{"Escuro", "Claro"});
        tema.setSelectedIndex(temaClaro ? 1 : 0);
        a.gridy = 0; aparencia.add(rotulo("Tema do aplicativo"), a);
        a.gridy = 1; aparencia.add(tema, a);
        final Color[] corFundo = {fundoConversa};
        JButton escolherFundo = criarBotao("Escolher fundo da conversa", HOVER, TEXT);
        escolherFundo.addActionListener(e -> {
            Color escolhida = JColorChooser.showDialog(dialogo, "Fundo da conversa", corFundo[0]);
            if (escolhida != null) {
                corFundo[0] = escolhida;
                escolherFundo.setText(String.format(Locale.ROOT, "Fundo selecionado · #%06X",
                        escolhida.getRGB() & 0xFFFFFF));
            }
        });
        a.gridy = 2; aparencia.add(rotulo("Plano de fundo das mensagens"), a);
        a.gridy = 3; aparencia.add(escolherFundo, a);
        JButton padrao = criarBotao("Restaurar fundo padrão", HOVER, TEXT);
        padrao.addActionListener(e -> {
            corFundo[0] = tema.getSelectedIndex() == 1
                    ? new Color(246, 248, 251) : new Color(13, 18, 25);
            escolherFundo.setText("Escolher fundo da conversa");
        });
        a.gridy = 4; aparencia.add(padrao, a);
        JLabel dicaAparencia = new JLabel("As escolhas ficam salvas neste computador.");
        dicaAparencia.setForeground(MUTED);
        a.gridy = 5; aparencia.add(dicaAparencia, a);

        JPanel privacidade = new JPanel(new BorderLayout());
        privacidade.setBackground(BG);
        privacidade.setBorder(new EmptyBorder(20, 22, 20, 22));
        JLabel notaPrivacidade = new JLabel("<html><div style='width:390px;'>"
                + "Cada contato tem opções próprias para bloquear, silenciar, limpar ou remover. "
                + "Essas preferências são locais e não alteram o histórico da outra pessoa.</div></html>");
        notaPrivacidade.setForeground(TEXT);
        privacidade.add(notaPrivacidade, BorderLayout.NORTH);

        abas.addTab("Perfil", perfil);
        abas.addTab("Aparência", aparencia);
        abas.addTab("Privacidade", privacidade);
        JButton salvar = criarBotao("Salvar configurações", CYAN, new Color(10, 18, 25));
        salvar.addActionListener(e -> {
            String novoNome = nome.getText().trim();
            if (novoNome.isEmpty() || novoNome.length() > 120) {
                JOptionPane.showMessageDialog(dialogo, "Use um nome entre 1 e 120 caracteres.");
                abas.setSelectedComponent(perfil);
                nome.requestFocusInWindow();
                return;
            }
            boolean nomeMudou = !novoNome.equals(meuNome);
            boolean novoTema = tema.getSelectedIndex() == 1;
            boolean temaMudou = novoTema != temaClaro;
            meuNome = novoNome;
            temaClaro = novoTema;
            definirPaleta(temaClaro);
            if (!fundoPersonalizado && temaMudou) corFundo[0] = CANVAS;
            fundoConversa = corFundo[0];
            fundoPersonalizado = !fundoConversa.equals(CANVAS);
            if (nomeMudou && registrado && escritor != null) {
                escritor.println("/profile\t" + codificar(meuNome));
            }
            salvarPreferencias();
            dialogo.dispose();
            reconstruirInterface();
        });
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rodape.setBackground(BG);
        rodape.add(salvar);
        dialogo.setLayout(new BorderLayout());
        dialogo.add(abas, BorderLayout.CENTER);
        dialogo.add(rodape, BorderLayout.SOUTH);
        dialogo.setVisible(true);
    }
    private void setEstadoConexao(String texto, Color cor) {
        if (estadoConexao != null) {
            estadoConexao.setText(texto);
            estadoConexao.setForeground(cor);
        }
    }

    private void atualizarEstadoConversa() {
        if (estadoConexao == null) return;
        if (!servidorDisponivel || !registrado || socket == null || socket.isClosed()) {
            setEstadoConexao("●  Servidor indisponível", new Color(239, 133, 125));
        } else if (conversaAtual == null) {
            setEstadoConexao("●  Pronto", MUTED);
        } else if (conversaAtual.salaGeral) {
            setEstadoConexao("●  Sala geral", CYAN);
        } else if (conversaAtual.pessoal) {
            setEstadoConexao("●  Seu espaço", CYAN);
        } else if (conversaAtual.online) {
            setEstadoConexao("●  Online", new Color(83, 211, 155));
        } else {
            setEstadoConexao("●  Offline", MUTED);
        }
    }

    private void consultarPresenca(Conversa conversa) {
        if (conversa == null || conversa.pessoal || conversa.salaGeral
                || escritor == null || socket == null || socket.isClosed()) return;
        escritor.println("/presence\t" + codificar(conversa.idContato));
    }

    private void marcarTodosOffline() {
        finalizarEnvioSala("Conexão encerrada; a mensagem continua no campo.");
        if (!arquivosPendentes.isEmpty()) {
            arquivosPendentes.clear();
            avisoConversa.setText("Conexão encerrada antes da confirmação do arquivo.");
            avisoConversa.setForeground(new Color(239, 133, 125));
        }
        for (Conversa conversa : conversas.values()) conversa.online = false;
        atualizarListaConversas();
        atualizarEstadoConversa();
    }

    private void notificarNovaMensagem(Conversa conversa, String resumo) {
        if (conversa.silenciado || conversa == conversaAtual || !isShowing()) return;
        JWindow aviso = new JWindow(this);
        JPanel painel = new JPanel(new BorderLayout(10, 5));
        painel.setBackground(SURFACE);
        painel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true), new EmptyBorder(12, 14, 12, 12)));
        JLabel texto = new JLabel("<html><b>" + escaparHtml(conversa.nome)
                + "</b><br>" + escaparHtml(resumo.length() > 52
                    ? resumo.substring(0, 49) + "..." : resumo) + "</html>");
        texto.setForeground(TEXT);
        JButton abrir = criarBotao("Abrir", CYAN, new Color(10, 18, 25));
        abrir.addActionListener(e -> {
            aviso.dispose();
            selecionarConversa(conversa);
            toFront();
        });
        painel.add(texto, BorderLayout.CENTER);
        painel.add(abrir, BorderLayout.EAST);
        aviso.setContentPane(painel);
        aviso.setSize(330, 82);
        aviso.setLocation(getX() + getWidth() - aviso.getWidth() - 24,
                getY() + getHeight() - aviso.getHeight() - 54);
        aviso.setVisible(true);
        javax.swing.Timer temporizador = new javax.swing.Timer(6000, e -> aviso.dispose());
        temporizador.setRepeats(false);
        temporizador.start();
    }

    private void restaurarPlaceholder() {
        if (campoTexto != null && campoTexto.getText().isBlank()) {
            campoTexto.setText("Escreva uma mensagem...");
            campoTexto.setForeground(MUTED);
            placeholder = true;
        }
    }    private void desconectar() {
        encerrando = true;
        if (escritor != null) escritor.println("/sair");
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
            // A conexão já foi encerrada.
        }
        dispose();
        System.exit(0);
    }

    private String codificar(String valor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private String decodificar(String valor) {
        return new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
    }

    private String chaveLocal(String id) {
        return normalizarId(id);
    }

    private String normalizarId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private String escaparHtml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String iniciais(String texto) {
        if (texto == null || texto.isBlank()) return "?";
        String[] partes = texto.trim().split("\\s+");
        if (partes.length == 1) return partes[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (partes[0].substring(0, 1) + partes[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private class Conversa {
        private final String idContato;
        private String nome;
        private boolean pessoal;
        private boolean salaGeral;
        private boolean historicoSolicitado;
        private boolean historicoCarregado;
        private boolean online;
        private boolean bloqueado;
        private boolean silenciado;
        private LocalDateTime limpoAte;
        private final List<MensagemPrivada> mensagens = new ArrayList<>();

        private Conversa(String idContato, String nome, boolean pessoal) {
            this.idContato = idContato;
            this.nome = nome;
            this.pessoal = pessoal;
        }
    }

    private static class SettingsAvatarBadge extends JPanel {
        SettingsAvatarBadge() {
            setOpaque(false);
            setPreferredSize(new Dimension(48, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int tamanho = Math.min(getWidth(), getHeight()) - 6;
            int x = (getWidth() - tamanho) / 2;
            int y = (getHeight() - tamanho) / 2;
            g2.setColor(CYAN_SOFT);
            g2.fillOval(x, y, tamanho, tamanho);
            g2.setColor(CYAN);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(x, y, tamanho, tamanho);
            g2.setFont(new Font("Segoe UI Symbol", Font.BOLD, 25));
            FontMetrics fm = g2.getFontMetrics();
            String engrenagem = "\u2699";
            g2.drawString(engrenagem, (getWidth() - fm.stringWidth(engrenagem)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static class AvatarBadge extends JPanel {
        private final String texto;
        private final Color fundo;
        private final Color tinta;

        AvatarBadge(String texto, Color fundo, Color tinta) {
            this.texto = texto;
            this.fundo = fundo;
            this.tinta = tinta;
            setOpaque(false);
            setPreferredSize(new Dimension(36, 36));
            setMinimumSize(new Dimension(36, 36));
            setMaximumSize(new Dimension(36, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fundo);
            g2.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
            g2.setColor(new Color(51, 74, 94));
            g2.drawOval(1, 1, getWidth() - 2, getHeight() - 2);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(tinta);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color preenchimento;
        private final Color contorno;
        private final int raio;

        RoundedPanel(Color preenchimento, Color contorno, int raio) {
            this.preenchimento = preenchimento;
            this.contorno = contorno;
            this.raio = raio;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int largura = getWidth() - 1;
            int altura = getHeight() - 1;
            g2.setColor(preenchimento);
            g2.fillRoundRect(0, 0, largura, altura, raio, raio);
            g2.setColor(contorno);
            g2.drawRoundRect(0, 0, largura, altura, raio, raio);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteChat app = new ClienteChat();
            app.setVisible(true);
        });
    }
}