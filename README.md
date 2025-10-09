# Rpg-de-turno
Trabalho da matéria de redes da faculdade. Jogo de turno battle royale usando sockets

Protocolos:
Mensagens Cliente → Servidor
JOIN|nome|classe
- Solicita entrada no lobby
- Exemplo: JOIN|Player1|Warrior
ACTION|nome|tipo_ação|alvo|habilidade
- Envia ação do turno
- Tipos: attack, defense, item, paralyzed, skip
- Exemplo: ACTION|Player1|attack|Player2|Golpe de Espada

Mensagens Servidor → Cliente
JOIN_SUCCESS
- Confirma entrada no lobby
JOIN_REJECTED|motivo
- Rejeita entrada (sala cheia, jogo iniciado)
LOBBY_UPDATE|jogador1,classe1|jogador2,classe2|
- Atualiza lista de jogadores no lobby
- Exemplo: LOBBY_UPDATE|nome1,Mage|nome2,Warrior
GAME_START
- Sinaliza início da partida
PLAYERS_INFO|dados_completos
- Envia informações iniciais dos jogadores
- Formato: nome,classe,agilidade,hp,maxHP,mp,maxMP,isAlive,statusEffects
PLAYERS_UPDATE|dados_completos
- Atualiza status dos jogadores durante batalha


START_TURN|número_turno
- Inicia novo turno de combate
TURN_RESULT|resultado1|resultado2|...
- Resultados das ações do turno
- Exemplo: TURN_RESULT|nome1 atacou nome2 com Bola de Fogo causando 16 de dano
GAME_END|vencedor
- Fim da partida com nome do vencedor ou "Empate"


Fluxo de Comunicação
Conexão: Cliente conecta → Envia nome/classe → Servidor valida
Lobby: Servidor broadcast atualizações para todos
Batalha:
- Servidor envia START_TURN
- Clientes enviam ações com timeout de 2 minutos
- Servidor aguarda todas as ações dos vivos
- Processa efeitos de status
- Executa ações por ordem de agilidade
- Envia TURN_RESULT com resultados
- Envia PLAYERS_UPDATE com status atualizado
- Repete até restar 1 vivo
