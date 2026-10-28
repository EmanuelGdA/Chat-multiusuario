let meuNome = localStorage.getItem("chat_nome") || "";
let meuTelefone = localStorage.getItem("chat_telefone") || "";
let meuIdentificador = "";

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

// 1. Inicializa o perfil
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

// 2. Adicionar novos contatos na lista
btnAddContact.addEventListener("click", () => {
    const num = prompt("Digite o número do contato (ex: 41 98888-0000):");
    if (num && num.trim()) {
        const item = document.createElement("div");
        item.className = "contact-item";
        item.innerHTML = `
            <div class="avatar-chat">📞</div>
            <div class="contact-info">
                <h4>${num.trim()}</h4>
                <p>Conversa direta</p>
            </div>
        `;
        contactsList.appendChild(item);
    }
});

// 3. Enviar mensagem
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
        const url = `/api/enviar?remetente=${encodeURIComponent(meuIdentificador)}&conteudo=${encodeURIComponent(texto)}`;
        await fetch(url);
        carregarMensagens();
    } catch (e) {
        console.error("Erro ao enviar mensagem:", e);
    }
}

// 4. Carregar mensagens salvas do MySQL
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

    mensagens.forEach(msg => {
        const isMe = meuIdentificador && msg.remetente.includes(meuIdentificador);

        const row = document.createElement("div");
        row.className = `message-row ${isMe ? "me" : "other"}`;

        row.innerHTML = `
            <div class="bubble">
                <div class="bubble-sender">${msg.remetente}</div>
                <div class="bubble-text">${msg.conteudo}</div>
                <div class="bubble-time">${msg.horario}</div>
            </div>
        `;

        messagesContainer.appendChild(row);
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