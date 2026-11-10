let meuNome = localStorage.getItem("chat_nome") || "";
let meuTelefone = localStorage.getItem("chat_telefone") || "";
let meuIdentificador = "";
let conversaAtiva = "GERAL";
let contatosOcultos = JSON.parse(
  localStorage.getItem("contatos_ocultos") || "[]",
);
let ultimasMensagensDaConversa = [];
let totalMensagensAnterior = 0;

const loginModal = document.getElementById("loginModal");
const inputName = document.getElementById("inputName");
const inputPhone = document.getElementById("inputPhone");
const btnEnter = document.getElementById("btnEnter");

const myNameDisplay = document.getElementById("myNameDisplay");
const myPhoneDisplay = document.getElementById("myPhoneDisplay");
const btnAddContact = document.getElementById("btnAddContact");
const contactsList = document.getElementById("contactsList");

const messagesContainer = document.getElementById("messagesContainer");
const inputMessage = document.getElementById("inputMessage");
const btnSend = document.getElementById("btnSend");

// Remove tudo que não for número (acessível em todo o arquivo)
function soDigitos(str) {
  return (str || "").replace(/\D/g, "");
}

// === MÁSCARA AUTOMÁTICA DE TELEFONE: (XX) XXXXX-XXXX ===
function aplicarMascaraTelefone(input) {
  input.addEventListener("input", (e) => {
    let v = e.target.value.replace(/\D/g, ""); // Remove tudo que não for número
    if (v.length > 11) v = v.substring(0, 11); // Limita a 11 dígitos

    if (v.length > 6) {
      v = `(${v.substring(0, 2)}) ${v.substring(2, 7)}-${v.substring(7)}`;
    } else if (v.length > 2) {
      v = `(${v.substring(0, 2)}) ${v.substring(2)}`;
    } else if (v.length > 0) {
      v = `(${v}`;
    }
    e.target.value = v;
  });
}

// Ativa a máscara no campo de telefone do login
if (inputPhone) aplicarMascaraTelefone(inputPhone);

// Inicializa o perfil
if (meuNome && meuTelefone) {
  atualizarPerfil(meuNome, meuTelefone);
  loginModal.style.display = "none";
} else {
  loginModal.style.display = "flex";
}

btnEnter.addEventListener("click", () => {
  const nome = inputName.value.trim();
  const tel = inputPhone.value.trim();

  if (!nome || !tel) {
    alert("Preencha nome e telefone!");
    return;
  }

  localStorage.setItem("chat_nome", nome);
  localStorage.setItem("chat_telefone", tel);
  atualizarPerfil(nome, tel);
  loginModal.style.display = "none";
});

// Botão para sair, limpar dados locais e cadastrar outro número
const btnResetProfile = document.getElementById("btnResetProfile");
if (btnResetProfile) {
  btnResetProfile.addEventListener("click", () => {
    if (confirm("Deseja sair deste perfil e criar ou usar outro número?")) {
      localStorage.clear();
      location.reload();
    }
  });
}

function atualizarPerfil(nome, tel) {
  // Registra esse número no banco de dados para os outros poderem adicionar você
  fetch(
    `/api/registrar-usuario?nome=${encodeURIComponent(nome)}&telefone=${encodeURIComponent(tel)}`,
  );

  meuNome = nome;
  meuTelefone = tel;
  meuIdentificador = `${nome} (${tel})`;
  myNameDisplay.textContent = nome;
  myPhoneDisplay.textContent = tel;
  inputMessage.focus();
}

// Adicionar novos contatos na lista (validando se o número existe no banco)
btnAddContact.addEventListener("click", async () => {
  const num = prompt("Digite o número do contato com DDD (ex: 41988887777):");
  if (!num) return;

  // Aplica o padrão (XX) XXXXX-XXXX
  let limpo = num.replace(/\D/g, "");
  if (limpo.length !== 11) {
    alert("Número inválido! Digite os 11 dígitos com DDD (ex: 41999998888).");
    return;
  }
  const formatado = `(${limpo.substring(0, 2)}) ${limpo.substring(2, 7)}-${limpo.substring(7)}`;

  // Impede adicionar a si mesmo
  if (soDigitos(formatado) === soDigitos(meuTelefone)) {
    alert("Você não pode adicionar seu próprio número!");
    return;
  }

  // Consulta o banco para checar se o número já existe
  try {
    const res = await fetch(
      `/api/verificar-numero?telefone=${encodeURIComponent(limpo)}`,
    );
    const dados = await res.json();

    if (!dados.existe) {
      alert(
        `O número ${formatado} ainda não possui cadastro no ConnectChat!\nPeça para a pessoa entrar no link primeiro.`,
      );
      return;
    }

    // Se existe, desbloqueia e adiciona na lista
    contatosOcultos = contatosOcultos.filter((n) => n !== limpo);
    localStorage.setItem("contatos_ocultos", JSON.stringify(contatosOcultos));

    adicionarContatoNaLista(formatado);
  } catch (e) {
    alert("Erro ao verificar o número no servidor.");
  }
});

function adicionarContatoNaLista(numero) {
  const item = document.createElement("div");
  item.className = "contact-item";
  item.dataset.destinatario = numero;
  item.innerHTML = `
        <div style="display:flex; align-items:center; gap:12px;">
            <div class="avatar-chat">📱</div>
            <div class="contact-info">
                <h4>${numero}</h4>
                <p>Conversa Privada</p>
            </div>
        </div>
        <button class="btn-del-contact" title="Remover contato">🗑️</button>
    `;

  // Botão para remover contato da barra
  const btnDel = item.querySelector(".btn-del-contact");
  if (btnDel) {
    btnDel.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (
        confirm(
          `Deseja remover o contato ${numero} e apagar as mensagens dele?`,
        )
      ) {
        const numLimpo = soDigitos(numero);

        // 1. Salva na lista de apagados para o chat não ressuscitar ele
        if (!contatosOcultos.includes(numLimpo)) {
          contatosOcultos.push(numLimpo);
          localStorage.setItem(
            "contatos_ocultos",
            JSON.stringify(contatosOcultos),
          );
        }

        // 2. Apaga as mensagens dele no banco
        const promises = ultimasMensagensDaConversa.map((m) =>
          fetch(`/api/apagar-mensagem?id=${m.id}`),
        );
        await Promise.all(promises);

        // 3. Remove da tela e volta para a Sala Geral
        item.remove();
        selecionarConversa(
          "GERAL",
          "Sala Geral",
          document.querySelector(".contact-item"),
        );
        carregarMensagens();
      }
    });
  }

  item.addEventListener("click", () =>
    selecionarConversa(numero, `📱 ${numero}`, item),
  );
  contactsList.appendChild(item);
  selecionarConversa(numero, `📱 ${numero}`, item);
}

// Troca de conversa ao clicar no contato
function selecionarConversa(destinatario, titulo, elemento) {
  conversaAtiva = destinatario;
  document
    .querySelectorAll(".contact-item")
    .forEach((el) => el.classList.remove("active"));
  if (elemento) elemento.classList.add("active");

  const headerTitle = document.querySelector(".chat-header h2");
  if (headerTitle) headerTitle.textContent = titulo;

  carregarMensagens(); // Recarrega filtrando a conversa escolhida
}

// Clique na Sala Geral original
const salaGeralItem = document.querySelector(".contact-item");
if (salaGeralItem) {
  salaGeralItem.addEventListener("click", () => {
    selecionarConversa("GERAL", "Sala Geral", salaGeralItem);
  });
}

// Enviar mensagem
btnSend.addEventListener("click", enviar);
inputMessage.addEventListener("keypress", (e) => {
  if (e.key === "Enter") enviar();
});

async function enviar() {
  const texto = inputMessage.value.trim();
  if (!texto) return;

  if (!meuIdentificador) {
    loginModal.style.display = "flex";
    return;
  }

  inputMessage.value = "";

  try {
    const url = `/api/enviar?remetente=${encodeURIComponent(meuIdentificador)}&destinatario=${encodeURIComponent(conversaAtiva)}&conteudo=${encodeURIComponent(texto)}`;
    await fetch(url);
    carregarMensagens();
  } catch (e) {
    console.error("Erro ao enviar mensagem:", e);
  }
}

// Limpa o histórico da conversa atual (Geral ou Privada)
const btnClearChat = document.getElementById("btnClearChat");
if (btnClearChat) {
  btnClearChat.addEventListener("click", async () => {
    const nomeConversa =
      conversaAtiva === "GERAL" ? "Sala Geral" : "esta conversa privada";
    if (
      confirm(
        `Tem certeza que deseja apagar todas as mensagens de ${nomeConversa}?`,
      )
    ) {
      // Esvazia a tela imediatamente
      messagesContainer.innerHTML = "";
      cacheHistorico = "";

      if (conversaAtiva === "GERAL") {
        // Na Sala Geral apaga tudo que é geral
        await fetch(`/api/limpar-conversa?conversa=GERAL`);
      } else {
        // Na Privada, apaga direto pelo ID de cada mensagem da conversa
        const promises = ultimasMensagensDaConversa.map((m) =>
          fetch(`/api/apagar-mensagem?id=${m.id}`),
        );
        await Promise.all(promises);
      }

      carregarMensagens();
    }
  });
}

// Carregar mensagens salvas do MySQL
async function carregarMensagens() {
  try {
    const res = await fetch("/api/mensagens");
    const mensagens = await res.json();
    renderizarMensagens(mensagens);
  } catch (e) {
    console.error("Erro ao buscar histórico:", e);
  }
}

function renderizarMensagens(mensagens) {
  // 1. PRIMEIRO cria a função soDigitos:
  const soDigitos = (s) => (s || "").replace(/\D/g, "");

  // 2. DEPOIS verifica o som usando ela:
  if (mensagens.length > totalMensagensAnterior && totalMensagensAnterior > 0) {
    const ultima = mensagens[mensagens.length - 1];
    if (ultima && !ultima.remetente.includes(soDigitos(meuTelefone))) {
      tocarSomNotificacao();
    }
  }
  totalMensagensAnterior = mensagens.length;
  messagesContainer.innerHTML = "";

  const meuNum = soDigitos(meuTelefone);
  const conversaNum = soDigitos(conversaAtiva);

  // 1. Detecta mensagens recebidas e adiciona contato na lateral se não existir
  mensagens.forEach((msg) => {
    const destNum = soDigitos(msg.destinatario);
    const remNum = soDigitos(msg.remetente);

    // Se o contato for apagado, não ressuscita ele na lista
    if (contatosOcultos.includes(remNum)) return;

    if (destNum === meuNum && remNum && remNum !== meuNum) {
      const jaExiste = Array.from(
        document.querySelectorAll(".contact-item"),
      ).some((el) => soDigitos(el.dataset.destinatario) === remNum);

      if (!jaExiste) {
        const item = document.createElement("div");
        item.className = "contact-item";
        item.dataset.destinatario = remNum;
        item.innerHTML = `
                    <div style="display:flex; align-items:center; gap:12px;">
                        <div class="avatar-chat">📱</div>
                        <div class="contact-info">
                            <h4>${msg.remetente}</h4>
                            <p>Conversa Privada</p>
                        </div>
                    </div>
                    <button class="btn-del-contact" title="Remover contato">🗑️</button>
                `;
        item.addEventListener("click", () =>
          selecionarConversa(remNum, `📱 ${msg.remetente}`, item),
        );
        contactsList.appendChild(item);
      }
    }
  });

  // 2. Filtra as mensagens pertencentes à conversa atual
  const mensagensFiltradas = mensagens.filter((msg) => {
    const destNum = soDigitos(msg.destinatario);
    const remNum = soDigitos(msg.remetente);

    if (conversaAtiva === "GERAL") {
      return !msg.destinatario || msg.destinatario === "GERAL";
    } else {
      const enviadaPorMim = remNum.includes(meuNum) && destNum === conversaNum;
      const enviadaParaMim = remNum.includes(conversaNum) && destNum === meuNum;
      return enviadaPorMim || enviadaParaMim;
    }
  });

  // Guarda as mensagens ativas da tela
  ultimasMensagensDaConversa = mensagensFiltradas;

  // 3. Renderiza os balões na tela
  mensagensFiltradas.forEach((msg) => {
    const remNum = soDigitos(msg.remetente);
    const isMe = meuNum && remNum.includes(meuNum);
    adicionarBalaoNaTela(
      msg.remetente,
      msg.conteudo,
      msg.horario,
      isMe,
      msg.id,
    );
  });

  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Inicia buscando mensagens e atualiza a cada 1.5s
carregarMensagens();
setInterval(carregarMensagens, 1500);

// Abrir/Fechar a barra lateral ao clicar no botão sanduíche (☰)
const btnMenuToggle = document.getElementById("btnMenuToggle");
const sidebar = document.querySelector(".sidebar");

if (btnMenuToggle && sidebar) {
  btnMenuToggle.addEventListener("click", () => {
    sidebar.classList.toggle("aberta");
  });

  // Fecha a barra lateral se clicar na área de mensagens
  messagesContainer.addEventListener("click", () => {
    sidebar.classList.remove("aberta");
  });
}

// === FUNÇÃO QUE CRIA E DESENHA O BALÃO NA TELA ===
function adicionarBalaoNaTela(remetente, conteudo, horario, isMe, idMsg = 0) {
  const row = document.createElement("div");
  row.className = `message-row ${isMe ? "me" : "other"}`;

  // Mostra o botãozinho ✕ para apagar se for uma mensagem que você enviou
  const btnApagar =
    isMe && idMsg
      ? `<button class="btn-delete-msg" onclick="apagarMensagem(${idMsg})" title="Apagar esta mensagem">✕</button>`
      : "";

  row.innerHTML = `
        <div class="bubble">
            ${btnApagar}
            <div class="bubble-sender">${remetente}</div>
            <div class="bubble-text">${conteudo}</div>
            <div class="bubble-time">${horario}</div>
        </div>
    `;

  messagesContainer.appendChild(row);
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Função para deletar uma mensagem específica no banco
window.apagarMensagem = async function (id) {
  if (confirm("Deseja excluir esta mensagem?")) {
    await fetch(`/api/apagar-mensagem?id=${id}`);
    carregarMensagens();
  }
};
// ========================================================
// 1. RECURSO: SOM DE NOTIFICAÇÃO ("POP / DING")
// ========================================================
function tocarSomNotificacao() {
  try {
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    osc.type = "sine";
    // Efeito "Ding": tom sobe suavemente de 580Hz para 880Hz
    osc.frequency.setValueAtTime(587, audioCtx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(880, audioCtx.currentTime + 0.1);

    gainNode.gain.setValueAtTime(0.15, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(
      0.01,
      audioCtx.currentTime + 0.25,
    );

    osc.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    osc.start();
    osc.stop(audioCtx.currentTime + 0.25);
  } catch (e) {
    // Bloqueio padrão de áudio até o usuário interagir
  }
}

// ========================================================
// 2. RECURSO: MODO NOTURNO (DARK MODE 🌙 / ☀️)
// ========================================================
const btnThemeToggle = document.getElementById("btnThemeToggle");

// Aplica o tema salvo na memória
if (localStorage.getItem("tema_escuro") === "true") {
  document.body.classList.add("dark-mode");
  if (btnThemeToggle) btnThemeToggle.textContent = "☀️";
}

if (btnThemeToggle) {
  btnThemeToggle.addEventListener("click", () => {
    const ativo = document.body.classList.toggle("dark-mode");
    btnThemeToggle.textContent = ativo ? "☀️" : "🌙";
    localStorage.setItem("tema_escuro", ativo);
  });
}

// ========================================================
// 3. RECURSO: BARRA DE EMOJIS RÁPIDOS
// ========================================================
document.querySelectorAll(".emoji-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    if (inputMessage) {
      inputMessage.value += btn.getAttribute("data-emoji");
      inputMessage.focus();
    }
  });
});
