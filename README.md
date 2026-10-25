# ConnectChat

Aplicativo desktop de mensagens em Java Swing com servidor TCP, conversas diretas, Sala geral e persistência MySQL. O projeto usa Java 17, Maven e MySQL Connector/J.

## Recursos

- Sala geral com mensagens e histórico.
- Conversas diretas identificadas por ID.
- Indicador de presença enquanto o usuário está conectado ao servidor.
- Envio de fotos, PDFs e outros arquivos de até 5 MB.
- Histórico de mensagens privadas e arquivos mantido no MySQL.
- Preferências locais de perfil, tema e conversas.

## Tecnologias e organização

| Pasta | Responsabilidade |
| --- | --- |
| client | Interface Java Swing e conexão do usuário |
| server | Socket TCP, atendimento de clientes e entrega de mensagens/arquivos |
| model | Mensagens, arquivos e conversas |
| persistence | Repositórios JDBC e conexão com MySQL |
| sql | Script para criar o banco e as tabelas |

O servidor atende conexões TCP na porta 12345. A conexão do cliente está configurada para localhost; a porta MySQL 3306 é separada e usada pela persistência do servidor.

## Requisitos

- JDK 17 ou superior.
- Maven instalado e disponível no terminal (mvn -v).
- MySQL 8.x acessível pela porta 3306.
- MySQL Connector/J 8.3.0, incluído como dependência Maven.

## Configurar o banco MySQL

1. Inicie o serviço MySQL.
2. No MySQL Workbench, abra e execute o script sql/criar_tabelas.sql.
3. O script cria o banco chat_db e as tabelas mensagens, mensagens_privadas e arquivos_chat. Pode ser executado novamente: as tabelas existentes não são substituídas.
4. Tenha em mãos um usuário MySQL autorizado a ler e gravar nesse banco.

A URL padrão do projeto é:

~~~text
jdbc:mysql://localhost:3306/chat_db?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true
~~~

A senha não fica no código nem neste README. Configure as variáveis no terminal PowerShell que iniciará o servidor:

~~~powershell
$env:CHAT_DB_USER = "root"
$env:CHAT_DB_PASSWORD = "SUA_SENHA_DO_MYSQL"
~~~

Se o banco estiver em outro computador, configure a URL no mesmo terminal. Troque HOST_DO_MYSQL pelo nome ou endereço que seja alcançável a partir do computador que executa o servidor ConnectChat:

~~~powershell
$env:CHAT_DB_URL = "jdbc:mysql://HOST_DO_MYSQL:3306/chat_db?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true"
~~~

O processo Servidor é quem acessa o MySQL; os clientes não precisam das credenciais do banco. Use um usuário MySQL próprio com permissões limitadas ao chat_db, em vez de compartilhar a conta root. Não publique senhas nem exponha o MySQL à Internet sem controle de acesso.

## Compilar e executar no Windows

Abra a pasta do projeto no VS Code. Use o perfil PowerShell, não o Prompt de Comando (CMD).

### 1. Compilar

Na raiz do projeto:

~~~powershell
mvn clean package
~~~

### 2. Iniciar o servidor

No primeiro terminal PowerShell, configure CHAT_DB_USER e CHAT_DB_PASSWORD e execute:

~~~powershell
.\LIGAR_SERVIDOR.bat
~~~

Deixe esse terminal aberto. Ele deve indicar que o servidor está ouvindo na porta 12345.

### 3. Iniciar o cliente

Abra outro terminal PowerShell na mesma pasta:

~~~powershell
.\ABRIR_CHAT.bat
~~~

Informe um nome e um ID. Cada instância de cliente deve usar um ID diferente. Para testar várias contas no mesmo computador, inicie o cliente novamente com outro ID.

Se preferir compilar sem Maven, no PowerShell:

~~~powershell
$classes = Join-Path $PWD "target/classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$arquivos = Get-ChildItem .\src\main\java -Filter *.java -Recurse | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -d $classes $arquivos
~~~

## Usar o banco do colega

Se o colega hospeda o MySQL, o servidor ConnectChat precisa alcançar o computador dele pela rede e usar CHAT_DB_URL com esse endereço. Ele precisa manter o MySQL ativo, criar o banco/tabelas pelo script e autorizar um usuário restrito para a máquina do servidor.

localhost sempre significa “este computador”. Portanto, no computador do colega, a URL padrão aponta para o MySQL local dele. Para acessar o banco de outro computador, localhost deve ser substituído pelo endereço de rede do host do MySQL.

Compartilhar este repositório permite que a equipe obtenha o código. A aplicação ainda usa localhost para a conexão cliente-servidor e não está configurada para conversas pela Internet entre computadores diferentes. O acesso remoto ao chat exige hospedar o servidor ConnectChat em um endereço alcançável e configurar os clientes para esse servidor; mudar a porta MySQL para 3306, por si só, não habilita essa comunicação.

## Trabalho em equipe com Git

Depois que a branch feature/team-chat-3306-readme estiver publicada no GitHub, o colega pode obter o projeto com:

~~~powershell
git clone https://github.com/EmanuelGdA/Chat-multiusuario.git
cd Chat-multiusuario
git switch feature/team-chat-3306-readme
~~~

Cada integrante pode criar uma branch a partir dela e enviar alterações para revisão. Para enviar commits diretamente ao repositório, a conta do integrante precisa ter permissão de colaborador no GitHub.

## Arquivos e limites

- Anexos têm limite de 5 MB por arquivo.
- Arquivos recebidos são gravados em data/arquivos-recebidos/.
- Preferências e contatos locais ficam em data/.
- A entrega em tempo real depende do servidor estar ativo e do contato estar conectado.
- Para entregar arquivos quando o contato estiver offline, o servidor precisa conseguir salvar no MySQL e a tabela arquivos_chat deve existir.

## Solução de problemas

| Mensagem ou situação | O que conferir |
| --- | --- |
| Communications link failure | MySQL ativo, endereço/porta em CHAT_DB_URL e acesso de rede ao host |
| Access denied | CHAT_DB_USER, CHAT_DB_PASSWORD e permissões do usuário para chat_db |
| Tabela arquivos_chat ausente | Executar novamente sql/criar_tabelas.sql no Workbench |
| Address already in use na porta 12345 | Já existe outra instância do servidor; feche-a antes de iniciar outra |
| Cliente indica servidor indisponível | Inicie Servidor antes de abrir o cliente |
| Cliente distante não conecta | O cliente ainda aponta para localhost; configure e hospede um servidor alcançável |
| Arquivo recusado | Confirme que tem até 5 MB e que o servidor recebeu o resultado do envio |

## Segurança e dados locais

Não inclua senhas, tokens, dados pessoais ou arquivos recebidos nos commits. A senha do MySQL deve permanecer em uma variável de ambiente local. O banco contém mensagens e os bytes dos anexos; combine com a equipe como manter o banco e as cópias de segurança.