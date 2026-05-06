# ⚙️ Khepri Orchestrator

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Apache OFBiz](https://img.shields.io/badge/Apache_OFBiz-24.09-D22128?style=for-the-badge&logo=apache&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-BFF%20%2F%20Gateway-007396?style=for-the-badge)

O **Khepri Orchestrator** é o motor de integração e Backend For Frontend (BFF) desenvolvido para modernizar e assumir o controle total das operações da **Oficina Mecânica Tuaregue**. 

Ele atua como a ponte inteligente entre interfaces de usuário ágeis e o robusto motor de ERP do Apache OFBiz, garantindo que a complexidade industrial do back-end seja traduzida em fluidez e eficiência no chão de fábrica.

---

## 📖 O Projeto (Metodologia STAR)

### 🚨 Situação (Situation)
A **Oficina Tuaregue** (localizada em Atibaia-SP) enfrentava um gargalo operacional grave. A empresa dependia de um sistema SaaS (Software as a Service) genérico e online que apresentava três problemas críticos:
1. **Alto Custo:** Mensalidades caras que não se justificavam pelo valor entregue.
2. **Engessamento Operacional:** O sistema era genérico, com usabilidade ruim e não se adaptava ao fluxo real da oficina (diagnósticos, aditivos de orçamento, gestão de pátio).
3. **Suporte Ineficiente:** Qualquer dúvida ou necessidade de adaptação esbarrava em um atendimento demorado e ineficaz por parte do fornecedor.

### 🎯 Tarefa (Task)
Criar uma solução tecnológica **proprietária, local e altamente responsiva** que eliminasse a dependência do SaaS de terceiros. O sistema precisava ser desenhado "sob medida" (tailor-made) para o fluxo da oficina, garantindo excelência desde a recepção do cliente até o faturamento, com controle milimétrico do inventário de peças e do tempo dos mecânicos.

### ⚡ Ação (Action)
Em vez de reinventar a roda construindo um ERP do zero, adotamos o framework open-source **Apache OFBiz (v24.09)** como motor de retaguarda (contabilidade, regras de estoque, entidades). 
Para resolver o problema de usabilidade e integração, desenvolvemos o **Khepri Orchestrator**. Este componente atua como um BFF/API Gateway que:
* Encapsula os serviços complexos do OFBiz (XML, Groovy, Entity Engine).
* Expõe APIs RESTful modernas, limpas e documentadas para o Front-end.
* Orquestra fluxos transacionais pesados (ex: aprovar orçamento, baixar estoque e gerar fatura) em chamadas únicas e resilientes.

### ✨ Resultado (Result)
* **Autonomia e Economia:** Eliminação da assinatura mensal do software anterior, transformando despesa recorrente em ativo tecnológico próprio (CAPEX).
* **Fluxo Otimizado:** Operadores e mecânicos utilizam interfaces desenhadas para o processo exato da Tuaregue, sem cliques desnecessários ou jargões contábeis.
* **Suporte Imediato:** Sendo um sistema *in-house*, correções e novas regras de negócio são implementadas e validadas diretamente com os donos, sem SLAs de terceiros.

---

## 🏗️ Arquitetura

O Khepri atua no padrão **Backend For Frontend (BFF)**. Ele não possui banco de dados próprio para regras de negócio; sua função é a orquestração.
```text
[ Front-end (Web/Mobile) ]
          │
          ▼ (JSON / REST)
 ┌─────────────────────────┐
 │  KHEPRI ORCHESTRATOR    │ ◄ (Validação, Orquestração, Tratamento de Exceções)
 └────────┬────────┬───────┘
          │        │ (XML-RPC / RMI / Direct Java)
          ▼        ▼
 ┌─────────────────────────┐
 │     APACHE OFBIZ        │ ◄ (Entity Engine, Service Engine, Contabilidade)
 │  (Tuaregue ERP Core)    │
 └─────────────────────────┘
