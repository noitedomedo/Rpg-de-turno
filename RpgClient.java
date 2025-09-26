import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import java.util.*;
import java.util.List;
import java.io.*;
import java.net.*;

public class RpgClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    // Conexão
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;
    
    // GUI
    int boardwidth = 800;
    int boardheight = 600;
    JFrame frame = new JFrame("RPG Battle");

    // Telas
    JPanel namePanel = new JPanel(new GridBagLayout());
    JLabel nameLabel = new JLabel("Digite seu nome (max 16 caracteres):");
    JTextField nameField = new JTextField(22);
    JButton nameConfirmButton = new JButton("Confirmar");

    JLabel textLabel = new JLabel();
    JPanel textPanel = new JPanel();
    JPanel boardPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JButton[] classButtons = new JButton[6];
    JButton confirmButton = new JButton("Confirmar");

    JPanel lobbyPanel = new JPanel();
    JTextArea playersListArea = new JTextArea();
    JLabel waitingLabel = new JLabel("Conectando ao servidor...", SwingConstants.CENTER);

    JTextArea battleLog = new JTextArea();
    JPanel actionPanel = new JPanel();
    
    // Status do jogador
    String selectedClass = null;
    String playerName = null;
    String[] classes = {"Mage", "Warrior", "Assassin", "Archer", "Necromancer", "Lancer"};
    
    // Combate
    Timer battleTimer;
    JLabel timerLabel;
    int timeRemaining = 120;
    boolean actionSent = false;
    List<Player> allPlayers = new ArrayList<>();
    Player currentPlayer;
    int healthPotions = 3;
    int manaPotions = 3;
    int currentTurnNumber = 0;
    
    // Componentes dos status
    JLabel hpLabel;
    JProgressBar hpBar;
    JLabel mpLabel;
    JProgressBar mpBar;
    JLabel classLabel;
    JLabel itemsLabel;
    JLabel statusEffectsLabel;
    JLabel turnLabel;

    public RpgClient() {
        setupGUI();
        connectToServer();
    }
    
    private void setupGUI() {
        frame.setSize(boardwidth, boardheight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });

        showNameScreen();
        frame.setVisible(true);
    }
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            
            Thread receiveThread = new Thread(this::receiveMessages);
            receiveThread.setDaemon(true);
            receiveThread.start();
            
            SwingUtilities.invokeLater(() -> {
                waitingLabel.setText("Conectado ao servidor!");
                waitingLabel.setForeground(Color.GREEN);
            });
            
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, 
                    "Erro ao conectar ao servidor!\nVerifique se o servidor está rodando na porta " + SERVER_PORT,
                    "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                waitingLabel.setText("Falha na conexão com o servidor");
                waitingLabel.setForeground(Color.RED);
            });
        }
    }
    
    private void receiveMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                System.out.println("[CLIENT] Recebido: " + message);
                processServerMessage(message);
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Conexão com servidor perdida!");
                });
            }
        }
    }
    
    private void processServerMessage(String message) {
        String[] parts = message.split("\\|");
        String command = parts[0];
        
        SwingUtilities.invokeLater(() -> {
            switch (command) {
                case "JOIN_SUCCESS":
                    showLobbyScreen();
                    break;
                    
                case "JOIN_REJECTED":
                    String reason = parts.length > 1 ? parts[1] : "Motivo desconhecido";
                    JOptionPane.showMessageDialog(frame, "Entrada rejeitada: " + reason);
                    break;
                    
                case "LOBBY_UPDATE":
                    updateLobbyDisplay(parts);
                    break;
                    
                case "GAME_START":
                    showBattleScreen();
                    break;
                    
                case "PLAYERS_INFO":
                    updatePlayersInfo(parts);
                    sendPlayersInfoMessage(parts);
                    break;
                    
                case "PLAYERS_UPDATE":
                    updatePlayersInfo(parts);
                    updatePlayerStatus();
                    break;
                    
                case "START_TURN":
                    if (parts.length > 1) {
                        currentTurnNumber = Integer.parseInt(parts[1]);
                        startNewTurn();
                    }
                    break;
                    
                case "TURN_RESULT":
                    processTurnResult(parts);
                    break;
                    
                case "GAME_END":
                    String winner = parts.length > 1 ? parts[1] : "Desconhecido";
                    handleGameEnd(winner);
                    break;
                      
                default:
                    System.out.println("[CLIENT] Comando desconhecido: " + command);
                    break;
            }
        });
    }
    
    private void sendMessage(String message) {
        if (connected && out != null) {
            System.out.println("[CLIENT] Enviando: " + message);
            out.println(message);
        }
    }
    
    private void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {

        }
    }

    private void showNameScreen() {
        nameLabel.setFont(new Font("Times New Roman", Font.PLAIN, 25));
        nameConfirmButton.setFont(new Font("Arial", Font.BOLD, 20));
        nameField.setFont(new Font("Arial", Font.PLAIN, 20));

        nameField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if (nameField.getText().length() >= 16) {
                    e.consume();
                }
            }
            
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    nameConfirmButton.doClick();
                }
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0;
        namePanel.add(nameLabel, gbc);

        gbc.gridy = 1;
        namePanel.add(nameField, gbc);

        gbc.gridy = 2;
        namePanel.add(nameConfirmButton, gbc);

        frame.add(namePanel, BorderLayout.CENTER);

        nameConfirmButton.addActionListener(e -> {
            String inputName = nameField.getText().trim();
            if (inputName.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Digite um nome antes de continuar!");
            } else if (!connected) {
                JOptionPane.showMessageDialog(frame, "Não conectado ao servidor!");
            } else {
                playerName = inputName;
                frame.remove(namePanel);
                showClassScreen();
                frame.revalidate();
                frame.repaint();
            }
        });
    }

    private void showClassScreen() {
        textLabel.setFont(new Font("Times New Roman", Font.PLAIN, 35));
        textLabel.setHorizontalAlignment(JLabel.CENTER);
        textLabel.setText(playerName + ", escolha sua classe");
        textLabel.setOpaque(true);

        textPanel.setLayout(new BorderLayout());
        textPanel.add(textLabel, BorderLayout.CENTER);
        frame.add(textPanel, BorderLayout.NORTH);

        boardPanel.setLayout(new GridLayout(2, 3, 10, 10));
        boardPanel.setBackground(Color.black);
        boardPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        frame.add(boardPanel, BorderLayout.CENTER);

        Border defaultBorder = UIManager.getBorder("Button.border");

        for (int i = 0; i < classes.length; i++) {
            JButton classButton = new JButton(classes[i]);
            classButton.setFont(new Font("Arial", Font.BOLD, 18));
            classButton.setFocusable(false);

            final int idx = i;
            classButton.addActionListener(e -> {
                selectedClass = classes[idx];
                textLabel.setText(playerName + ", você escolheu: " + selectedClass);

                for (JButton b : classButtons) {
                    if (b != null) b.setBorder(defaultBorder);
                }
                classButton.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
            });

            classButtons[i] = classButton;
            boardPanel.add(classButton);
        }

        buttonPanel.setLayout(new FlowLayout());
        confirmButton.setFont(new Font("Arial", Font.BOLD, 25));
        confirmButton.setFocusable(false);
        buttonPanel.add(confirmButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        confirmButton.addActionListener(e -> {
            if (selectedClass == null) {
                JOptionPane.showMessageDialog(frame, "Você precisa escolher uma classe primeiro!");
            } else if (!connected) {
                JOptionPane.showMessageDialog(frame, "Não conectado ao servidor!");
            } else {
                sendMessage("JOIN|" + playerName + "|" + selectedClass);
                
                frame.remove(textPanel);
                frame.remove(boardPanel);
                frame.remove(buttonPanel);
                
                showWaitingScreen();
                
                frame.revalidate();
                frame.repaint();
            }
        });
    }
    
    private void showWaitingScreen() {
        JPanel waitingPanel = new JPanel(new BorderLayout());
        waitingLabel.setText("Entrando na sala...");
        waitingLabel.setFont(new Font("Arial", Font.BOLD, 20));
        waitingPanel.add(waitingLabel, BorderLayout.CENTER);
        frame.add(waitingPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }

    private void showLobbyScreen() {
        frame.getContentPane().removeAll();
        
        lobbyPanel = new JPanel(new BorderLayout());
        lobbyPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel lobbyTitle = new JLabel("Sala de Batalha", SwingConstants.CENTER);
        lobbyTitle.setFont(new Font("Times New Roman", Font.BOLD, 40));
        lobbyPanel.add(lobbyTitle, BorderLayout.NORTH);
        
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Jogadores Conectados"));
        
        playersListArea = new JTextArea();
        playersListArea.setEditable(false);
        playersListArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
        playersListArea.setBackground(Color.LIGHT_GRAY);
        JScrollPane playersScroll = new JScrollPane(playersListArea);
        playersScroll.setPreferredSize(new Dimension(400, 300));
        leftPanel.add(playersScroll, BorderLayout.CENTER);
        
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Informações da Sala"));
        
        JLabel roomInfo = new JLabel("<html><div style='text-align: center;'>" +
            "<h3>Modo de Jogo: Battle Royale</h3>" +
            "<p>Aguardando o servidor iniciar a partida...</p>" +
            "<br>" +
            "<p><b>Habilidades Especiais:</b></p>" +
            "<p>Mage: Congela inimigo</p>" +
            "<p>Warrior: Sangramento</p>" +
            "<p>Assassin: Veneno</p>" +
            "<p>Archer: Ataque perfurante</p>" +
            "<p>Necromancer: Drena vida</p>" +
            "<p>Lancer: Atordoa inimigo</p>" +
            "</div></html>");
        roomInfo.setFont(new Font("Arial", Font.PLAIN, 12));
        roomInfo.setHorizontalAlignment(JLabel.CENTER);
        rightPanel.add(roomInfo);
        
        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        lobbyPanel.add(centerPanel, BorderLayout.CENTER);
        
        waitingLabel = new JLabel("Aguardando outros jogadores...", SwingConstants.CENTER);
        waitingLabel.setFont(new Font("Arial", Font.BOLD, 18));
        waitingLabel.setForeground(Color.BLUE);
        lobbyPanel.add(waitingLabel, BorderLayout.SOUTH);
        
        frame.add(lobbyPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }
    
    private void updateLobbyDisplay(String[] parts) {
        StringBuilder playersList = new StringBuilder();
        playersList.append("JOGADORES CONECTADOS\n");
        playersList.append("==========================================\n\n");
        
        for (int i = 1; i < parts.length; i++) {
            String[] playerInfo = parts[i].split(",");
            if (playerInfo.length >= 2) {
                String name = playerInfo[0];
                String playerClass = playerInfo[1];
                playersList.append((i) + ". " + name + "\n");
                playersList.append("   Classe: " + playerClass + "\n");
                playersList.append("   Agilidade: " + getClassAgility(playerClass) + "\n\n");
            }
        }
        
        playersListArea.setText(playersList.toString());
        
        int playerCount = parts.length - 1;
        if (playerCount >= 2) {
            waitingLabel.setText("Sala pronta! Aguardando o servidor iniciar...");
            waitingLabel.setForeground(Color.GREEN);
        } else {
            waitingLabel.setText("Aguardando mais jogadores... (" + playerCount + "/6)");
            waitingLabel.setForeground(Color.ORANGE);
        }
    }
    
    private int getClassAgility(String className) {
        Map<String, Integer> agility = new HashMap<String, Integer>() {{
            put("Assassin", 6);
            put("Archer", 5);
            put("Mage", 4);
            put("Necromancer", 3);
            put("Lancer", 2);
            put("Warrior", 1);
        }};
        return agility.getOrDefault(className, 1);
    }

    private void showBattleScreen() {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Lado esquerdo
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(400, boardheight));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Turno
        turnLabel = new JLabel("TURNO 1", SwingConstants.CENTER);
        turnLabel.setFont(new Font("Arial", Font.BOLD, 20));
        turnLabel.setForeground(Color.BLUE);
        turnLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftPanel.add(turnLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // Timer
        timerLabel = new JLabel("Tempo: 02:00", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setForeground(Color.BLACK);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftPanel.add(timerLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Ações
        actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        actionPanel.setPreferredSize(new Dimension(380, 300)); 
        actionPanel.setMinimumSize(new Dimension(380, 300));
        actionPanel.setMaximumSize(new Dimension(380, 300));
        leftPanel.add(actionPanel);
        
        
        leftPanel.add(Box.createVerticalGlue());

        // Status do jogador 
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        statusPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusPanel.setMaximumSize(new Dimension(380, 100)); 

        int hp = currentPlayer != null ? currentPlayer.hp : 100;
        int maxHP = currentPlayer != null ? currentPlayer.maxHP : 100;
        int mp = currentPlayer != null ? currentPlayer.mp : 20;
        int maxMP = currentPlayer != null ? currentPlayer.maxMP : 20;

        // HP
        hpLabel = new JLabel("HP: " + hp + "/" + maxHP);
        hpLabel.setFont(new Font("Arial", Font.BOLD, 12));
        hpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hpBar = new JProgressBar(0, maxHP);
        hpBar.setValue(hp);
        hpBar.setForeground(Color.RED);
        hpBar.setStringPainted(true);
        hpBar.setPreferredSize(new Dimension(350, 18));

        // MP
        mpLabel = new JLabel("MP: " + mp + "/" + maxMP);
        mpLabel.setFont(new Font("Arial", Font.BOLD, 12));
        mpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mpBar = new JProgressBar(0, maxMP);
        mpBar.setValue(mp);
        mpBar.setForeground(Color.BLUE);
        mpBar.setStringPainted(true);
        mpBar.setPreferredSize(new Dimension(350, 18));

        statusPanel.add(hpLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        statusPanel.add(hpBar);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        statusPanel.add(mpLabel);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        statusPanel.add(mpBar);

        leftPanel.add(statusPanel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // Informações adicionais
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Informações"));
        infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.setMaximumSize(new Dimension(380, 65));

        classLabel = new JLabel("Classe: " + selectedClass);
        classLabel.setFont(new Font("Arial", Font.BOLD, 11));
        classLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        itemsLabel = new JLabel("Poções Vida: " + healthPotions + " | Poções Mana: " + manaPotions);
        itemsLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        itemsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        statusEffectsLabel = new JLabel("Status: Nenhum");
        statusEffectsLabel.setFont(new Font("Arial", Font.PLAIN, 9));
        statusEffectsLabel.setForeground(Color.MAGENTA);
        statusEffectsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        infoPanel.add(classLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        infoPanel.add(itemsLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        infoPanel.add(statusEffectsLabel);

        leftPanel.add(infoPanel);

        // Lado direito
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel logLabel = new JLabel("Log de batalha:");
        logLabel.setFont(new Font("Arial", Font.BOLD, 20));
        rightPanel.add(logLabel, BorderLayout.NORTH);

        battleLog = new JTextArea();
        battleLog.setEditable(false);
        battleLog.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(battleLog);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        frame.add(mainPanel, BorderLayout.CENTER);
        
        battleLog.append("=== BATALHA INICIADA! ===\n");
        battleLog.append("Você está jogando como: " + playerName + " (" + selectedClass + ")\n");
        battleLog.append("Tempo por turno: 2 minutos\n");
        battleLog.append("=====================================\n");
        
        frame.revalidate();
        frame.repaint();
    }
    
    private void startNewTurn() {
        actionSent = false;
        
        if (turnLabel != null) {
            turnLabel.setText("TURNO " + currentTurnNumber);
        }
        
        battleLog.append("\n=== TURNO " + currentTurnNumber + " INICIADO ===\n");
        
        if (currentPlayer != null && currentPlayer.isAlive) {
            battleLog.append("Escolha sua ação:\n");
            createMainActionButtons();
            startBattleTimer();
        } else if (currentPlayer != null && !currentPlayer.isAlive) {
            battleLog.append("Você está morto. Aguardando fim da partida...\n");
            for (Component comp : actionPanel.getComponents()) {
                comp.setEnabled(false);
            }
        }
        
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
    }
    
    private void sendPlayersInfoMessage(String[] parts) {
        battleLog.append("Jogadores na batalha:\n");
        for (int i = 1; i < parts.length; i++) {
            String[] playerData = parts[i].split(",");
            if (playerData.length >= 2) {
                String name = playerData[0];
                String playerClass = playerData[1];
                battleLog.append("- " + name + " (" + playerClass + ") - Agilidade: " + getClassAgility(playerClass) + "\n");
            }
        }
        battleLog.append("=====================================\n");
        battleLog.append("Aguardando início do primeiro turno...\n");
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
    }
    
    private void updatePlayersInfo(String[] parts) {
        allPlayers.clear();
        
        for (int i = 1; i < parts.length; i++) {
            String[] playerData = parts[i].split(",");
            if (playerData.length >= 9) {
                String name = playerData[0];
                String playerClass = playerData[1];
                int agility = Integer.parseInt(playerData[2]);
                int hp = Integer.parseInt(playerData[3]);
                int maxHP = Integer.parseInt(playerData[4]);
                int mp = Integer.parseInt(playerData[5]);
                int maxMP = Integer.parseInt(playerData[6]);
                boolean isAlive = Boolean.parseBoolean(playerData[7]);
                String statusEffects = playerData[8];
                
                Player player = new Player(name, playerClass);
                player.hp = hp;
                player.maxHP = maxHP;
                player.mp = mp;
                player.maxMP = maxMP;
                player.isAlive = isAlive;
                player.statusEffects = statusEffects;
                allPlayers.add(player);
                
                if (name.equals(playerName)) {
                    currentPlayer = player;
                }
            }
        }
        

        if (currentPlayer != null) {
            updatePlayerStatus();
        }
    }
    
    private void processTurnResult(String[] parts) {
        stopTimer();
        
        battleLog.append("\n=== RESULTADO DO TURNO " + currentTurnNumber + " ===\n");
        
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                battleLog.append(parts[i] + "\n");
            }
        }
        
        battleLog.append("=== FIM DO TURNO " + currentTurnNumber + " ===\n");
        battleLog.append("=====================================\n");
        
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
        

    }
    
    private void handleGameEnd(String winner) {
        stopTimer();
        
        battleLog.append("\n=== PARTIDA FINALIZADA! ===\n");
        if (winner.equals(playerName)) {
            battleLog.append("PARABÉNS! VOCÊ VENCEU!\n");
        } else if (winner.equals("Empate")) {
            battleLog.append("EMPATE!\n");
        } else {
            battleLog.append("Vencedor: " + winner + "\n");
        }
        battleLog.append("=====================================\n");
        
        for (Component comp : actionPanel.getComponents()) {
            comp.setEnabled(false);
        }
        
        String message = winner.equals(playerName) ? "Parabéns! Você venceu!" : 
                        winner.equals("Empate") ? "Empate!" : "Você foi derrotado! Vencedor: " + winner;
        
        Timer delayTimer = new Timer(2000, e -> {
            int result = JOptionPane.showConfirmDialog(frame, message + "\n\nDeseja sair?", 
                                                      "Fim de Jogo", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                disconnect();
                System.exit(0);
            }
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }
    
    private void createMainActionButtons() {
        if (actionSent || currentPlayer == null || !currentPlayer.isAlive) return;
        
        actionPanel.removeAll();
        
        // Verificar status
        if (currentPlayer.statusEffects.contains("frozen") || currentPlayer.statusEffects.contains("stunned")) {
            JLabel stunLabel = new JLabel("<html><center><b>Você está " + 
                (currentPlayer.statusEffects.contains("frozen") ? "CONGELADO" : "ATORDOADO") + 
                "!</b><br>Não pode agir este turno!</center></html>");
            stunLabel.setFont(new Font("Arial", Font.BOLD, 18));
            stunLabel.setForeground(Color.RED);
            actionPanel.add(stunLabel);
            
            // Ação automática
            sendMessage("ACTION|" + playerName + "|paralyzed| | ");
            actionSent = true;
            
            battleLog.append("Você está " + 
                (currentPlayer.statusEffects.contains("frozen") ? "congelado" : "atordoado") + 
                " e não pode agir!\n");
            battleLog.append("Aguardando ações dos outros jogadores...\n");
            battleLog.setCaretPosition(battleLog.getDocument().getLength());
            
            showWaitingMessage();
            
        } else {
            String[] actions = {"Ataque", "Defesa", "Itens"};
            for (String action : actions) {
                JButton actionButton = new JButton(action);
                actionButton.setFont(new Font("Arial", Font.BOLD, 22));
                actionButton.setPreferredSize(new Dimension(150, 70));
                actionButton.setFocusable(false);

                actionButton.addActionListener(e -> {
                    if (!actionSent) {
                        handleMainAction(action);
                    }
                });

                actionPanel.add(actionButton);
            }
        }
        
        actionPanel.revalidate();
        actionPanel.repaint();
    }
    
    private void handleMainAction(String action) {
        switch (action) {
            case "Ataque":
                showAttackOptions();
                break;
            case "Defesa":
                executeDefense();
                break;
            case "Itens":
                showItemOptions();
                break;
        }
    }
    
    private void showAttackOptions() {
        actionPanel.removeAll();
        
        JButton backButton = new JButton("Voltar");
        backButton.setFont(new Font("Arial", Font.BOLD, 16));
        backButton.setPreferredSize(new Dimension(100, 40));
        backButton.addActionListener(e -> createMainActionButtons());
        actionPanel.add(backButton);
        
        for (Player player : allPlayers) {
            if (!player.name.equals(playerName) && player.isAlive) {
                JButton targetButton = new JButton("<html><center>" + player.name + "<br>(" + player.playerClass + ")<br>HP: " + player.hp + "/" + player.maxHP + "</center></html>");
                targetButton.setFont(new Font("Arial", Font.BOLD, 10));
                targetButton.setPreferredSize(new Dimension(140, 70));
                targetButton.addActionListener(e -> showSkillOptions(player.name));
                actionPanel.add(targetButton);
            }
        }
        
        actionPanel.revalidate();
        actionPanel.repaint();
    }
    
    private void showSkillOptions(String target) {
        actionPanel.removeAll();
        
        JButton backButton = new JButton("Voltar");
        backButton.setFont(new Font("Arial", Font.BOLD, 16));
        backButton.setPreferredSize(new Dimension(100, 40));
        backButton.addActionListener(e -> showAttackOptions());
        actionPanel.add(backButton);
        
        SkillInfo[] skills = getClassSkills(selectedClass);
        for (SkillInfo skill : skills) {
            JButton skillButton = new JButton("<html><center>" + skill.name + "<br>MP: " + skill.mpCost + 
                                             (skill.specialEffect != null ? "<br><i>" + skill.description + "</i>" : "") + 
                                             "</center></html>");
            skillButton.setFont(new Font("Arial", Font.BOLD, 10));
            skillButton.setPreferredSize(new Dimension(140, 70));
            
            if (currentPlayer != null && currentPlayer.mp < skill.mpCost) {
                skillButton.setEnabled(false);
                skillButton.setToolTipText("MP insuficiente");
            }
            
            skillButton.addActionListener(e -> executeAttack(target, skill.name));
            actionPanel.add(skillButton);
        }
        
        actionPanel.revalidate();
        actionPanel.repaint();
    }
    
    private SkillInfo[] getClassSkills(String className) {
        switch (className) {
            case "Mage":
                return new SkillInfo[]{
                    new SkillInfo("Bola de Fogo", 16, 2, null, "Dano básico de fogo"),
                    new SkillInfo("Raio Gélido", 25, 4, null, "Dano de gelo"),
                    new SkillInfo("Meteoro Congelante", 40, 8, "freeze", "Congela o inimigo!")
                };
            case "Warrior":
                return new SkillInfo[]{
                    new SkillInfo("Golpe de Espada", 10, 0, null, "Ataque básico com espada"),
                    new SkillInfo("Investida Furiosa", 15, 5, null, "Investida poderosa"),
                    new SkillInfo("Berserker", 30, 10, "burn", "Inimigo sangra!")
                };
            case "Assassin":
                return new SkillInfo[]{
                    new SkillInfo("Punhalada", 12, 0, null, "Ataque furtivo básico"),
                    new SkillInfo("Ataque Furtivo", 25, 5, null, "Ataque pelas costas"),
                    new SkillInfo("Lâmina Venenosa", 38, 10, "poison", "Veneno!")
                };
            case "Archer":
                return new SkillInfo[]{
                    new SkillInfo("Tiro Certeiro", 12, 0, null, "Tiro preciso"),
                    new SkillInfo("Chuva de Flechas", 22, 5, null, "Múltiplas flechas"),
                    new SkillInfo("Flecha Perfurante", 40, 10, "pierce", "Ignora defesa!")
                };
            case "Necromancer":
                return new SkillInfo[]{
                    new SkillInfo("Toque Sombrio", 15, 2, null, "Toque sombrio"),
                    new SkillInfo("Raio Necrótico", 25, 4, null, "Energia sombria"),
                    new SkillInfo("Drenar Vida", 33, 9, "lifesteal", "Drena vida do inimigo!")
                };
            case "Lancer":
                return new SkillInfo[]{
                    new SkillInfo("Investida da Lança", 10, 0, null, "Golpe básico de lança"),
                    new SkillInfo("Giro Mortal", 15, 5, null, "Ataque giratório"),
                    new SkillInfo("Perfuração Atordoante", 30, 10, "stun", "Atordoa o inimigo!")
                };
        }
        
        return new SkillInfo[]{
            new SkillInfo("Ataque Básico", 10, 0, null, "Ataque padrão"),
            new SkillInfo("Ataque Forte", 15, 5, null, "Ataque mais poderoso"),
            new SkillInfo("Ataque Especial", 30, 10, null, "Ataque especial")
        };
    }
    
    private void executeAttack(String target, String skill) {
        sendMessage("ACTION|" + playerName + "|attack|" + target + "|" + skill);
        actionSent = true;
        stopTimer();
        
        battleLog.append("Você preparou " + skill + " contra " + target + "!\n");
        battleLog.append("Aguardando ações dos outros jogadores...\n");
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
        
        showWaitingMessage();
    }
    
    private void executeDefense() {
        sendMessage("ACTION|" + playerName + "|defense| | ");
        actionSent = true;
        stopTimer();
        
        battleLog.append("Você se preparou para defender!\n");
        battleLog.append("Aguardando ações dos outros jogadores...\n");
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
        
        showWaitingMessage();
    }
    
    private void showItemOptions() {
        actionPanel.removeAll();
        
        JButton backButton = new JButton("Voltar");
        backButton.setFont(new Font("Arial", Font.BOLD, 16));
        backButton.setPreferredSize(new Dimension(100, 40));
        backButton.addActionListener(e -> createMainActionButtons());
        actionPanel.add(backButton);
        
        JButton healthPotionButton = new JButton("<html><center>Poção de Vida<br>(" + healthPotions + " restantes)<br>+30 HP</center></html>");
        healthPotionButton.setFont(new Font("Arial", Font.BOLD, 12));
        healthPotionButton.setPreferredSize(new Dimension(140, 60));
        if (healthPotions <= 0 || (currentPlayer != null && currentPlayer.hp >= currentPlayer.maxHP)) {
            healthPotionButton.setEnabled(false);
        }
        healthPotionButton.addActionListener(e -> useItem("Poção de Vida"));
        actionPanel.add(healthPotionButton);
        
        JButton manaPotionButton = new JButton("<html><center>Poção de Mana<br>(" + manaPotions + " restantes)<br>+15 MP</center></html>");
        manaPotionButton.setFont(new Font("Arial", Font.BOLD, 12));
        manaPotionButton.setPreferredSize(new Dimension(140, 60));
        if (manaPotions <= 0 || (currentPlayer != null && currentPlayer.mp >= currentPlayer.maxMP)) {
            manaPotionButton.setEnabled(false);
        }
        manaPotionButton.addActionListener(e -> useItem("Poção de Mana"));
        actionPanel.add(manaPotionButton);
        
        actionPanel.revalidate();
        actionPanel.repaint();
    }
    
    private void useItem(String item) {
        sendMessage("ACTION|" + playerName + "|item| |" + item);
        actionSent = true;
        stopTimer();
        
        if (item.equals("Poção de Vida")) {
            healthPotions--;
        } else if (item.equals("Poção de Mana")) {
            manaPotions--;
        }
        
        battleLog.append("Você usou " + item + "!\n");
        battleLog.append("Aguardando ações dos outros jogadores...\n");
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
        
        showWaitingMessage();
    }
    
    private void showWaitingMessage() {
        actionPanel.removeAll();
        
        JLabel waitingMsg = new JLabel("<html><center>Ação enviada!<br>Aguardando outros jogadores...</center></html>");
        waitingMsg.setFont(new Font("Arial", Font.BOLD, 18));
        waitingMsg.setHorizontalAlignment(SwingConstants.CENTER);
        actionPanel.add(waitingMsg);
        
        actionPanel.revalidate();
        actionPanel.repaint();
    }
    
    private void updatePlayerStatus() {
        if (currentPlayer != null) {
            if (hpLabel != null) {
                hpLabel.setText("HP: " + currentPlayer.hp + "/" + currentPlayer.maxHP);
                hpBar.setValue(currentPlayer.hp);
                hpBar.setMaximum(currentPlayer.maxHP);
            }
            
            if (mpLabel != null) {
                mpLabel.setText("MP: " + currentPlayer.mp + "/" + currentPlayer.maxMP);
                mpBar.setValue(currentPlayer.mp);
                mpBar.setMaximum(currentPlayer.maxMP);
            }
            
            if (itemsLabel != null) {
                itemsLabel.setText("Poções Vida: " + healthPotions + " | Poções Mana: " + manaPotions);
            }
            
            if (statusEffectsLabel != null) {
                if (currentPlayer.statusEffects != null && !currentPlayer.statusEffects.equals("Nenhum")) {
                    statusEffectsLabel.setText("Status: " + currentPlayer.statusEffects);
                    
                    // Colorir o texto do status
                    if (currentPlayer.statusEffects.contains("poison")) {
                        statusEffectsLabel.setForeground(Color.GREEN);
                    } else if (currentPlayer.statusEffects.contains("frozen")) {
                        statusEffectsLabel.setForeground(Color.CYAN);
                    } else if (currentPlayer.statusEffects.contains("bleeding")) {
                        statusEffectsLabel.setForeground(Color.ORANGE);
                    } else if (currentPlayer.statusEffects.contains("stunned")) {
                        statusEffectsLabel.setForeground(Color.YELLOW);
                    } else {
                        statusEffectsLabel.setForeground(Color.MAGENTA);
                    }
                } else {
                    statusEffectsLabel.setText("Status: Nenhum");
                    statusEffectsLabel.setForeground(Color.MAGENTA);
                }
            }
        }
    }
    
    private void startBattleTimer() {
        timeRemaining = 120;
        
        battleTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (actionSent || currentPlayer == null || !currentPlayer.isAlive) {
                    battleTimer.stop();
                    return;
                }
                
                timeRemaining--;
                int minutes = timeRemaining / 60;
                int seconds = timeRemaining % 60;
                
                if (timerLabel != null) {
                    timerLabel.setText(String.format("Tempo: %02d:%02d", minutes, seconds));
                    
                    if (timeRemaining <= 30) {
                        timerLabel.setForeground(Color.RED);
                    } else if (timeRemaining <= 60) {
                        timerLabel.setForeground(Color.ORANGE);
                    } else {
                        timerLabel.setForeground(Color.BLACK);
                    }
                }
                
                if (timeRemaining <= 0) {
                    battleTimer.stop();
                    if (!actionSent && currentPlayer != null && currentPlayer.isAlive) {
                        // Verificar se esta paralisado
                        if (currentPlayer.statusEffects.contains("frozen") || 
                            currentPlayer.statusEffects.contains("stunned")) {
                            battleLog.append("TEMPO ESGOTADO! (Você está paralisado)\n");
                            sendMessage("ACTION|" + playerName + "|paralyzed| | ");
                        } else {
                            battleLog.append("TEMPO ESGOTADO! Ação automática: DEFESA\n");
                            executeDefense();
                        }
                        actionSent = true;
                    }
                }
            }
        });
        
        battleTimer.start();
    }
    
    private void stopTimer() {
        if (battleTimer != null) {
            battleTimer.stop();
        }
    }

    // Informações do jogador
    static class Player {
        String name;
        String playerClass;
        int hp;
        int maxHP;
        int mp;
        int maxMP;
        boolean isAlive;
        boolean isDefending;
        String statusEffects;
        
        Player(String name, String playerClass) {
            this.name = name;
            this.playerClass = playerClass;
            this.hp = 100;
            this.maxHP = 100;
            this.mp = 20;
            this.maxMP = 20;
            this.isAlive = true;
            this.isDefending = false;
            this.statusEffects = "Nenhum";
        }
    }
    
    // Informações das habilidades
    static class SkillInfo {
        String name;
        int baseDamage;
        int mpCost;
        String specialEffect;
        String description;
        
        SkillInfo(String name, int baseDamage, int mpCost, String specialEffect, String description) {
            this.name = name;
            this.baseDamage = baseDamage;
            this.mpCost = mpCost;
            this.specialEffect = specialEffect;
            this.description = description;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
            
            }
            
            new RpgClient();
        });
    }
}