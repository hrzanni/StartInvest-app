# StartInvest Java — Plano de Fases

Sequência de implementação para `SPEC.md`. Ordenado para introduzir um conceito novo de Java/Spring/Android por vez, sem empilhar dois problemas desconhecidos na mesma fase. Cada fase lista: objetivo, conceitos praticados, entregável concreto, critério de "pronto" e um risco/erro comum.

Estimativas de tempo assumem estudo part-time (5-10h/semana), nível: já sabe programar, primeira vez sério com Spring/Android. Ajuste livremente — o que importa é a ordem, não o cronograma.

---

## Fase 0 — Ambiente (0,5-1 dia)

**Objetivo:** ambiente funcionando antes de escrever qualquer lógica.

**Instalar:**
- JDK 21 (Temurin, via `winget install EclipseAdoptium.Temurin.21.JDK` ou instalador manual). Sua máquina hoje só tem JRE 8, sem `javac` — confirmado nesta sessão.
- Maven (ou usar o wrapper `mvnw` que o Spring Initializr gera).
- Docker Desktop (você já tem — confirmado). Vai rodar Postgres local via `docker-compose`.
- Android Studio (inclui Android SDK, emulador).
- Cliente HTTP para testar API manualmente: Insomnia ou Postman, ou extensão REST Client do VS Code.

**Entregável:**
- Projeto backend gerado via [start.spring.io](https://start.spring.io) (Maven, Java 21, Spring Boot 4.1.1, dependências: Web, JPA, Security, Validation, PostgreSQL Driver, Flyway) e importado na IDE.
- `docker-compose.yml` com um serviço Postgres subindo localmente.
- Projeto Android novo (Empty Views Activity, Java, não Kotlin — Android Studio tenta empurrar Kotlin por padrão, escolha Java explicitamente no wizard).

**Pronto quando:** `mvn spring-boot:run` sobe o backend em branco e responde em `/actuator/health` (adicione o Actuator já nesta fase); o app Android compila e mostra uma tela vazia no emulador.

**Erro comum:** esquecer que o Postgres do docker-compose precisa da mesma porta/credenciais que `application.yml` espera.

---

## Fase 1 — Backend: primeiro recurso de ponta a ponta (2-4 dias)

**Objetivo:** aprender o ciclo completo Controller → Service → Repository → Entity com o recurso mais simples do domínio: **News** (RF: seção "Notícias" da Home). Sem auth ainda.

**Conceitos praticados:** anatomia de um projeto Spring Boot, `@Entity`/`@Table`, `JpaRepository`, injeção de dependência por construtor, `@RestController`, DTOs de request/response (não exponha a entidade JPA direto), Bean Validation (`@NotBlank` etc.), migração Flyway (`V1__news.sql`), tratamento de erro com `ProblemDetail`.

**Entregável:**
- Tabela `news` via Flyway.
- `GET /api/news?category=` funcionando, com paginação simples (`Pageable`).
- Um endpoint de escrita (`POST /api/news`, sem auth por ora — é só para popular dados de teste) para não depender de inserir SQL manualmente.
- Teste de integração com `@SpringBootTest` + `MockMvc` cobrindo o `GET`.

**Pronto quando:** você consegue explicar, sem olhar código, por que o controller não deveria retornar a entidade `News` diretamente (acoplamento de serialização com schema de banco, campos internos vazando).

**Risco comum:** `open-in-view` ligado por padrão mascarando N+1 queries. Desligue (`spring.jpa.open-in-view: false`, já sugerido na spec) desde já e sinta a diferença quando esquecer um `JOIN FETCH`.

---

## Fase 2 — Autenticação (4-6 dias)

**Objetivo:** RF01-03. Registro, login, JWT, proteção de rotas.

**Conceitos praticados:** `PasswordEncoder`/BCrypt, `SecurityFilterChain` (API do Spring Security 6+, não a antiga `WebSecurityConfigurerAdapter` — está removida), filtro customizado de JWT, `UserDetailsService`, geração/validação de token com jjwt, `@ConfigurationProperties` para o segredo JWT, testes com `spring-security-test` (`@WithMockUser` e afins).

**Entregável:**
- Tabela `users`.
- `POST /api/auth/register`, `POST /api/auth/login`.
- Todas as rotas exceto `/api/auth/**` e `/api/news` exigindo `Authorization: Bearer`.
- `GET /api/users/me` retornando os dados do usuário autenticado (via `Authentication`/`SecurityContext`, não via parâmetro na URL — esse é o ponto didático: nunca confie em um `userId` vindo do cliente).

**Pronto quando:** uma chamada sem token a uma rota protegida retorna `401`, uma com token inválido/expirado também, e você entende o fluxo completo do filtro (request → `JwtAuthFilter` → `SecurityContextHolder` → controller).

**Risco comum:** guardar o JWT secret no código. Use variável de ambiente desde o primeiro commit, nem que seja só para praticar o hábito.

---

## Fase 3 — Domínio complexo: carteira e trades (5-8 dias)

**Objetivo:** RF07-11. Esta é a fase mais valiosa do backend — concorrência e transações de verdade.

**Conceitos praticados:** relacionamentos JPA (`@OneToMany`/`@ManyToOne` entre wallet/position/trade), `@Transactional` e seu escopo real, controle de concorrência (escolha um: lock otimista com `@Version` ou lock pessimista com `SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)`), exceções de negócio customizadas mapeadas para status HTTP via `@ExceptionHandler`/`@ControllerAdvice`, Testcontainers para testar a lógica de concorrência contra Postgres real (H2 não replica bem comportamento de lock).

**Entregável:**
- `POST /api/wallets`, `GET /api/wallets/{id}/positions`, `GET /api/wallets/{id}/trades`.
- `POST /api/wallets/{id}/trades` com as regras da seção 6 da spec (saldo, posição, preço vindo do servidor — nesta fase pode mockar o preço com um valor fixo, o brapi.dev entra na Fase 4).
- Um teste que dispara duas vendas concorrentes da mesma posição e comprova que só uma passa.

**Pronto quando:** o teste de concorrência falha se você remove o lock, e passa com ele. Esse experimento (quebrar de propósito) é o objetivo de aprendizado real da fase.

**Risco comum:** `@Transactional` em método privado ou chamado internamente pela mesma classe não funciona (proxy do Spring não intercepta self-invocation). Você vai esbarrar nisso — é intencional, é uma das pegadinhas mais citadas do Spring.

---

## Fase 4 — Integração externa e cache (2-3 dias)

**Objetivo:** RF07-09 completos, dados de mercado reais via brapi.dev.

**Conceitos praticados:** `RestClient` para chamar API externa, `@Cacheable`/`@CacheEvict` com Caffeine, `@ConfigurationProperties` para URL base/token, tratamento de timeout/erro de serviço externo (o brapi pode cair — decida um fallback: erro claro para o cliente, não uma exceção genérica 500).

**Entregável:**
- `GET /api/market/assets/{ticker}/quote` cacheado (TTL curto, ~60s).
- Trade da Fase 3 agora busca preço real em vez de mockado.

**Pronto quando:** você consegue provar via log que uma rajada de 10 requisições ao mesmo ticker gera 1 chamada real ao brapi.dev, não 10.

---

## Fase 5 — Gamificação (3-5 dias)

**Objetivo:** RF20-22, RF12-15 (vídeo-aulas com bloqueio), RF05 (missões diárias).

**Conceitos praticados:** `ApplicationEventPublisher`/`@EventListener` (desacoplar "trade executado" de "conceder XP" via evento de domínio em vez de chamar o serviço de XP direto do serviço de trade), consultas JPQL com `JOIN`, definição e teste unitário puro da fórmula de XP/nível (sem Spring nenhum — é lógica pura, teste com JUnit simples).

**Entregável:**
- Tabelas `courses`, `lessons`, `user_lesson_progress`, `missions`, `user_missions`, `badges`, `user_badges`.
- Endpoints da seção 5.4 e 5.7 da spec.
- Cálculo de `locked` nos cursos, cálculo de nível a partir de XP.

**Pronto quando:** completar uma aula ou um trade dispara XP através de um evento (você vê no debugger o listener sendo chamado assincronamente ou na mesma thread, dependendo de como configurar), não de uma chamada direta acoplada.

---

## Fase 6 — Social e ranking (2-4 dias)

**Objetivo:** RF16-19.

**Conceitos praticados:** paginação (`Pageable`/`Page<T>`), modelagem de relação assimétrica (pedido de amizade com estado `pending`/`accepted`), evitar N+1 em consultas de ranking com `JOIN FETCH` ou projeção DTO direta na query.

**Entregável:** endpoints da seção 5.6.

**Pronto quando:** o ranking com 1000 usuários fake (gere com um script SQL ou `@Bean CommandLineRunner` de seed) não faz 1000 queries — confirme olhando o log de SQL do Hibernate (`spring.jpa.show-sql: true` temporariamente).

---

## Backend: ponto de checagem

Ao fim da Fase 6, a API cobre os 22 RFs. Rode a suíte de testes inteira, confira cobertura das regras de negócio críticas (seção 6 da spec), e só então comece o Android. Não vale a pena migrar para o cliente com uma API instável — o custo de retrabalho no app é maior.

---

## Fase 7 — Android: fundação (4-6 dias)

**Objetivo:** esqueleto do app conectado à API real.

**Conceitos praticados:** ciclo de vida de Activity/Fragment, Retrofit (interface de serviço, converter Gson/Moshi), `OkHttp Interceptor` para anexar o JWT automaticamente, armazenamento seguro do token (`EncryptedSharedPreferences`), Navigation Component com `BottomNavigationView` (Home, Praticar, Aprender, Ranking, Perfil — mapeando o `AppShell` do original), `ServiceLocator` manual para DI (ver spec §8).

**Entregável:**
- Tela de login/registro funcional contra o backend real (rodando local, emulador acessa via `10.0.2.2` em vez de `localhost`).
- Navegação entre as 5 seções principais, ainda com conteúdo vazio/mock.
- Token persistido entre reinícios do app.

**Pronto quando:** fechar e reabrir o app mantém o usuário logado, e uma chamada autenticada (`GET /users/me`) funciona fim a fim.

**Risco comum:** tentar acessar `localhost` do emulador achando que é a máquina host — não é, é o próprio emulador. Use `10.0.2.2` ou um dispositivo físico na mesma rede.

---

## Fase 8 — Android: features (10-15 dias, a maior fase)

Construa nesta ordem, cada item reaproveita padrão do anterior:

1. **Home (RF04-06):** dados de `GET /users/me` + `GET /missions/daily`, `RecyclerView` simples.
2. **Vídeo-aulas (RF12-15):** lista de módulos com cadeado, player de vídeo (`VideoView` ou ExoPlayer se quiser ir além do mínimo), progresso.
3. **Simulador/Jogos (RF07-11):** a tela mais complexa — lista de ativos por categoria, tela de compra/venda, gráfico de posição com MPAndroidChart.
4. **Notícias:** lista simples, sem auth.
5. **Ranking e amigos (RF16-19):** lista paginada, busca de amigo por e-mail, tela de comparação.
6. **Perfil:** XP, nível, badges conquistados.

**Conceitos praticados:** `ViewModel` + `LiveData` por tela, `RecyclerView.Adapter` com `DiffUtil`, Room para cache offline de cursos/missões (opcional mas recomendado para praticar), tratamento de erro de rede na UI (estado loading/error/success — o mesmo padrão que `AuthLoading`/`AuthError` do BLoC original, agora como `sealed`-like via classes `Resource<T>` em Java).

**Pronto quando:** as 6 seções acima funcionam fim a fim contra o backend real, incluindo os estados de erro (sem internet, sessão expirada → volta para login).

---

## Fase 9 — Testes, polimento e stretch goals (variável)

**Obrigatório:**
- Testes de `ViewModel` com JUnit + Mockito (mock do repository).
- Teste Espresso do fluxo crítico: login → compra de ativo → ver posição atualizada.
- `Dockerfile` do backend + `docker-compose.yml` unindo backend e Postgres para rodar com um comando.
- README raiz descrevendo como subir os dois lados.

**Stretch (escolha conforme o que quer aprender a mais):**
- Trocar `ServiceLocator` manual por **Hilt** (ver spec §8 — só depois de já ter sentido a dor do manual).
- Refresh token em vez de JWT de vida longa.
- FCM puro (sem Firebase Auth/Firestore) para notificação de missão diária.
- Deploy do backend em Render/Railway free tier e apontar o app para produção.
- CI no GitHub Actions rodando `mvn test` no push.

---

## Ordem resumida

| Fase | Foco | Duração estimada |
|---|---|---|
| 0 | Ambiente | 0,5-1 dia |
| 1 | Backend: recurso simples (News) | 2-4 dias |
| 2 | Backend: auth/JWT | 4-6 dias |
| 3 | Backend: carteira/trades/concorrência | 5-8 dias |
| 4 | Backend: integração externa/cache | 2-3 dias |
| 5 | Backend: gamificação/eventos | 3-5 dias |
| 6 | Backend: social/ranking | 2-4 dias |
| 7 | Android: fundação | 4-6 dias |
| 8 | Android: features | 10-15 dias |
| 9 | Testes/polimento/stretch | variável |

Total aproximado: 8-12 semanas part-time até um MVP completo e testado.

## Como usar este plano

Não pule fases para "ir mais rápido nas telas" — a ordem existe para não empilhar Spring Security desconhecido em cima de JPA desconhecido em cima de Docker desconhecido na mesma semana. Se travar mais de um dia inteiro numa fase, é sinal de que o conceito da fase anterior não consolidou; vale voltar e reforçar antes de seguir.
