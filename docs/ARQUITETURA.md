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
`exchange`, `notification`, `security`, `config` e `api` (`api.v1` para a consulta
pública, `api.admin` para a administração).

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

Na implementação (sprint 4), o cache guarda cada par de moedas por 12 h em vez de um dia
inteiro: o BCE publica uma vez por dia útil, e 12 h evita carregar a cotação da véspera
por quase dois dias. Uma moeda que o BCE não publica fica marcada pelo mesmo tempo; uma
falha de rede não fica no cache e é tentada de novo na próxima consulta. Em qualquer
falha a consulta responde normalmente, só sem o valor convertido (`costInOriginCurrency`
nulo). O histórico mostra os valores como foram publicados, sem conversão.

O BCE cobre as moedas de todas as origens (BRL, JPY, EUR, CAD, ZAR), mas não EGP nem ARS.
Isso só importa se Egito ou Argentina publicarem taxas na moeda local; nesse caso a
resposta traz apenas o valor original, sem conversão.

## Coleta (scraping)

- Um `VisaScraper` por país de destino. Ele lê as páginas de "lista de
  nacionalidades" do governo e devolve um resultado por país de origem.
- Os nomes de país das listas viram código ISO pelo `CountryNames`, que usa os nomes do JDK
  no idioma da página (inglês, alemão) e uma lista de formas oficiais que o JDK não usa
  ("Korea, Republic of", "Kongo (Demokratische Republik)", "Türkiye"). Um teste confere que
  todos os países das listas salvas são reconhecidos, exceto Kosovo, que não tem código ISO.
- Cada lista tem um tamanho mínimo: se o site mudar e a lista vier vazia ou curta, a coleta
  falha em vez de concluir que quase ninguém precisa de visto. Isso importa principalmente no
  Reino Unido, onde quem não está na lista precisa de ETA.
- Páginas de detalhe (visto, eTA/ETA) só são baixadas quando alguma origem precisa delas.

| Destino | Lista de nacionalidades | Resultado por origem |
|---------|-------------------------|----------------------|
| CAN | "What you need to enter Canada" (IRCC): listas de visto, de eTA e de quem pode trocar o visto por eTA ao voar | `VISA_REQUIRED` com o visto de visitante (e o eTA, quando a origem pode trocar) ou `ELECTRONIC_TRAVEL_AUTHORIZATION` com taxa de CAN$7 |
| GBR | Immigration Rules, Appendix Visitor: visa national list | `VISA_REQUIRED` com o Standard Visitor (£135, 6 meses) para quem está na lista; os demais `ELECTRONIC_TRAVEL_AUTHORIZATION` (£20). Irlandeses ficam de fora (Common Travel Area) |
| DEU | Staatenliste zur Visumpflicht (Auswärtiges Amt): tabela Ja/Nein com notas numeradas | `VISA_FREE` (90 dias em 180) ou `VISA_REQUIRED`; as notas citadas na linha viram requisitos, em alemão |
- Todo acesso passa pelo `HtmlFetcher`, que:
  - só aceita `https`;
  - se identifica com um User-Agent honesto, com link para o repositório;
  - respeita o `robots.txt` de cada site;
  - espera um intervalo mínimo entre requisições ao mesmo host;
  - limita tempo de resposta e tamanho da página.
- Testes de scraper usam cópias do HTML salvas em `src/test/resources/fixtures`,
  nunca o site real. Um workflow agendado separado roda contra os sites reais e
  avisa quando um deles muda de estrutura.

### Execução da coleta

- `POST /admin/collections` inicia a coleta em segundo plano e responde `202` com o
  `runId`. Só uma coleta roda por vez (`409` se já houver outra). `?destination=GBR`
  coleta um destino só.
- Para cada scraper, o `CollectionService` grava um `CollectionLog` com status
  `SUCCESS`, `PARTIAL` (alguns pares falharam) ou `FAILED`. Um destino com falha não
  impede os outros.
- O `VisaRequirementWriter` compara o SHA-256 do conteúdo novo com o salvo:
  - igual: atualiza só `collectedAt`;
  - diferente: copia o conteúdo anterior para `visa_history` e grava o novo;
  - par corrigido manualmente por um admin (`dataOrigin = MANUAL`): não é sobrescrito,
    e o log conta como `pairsSkipped`.
- Se houver alguma falha, é enviado **um** alerta por execução (e-mail via SMTP, ou só log
  quando o e-mail não está configurado). Os dados anteriores continuam disponíveis.
- Agendamento em produção: workflow `collect.yml` (todo dia às 05:00 UTC). Para quem roda
  o app sempre ligado, existe também `COLLECTION_CRON` (desligado por padrão).

### Sites que bloqueiam robôs

Na verificação inicial (out/2026), alguns sites oficiais responderam `403` a um
cliente que se identifica como robô (por exemplo MOFA do Japão, INM do México,
travel.state.gov e france-visas). O sistema **não** tenta contornar esses bloqueios,
coerente com a delimitação legal do TCC. Para esses casos as alternativas são:
outra fonte oficial do mesmo governo, um documento oficial publicado (PDF) ou
entrada manual por um admin, sempre com `dataOrigin = MANUAL` e a URL de referência.

## Administração dos dados (sprint 5)

- **Fontes oficiais**: `GET/POST /admin/countries/{iso3}/sources`, e `PUT`/`DELETE` com
  `?url=` (a URL identifica a fonte e não se repete no mesmo país). Cada alteração é um único
  update atômico no documento do país (`$push`, `$set` posicional, `$pull`), então dois admins
  editando ao mesmo tempo não perdem a alteração um do outro, e o seed nunca as sobrescreve.
  `verified: true` grava `verifiedAt` com a hora atual.
- **Correção manual**: `PUT /admin/visas?origin=&destination=` grava o conteúdo com
  `dataOrigin = MANUAL`, e a coleta passa a pular o par (`pairsSkipped`). Uma mudança real vai
  para o histórico com `changeOrigin = MANUAL`, como uma coletada. Toda correção exige ao menos
  uma URL de origem, para manter a rastreabilidade. `collectedAt` continua indicando a última
  leitura do site (nulo se o par nunca foi coletado).
- `DELETE /admin/visas/manual?origin=&destination=` devolve o par ao scraper: o conteúdo fica
  até a próxima coleta, que o substitui (e o move para o histórico, se for diferente).
- Se uma coleta e uma correção gravarem o mesmo par no mesmo instante, o `@Version` do documento
  faz uma delas falhar (`409 Concurrent update` para o admin), sem perder dados.

## API pública

| Rota | Resposta |
|------|----------|
| `GET /api/v1/countries` | Países cobertos, com moeda, papel (origem/destino) e fontes oficiais |
| `GET /api/v1/visas?origin=&destination=` | Política, vistos, custo nas duas moedas, URLs de origem, `dataOrigin`, `collectedAt` e `updatedAt` |
| `GET /api/v1/visas/history?origin=&destination=` | Mudanças do último ano, da mais recente para a mais antiga |

- Códigos em ISO alfa-3 maiúsculo (`BRA`); outro formato responde `400`.
- País fora do catálogo, ou fora do papel pedido (por exemplo `GBR` como origem), responde
  `404 Unknown country`. Par válido ainda não coletado responde `404 No visa data`.
  Origem igual ao destino responde `400`.
- O histórico também filtra pela data, além do TTL: o monitor de TTL do MongoDB roda a
  cada minuto, então um registro vencido poderia aparecer por alguns segundos.
- Documentação OpenAPI em `/v3/api-docs` e Swagger UI em `/swagger-ui.html`, só com as
  rotas `/api/**` e `/admin/**` (as do admin aparecem com o esquema `X-Admin-Key`).

## Segurança

- Consulta pública (`/api/**`) com limite de requisições por IP (Bucket4j, token bucket):
  60 por minuto por padrão (`RATE_LIMIT_CAPACITY`), com `X-RateLimit-Limit` e
  `X-RateLimit-Remaining` em toda resposta e `429` com `Retry-After` acima do limite.
  Os contadores ficam em memória, com no máximo 100 mil IPs e descarte dos ociosos,
  para que muitos endereços não esgotem a memória. O IP vem de `X-Forwarded-For`,
  porque a aplicação confia no proxy do provedor (`forward-headers-strategy`); fora de
  um proxy que reescreva esse cabeçalho, o cliente poderia trocar de IP a cada
  requisição. Com mais de uma instância, cada uma teria o seu limite (aceitável no
  plano gratuito, que roda uma só).
- Endpoints `/admin/**` protegidos por Spring Security: cabeçalho `X-Admin-Key` com a
  chave de `ADMIN_API_KEY` (mínimo de 32 caracteres), comparada por hash em tempo
  constante. Sem chave configurada, o `/admin` fica fechado. API sem sessão nem cookies;
  fora do `/admin`, só `GET` é aceito.
- URLs cadastradas (fontes e correções manuais) passam pelo `PublicUrlValidator`, contra SSRF:
  só `https` na porta padrão, sem usuário/senha na URL, host por nome de domínio (não IP, o que
  também barra formas como `2130706433` ou `0x7f.0.0.1`) e **todos** os endereços do host
  públicos (sem loopback, redes privadas, link-local como o metadata da nuvem `169.254.169.254`,
  CGNAT, `fc00::/7`, multicast nem IPv4 embutido em NAT64).
- O `PoliteHtmlFetcher` repete essa validação antes de cada requisição e segue os
  redirecionamentos ele mesmo (no máximo 5), validando cada salto: um site oficial não consegue
  redirecionar a coleta para a rede interna. Resta a janela entre a consulta de DNS da validação
  e a da conexão (DNS rebinding); fechá-la exigiria fixar o IP na conexão, o que o Jsoup não
  oferece. O risco é baixo porque as URLs vêm de admins e de sites governamentais.
- Cabeçalhos em toda resposta: `Content-Security-Policy: default-src 'none'` (o Swagger UI tem
  uma política própria, que só permite os próprios scripts e estilos), `X-Content-Type-Options:
  nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`,
  `Cache-Control: no-store` e HSTS quando a requisição chega por https.
- CORS fechado por padrão: nenhum site de outra origem lê as respostas no navegador. Origens
  listadas em `CORS_ALLOWED_ORIGINS` podem fazer só `GET` em `/api/**`, sem credenciais; o
  `/admin` nunca é liberado. CORS não afeta clientes fora do navegador (curl, servidores).
- Erros respondidos em Problem Details (RFC 9457), sem stack trace.
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
