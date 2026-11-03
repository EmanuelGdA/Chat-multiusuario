let meuNome = localStorage.getItem("chat_nome") || "";
let meuTelefone = localStorage.getItem("chat_telefone") || "";
let meuIdentificador = "";
let conversaAtiva = "GERAL";

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

function atualizarPerfil(nome, tel) {
    meuNome = nome;
    meuTelefone = tel;
    meuIdentificador = `${nome} (${tel})`;
    myNameDisplay.textContent = nome;
    myPhoneDisplay.textContent = tel;
    inputMessage.focus();
}

// Adicionar novos contatos na lista
btnAddContact.addEventListener("click", () => {
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
    if (formatado === meuTelefone) {
        alert("Você não pode adicionar seu próprio número!");
        return;
    }

    adicionarContatoNaLista(formatado);
});

function adicionarContatoNaLista(numero) {
    const item = document.createElement("div");
    item.className = "contact-item";
    item.dataset.destinatario = numero;
    item.innerHTML = `
        <div class="avatar-chat">📱</div>
        <div class="contact-info">
            <h4>${numero}</h4>
            <p>Conversa Privada</p>
        </div>
    `;

    item.addEventListener("click", () => selecionarConversa(numero, `📱 ${numero}`, item));
    contactsList.appendChild(item);
    selecionarConversa(numero, `📱 ${numero}`, item);
}

// Troca de conversa ao clicar no contato
function selecionarConversa(destinatario, titulo, elemento) {
    conversaAtiva = destinatario;
    document.querySelectorAll(".contact-item").forEach(el => el.classList.remove("active"));
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
    messagesContainer.innerHTML = "";

    // Filtra as mensagens de acordo com a conversa selecionada
            const mensagensFiltradas = mensagens.filter(msg => {
                const dest = msg.destinatario || "GERAL";
                if (conversaAtiva === "GERAL") {
                    return dest === "GERAL";
                } else {
                    // Conversa privada: exibe se eu mandei para ele OU se ele mandou para mim
                    const enviadaPorMimParaEle = msg.remetente.includes(meuTelefone) && dest === conversaAtiva;
                    const enviadaPorEleParaMim = msg.remetente.includes(conversaAtiva) && dest === meuTelefone;
                    return enviadaPorMimParaEle || enviadaPorEleParaMim;
                }
            });

            mensagensFiltradas.forEach(msg => {
                const isMe = meuIdentificador && msg.remetente.includes(meuIdentificador);
                adicionarBalaoNaTela(msg.remetente, msg.conteudo, msg.horario, isMe);
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
function adicionarBalaoNaTela(remetente, conteudo, horario, isMe) {
    const row = document.createElement("div");
    row.className = `message-row ${isMe ? "me" : "other"}`;

    row.innerHTML = `
        <div class="bubble">
            <div class="bubble-sender">${remetente}</div>
            <div class="bubble-text">${conteudo}</div>
            <div class="bubble-time">${horario}</div>
        </div>
    `;

    messagesContainer.appendChild(row);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}