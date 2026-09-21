# Diagrama Entidade-Relacionamento

Modelo completo do backend, derivado do schema em `SPEC.md` §4. Este arquivo é a referência visual; o `SPEC.md` continua sendo a fonte de verdade para tipos exatos e DDL. Renderiza nativo no GitHub; no VS Code precisa da extensão "Markdown Preview Mermaid Support".

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        date birth_date
        int xp
        int level
        varchar league
        int login_streak
        timestamptz last_login_at
        timestamptz created_at
    }

    WALLETS {
        bigint id PK
        bigint user_id FK
        varchar category "conservative | moderate | aggressive"
        numeric cash_balance
        timestamptz created_at
    }

    POSITIONS {
        bigint id PK
        bigint wallet_id FK
        varchar ticker
        numeric quantity
        numeric avg_price
    }

    TRADES {
        bigint id PK
        bigint wallet_id FK
        varchar ticker
        varchar side "BUY | SELL"
        numeric quantity
        numeric price
        timestamptz executed_at
    }

    COURSES {
        bigint id PK
        varchar title
        text description
        int display_order
        bigint requires_course_id FK "nullable, auto-referencia"
    }

    LESSONS {
        bigint id PK
        bigint course_id FK
        varchar title
        varchar video_url
        int duration_seconds
        int display_order
    }

    USER_LESSON_PROGRESS {
        bigint user_id PK, FK
        bigint lesson_id PK, FK
        timestamptz completed_at
    }

    MISSIONS {
        bigint id PK
        varchar title
        text description
        varchar category "learning | practice"
        int required_level
        int xp_reward
    }

    USER_MISSIONS {
        bigint user_id PK, FK
        bigint mission_id PK, FK
        timestamptz completed_at
    }

    BADGES {
        bigint id PK
        varchar code UK
        varchar title
        text description
    }

    USER_BADGES {
        bigint user_id PK, FK
        bigint badge_id PK, FK
        timestamptz earned_at
    }

    NEWS {
        bigint id PK
        varchar title
        text content
        varchar source
        varchar category "stocks | crypto | economy | tech"
        timestamptz published_at
    }

    FRIENDSHIPS {
        bigint requester_id PK, FK
        bigint addressee_id PK, FK
        varchar status "pending | accepted"
        timestamptz created_at
    }

    USERS ||--o{ WALLETS : "possui (0..3, 1 por categoria)"
    WALLETS ||--o{ POSITIONS : "contem"
    WALLETS ||--o{ TRADES : "registra"

    COURSES ||--o{ LESSONS : "contem"
    COURSES |o--o{ COURSES : "requer (requires_course_id)"

    USERS ||--o{ USER_LESSON_PROGRESS : "completa"
    LESSONS ||--o{ USER_LESSON_PROGRESS : "e completada em"

    USERS ||--o{ USER_MISSIONS : "completa"
    MISSIONS ||--o{ USER_MISSIONS : "e completada em"

    USERS ||--o{ USER_BADGES : "conquista"
    BADGES ||--o{ USER_BADGES : "e conquistado em"

    USERS ||--o{ FRIENDSHIPS : "envia (requester_id)"
    USERS ||--o{ FRIENDSHIPS : "recebe (addressee_id)"
```

## Lendo o diagrama

- `||--o{` = um-para-muitos obrigatório do lado "um" (ex.: um usuário pode ter zero ou várias carteiras, mas toda carteira pertence a exatamente um usuário).
- `|o--o{` = um-para-muitos opcional dos dois lados (usado só em `COURSES` auto-referenciando `COURSES`, porque `requires_course_id` pode ser nulo — nem todo curso exige um anterior).
- `PK, FK` nas tabelas de junção (`USER_LESSON_PROGRESS`, `USER_MISSIONS`, `USER_BADGES`, `FRIENDSHIPS`) significa **chave primária composta**: os dois campos juntos formam o `PRIMARY KEY`, e cada um também é `FOREIGN KEY` para sua tabela de origem. É assim que se modela relação muitos-para-muitos em SQL — não existe `@ManyToMany` direto entre `USERS` e `LESSONS`, existe uma tabela no meio.

## Três padrões de relacionamento que você vai implementar em JPA

1. **Um-para-muitos simples** (`USERS` → `WALLETS`, `WALLETS` → `POSITIONS`/`TRADES`, `COURSES` → `LESSONS`): no lado "muitos", um campo `@ManyToOne` apontando pro pai. Normalmente não precisa do `@OneToMany` inverso a menos que você realmente precise navegar do pai pros filhos em memória — evite adicionar isso "por via das dúvidas", é fonte comum de N+1 query.
2. **Muitos-para-muitos via tabela de junção com atributo próprio** (`USER_LESSON_PROGRESS`, `USER_MISSIONS`, `USER_BADGES`): como cada linha tem um `completed_at`/`earned_at`, isso não é um `@ManyToMany` puro do JPA (que não suporta bem atributos extras na tabela de junção) — vira uma **entidade própria** com chave composta (`@EmbeddedId` ou `@IdClass`), com dois `@ManyToOne`. Você vai sentir essa diferença na prática na Fase 5.
3. **Auto-relacionamento opcional** (`COURSES.requires_course_id`): um `@ManyToOne` de `Course` para `Course` mesmo, campo anulável. É o mecanismo por trás do bloqueio de módulos (RF14).
4. **Duas FKs para a mesma tabela** (`FRIENDSHIPS.requester_id` e `.addressee_id`, ambas para `USERS`): também chave composta, mas com dois `@ManyToOne` diferentes apontando para a mesma entidade `User` — o JPA não confunde os dois desde que cada `@JoinColumn` tenha o nome certo.

## O que o diagrama não mostra

Regras de negócio não são estrutura de FK, então não aparecem aqui — estão em `SPEC.md` §6: uma carteira por categoria por usuário (`UNIQUE(user_id, category)`), saldo nunca negativo, XP/nível só mudam como efeito colateral de ação validada no servidor. O diagrama garante que os dados *podem* se relacionar dessa forma; as regras garantem que eles relacionam *corretamente*.
