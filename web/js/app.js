let meuNome = localStorage.getItem("chat_nome") || "";
let meuTelefone = localStorage.getItem("chat_telefone") || "";
let meuIdentificador = "";
let conversaAtiva = "GERAL";
let contatosOcultos = JSON.parse(
  localStorage.getItem("contatos_ocultos") || "[]",
);
let ultimasMensagensDaConversa = [];
let maiorIdRecebido;
let registroUsuario;
let consultaMensagens;
let mensagensCarregadas = [];
let conversaRenderizada;
let leitura;

const btnContactProfile = document.getElementById("btnContactProfile");
const contactProfile = document.getElementById("contactProfile");
const btnSearch = document.getElementById("btnSearch");
const searchPanel = document.getElementById("conversationSearch");
const searchInput = document.getElementById("searchMessages");
const searchStatus = document.getElementById("searchStatus");
const searchPrev = document.getElementById("searchPrev");
const searchNext = document.getElementById("searchNext");
let resultadosBusca = [];
let indiceBusca = -1;

function chaveConversa(conversa) {
  return conversa === "GERAL" ? conversa : soDigitos(conversa);
}

function separarRemetente(remetente) {
  const abertura = remetente.lastIndexOf(" (");
  if (abertura === -1 || !remetente.endsWith(")")) {
    return { nome: remetente, telefone: "" };
  }
  return {
    nome: remetente.slice(0, abertura),
    telefone: soDigitos(remetente.slice(abertura + 2, -1)),
  };
}

function telefoneRemetente(remetente) {
  return separarRemetente(remetente).telefone;
}

async function buscarPerfil(telefone) {
  const resposta = await fetch(`/api/perfil?telefone=${encodeURIComponent(telefone)}`);
  if (!resposta.ok) throw new Error("Perfil indisponível");
  return resposta.json();
}

function textoAvatar(nome, avatar) {
  if (avatar && avatar !== "initials") return avatar;
  return nome.trim().split(/\s+/).slice(0, 2)
    .map((parte) => Array.from(parte)[0]).join("").toLocaleUpperCase("pt-BR") || "👤";
}

async function abrirPerfilContato(telefone, nome) {
  const titulo = document.getElementById("contactProfileName");
  const bio = document.getElementById("contactProfileBio");
  const avatar = document.getElementById("contactAvatar");
  titulo.textContent = nome || "Perfil do contato";
  document.getElementById("contactProfilePhone").textContent = telefone;
  avatar.textContent = textoAvatar(nome || "");
  bio.textContent = "Carregando perfil…";
  contactProfile.showModal();
  const pedido = Symbol();
  contactProfile.pedido = pedido;
  try {
    const perfil = await buscarPerfil(telefone);
    if (contactProfile.pedido !== pedido || !contactProfile.open) return;
    titulo.textContent = perfil.nome;
    avatar.textContent = textoAvatar(perfil.nome, perfil.avatar);
    bio.textContent = perfil.bio || "Ainda sem descrição.";
  } catch {
    if (contactProfile.pedido === pedido && contactProfile.open) {
      bio.textContent = "Não foi possível carregar o perfil.";
    }
  }
}

btnContactProfile.addEventListener("click", () => {
  abrirPerfilContato(chaveConversa(conversaAtiva), "");
});

const loginModal = document.getElementById("loginModal");
const inputName = document.getElementById("inputName");
const inputPhone = document.getElementById("inputPhone");
const btnEnter = document.getElementById("btnEnter");

const btnMyProfile = document.getElementById("btnMyProfile");
const myAvatar = document.getElementById("myAvatar");
const profileDialog = document.getElementById("profileDialog");
const profileForm = document.getElementById("profileForm");
const profileAvatar = document.getElementById("profileAvatar");
const profileBio = document.getElementById("profileBio");

const profileError = document.getElementById("profileError");
const btnSaveProfile = profileForm.querySelector('[type="submit"]');

btnMyProfile.addEventListener("click", async () => {
  document.getElementById("profileName").textContent = meuNome;
  document.getElementById("profilePhone").textContent = meuTelefone;
  profileAvatar.value = localStorage.getItem("chat_profile_avatar") || "initials";
  profileBio.value = localStorage.getItem("chat_profile_bio") || "";
  profileError.textContent = "";
  profileDialog.showModal();
  const valoresIniciais = [profileAvatar.value, profileBio.value];
  const pedido = Symbol();
  profileDialog.pedido = pedido;
  try {
    await registroUsuario;
    const perfil = await buscarPerfil(soDigitos(meuTelefone));
    if (profileDialog.open && profileDialog.pedido === pedido && perfil.avatar
        && profileAvatar.value === valoresIniciais[0] && profileBio.value === valoresIniciais[1]
        && !btnSaveProfile.disabled) {
      profileAvatar.value = perfil.avatar;
      profileBio.value = perfil.bio;
      myAvatar.textContent = textoAvatar(meuNome, perfil.avatar);
    }
  } catch {
    // O formulário mantém os dados locais se o servidor estiver indisponível.
  }
});

document.getElementById("btnCloseProfile").addEventListener("click", () => {
  profileDialog.close();
});

profileForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const telefone = soDigitos(meuTelefone);
  const avatar = profileAvatar.value;
  const bio = profileBio.value.trim();
  const chaveLocal = `chat_profile_key_${telefone}`;
  let chave = localStorage.getItem(chaveLocal);
  if (!chave) {
    chave = Array.from(crypto.getRandomValues(new Uint8Array(32)),
      (byte) => byte.toString(16).padStart(2, "0")).join("");
    localStorage.setItem(chaveLocal, chave);
  }

  btnSaveProfile.disabled = true;
  profileError.textContent = "Salvando perfil…";
  try {
    await registroUsuario;
    const resposta = await fetch("/api/perfil", {
      method: "POST",
      body: new URLSearchParams({ telefone, avatar, bio, chave }),
    });
    if (resposta.status === 403) {
      profileError.textContent = "Use o navegador em que este perfil foi publicado pela primeira vez.";
      return;
    }
    if (!resposta.ok) throw new Error("Falha ao salvar perfil");
    localStorage.setItem("chat_profile_avatar", avatar);
    localStorage.setItem("chat_profile_bio", bio);
    atualizarAvatar();
    profileDialog.close();
  } catch {
    profileError.textContent = "Não foi possível salvar o perfil. Tente novamente.";
  } finally {
    btnSaveProfile.disabled = false;
  }
});

function atualizarAvatar() {
  myAvatar.textContent = textoAvatar(meuNome, localStorage.getItem("chat_profile_avatar"));
}

const myNameDisplay = document.getElementById("myNameDisplay");
const myPhoneDisplay = document.getElementById("myPhoneDisplay");
const btnAddContact = document.getElementById("btnAddContact");
const contactsList = document.getElementById("contactsList");

const messagesContainer = document.getElementById("messagesContainer");
const inputMessage = document.getElementById("inputMessage");
const btnSend = document.getElementById("btnSend");
const connectionStatus = document.getElementById("connectionStatus");
const sendStatus = document.getElementById("sendStatus");
const btnMenuToggle = document.getElementById("btnMenuToggle");
const sidebar = document.getElementById("chatSidebar");

function chaveRascunho(conversa) {
  return `chat_draft_${soDigitos(meuTelefone)}_${chaveConversa(conversa)}`;
}

function guardarRascunho() {
  const chave = chaveRascunho(conversaAtiva);
  if (inputMessage.value) {
    sessionStorage.setItem(chave, inputMessage.value);
  } else {
    sessionStorage.removeItem(chave);
  }
}

function mostrarConexao(estado) {
  if (connectionStatus.dataset.state === estado) return;
  connectionStatus.dataset.state = estado;
  connectionStatus.textContent = estado === "online" ? "● Online" : "Reconectando…";
}

function definirMenuConversas(aberto) {
  sidebar.classList.toggle("aberta", aberto);
  btnMenuToggle.setAttribute("aria-expanded", String(aberto));
}

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
  carregarMensagens();
});

// Botão para sair, limpar dados locais e cadastrar outro número
const btnResetProfile = document.getElementById("btnResetProfile");
if (btnResetProfile) {
  btnResetProfile.addEventListener("click", () => {
    if (confirm("Deseja sair deste perfil e criar ou usar outro número?")) {
      for (const chave of Object.keys(localStorage)) {
        if (!chave.startsWith("chat_profile_key_") && !chave.startsWith("chat_read_")) {
          localStorage.removeItem(chave);
        }
      }
      location.reload();
    }
  });
}

function atualizarPerfil(nome, tel) {
  // Registra esse número no banco de dados para os outros poderem adicionar você
  registroUsuario = fetch(
    `/api/registrar-usuario?nome=${encodeURIComponent(nome)}&telefone=${encodeURIComponent(tel)}`,
  ).catch(() => null);
  leitura = JSON.parse(localStorage.getItem(`chat_read_${soDigitos(tel)}`) || "null");
  maiorIdRecebido = undefined;

  meuNome = nome;
  meuTelefone = tel;
  meuIdentificador = `${nome} (${tel})`;
  myNameDisplay.textContent = nome;
  myPhoneDisplay.textContent = tel;
  atualizarAvatar();
  btnMyProfile.disabled = false;
  inputMessage.value = sessionStorage.getItem(chaveRascunho(conversaAtiva)) || "";
  if (!window.matchMedia("(max-width: 768px)").matches) inputMessage.focus();
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

    adicionarContatoNaLista(formatado, dados.nome, true);
  } catch (e) {
    alert("Erro ao verificar o número no servidor.");
  }
});

function adicionarContatoNaLista(numero, nome, selecionar = false) {
  const existente = Array.from(contactsList.querySelectorAll(".contact-item"))
    .find((item) => chaveConversa(item.dataset.destinatario) === soDigitos(numero));
  if (existente) {
    if (selecionar) selecionarConversa(numero, nome || numero, existente);
    return;
  }

  const item = document.createElement("div");
  item.className = "contact-item";
  item.dataset.destinatario = numero;
  item.innerHTML = `
        <div class="avatar-chat" aria-hidden="true">📱</div>
        <div class="contact-info">
            <div class="contact-heading">
                <h4></h4>
                <time class="contact-time" hidden></time>
            </div>
            <p class="contact-preview">Sem mensagens recentes</p>
        </div>
        <span class="unread-badge" hidden></span>
        <button class="btn-del-contact" title="Remover contato">🗑️</button>
    `;
  item.querySelector("h4").textContent = nome || numero;

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
        const meuNum = soDigitos(meuTelefone);
        const doContato = mensagensCarregadas.filter((msg) => {
          const de = telefoneRemetente(msg.remetente);
          const para = soDigitos(msg.destinatario);
          return (de === meuNum && para === numLimpo) || (de === numLimpo && para === meuNum);
        });
        await Promise.all(doContato.map((msg) => fetch(`/api/apagar-mensagem?id=${msg.id}`)));

        // 3. Remove da tela e volta para a Sala Geral
        item.remove();
        selecionarConversa(
          "GERAL",
          "Sala Geral",
          document.querySelector('[data-destinatario="GERAL"]'),
        );
        carregarMensagens();
      }
    });
  }

  item.addEventListener("click", () =>
    selecionarConversa(numero, nome || numero, item),
  );
  contactsList.appendChild(item);
  if (selecionar) selecionarConversa(numero, nome || numero, item);
}

// Troca de conversa ao clicar no contato
function selecionarConversa(destinatario, titulo, elemento) {
  definirMenuConversas(false);
  if (chaveConversa(destinatario) !== chaveConversa(conversaAtiva)) {
    guardarRascunho();
    inputMessage.value = sessionStorage.getItem(chaveRascunho(destinatario)) || "";
    sendStatus.textContent = "";
    ultimasMensagensDaConversa = [];
    messagesContainer.replaceChildren();
    fecharBusca(false);
  }
  conversaAtiva = destinatario;
  btnContactProfile.hidden = destinatario === "GERAL";
  document
    .querySelectorAll(".contact-item")
    .forEach((el) => el.classList.remove("active"));
  if (elemento) elemento.classList.add("active");

  const headerTitle = document.querySelector(".chat-header h2");
  if (headerTitle) headerTitle.textContent = titulo;

  if (mensagensCarregadas.length) renderizarMensagens(mensagensCarregadas);
  carregarMensagens();
}

// Clique na Sala Geral original
const salaGeralItem = document.querySelector('[data-destinatario="GERAL"]');
if (salaGeralItem) {
  salaGeralItem.addEventListener("click", () => {
    selecionarConversa("GERAL", "Sala Geral", salaGeralItem);
  });
}

// Enviar mensagem
btnSend.addEventListener("click", enviar);
inputMessage.addEventListener("input", guardarRascunho);
inputMessage.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.isComposing) {
    event.preventDefault();
    enviar();
  }
});

async function enviar() {
  const rascunho = inputMessage.value;
  const texto = rascunho.trim();
  if (!texto || btnSend.disabled) return;

  if (!meuIdentificador) {
    loginModal.style.display = "flex";
    return;
  }

  const destinatario = conversaAtiva;
  const chave = chaveRascunho(destinatario);
  guardarRascunho();
  btnSend.disabled = true;
  sendStatus.textContent = "";

  try {
    const parametros = new URLSearchParams({
      remetente: meuIdentificador, destinatario, conteudo: texto,
    });
    const resposta = await fetch(`/api/enviar?${parametros}`, {
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });
    if (!resposta.ok || (await resposta.json()).status !== "ok") {
      throw new Error("Envio sem confirmação");
    }

    if (sessionStorage.getItem(chave) === rascunho) {
      sessionStorage.removeItem(chave);
      if (chaveRascunho(conversaAtiva) === chave) inputMessage.value = "";
    }
    await consultaMensagens;
    carregarMensagens();
  } catch {
    if (chaveRascunho(conversaAtiva) === chave) {
      sendStatus.textContent = "Envio sem confirmação. O rascunho foi mantido.";
    }
    carregarMensagens();
  } finally {
    btnSend.disabled = false;
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
      messagesContainer.replaceChildren();
      conversaRenderizada = undefined;
      await consultaMensagens;

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

// Mantém uma consulta por vez para que uma resposta antiga não sobrescreva a atual.
function carregarMensagens() {
  if (!consultaMensagens) {
    consultaMensagens = fetch("/api/mensagens", {
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    })
      .then((resposta) => {
        if (!resposta.ok) throw new Error("Histórico indisponível");
        return resposta.json();
      })
      .then((mensagens) => {
        if (!Array.isArray(mensagens)) throw new Error("Histórico inválido");
        mensagensCarregadas = mensagens;
        mostrarConexao(navigator.onLine ? "online" : "reconnecting");
        renderizarMensagens(mensagens);
      })
      .catch(() => mostrarConexao("reconnecting"))
      .finally(() => { consultaMensagens = undefined; });
  }
  return consultaMensagens;
}

function renderizarMensagens(mensagens) {
  const meuNum = soDigitos(meuTelefone);
  if (!meuNum) return;

  const ativa = chaveConversa(conversaAtiva);
  const maiorId = mensagens.at(-1)?.id || 0;
  const leituraAnterior = JSON.stringify(leitura);
  leitura ||= { inicio: maiorId, conversas: {} };
  const naTela = document.visibilityState === "visible";
  const naoLidas = new Map();
  const ultimasPorConversa = new Map();
  const mensagensDaConversa = [];
  let notificar = false;

  for (const msg of mensagens) {
    const remetente = telefoneRemetente(msg.remetente);
    const destinatario = chaveConversa(msg.destinatario);
    const minha = remetente === meuNum;
    const geral = !msg.destinatario || destinatario === "GERAL";
    if (!geral && !minha && destinatario !== meuNum) continue;

    const conversa = geral ? "GERAL" : minha ? destinatario : remetente;
    if (!conversa || contatosOcultos.includes(conversa)) continue;
    if (!geral && !minha) {
      adicionarContatoNaLista(conversa, separarRemetente(msg.remetente).nome);
    }
    const ultima = ultimasPorConversa.get(conversa);
    if (!ultima || msg.id > ultima.id) ultimasPorConversa.set(conversa, msg);

    const lidaAte = leitura.conversas[conversa] ?? leitura.inicio;
    if (conversa === ativa) {
      mensagensDaConversa.push(msg);
      if (naTela && msg.id > lidaAte) leitura.conversas[conversa] = msg.id;
    }
    if (!minha && msg.id > lidaAte && (conversa !== ativa || !naTela)) {
      naoLidas.set(conversa, (naoLidas.get(conversa) || 0) + 1);
    }
    if (!minha && maiorIdRecebido !== undefined && msg.id > maiorIdRecebido) {
      notificar = true;
    }
  }

  maiorIdRecebido = Math.max(maiorId, maiorIdRecebido ?? 0);
  if (notificar) tocarSomNotificacao();

  const leituraAtual = JSON.stringify(leitura);
  if (leituraAtual !== leituraAnterior) {
    localStorage.setItem(`chat_read_${meuNum}`, leituraAtual);
  }

  for (const conversa of ultimasPorConversa.keys()) {
    if (conversa !== "GERAL") adicionarContatoNaLista(conversa, conversa);
  }
  atualizarListaConversas(ultimasPorConversa, naoLidas, meuNum);

  const assinatura = JSON.stringify([ativa, mensagensDaConversa]);
  if (assinatura === conversaRenderizada) return;
  const anterior = messagesContainer.scrollTop;
  const estavaNoFim = messagesContainer.scrollHeight - anterior - messagesContainer.clientHeight < 40;
  const idEncontrado = resultadosBusca[indiceBusca]?.dataset.messageId;

  conversaRenderizada = assinatura;
  ultimasMensagensDaConversa = mensagensDaConversa;
  messagesContainer.replaceChildren();
  for (const msg of mensagensDaConversa) {
    adicionarBalaoNaTela(msg, telefoneRemetente(msg.remetente) === meuNum);
  }
  messagesContainer.scrollTop = estavaNoFim && searchPanel.hidden
    ? messagesContainer.scrollHeight : anterior;
  if (!searchPanel.hidden) atualizarBusca(idEncontrado, false);
}

function atualizarListaConversas(ultimas, naoLidas, meuNum) {
  const itens = Array.from(contactsList.querySelectorAll(".contact-item"));
  const ultimaDe = (item) => ultimas.get(chaveConversa(item.dataset.destinatario));
  itens.sort((a, b) => (ultimaDe(b)?.id || 0) - (ultimaDe(a)?.id || 0));

  itens.forEach((item, indice) => {
    const ultima = ultimaDe(item);
    const previa = item.querySelector(".contact-preview");
    const horario = item.querySelector(".contact-time");
    const minha = ultima && telefoneRemetente(ultima.remetente) === meuNum;
    const texto = ultima ? (minha ? "Você: " : "") + ultima.conteudo : "Sem mensagens recentes";
    if (previa.textContent !== texto) {
      previa.textContent = texto;
      previa.title = texto;
    }
    const hora = ultima?.horario?.slice(0, 5) || "";
    horario.hidden = !hora;
    if (horario.textContent !== hora) horario.textContent = hora;

    const badge = item.querySelector(".unread-badge");
    const total = naoLidas.get(chaveConversa(item.dataset.destinatario)) || 0;
    badge.hidden = total === 0;
    if (badge.textContent !== String(total)) {
      badge.textContent = total;
      badge.setAttribute("aria-label", `${total} mensagens não lidas`);
    }

    const posicaoAtual = contactsList.children[indice];
    if (posicaoAtual !== item) contactsList.insertBefore(item, posicaoAtual);
  });
}

function fecharBusca(devolverFoco = true) {
  searchPanel.hidden = true;
  btnSearch.setAttribute("aria-expanded", "false");
  searchInput.value = "";
  atualizarBusca();
  if (devolverFoco) btnSearch.focus();
}

function textoBusca(texto) {
  return texto.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase("pt-BR");
}

function atualizarBusca(idPreferido, rolar = true) {
  messagesContainer.querySelectorAll(".search-match, .search-current").forEach((row) => {
    row.classList.remove("search-match", "search-current");
  });
  const termo = textoBusca(searchInput.value.trim());
  resultadosBusca = termo
    ? Array.from(messagesContainer.querySelectorAll(".message-row"))
      .filter((row) => textoBusca(row.querySelector(".bubble-text").textContent).includes(termo))
    : [];
  resultadosBusca.forEach((row) => row.classList.add("search-match"));
  const preferido = resultadosBusca.findIndex((row) => row.dataset.messageId === idPreferido);
  indiceBusca = preferido >= 0 ? preferido : resultadosBusca.length - 1;
  mostrarResultadoBusca(rolar);
}

function mostrarResultadoBusca(rolar = true) {
  const resultado = resultadosBusca[indiceBusca];
  searchPrev.disabled = searchNext.disabled = resultadosBusca.length < 2;
  searchStatus.textContent = resultado ? `${indiceBusca + 1} de ${resultadosBusca.length}`
    : searchInput.value.trim() ? "Sem resultados" : "";
  if (resultado) {
    resultado.classList.add("search-current");
    if (rolar) resultado.scrollIntoView({ block: "nearest" });
  }
}

function navegarBusca(passo) {
  if (!resultadosBusca.length) return;
  resultadosBusca[indiceBusca].classList.remove("search-current");
  indiceBusca = (indiceBusca + passo + resultadosBusca.length) % resultadosBusca.length;
  mostrarResultadoBusca();
}

btnSearch.addEventListener("click", () => {
  if (!searchPanel.hidden) {
    fecharBusca();
    return;
  }
  searchPanel.hidden = false;
  btnSearch.setAttribute("aria-expanded", "true");
  atualizarBusca();
  searchInput.focus();
});
document.getElementById("searchClose").addEventListener("click", () => fecharBusca());
searchInput.addEventListener("input", () => atualizarBusca());
searchInput.addEventListener("keydown", (event) => {
  if (event.key === "Escape") fecharBusca();
  if (event.key === "Enter") navegarBusca(event.shiftKey ? -1 : 1);
});
searchPrev.addEventListener("click", () => navegarBusca(-1));
searchNext.addEventListener("click", () => navegarBusca(1));

document.addEventListener("visibilitychange", carregarMensagens);
carregarMensagens();
setInterval(carregarMensagens, 1500);

window.addEventListener("offline", () => mostrarConexao("reconnecting"));
window.addEventListener("online", carregarMensagens);
btnMenuToggle.addEventListener("click", () => {
  definirMenuConversas(!sidebar.classList.contains("aberta"));
});
messagesContainer.addEventListener("click", () => definirMenuConversas(false));

const viewportVisivel = window.visualViewport;
if (viewportVisivel) {
  const atualizarViewport = () => {
    const layout = document.documentElement.style;
    layout.setProperty("--chat-height", `${viewportVisivel.height}px`);
    layout.setProperty("--chat-top", `${viewportVisivel.offsetTop}px`);
  };
  viewportVisivel.addEventListener("resize", atualizarViewport);
  viewportVisivel.addEventListener("scroll", atualizarViewport);
  atualizarViewport();
}

function adicionarBalaoNaTela(msg, minha) {
  const row = document.createElement("div");
  row.className = `message-row ${minha ? "me" : "other"}`;
  row.dataset.messageId = msg.id;
  row.innerHTML = `
    <div class="bubble">
      <details class="message-actions">
        <summary aria-label="Opções da mensagem">⋯</summary>
        <div class="message-options"><button type="button" class="copy-message">Copiar mensagem</button></div>
      </details>
      <div class="bubble-sender"></div>
      <div class="bubble-text"></div>
      <div class="bubble-time"></div>
    </div>
  `;

  const { nome, telefone } = separarRemetente(msg.remetente);
  const remetente = row.querySelector(".bubble-sender");
  if (telefone && !minha) {
    const perfil = document.createElement("button");
    perfil.type = "button";
    perfil.className = "sender-profile";
    perfil.textContent = msg.remetente;
    perfil.addEventListener("click", () => abrirPerfilContato(telefone, nome));
    remetente.appendChild(perfil);
  } else {
    remetente.textContent = msg.remetente;
  }
  row.querySelector(".bubble-text").textContent = msg.conteudo;
  row.querySelector(".bubble-time").textContent = msg.horario;

  const menu = row.querySelector(".message-actions");
  row.querySelector(".copy-message").addEventListener("click", async (event) => {
    const botao = event.currentTarget;
    const status = document.getElementById("clipboardStatus");
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(msg.conteudo);
      } else {
        const campo = document.createElement("textarea");
        campo.value = msg.conteudo;
        campo.className = "sr-only";
        campo.readOnly = true;
        document.body.appendChild(campo);
        campo.select();
        try {
          if (!document.execCommand("copy")) throw new Error("Cópia indisponível");
        } finally {
          campo.remove();
        }
      }
      status.textContent = "Mensagem copiada.";
      botao.textContent = "Copiada!";
      setTimeout(() => { botao.textContent = "Copiar mensagem"; }, 1500);
    } catch {
      status.textContent = "Não foi possível copiar a mensagem.";
      botao.textContent = "Falha ao copiar";
    }
  });

  if (minha && msg.id) {
    const apagar = document.createElement("button");
    apagar.type = "button";
    apagar.textContent = "Apagar mensagem";
    apagar.addEventListener("click", () => {
      menu.open = false;
      window.apagarMensagem(msg.id);
    });
    row.querySelector(".message-options").appendChild(apagar);
  }
  messagesContainer.appendChild(row);
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
      guardarRascunho();
      inputMessage.focus();
    }
  });
});
