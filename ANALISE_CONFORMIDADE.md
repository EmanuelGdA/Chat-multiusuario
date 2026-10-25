# Análise de conformidade — ConnectChat

Comparação da cópia do projeto com a especificação formal Especificacao_4_Chat_Multiusuario e os materiais SEMANA1, SEMANA2, SEMANA3 e SEMANA4 de Programação Orientada a Objetos 2026.

## Alterações realizadas nesta cópia

- Removida a funcionalidade de resumo por IA: botão e painel lateral, escolha de data, configuração de chave, chamada HTTP, serviço OpenAI e instruções dessa integração.
- Recolocada na interface a conversa Sala geral. As mensagens são salvas no MySQL antes do broadcast; quando a gravação falha, o servidor avisa o remetente e não transmite a mensagem.
- Mantidas conversas privadas e espaço pessoal como recursos adicionais. Eles não substituem a Sala geral exigida pela especificação.
- Removida a senha do MySQL que estava no fonte. ConexaoFactory agora lê CHAT_DB_PASSWORD; CHAT_DB_USER e CHAT_DB_URL são opcionais, com usuário root e URL localhost:3306/chat_db como padrões.
- Adicionado .gitignore para target, contatos locais e arquivos de ambiente.
- Criado este relatório para registrar a comparação e explicar a concorrência.

## Especificação do projeto — critérios e rubrica

| Critério | Resultado observado |
|---|---|
| Múltiplos clientes e thread por conexão — 1,3 ponto | O servidor aceita sockets em laço e cria uma thread para cada ClienteHandler. |
| Broadcast sincronizado — 1,2 ponto | A Sala geral transmite eventos estruturados a todos os clientes conectados. A iteração da lista sincronizada ocorre dentro de synchronized(clientes); os métodos de escrita dos handlers também são sincronizados. |
| JDBC para salvar e recuperar — 1,0 ponto | MensagemRepository usa PreparedStatement e try-with-resources; o servidor só transmite a mensagem da sala após salvar. O histórico de até 50 mensagens é enviado ao entrar/abrir a sala. Depende do MySQL, script e credenciais válidas. |
| Pacotes e responsabilidades — 0,5 ponto | Há client, server, model e persistence. O SQL está nos repositories. |
| Pelo menos cinco testes JUnit — 0,5 ponto | MensagemTest contém cinco métodos @Test de regras e formatação, sem banco/rede real. A suíte não foi executada durante esta revisão. |
| Organização, relatório e clareza — 0,5 ponto | Este arquivo documenta arquitetura e concorrência. README contém instruções de execução; falta gravar a demonstração em vídeo. |

A especificação também exige que a desconexão de um cliente não derrube os demais e que cada mensagem registre remetente e horário. ClienteHandler encerra a conexão no finally e o servidor remove o handler; Mensagem registra remetente, conteúdo e horário. O código compila para Java 17, mas não validei a execução real com MySQL nem fiz teste com três instâncias.

## Comparação com as Semanas 1–4

### Semana 1 — organização, domínio, Repository e segredos

- Alinhado: pacotes separados; Mensagem e Usuario estão em model; SQL está fora das classes de domínio; PreparedStatement protege os comandos SQL.
- Ajustado: a senha do banco não fica mais no fonte. Configure CHAT_DB_PASSWORD no terminal que iniciar o servidor.
- Observação: há duas persistências concretas (sala e mensagens privadas), mas o trabalho principal de broadcast/histórico usa MensagemRepository.

### Semana 2 — threads, sincronização e testes

- Alinhado: uma thread atende cada conexão; a lista compartilhada é sincronizada; o broadcast sincroniza a iteração; desconexão é tratada por handler.
- Ajustado: mensagens da Sala geral são salvas antes do envio, para o caminho principal não divulgar mensagem que não entrou no histórico.
- Parcial: os cinco testes cobrem regras simples de Mensagem/Usuario, mas não há teste automatizado da concorrência ou do broadcast sem sockets, atividade enfatizada na Semana 2. O teste de desconexão e a troca simultânea de mensagens entre três clientes ainda precisam ser demonstrados.

### Semana 3 — persistência, Swing e entrega

- Alinhado: repositories usam JDBC, PreparedStatement e fechamento automático dos recursos; a sala e mensagens privadas têm tabelas no script SQL.
- Alinhado: eventos recebidos na thread de rede atualizam a tela via SwingUtilities.invokeLater.
- Pendente de confirmação: o histórico depende do MySQL ativo, com as tabelas criadas e variáveis configuradas. O relatório está aqui; o vídeo de três clientes não está no projeto.
- A aula tem uma divergência sobre interface gráfica; a especificação/rubrica formal prioriza cliente-servidor, broadcast, JDBC e testes. A interface Swing existente é válida, mas não substitui esses critérios.

### Semana 4 — arquitetura, SOLID e injeção

- Alinhado: há separação entre tela/cliente, servidor/handler, modelo e persistência; a tela não executa SQL.
- Recomendação não obrigatória da arquitetura: Servidor instancia diretamente os repositories. Injetá-los pelo construtor e criar interfaces de repositório facilitaria testes sem banco, como recomenda o material de arquitetura/SOLID.
- Recomendação de manutenção: ClienteChat concentra interface, protocolo de socket e estado das conversas em uma classe grande. Separar View e Controller reduziria responsabilidades, mas não é requisito funcional explícito da rubrica.

## O que falta antes da entrega

1. Executar sql/criar_tabelas.sql no MySQL da máquina e configurar CHAT_DB_PASSWORD no mesmo terminal que inicia o servidor. A porta padrão permanece 3306.
2. Executar a suíte JUnit e confirmar que os cinco testes passam.
3. Demonstrar troca simultânea, histórico e desconexão usando pelo menos três clientes; gravar o vídeo pedido pela especificação.
4. Se duas pessoas puderem usar o mesmo nome, considerar adicionar o ID do remetente na tabela de mensagens da Sala geral. Hoje o nome identifica o remetente no histórico, o que atende ao campo pedido, mas não distingue homônimos.

## Arquivos principais

- src/main/java/client/ClienteChat.java — interface Swing e protocolo do cliente.
- src/main/java/server/Servidor.java — accept, lista de conexões, broadcast e histórico.
- src/main/java/server/ClienteHandler.java — leitura do cliente e protocolo por conexão.
- src/main/java/model/Mensagem.java — remetente, conteúdo e horário da sala.
- src/main/java/persistence/MensagemRepository.java — persistência JDBC da sala.
- src/main/java/persistence/ConexaoFactory.java — conexão MySQL sem senha embutida.
- src/test/java/model/MensagemTest.java — cinco testes unitários existentes.
- sql/criar_tabelas.sql — schema MySQL.
