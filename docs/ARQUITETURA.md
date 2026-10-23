# Arquitetura e decisões

Este documento registra as decisões técnicas do projeto e o motivo de cada uma.
Quando uma decisão mudar, atualize a seção correspondente em vez de apagá-la,
para manter o histórico (o TCC cita rastreabilidade como contribuição central).

## Escopo inicial

| ISO3 | País           | Continente    | Idioma da fonte | Origem | Destino |
|------|----------------|---------------|-----------------|:------:|:-------:|
| BRA  | Brasil         | América do Sul| pt              |   ✔    |    ✔    |
| JPN  | Japão          | Ásia          | ja / en         |   ✔    |    ✔    |
| DEU  | Alemanha       | Europa        | de              |   ✔    |    ✔    |
| CAN  | Canadá         | América do Norte | en / fr      |   ✔    |    ✔    |
| ZAF  | África do Sul  | África        | en              |   ✔    |    ✔    |
| GBR  | Reino Unido    | Europa        | en              |        |    ✔    |
| AUS  | Austrália      | Oceania       | en              |        |    ✔    |
| KOR  | Coreia do Sul  | Ásia          | ko / en         |        |    ✔    |
| EGY  | Egito          | África        | ar / en         |        |    ✔    |
| ARG  | Argentina      | América do Sul| es              |        |    ✔    |

5 origens × 10 destinos = 45 pares (o par de um país com ele mesmo não é armazenado).
O catálogo fica em `api-visa/src/main/resources/seed/countries.json` e é carregado no
banco na inicialização, sem sobrescrever correções feitas por um admin.

## Camadas

```
api (controllers, DTOs)  ->  service  ->  repository  ->  MongoDB
                               |
                               +-> scraper (um por destino) -> HtmlFetcher -> sites oficiais
                               +-> exchange (câmbio)        -> Frankfurter (BCE)
                               +-> notification (e-mail)    -> SMTP
```

Pacotes em `com.tcc.api_visa`: `domain`, `repository`, `service`, `scraper`,
`config` e, a partir da sprint da API, `api`.

## Modelo de dados

- **countries**: `_id` = ISO alpha-3. Guarda nome, continente, moeda (ISO 4217),
  idiomas, se é origem/destino e a lista de fontes oficiais (`url`, `language`,
  `verifiedAt`). `verifiedAt` vazio significa que a URL ainda não foi confirmada.
- **visa_requirements**: um documento por par origem/destino, com índice único
  `(originIso3, destinationIso3)`. Contém a política geral (`VISA_FREE`,
  `VISA_REQUIRED`, `E_VISA` …), a lista de vistos disponíveis (tipo, estadia máxima,
  custo com moeda, requisitos), as URLs de origem, `collectedAt` (última coleta),
  `updatedAt` (última mudança de conteúdo), `contentHash` e `dataOrigin`
  (`SCRAPER` ou `MANUAL`).
- **visa_history**: cópia do conteúdo anterior, gravada **só quando o hash muda**.
  Índice TTL de 365 dias apaga sozinho o que passou de um ano.
- **collection_logs**: uma entrada por execução de scraper (status, pares coletados,
  pares alterados, erro). Índice TTL de 180 dias.

Valores em dinheiro usam `BigDecimal` + código da moeda, nunca `float`.
Datas usam `Instant` (UTC).

## Custo nas duas moedas

O scraper grava o custo na moeda publicada pelo destino. A conversão para a moeda
da origem acontece na leitura, usando a API pública do Banco Central Europeu via
[Frankfurter](https://frankfurter.dev) (gratuita, sem chave), com cache diário.
A resposta informa a taxa e a data da cotação, para não parecer um valor oficial.

O BCE cobre as moedas de todas as origens (BRL, JPY, EUR, CAD, ZAR), mas não EGP nem ARS.
Isso só importa se Egito ou Argentina publicarem taxas na moeda local; nesse caso a
resposta traz apenas o valor original, sem conversão.

## Coleta (scraping)

- Um `VisaScraper` por país de destino. Ele lê as páginas de "lista de
  nacionalidades" do governo e devolve um resultado por país de origem.
- Todo acesso passa pelo `HtmlFetcher`, que:
  - só aceita `https`;
  - se identifica com um User-Agent honesto, com link para o repositório;
  - respeita o `robots.txt` de cada site;
  - espera um intervalo mínimo entre requisições ao mesmo host;
  - limita tempo de resposta e tamanho da página.
- Testes de scraper usam cópias do HTML salvas em `src/test/resources/fixtures`,
  nunca o site real. Um workflow agendado separado roda contra os sites reais e
  avisa quando um deles muda de estrutura.

### Sites que bloqueiam robôs

Na verificação inicial (out/2026), alguns sites oficiais responderam `403` a um
cliente que se identifica como robô (por exemplo MOFA do Japão, INM do México,
travel.state.gov e france-visas). O sistema **não** tenta contornar esses bloqueios,
coerente com a delimitação legal do TCC. Para esses casos as alternativas são:
outra fonte oficial do mesmo governo, um documento oficial publicado (PDF) ou
entrada manual por um admin, sempre com `dataOrigin = MANUAL` e a URL de referência.

## Segurança

- Consulta pública com limite de requisições por IP.
- Endpoints `/admin/**` protegidos (Spring Security, credencial vinda de variável
  de ambiente, nunca do código).
- Actuator expõe só `health`, sem detalhes para usuários anônimos.
- Container sem shell e sem root (distroless `nonroot`), imagens fixadas por digest.
- Segredos só em variáveis de ambiente ou GitHub Secrets; `.env` fica fora do git.
- CI com CodeQL, revisão de dependências, Gitleaks, scan da imagem e OpenSSF
  Scorecard. Actions fixadas por SHA e com permissões mínimas.

## Deploy (gratuito)

- **App**: Render (web service gratuito, a partir do Dockerfile). O plano gratuito
  hiberna após inatividade, então o agendamento não depende do processo ficar no ar.
- **Banco**: MongoDB Atlas M0, com usuário de privilégio mínimo e lista de IPs.
- **Agendamento**: workflow agendado do GitHub Actions chama o endpoint admin de
  coleta (acorda o serviço e dispara o scraping).
- **E-mail de falha**: SMTP genérico (Spring Mail). Provedor sugerido: Brevo
  (300 e-mails/dia grátis). Trocar de provedor é só mudar variáveis de ambiente.

## Fluxo de branches

`feature/*` → PR para `dev` (toda a sprint) → `hom` (após teste) → `main` (entrega).
O CI roda em PRs e pushes para essas três branches.
