import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RpgServer extends JFrame {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private java.util.List<Player> lobbyPlayers = Collections.synchronizedList(new ArrayList<>());
    private boolean gameStarted = false;
    private boolean battleInProgress = false;
    private Map<String, PlayerAction> currentTurnActions = new ConcurrentHashMap<>();
    private int currentTurnNumber = 0;
    
    
    private JTextArea serverLog;
    private JTextArea playersArea;
    private JButton startGameButton;
    private JLabel statusLabel;
    
    // Ordem de agilidade
    private Map<String, Integer> classAgility = new HashMap<String, Integer>() {{
        put("Assassin", 6);
        put("Archer", 5);
        put("Mage", 4);
        put("Necromancer", 3);
        put("Lancer", 2);
        put("Warrior", 1);
    }};

    public RpgServer() {
        setupGUI();
        startServer();
    }
    
    private void setupGUI() {
        setTitle("RPG Server - Porta " + PORT);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Painel superior
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Servidor iniciado - Aguardando conexões", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setForeground(Color.GREEN);
        topPanel.add(statusLabel, BorderLayout.CENTER);
        
        startGameButton = new JButton("INICIAR PARTIDA");
        startGameButton.setFont(new Font("Arial", Font.BOLD, 14));
        startGameButton.setEnabled(false);
        startGameButton.addActionListener(e -> startGame());
        topPanel.add(startGameButton, BorderLayout.EAST);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Painel central
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // Log do servidor
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Log do servidor"));
        
        serverLog = new JTextArea();
        serverLog.setEditable(false);
        serverLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(serverLog);
        leftPanel.add(logScroll, BorderLayout.CENTER);
        
        // Jogadores conectados
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Jogadores Conectados"));
        
        playersArea = new JTextArea();
        playersArea.setEditable(false);
        playersArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane playersScroll = new JScrollPane(playersArea);
        rightPanel.add(playersScroll, BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(400);
        
        add(splitPane, BorderLayout.CENTER);
        
        setLocationRelativeTo(null);
    }
    
    // ServerSocket e conexões
    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            logMessage("Servidor iniciado na porta " + PORT);
            
            Thread acceptThread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler clientHandler = new ClientHandler(clientSocket);
                        Thread clientThread = new Thread(clientHandler);
                        clientThread.start();
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            logMessage("Erro ao aceitar conexão: " + e.getMessage());
                        }
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
            
        } catch (IOException e) {
            logMessage("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            
            serverLog.append("" + message + "\n");
            serverLog.setCaretPosition(serverLog.getDocument().getLength());
        });
        System.out.println("[SERVER] " + message);
    }
    
    private void updatePlayersDisplay() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("JOGADORES NO LOBBY (").append(lobbyPlayers.size()).append("/6)\n");
            sb.append("================================\n");
            if (battleInProgress) {
                sb.append("TURNO ATUAL: ").append(currentTurnNumber).append("\n");
                sb.append("================================\n");
            }
            
            for (int i = 0; i < lobbyPlayers.size(); i++) {
                Player player = lobbyPlayers.get(i);
                sb.append(i + 1).append(". ").append(player.name).append("\n");
                sb.append("   Classe: ").append(player.playerClass).append("\n");
                sb.append("   Agilidade: ").append(player.getAgility()).append("\n");
                sb.append("   Status: ").append(player.isAlive ? "Vivo" : "Morto").append("\n");
                sb.append("   HP: ").append(player.hp).append("/").append(player.maxHP).append("\n");
                sb.append("   MP: ").append(player.mp).append("/").append(player.maxMP).append("\n");
                if (!player.statusEffects.isEmpty()) {
                    sb.append("   Efeitos: ").append(player.getStatusEffectsString()).append("\n");
                }
                sb.append("\n");
            }
            
            playersArea.setText(sb.toString());
            
            // Botao de iniciar jogo
            startGameButton.setEnabled(lobbyPlayers.size() >= 2 && !gameStarted);
            
            if (lobbyPlayers.size() >= 2) {
                statusLabel.setText("Pronto para iniciar! (" + lobbyPlayers.size() + " jogadores)");
                statusLabel.setForeground(Color.GREEN);
            } else {
                statusLabel.setText("Aguardando jogadores");
                statusLabel.setForeground(Color.ORANGE);
            }
        });
    }
    
    private void startGame() {
        if (lobbyPlayers.size() < 2) {
            JOptionPane.showMessageDialog(this, "Mínimo de 2 jogadores necessário");
            return;
        }
        
        gameStarted = true;
        battleInProgress = true;
        currentTurnNumber = 1;
        startGameButton.setEnabled(false);
        statusLabel.setText("PARTIDA EM ANDAMENTO");
        statusLabel.setForeground(Color.RED);
        
        logMessage("=== PARTIDA INICIADA ===");
        logMessage("Jogadores participantes: " + lobbyPlayers.size());
        
        
        broadcastMessage("GAME_START");
        
        
        Timer delayTimer = new Timer(500, e -> {
            sendPlayersInfo();
            
            broadcastMessage("START_TURN|" + currentTurnNumber);
            logMessage("Turno " + currentTurnNumber + " iniciado");
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }
    
    private void sendPlayersInfo() {
        StringBuilder playersInfo = new StringBuilder("PLAYERS_INFO");
        synchronized (lobbyPlayers) {
            for (Player player : lobbyPlayers) {
                playersInfo.append("|").append(player.name)
                          .append(",").append(player.playerClass)
                          .append(",").append(player.getAgility())
                          .append(",").append(player.hp)
                          .append(",").append(player.maxHP)
                          .append(",").append(player.mp)
                          .append(",").append(player.maxMP)
                          .append(",").append(player.isAlive)
                          .append(",").append(player.getStatusEffectsString());
            }
        }
        broadcastMessage(playersInfo.toString());
    }
    
    private void sendPlayersUpdate() {
        StringBuilder playersInfo = new StringBuilder("PLAYERS_UPDATE");
        synchronized (lobbyPlayers) {
            for (Player player : lobbyPlayers) {
                playersInfo.append("|").append(player.name)
                          .append(",").append(player.playerClass)
                          .append(",").append(player.getAgility())
                          .append(",").append(player.hp)
                          .append(",").append(player.maxHP)
                          .append(",").append(player.mp)
                          .append(",").append(player.maxMP)
                          .append(",").append(player.isAlive)
                          .append(",").append(player.getStatusEffectsString());
            }
        }
        broadcastMessage(playersInfo.toString());
    }
    
    private void broadcastMessage(String message) {
        synchronized (connectedClients) {
            Iterator<Map.Entry<String, ClientHandler>> it = connectedClients.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ClientHandler> entry = it.next();
                ClientHandler client = entry.getValue();
                if (!client.sendMessage(message)) {
                    // jogador desconectado
                    it.remove();
                }
            }
        }
    }
    
    private Player getPlayerByName(String name) {
        synchronized (lobbyPlayers) {
            return lobbyPlayers.stream()
                    .filter(player -> player.name.equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }
    
    private void processTurnActions() {
        synchronized (currentTurnActions) {
            // Calcular acoes dos jogadores
            int expectedActions = 0;
            synchronized (lobbyPlayers) {
                for (Player player : lobbyPlayers) {
                    if (player.isAlive) {
                        expectedActions++;
                    }
                }
            }
            
            logMessage("Ações recebidas: " + currentTurnActions.size() + "/" + expectedActions);
            
            if (currentTurnActions.size() < expectedActions) {
                return;
            }
            
            logMessage("Processando turno " + currentTurnNumber + " com " + currentTurnActions.size() + " ações");
            
            
            processStatusEffects();
            
            // Organizar ações
            ArrayList<PlayerAction> sortedActions = new ArrayList<>(currentTurnActions.values());
            sortedActions.sort((a, b) -> Integer.compare(b.priority, a.priority));
            
            StringBuilder turnResult = new StringBuilder("TURN_RESULT");
            
            // Processar todas as ações
            for (PlayerAction action : sortedActions) {
                Player actor = getPlayerByName(action.playerName);
                if (actor != null && actor.isAlive) {
                    String result = executePlayerAction(action);
                    turnResult.append("|").append(result);
                    logMessage("Executada ação de " + action.playerName + ": " + action.actionType);
                }
            }
            
            
            currentTurnActions.clear();
            logMessage("Ações do turno " + currentTurnNumber + " processadas");
            
            // Decrementar duração de efeitos
            synchronized (lobbyPlayers) {
                for (Player player : lobbyPlayers) {
                    player.isDefending = false;
                    player.decrementStatusEffects();
                }
            }
            
            
            sendPlayersUpdate();
            
            
            broadcastMessage(turnResult.toString());
            
            
            if (!checkGameEnd()) {
                currentTurnNumber++;
                Timer nextTurnTimer = new Timer(1000, e -> {
                    broadcastMessage("START_TURN|" + currentTurnNumber);
                    logMessage("Turno " + currentTurnNumber + " iniciado");
                });
                nextTurnTimer.setRepeats(false);
                nextTurnTimer.start();
            }
            
            
            updatePlayersDisplay();
        }
    }
    
    private void processStatusEffects() {
        StringBuilder effects = new StringBuilder();
        synchronized (lobbyPlayers) {
            for (Player player : lobbyPlayers) {
                if (!player.isAlive) continue;
                
                if (player.hasStatusEffect("poison")) {
                    player.hp = Math.max(0, player.hp - 5);
                    effects.append(player.name).append(" sofreu 5 de dano por veneno! ");
                    logMessage(player.name + " sofreu 5 de dano por veneno!");
                    if (player.hp <= 0) {
                        player.isAlive = false;
                        effects.append(player.name).append(" foi derrotado pelo veneno! ");
                    }
                }
                
                if (player.hasStatusEffect("bleeding")) {
                    player.hp = Math.max(0, player.hp - 6);
                    effects.append(player.name).append(" sofreu 6 de dano por sangramento! ");
                    logMessage(player.name + " sofreu 6 de dano por sangramento!");
                    if (player.hp <= 0) {
                        player.isAlive = false;
                        effects.append(player.name).append(" foi derrotado pelo sangramento! ");
                    }
                }
            }
        }
        
        if (effects.length() > 0) {
            logMessage("Efeitos de status processados: " + effects.toString());
        }
    }
    
    private String executePlayerAction(PlayerAction action) {
        StringBuilder result = new StringBuilder();
        result.append(action.playerName).append(": ");
        
        Player actor = getPlayerByName(action.playerName);
        if (actor == null || !actor.isAlive) {
            return result.append("não pode agir").toString();
        }
        
        switch (action.actionType) {
            case "attack":
                String targetName = action.target != null ? action.target.trim() : "";
                if (targetName.isEmpty()) {
                    result.append("tentou atacar mas não selecionou alvo");
                    break;
                }
                
                Player target = getPlayerByName(targetName);
                if (target != null && target.isAlive) {
                    SkillInfo skillInfo = getSkillInfo(action.playerName, action.skill);
                    
                    if (actor.mp < skillInfo.mpCost) {
                        result.append("não tinha MP suficiente para usar ").append(action.skill);
                        break;
                    }
                    
                    actor.mp = Math.max(0, actor.mp - skillInfo.mpCost);
                    
                    int damage = skillInfo.baseDamage;
                    
                    // Dano na defesa
                    if (target.isDefending) {
                        damage = (int)(damage * 0.5);
                        result.append("atacou ").append(target.name).append(" (defendendo) com ").append(action.skill);
                    } else {
                        result.append("atacou ").append(target.name).append(" com ").append(action.skill);
                    }
                    
                    // Ignorar defesa
                    if ("pierce".equals(skillInfo.specialEffect) && target.isDefending) {
                        damage = skillInfo.baseDamage + (int)(Math.random() * 20);
                        result.append(" (ataque perfurante ignorou defesa!)");
                    }
                    
                    target.hp = Math.max(0, target.hp - damage);
                    result.append(" causando ").append(damage).append(" de dano");
                    
                    // Efeitos especiais
                    if (skillInfo.specialEffect != null && !skillInfo.specialEffect.isEmpty()) {
                        applySpecialEffect(actor, target, skillInfo, result);
                    }
                    
                    if (target.hp <= 0) {
                        target.isAlive = false;
                        result.append(" - ").append(target.name).append(" foi derrotado!");
                    }
                } else {
                    result.append("tentou atacar um alvo inválido");
                }
                break;
                
            case "defense":
                actor.isDefending = true;
                result.append("assumiu posição defensiva");
                break;
                
            case "item":
                String itemName = action.skill != null ? action.skill.trim() : "";
                if (itemName.equals("Poção de Vida")) {
                    int healAmount = Math.min(30, actor.maxHP - actor.hp);
                    actor.hp += healAmount;
                    result.append("usou Poção de Vida e recuperou ").append(healAmount).append(" HP");
                } else if (itemName.equals("Poção de Mana")) {
                    int manaAmount = Math.min(15, actor.maxMP - actor.mp);
                    actor.mp += manaAmount;
                    result.append("usou Poção de Mana e recuperou ").append(manaAmount).append(" MP");
                } else {
                    result.append("usou item desconhecido");
                }
                break;
                
            case "paralyzed":
                // Jogadores paralisados
                if (actor.hasStatusEffect("frozen")) {
                    result.append("está congelado e não pode agir");
                } else if (actor.hasStatusEffect("stunned")) {
                    result.append("está atordoado e não pode agir");
                } else {
                    result.append("estava paralisado mas o efeito passou");
                }
                break;
                
            case "skip":
                result.append("pulou o turno");
                break;
                
            default:
                result.append("executou ação desconhecida: ").append(action.actionType);
                break;
        }
        
        return result.toString();
    }
    
    private void applySpecialEffect(Player attacker, Player target, SkillInfo skillInfo, StringBuilder result) {
        switch (skillInfo.specialEffect) {
            case "freeze":
                target.addStatusEffect("frozen", 2);
                result.append(" - ").append(target.name).append(" está CONGELADO por 2 turnos!");
                break;
            case "poison":
                target.addStatusEffect("poison", 3);
                result.append(" - ").append(target.name).append(" está ENVENENADO por 3 turnos!");
                break;
            case "burn":
                target.addStatusEffect("bleeding", 2);
                result.append(" - ").append(target.name).append(" está SANGRANDO por 2 turnos!");
                break;
            case "stun":
                target.addStatusEffect("stunned", 2);
                result.append(" - ").append(target.name).append(" está ATORDOADO por 2 turnos!");
                break;
            case "lifesteal":
                int healAmount = Math.min(15, attacker.maxHP - attacker.hp);
                attacker.hp += healAmount;
                result.append(" - ").append(attacker.name).append(" drenou ").append(healAmount).append(" HP!");
                break;
            case "pierce":
                break;
        }
    }
    
    private SkillInfo getSkillInfo(String playerName, String skillName) {
        Player player = getPlayerByName(playerName);
        if (player == null) return new SkillInfo(skillName, 10, 0, null);
        
        switch (player.playerClass) {
            case "Mage":
                switch (skillName) {
                    case "Bola de Fogo": return new SkillInfo(skillName, 16, 2, null);
                    case "Raio Gélido": return new SkillInfo(skillName, 25, 4, null);
                    case "Meteoro Congelante": return new SkillInfo(skillName, 40, 8, "freeze");
                }
                break;
            case "Warrior":
                switch (skillName) {
                    case "Golpe de Espada": return new SkillInfo(skillName, 10, 0, null);
                    case "Investida Furiosa": return new SkillInfo(skillName, 15, 5, null);
                    case "Berserker": return new SkillInfo(skillName, 30, 10, "burn");
                }
                break;
            case "Assassin":
                switch (skillName) {
                    case "Punhalada": return new SkillInfo(skillName, 12, 0, null);
                    case "Ataque Furtivo": return new SkillInfo(skillName, 25, 5, null);
                    case "Lâmina Venenosa": return new SkillInfo(skillName, 38, 10, "poison");
                }
                break;
            case "Archer":
                switch (skillName) {
                    case "Tiro Certeiro": return new SkillInfo(skillName, 12, 0, null);
                    case "Chuva de Flechas": return new SkillInfo(skillName, 22, 5, null);
                    case "Flecha Perfurante": return new SkillInfo(skillName, 40, 10, "pierce");
                }
                break;
            case "Necromancer":
                switch (skillName) {
                    case "Toque Sombrio": return new SkillInfo(skillName, 15, 2, null);
                    case "Raio Necrótico": return new SkillInfo(skillName, 25, 4, null);
                    case "Drenar Vida": return new SkillInfo(skillName, 33, 9, "lifesteal");
                }
                break;
            case "Lancer":
                switch (skillName) {
                    case "Investida da Lança": return new SkillInfo(skillName, 10, 0, null);
                    case "Giro Mortal": return new SkillInfo(skillName, 15, 5, null);
                    case "Perfuração Atordoante": return new SkillInfo(skillName, 30, 10, "stun");
                }
                break;
        }
        
        return new SkillInfo(skillName, 10, 0, null);
    }
    
    private int getAlivePlayersCount() {
        synchronized (lobbyPlayers) {
            return (int) lobbyPlayers.stream().filter(player -> player.isAlive).count();
        }
    }
    
    private boolean checkGameEnd() {
        int aliveCount = getAlivePlayersCount();
        if (aliveCount <= 1) {
            battleInProgress = false;
            gameStarted = false;
            
            Player winner = null;
            synchronized (lobbyPlayers) {
                winner = lobbyPlayers.stream()
                        .filter(player -> player.isAlive)
                        .findFirst()
                        .orElse(null);
            }
            
            String endMessage = "GAME_END|" + (winner != null ? winner.name : "Empate");
            broadcastMessage(endMessage);
            
            logMessage("=== PARTIDA FINALIZADA ===");
            logMessage("Vencedor: " + (winner != null ? winner.name : "Empate"));
            
            final String winnerName = winner != null ? winner.name : "Empate";
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Partida finalizada - Vencedor: " + winnerName);
                statusLabel.setForeground(Color.BLUE);
                startGameButton.setText("NOVA PARTIDA");
                startGameButton.setEnabled(true);
            });
            
            currentTurnActions.clear();
            currentTurnNumber = 0;
            synchronized (lobbyPlayers) {
                for (Player player : lobbyPlayers) {
                    player.hp = player.maxHP;
                    player.mp = player.maxMP;
                    player.isAlive = true;
                    player.isDefending = false;
                    player.statusEffects.clear();
                }
            }
            
            return true;
        }
        return false;
    }
    
    static class Player {
        String name;
        String playerClass;
        int hp;
        int maxHP;
        int mp;
        int maxMP;
        boolean isAlive;
        boolean isDefending;
        Map<String, Integer> statusEffects;
        
        Player(String name, String playerClass) {
            this.name = name;
            this.playerClass = playerClass;
            this.hp = 100;
            this.maxHP = 100;
            this.mp = 20;
            this.maxMP = 20;
            this.isAlive = true;
            this.isDefending = false;
            this.statusEffects = new HashMap<>();
        }
        
        int getAgility() {
            Map<String, Integer> agility = new HashMap<String, Integer>() {{
                put("Assassin", 6);
                put("Archer", 5);
                put("Mage", 4);
                put("Necromancer", 3);
                put("Lancer", 2);
                put("Warrior", 1);
            }};
            return agility.getOrDefault(playerClass, 1);
        }
        
        void addStatusEffect(String effect, int duration) {
            statusEffects.put(effect, duration);
        }
        
        boolean hasStatusEffect(String effect) {
            return statusEffects.containsKey(effect);
        }
        
        String getStatusEffectsString() {
            if (statusEffects.isEmpty()) return "Nenhum";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : statusEffects.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
            }
            return sb.toString();
        }
        
        void decrementStatusEffects() {
            Iterator<Map.Entry<String, Integer>> it = statusEffects.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> entry = it.next();
                int newDuration = entry.getValue() - 1;
                if (newDuration <= 0) {
                    it.remove();
                } else {
                    entry.setValue(newDuration);
                }
            }
        }
    }
    
    static class PlayerAction {
        String playerName;
        String actionType;
        String target;
        String skill;
        int priority;
        
        PlayerAction(String playerName, String actionType, String target, String skill, int priority) {
            this.playerName = playerName;
            this.actionType = actionType;
            this.target = target;
            this.skill = skill;
            this.priority = priority;
        }
    }
    
    static class SkillInfo {
        String name;
        int baseDamage;
        int mpCost;
        String specialEffect;
        
        SkillInfo(String name, int baseDamage, int mpCost, String specialEffect) {
            this.name = name;
            this.baseDamage = baseDamage;
            this.mpCost = mpCost;
            this.specialEffect = specialEffect;
        }
    }
    
    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String playerName;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                logMessage("Erro ao configurar cliente: " + e.getMessage());
            }
        }
        
        public boolean sendMessage(String message) {
            if (out != null && !socket.isClosed()) {
                try {
                    out.println(message);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }
        
        @Override
        public void run() {
            try {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    processClientMessage(inputLine);
                }
            } catch (IOException e) {
                logMessage("Cliente desconectado: " + (playerName != null ? playerName : "Desconhecido"));
            } finally {
                disconnect();
            }
        }
        
        private void processClientMessage(String message) {
            String[] parts = message.split("\\|", -1);
            String command = parts[0];
            
            switch (command) {
                case "JOIN":
                    if (parts.length >= 3) {
                        String name = parts[1];
                        String playerClass = parts[2];
                        handlePlayerJoin(name, playerClass);
                    }
                    break;
                    
                case "ACTION":
                    if (parts.length >= 5) {
                        String name = parts[1];
                        String actionType = parts[2];
                        String target = parts[3].trim();
                        String skill = parts[4].trim();
                        handlePlayerAction(name, actionType, target, skill);
                    }
                    break;
                    
                default:
                    logMessage("Comando desconhecido: " + command + " de " + 
                              (playerName != null ? playerName : "cliente desconhecido"));
                    break;
            }
        }
        
        private void handlePlayerJoin(String name, String playerClass) {
            if (gameStarted) {
                sendMessage("JOIN_REJECTED|Partida já iniciada");
                return;
            }
            
            if (lobbyPlayers.size() >= 6) {
                sendMessage("JOIN_REJECTED|Sala lotada");
                return;
            }
            
            boolean nameExists;
            synchronized (lobbyPlayers) {
                nameExists = lobbyPlayers.stream()
                        .anyMatch(player -> player.name.equals(name));
            }
            
            if (nameExists) {
                sendMessage("JOIN_REJECTED|Nome já existe");
                return;
            }
            
            this.playerName = name;
            Player newPlayer = new Player(name, playerClass);
            
            synchronized (lobbyPlayers) {
                lobbyPlayers.add(newPlayer);
            }
            
            synchronized (connectedClients) {
                connectedClients.put(name, this);
            }
            
            sendMessage("JOIN_SUCCESS");
            logMessage("Jogador conectado: " + name + " (" + playerClass + ")");
            
            updateAllClientsLobby();
            updatePlayersDisplay();
        }
        
        private void handlePlayerAction(String playerName, String actionType, String target, String skill) {
            if (!battleInProgress) {
                logMessage("Ação ignorada - batalha não está em andamento: " + playerName);
                return;
            }
            
            Player player = getPlayerByName(playerName);
            if (player == null || !player.isAlive) {
                logMessage("Ação ignorada - jogador inválido ou morto: " + playerName);
                return;
            }
            
            synchronized (currentTurnActions) {
                if (currentTurnActions.containsKey(playerName)) {
                    logMessage("Ação ignorada - " + playerName + " já enviou ação este turno");
                    return;
                }
                
                if ((player.hasStatusEffect("frozen") || player.hasStatusEffect("stunned")) 
                    && !actionType.equals("paralyzed")) {
                    logMessage("Ação ignorada - " + playerName + " está impedido e tentou agir");
                    return;
                }
                
                PlayerAction action = new PlayerAction(playerName, actionType, target, skill, 
                        classAgility.getOrDefault(player.playerClass, 1));
                currentTurnActions.put(playerName, action);
                
                logMessage("Ação recebida de " + playerName + ": " + actionType + 
                          (target != null && !target.isEmpty() && !target.trim().isEmpty() ? " -> " + target : "") +
                          (skill != null && !skill.isEmpty() && !skill.trim().isEmpty() ? " (" + skill + ")" : ""));
            }
            
            processTurnActions();
        }
        
        private void updateAllClientsLobby() {
            StringBuilder lobbyInfo = new StringBuilder("LOBBY_UPDATE");
            synchronized (lobbyPlayers) {
                for (Player player : lobbyPlayers) {
                    lobbyInfo.append("|").append(player.name).append(",").append(player.playerClass);
                }
            }
            broadcastMessage(lobbyInfo.toString());
        }
        
        private void disconnect() {
            if (playerName != null) {
                synchronized (connectedClients) {
                    connectedClients.remove(playerName);
                }
                
                boolean wasInGame = false;
                synchronized (lobbyPlayers) {
                    Player disconnectedPlayer = getPlayerByName(playerName);
                    if (disconnectedPlayer != null && battleInProgress) {
                        disconnectedPlayer.isAlive = false;
                        wasInGame = true;
                        logMessage("Jogador " + playerName + " desconectou durante a batalha!");
                        
                        synchronized (currentTurnActions) {
                            if (!currentTurnActions.containsKey(playerName)) {
                                PlayerAction skipAction = new PlayerAction(playerName, "skip", "", "", 0);
                                currentTurnActions.put(playerName, skipAction);
                            }
                        }
                    }
                    
                    if (!battleInProgress) {
                        lobbyPlayers.removeIf(player -> player.name.equals(playerName));
                    }
                }
                
                updatePlayersDisplay();
                
                if (!battleInProgress) {
                    updateAllClientsLobby();
                } else if (wasInGame) {
                    sendPlayersUpdate();
                    processTurnActions();
                }
                
                logMessage("Jogador desconectado: " + playerName);
            }
            
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
            }
            
            RpgServer server = new RpgServer();
            server.setVisible(true);
        });
    }
}