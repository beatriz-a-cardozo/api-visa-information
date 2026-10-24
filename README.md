# api-visa-information

[![CI](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/ci.yml)
[![CodeQL](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/codeql.yml/badge.svg?branch=dev)](https://github.com/beatriz-a-cardozo/api-visa-information/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/beatriz-a-cardozo/api-visa-information/badge)](https://scorecard.dev/viewer/?uri=github.com/beatriz-a-cardozo/api-visa-information)

API REST que coleta requisitos de visto diretamente de fontes governamentais oficiais,
normaliza tudo em um schema único e registra de onde e quando cada dado veio.

Trabalho de Conclusão de Curso — Engenharia de Computação, Inatel.

## Stack

Java 21 · Spring Boot 4 · MongoDB · Jsoup · Docker · GitHub Actions

## Rodando localmente

Pré-requisitos: Docker. Para desenvolver, também JDK 21.

```bash
cd api-visa
cp .env.example .env   # troque a senha
docker compose up --build
```

A API sobe em `http://localhost:8080` e o health em `/actuator/health`.

Sem Docker para a aplicação (o Mongo pode continuar no compose):

```bash
cd api-visa
./mvnw spring-boot:run
```

## Testes

```bash
cd api-visa
./mvnw verify            # formatação, testes, cobertura mínima
./mvnw spotless:apply    # formata o código
```

Os testes de integração usam Testcontainers, então o Docker precisa estar rodando.
Os testes de scraper usam HTML salvo em `src/test/resources/fixtures`; para rodar
contra os sites reais:

```bash
./mvnw test -Dgroups=live -DexcludedGroups=
```

## Documentação

- [Arquitetura e decisões](docs/ARQUITETURA.md)
- [Roadmap](docs/ROADMAP.md)
- [Política de segurança](SECURITY.md)

## Fluxo de contribuição

`feature/*` → PR para `dev` → `hom` → `main`. O CI precisa passar e o PR precisa de revisão.
