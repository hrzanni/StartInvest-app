# StartInvest Java — Especificação Técnica

Reimplementação do StartInvest (originalmente Flutter + Firebase) como projeto de estudo de Java e Spring Boot. Objetivo do projeto: consolidar conhecimento, não entregar rápido. Escopo: **Android nativo em Java** consumindo uma **API própria em Spring Boot**. Sem iOS.

Fonte de verdade do domínio: RF01-RF22 do StartInvest original (`docs/features/requirements.md` do repo Flutter) e o schema Firestore documentado em `docs/technical/firestore-schemas.md`. Este documento adapta os dois para um backend relacional próprio.

---

## 1. Visão e escopo

Plataforma gamificada de educação financeira para jovens de 18-30 anos. Três pilares: aprender (vídeo-aulas), praticar (simulador de carteira com dados reais de mercado) e competir (XP, níveis, ranking, amigos).

**Fora de escopo nesta reimplementação** (presentes no original, cortados aqui para não inflar o projeto):
- iOS.
- Login social (Google Sign-In). Login é e-mail/senha apenas.
- Push notifications (Firebase Messaging). Pode voltar como stretch goal via FCM puro, sem Firebase Auth/Firestore.
- Upload de imagem de perfil para storage externo (usa avatar padrão ou URL estática).

Essas exclusões são cortes de escopo deliberados para o projeto de estudo, não incapacidade técnica. Revise-as se o objetivo mudar de "aprender Spring" para "portar o app inteiro".

---

## 2. Arquitetura

```
┌─────────────────────────┐         HTTPS/JSON          ┌──────────────────────────┐
│   Android App (Java)    │ ───────────────────────────▶ │  Spring Boot API (Java)  │
│   MVVM + Repository     │ ◀─────────────────────────── │  Camadas: web/service/   │
│   Retrofit + Room       │                               │  repository/domain       │
└─────────────────────────┘                               └────────────┬─────────────┘
                                                                         │ JPA
                                                                         ▼
                                                              ┌──────────────────┐
                                                              │   PostgreSQL      │
                                                              └──────────────────┘
                                                                         │
                                                              ┌──────────────────┐
                                                              │  brapi.dev (HTTP) │  ← dados de mercado, via cache
                                                              └──────────────────┘
```

**Regra de dependência no backend:** `web` (controllers/DTOs) → `service` (regra de negócio) → `repository` (Spring Data JPA) → `domain` (entidades). Controllers nunca tocam repositórios diretamente. Services nunca retornam entidades JPA para o controller — sempre DTOs.

**Regra de dependência no Android:** `presentation` (Activity/Fragment/ViewModel) → `domain` (use cases, POJOs puros) → `data` (implementação via Retrofit/Room). `domain` não importa nada de `android.*`.

Por que manter Clean Architecture / MVVM mesmo em projeto de estudo: é o que o StartInvest original já usa (BLoC + Clean Architecture), então a migração de conceito é direta, e força a prática de DI e separação de camadas em Java, que é onde a maior parte do aprendizado de Spring se transfere (os mesmos princípios de camadas, DTOs e injeção de dependência).

---

## 3. Stack técnica

### Backend
| Item | Escolha | Motivo |
|---|---|---|
| Linguagem | Java 21 (LTS) | Compatível com Spring Boot 4.x, records e pattern matching disponíveis |
| Framework | Spring Boot 4.1.1 (verificado na doc oficial em set/2026) | Exige Java 17+ |
| Build | Maven | Mais comum em ambiente acadêmico/didático que Gradle para primeiro contato |
| Persistência | PostgreSQL + Spring Data JPA/Hibernate | Relacional se encaixa melhor que Firestore no domínio (carteiras, trades, ranking com joins) |
| Migrações | Flyway | Versiona schema, hábito profissional desde o início |
| Segurança | Spring Security + JWT (jjwt) | Stateless, sem sessão de servidor, adequado a cliente mobile |
| Cache | Spring Cache + Caffeine | Cachear cotações do brapi.dev evita estourar rate limit |
| Cliente HTTP externo | `RestClient` (nativo do Spring 6.1+/Boot 3.2+, mantido no 4.x) | Chamar brapi.dev sem dependência extra |
| Testes | JUnit 5, Mockito, Testcontainers (Postgres), spring-security-test | Testcontainers evita "funciona só no H2" |

### Android
| Item | Escolha | Motivo |
|---|---|---|
| Linguagem | Java 17 (toolchain do Android Gradle Plugin) | Requisito do escopo: Java, não Kotlin |
| UI | Views + XML + ViewBinding | Jetpack Compose é Kotlin-first; ViewBinding é o caminho Java idiomático |
| Arquitetura | MVVM: `ViewModel` + `LiveData` (androidx.lifecycle) | Equivalente direto ao BLoC do original (Event → estado observável) |
| Navegação | Navigation Component (`nav_graph.xml`) + `BottomNavigationView` | Equivalente ao GoRouter + ShellRoute/AppShell |
| DI | Fase 7: Service Locator manual. Fase 9 (stretch): Hilt | Ver seção 8 — manual primeiro para entender o problema que a DI resolve |
| Rede | Retrofit + OkHttp (interceptor de JWT) | Padrão de mercado para Java Android |
| Persistência local | Room (cache de missões/cursos) + `EncryptedSharedPreferences` (token) | Equivalente ao Hive + flutter_secure_storage |
| Gráficos | MPAndroidChart | Equivalente ao fl_chart |
| Testes | JUnit (ViewModel/use case), Espresso (fluxos críticos) | bloc_test/mocktail do original viram Mockito/JUnit |

---

## 4. Modelo de dados (PostgreSQL)

Adaptado das coleções Firestore (`users`, `news`, `missions`) e das entidades de domínio do Flutter (`Wallet`, `Position`, `Trade`, `QuizQuestion` etc.), normalizado para relacional.

```sql
users
  id                      BIGSERIAL PK
  name                    VARCHAR(120) NOT NULL
  email                   VARCHAR(180) NOT NULL UNIQUE
  password_hash           VARCHAR(255) NOT NULL
  birth_date              DATE NOT NULL
  xp                      INTEGER NOT NULL DEFAULT 0
  level                   INTEGER NOT NULL DEFAULT 1
  league                  VARCHAR(20) NOT NULL DEFAULT 'bronze'   -- bronze | prata | ouro | elite
  login_streak            INTEGER NOT NULL DEFAULT 0
  last_login_at           TIMESTAMPTZ
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()

wallets
  id                      BIGSERIAL PK
  user_id                 BIGINT FK -> users.id
  category                VARCHAR(20) NOT NULL   -- conservative | moderate | aggressive  (RF11)
  cash_balance            NUMERIC(14,2) NOT NULL DEFAULT 100000.00
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  UNIQUE (user_id, category)  -- 1 carteira por categoria por usuário, ver §6.3

positions
  id                      BIGSERIAL PK
  wallet_id               BIGINT FK -> wallets.id
  ticker                  VARCHAR(12) NOT NULL
  quantity                NUMERIC(18,6) NOT NULL
  avg_price               NUMERIC(14,4) NOT NULL
  UNIQUE (wallet_id, ticker)

trades
  id                      BIGSERIAL PK
  wallet_id               BIGINT FK -> wallets.id
  ticker                  VARCHAR(12) NOT NULL
  side                    VARCHAR(4) NOT NULL     -- BUY | SELL
  quantity                NUMERIC(18,6) NOT NULL
  price                   NUMERIC(14,4) NOT NULL
  executed_at             TIMESTAMPTZ NOT NULL DEFAULT now()

courses
  id                      BIGSERIAL PK
  title                   VARCHAR(160) NOT NULL
  description             TEXT
  display_order           INTEGER NOT NULL
  requires_course_id      BIGINT NULL FK -> courses.id   -- bloqueio sequencial, RF14

lessons
  id                      BIGSERIAL PK
  course_id               BIGINT FK -> courses.id
  title                   VARCHAR(160) NOT NULL
  video_url               VARCHAR(500) NOT NULL
  duration_seconds        INTEGER
  display_order           INTEGER NOT NULL

user_lesson_progress
  user_id                 BIGINT FK -> users.id
  lesson_id               BIGINT FK -> lessons.id
  completed_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  PRIMARY KEY (user_id, lesson_id)

missions
  id                      BIGSERIAL PK
  title                   VARCHAR(160) NOT NULL
  description             TEXT
  category                VARCHAR(20) NOT NULL    -- learning | practice
  required_level          INTEGER NOT NULL DEFAULT 1
  xp_reward               INTEGER NOT NULL

user_missions
  user_id                 BIGINT FK -> users.id
  mission_id              BIGINT FK -> missions.id
  completed_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  PRIMARY KEY (user_id, mission_id)

badges
  id                      BIGSERIAL PK
  code                    VARCHAR(60) NOT NULL UNIQUE
  title                   VARCHAR(160) NOT NULL
  description             TEXT

user_badges
  user_id                 BIGINT FK -> users.id
  badge_id                BIGINT FK -> badges.id
  earned_at               TIMESTAMPTZ NOT NULL DEFAULT now()
  PRIMARY KEY (user_id, badge_id)

news
  id                      BIGSERIAL PK
  title                   VARCHAR(200) NOT NULL
  content                 TEXT NOT NULL
  source                  VARCHAR(120)
  category                VARCHAR(20) NOT NULL   -- stocks | crypto | economy | tech
  published_at            TIMESTAMPTZ NOT NULL

friendships
  requester_id            BIGINT FK -> users.id
  addressee_id            BIGINT FK -> users.id
  status                  VARCHAR(10) NOT NULL   -- pending | accepted
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  PRIMARY KEY (requester_id, addressee_id)
```

Cada tabela vira uma migração Flyway própria (`V1__users.sql`, `V2__wallets.sql`, ...), na ordem em que as fases do plano as introduzem. Não crie o schema inteiro de uma vez: é uma das partes mais didáticas de Flyway (migração incremental).

---

## 5. Contrato de API

Prefixo `/api`. Autenticação: header `Authorization: Bearer <jwt>` em toda rota exceto `/api/auth/**` e `/api/news`.

### 5.1 Auth
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | RF01. Body: `name, email, password, birthDate`. Retorna 201 + JWT. |
| POST | `/api/auth/login` | RF02. Body: `email, password`. Retorna JWT. |

Erros de validação (RF03) devem responder `400` no formato `ProblemDetail` (RFC 7807, suporte nativo do Spring), com um campo `errors: [{field, message}]` customizado.

### 5.2 Usuário
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/users/me` | RF04. Nome, nível, XP, liga, streak. |
| PATCH | `/api/users/me` | Editar nome/subtítulo. |

### 5.3 Carteiras / Simulador (RF07-11)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/wallets` | Lista carteiras do usuário (até 3, uma por categoria). |
| POST | `/api/wallets` | Cria carteira de uma categoria (conservative/moderate/aggressive). |
| GET | `/api/wallets/{id}/positions` | Posições atuais. |
| GET | `/api/wallets/{id}/trades` | Histórico de trades. |
| POST | `/api/wallets/{id}/trades` | Executa compra/venda. Body: `ticker, side, quantity`. Preço vem do backend (brapi), não do cliente — nunca confie em preço enviado pelo app. |
| GET | `/api/market/assets?category=` | Lista ativos disponíveis por categoria de risco. |
| GET | `/api/market/assets/{ticker}/quote` | Cotação atual (cacheada). |

### 5.4 Conteúdo / vídeo-aulas (RF12-15)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/courses` | Módulos com flag `locked` calculada por progresso do usuário. |
| GET | `/api/courses/{id}/lessons` | Aulas do módulo. |
| POST | `/api/lessons/{id}/complete` | Marca aula concluída, dispara XP. |

### 5.5 Notícias
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/news?category=` | Pública, sem auth. |

### 5.6 Amigos e ranking (RF16-19)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/friends` | Lista amigos aceitos. |
| POST | `/api/friends/requests` | Body: `addresseeEmail`. |
| POST | `/api/friends/requests/{id}/accept` | Aceita pedido. |
| GET | `/api/ranking?scope=global\|friends` | Paginado (`?page=&size=`), ordenado por XP/streak. |

### 5.7 Gamificação (RF20-22)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/missions/daily` | Missões do dia com flag `completed`. |
| GET | `/api/badges` | Catálogo. |
| GET | `/api/users/me/badges` | Badges conquistados. |

XP, nível e badges nunca são setados pelo cliente. Toda concessão de XP acontece como efeito colateral de uma ação validada no servidor (trade executado, aula concluída, missão batida) — nunca via endpoint tipo `POST /users/me/xp`. Essa é a correção mais importante em relação ao original: no Flutter, `calculate_portfolio_xp_usecase` roda no cliente, o que é trivialmente fraudável. Aqui a regra migra inteira para o backend.

---

## 6. Regras de negócio críticas

1. **Saldo de carteira nunca fica negativo.** Compra é rejeitada (`409 Conflict`) se `quantity * preço_atual > cash_balance`.
2. **Venda exige posição suficiente.** Rejeitada se `quantity > position.quantity`.
3. **Preço de execução vem sempre do backend**, buscado no momento do trade via brapi.dev (com cache curto, ~60s, para não travar em cada clique nem ficar velho demais).
4. **Concorrência em trades:** duas requisições simultâneas de venda não podem ambas passar pela checagem de saldo/posição e depois debitar — use transação com lock (`@Transactional` + `SELECT ... FOR UPDATE` ou controle otimista com `@Version`). Este é um dos pontos de aprendizado mais valiosos do projeto: veja Fase 3.
5. **Bloqueio de módulos (RF14):** um curso com `requires_course_id` só aparece destrancado se o usuário completou todas as lições do curso requisito.
6. **XP e nível:** função pura e testável, ex. `level = floor(sqrt(xp / 100)) + 1`. Defina a fórmula real na Fase 5 e documente-a ali — não aqui, para não travar o design antes de prototipar.

---

## 7. Requisitos não funcionais

- **Senhas:** BCrypt (`PasswordEncoder` do Spring Security), nunca texto plano em log.
- **JWT:** HS256, segredo mínimo 32 bytes vindo de variável de ambiente, expiração 7 dias (ajustável), sem refresh token no MVP — simplicidade deliberada, é possível adicionar depois.
- **Erros:** formato único `ProblemDetail` em toda a API, incluindo 404/409/500.
- **CORS:** desnecessário para app nativo, mas configure para permitir chamadas de `localhost` durante testes com Postman/Insomnia.
- **Observabilidade mínima:** Spring Boot Actuator (`/actuator/health`) ligado desde a Fase 1.
- **Rate limit externo:** cache de cotação evita estourar o free tier do brapi.dev; se múltiplos usuários pedem o mesmo ticker na janela de cache, uma única chamada externa serve a todos.

---

## 8. Decisões de DI no Android: por que manual antes de Hilt

Fazer `ServiceLocator` manual primeiro (Fase 7) e só trocar por Hilt depois (Fase 9, opcional) é deliberado: se você começa direto com Hilt, aprende a sintaxe de anotações do Hilt sem entender qual problema ele resolve. Construindo o grafo de dependências à mão uma vez, o valor de um framework de DI fica óbvio, e o Hilt (que é Dagger por baixo) passa a fazer sentido em vez de ser mágica.

Mesmo raciocínio se aplica ao lado do backend: o Spring já injeta por você desde o primeiro dia (é impossível evitar), mas vale entender manualmente o que `@Autowired`/injeção por construtor está fazendo antes de usar `@Service`/`@Repository` no piloto automático.

---

## 9. Checklist de "pronto" da spec

Esta spec está completa o suficiente para começar quando:
- [ ] Você concorda com os cortes de escopo da seção 1 (ou os ajusta).
- [ ] O modelo de dados da seção 4 cobre os RFs que você quer implementar primeiro.
- [ ] Você decidiu se quer JWT sem refresh (proposto) ou com refresh (mais realista, mais complexo).

Ver `PLANNING.md` para a sequência de implementação fase a fase.
