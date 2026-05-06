# ⚙️ Khepri Orchestrator

O **Khepri Orchestrator** é um plugin customizado para o **Apache OFBiz (v24.09)** desenvolvido para assumir o controle das operações da **Oficina Mecânica Tuaregue**, localizada em Atibaia-SP.

O objetivo do Khepri não é recriar o ERP, mas atuar como uma **camada de orquestração interna** que simplifica as regras do OFBiz para a realidade do "chão de oficina". Ele consolida dezenas de telas, cliques e serviços fragmentados do sistema base em fluxos ágeis, seguros e voltados para o dia a dia mecânico.

---

## 🚨 O Problema: OFBiz "Puro" vs. Realidade da Oficina

O Apache OFBiz é um ERP industrial poderoso, mas seu uso direto (Out-of-the-Box) gerou atritos operacionais graves na Tuaregue:

* **Burocracia na Recepção:** Cadastrar um cliente e seu veículo exige passar por telas de `Party`, `ContactMech`, `PostalAddress` e `FixedAsset`. Um processo que deveria levar 30 segundos levava minutos.
* **Execução sem Garantia Física:** O sistema padrão permitia que o mecânico iniciasse um serviço (`WorkEffort`) mesmo se a peça não estivesse fisicamente reservada (`InventoryItem`), causando paradas na oficina.
* **Orçamentos Flexíveis Demais:** Ausência de travas rígidas; era possível adulterar ordens de serviço em andamento sem gerar um "Aditivo de Orçamento" formal.
* **Fuga de Receita:** Veículos podiam ser sinalizados como "Prontos" e liberados fisicamente sem que a fatura (`Invoice`) estivesse 100% paga ou com as peças não faturadas corretamente.

---

## 🛠️ A Solução: O que o Khepri FAZ de verdade

O Khepri atua como o maestro do OFBiz. Ele intercepta as intenções do usuário final (via UI customizada ou API) e orquestra as regras de negócio internamente:

* **Cadastro Atômico (One-Click):** Cria `Party`, `PartyRole` e vincula o `FixedAsset` (Veículo) em uma única transação de serviço (`createTuaregueCustomerProfile`).
* **Trava de Estoque:** O Khepri não permite que uma Ordem de Serviço mude para "Em Execução" se a query de `OrderItemShipGrpInvRes` (Reserva de Estoque) não bater 100% com a necessidade da OS.
* **Controle de Aditivos:** Se um problema novo for encontrado durante a desmontagem, o Khepri trava a OS principal e força a criação de uma `Quote` (Aditivo). O serviço só volta a andar quando o aditivo vira `ORDER_APPROVED`.
* **Gate Pass (Bloqueio Financeiro):** A rotina de finalização da OS consulta o `AcctgTrans` e a `Invoice`. Se o status não for `PMNT_RECEIVED`, o botão de liberação do veículo é desabilitado na interface do recepcionista.

---

## 🏗️ Arquitetura

O Khepri é um **Plugin Nativo** dentro do ecosistema Apache OFBiz. 

Ele não é uma aplicação separada rodando Spring Boot. Ele utiliza o `Service Engine`, `Entity Engine` e `Event Handlers` nativos do OFBiz para garantir que todas as transações operem no mesmo banco de dados e respeitem a contabilidade do ERP.

* **Linguagem Base:** Java 17 e Groovy (para Services e Scripts).
* **Integração Front-end:** Exposição de endpoints dedicados via `controller.xml` do plugin, retornando views customizadas ou payloads JSON.
* **Regras de Negócio:** Concentradas em SECAs (Service Entity Condition Actions) customizadas no arquivo `secas.xml` do Khepri.

---

## 🚦 Status do Projeto

- [x] Estrutura base do plugin criada no OFBiz (`hot-deploy/khepri`).
- [ ] Orquestração da Recepção (Cadastro de Cliente + Veículo).
- [ ] Implementação da Trava de Estoque (Validação pré-execução).
- [ ] Fluxo de Aditivo de Orçamento.
- [ ] Validação de Pagamento vs. Liberação (Gate Pass).

---

## 💻 Como Rodar (Desenvolvimento)

O Khepri depende de uma instância funcional do Apache OFBiz.

1. **Clone o repositório** dentro da pasta `hot-deploy` (ou `plugins`) do seu OFBiz:
   ```bash
   cd /caminho-do-ofbiz/plugins
   git clone [https://github.com/seu-usuario/khepri-orchestrator.git](https://github.com/seu-usuario/khepri-orchestrator.git) khepri
   ```

2. **Carregue os dados semente (Seed Data)** do Khepri (Roles, Tipos de Serviço específicos da Tuaregue):
   ```bash
   cd /caminho-do-ofbiz
   ./gradlew "ofbiz --load-data readers=seed,seed-initial component=khepri"
   ```

3. **Inicie o OFBiz:**
   ```bash
   ./gradlew ofbiz
   ```

4. **Acesse o plugin:**




```
