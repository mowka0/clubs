# Progress Log — Клубы (Clubs) Telegram Mini App

> **Файл задач:** `tasks.json` (43 задачи)
> **PRD:** `PRD-Clubs.md`

## Формат записи
```
## [TASK-XXX] Краткое описание
- **Дата:** YYYY-MM-DD
- **Статус:** done / in_progress / blocked
- **Что сделано:** ...
- **Проблемы:** ...
- **Следующие шаги:** ...
```

---

## [TASK-001] Инициализация проекта: Kotlin + Spring Boot 3 + Gradle KTS
- **Дата:** 2026-03-06
- **Статус:** in_progress (файлы созданы, требуется bootstrap)
- **Что сделано:**
  - `backend/build.gradle.kts` — Spring Boot 3.4.3, Kotlin 2.1.10, jOOQ (nu.studer plugin), Flyway, Redis, JWT (jjwt 0.12.6), bucket4j
  - `backend/settings.gradle.kts`, `backend/gradlew`, `backend/gradle/wrapper/gradle-wrapper.properties` (Gradle 8.12.1)
  - `backend/src/main/kotlin/com/clubs/ClubsApplication.kt` — main entry point
  - `backend/src/main/kotlin/com/clubs/config/SecurityConfig.kt` — permit /actuator/**, /api/auth/**, требует JWT для /api/**
  - `backend/src/main/resources/application.yml` — dev/prod profiles, DB + Redis + actuator health
  - `docker-compose.yml` — PostgreSQL 16, Redis 7, MinIO
  - `backend/src/main/resources/db/migration/V1-V9__*.sql` — все 9 Flyway миграций (users, clubs, memberships, applications, events, event_responses, user_club_reputation, transactions, invite_links)
  - Структура пакетов: config/, auth/, club/, event/, membership/, reputation/, payment/, notification/, bot/, scheduler/
  - `.gitignore`, `setup.sh`
- **Проблемы:**
  - gradle-wrapper.jar требует загрузки (`curl` заблокирован в текущем режиме) — пользователь должен запустить `bash setup.sh`
  - git init заблокирован — нужно запустить вручную
- **Следующие шаги:**
  1. `bash setup.sh` — загрузит gradle-wrapper.jar, chmod gradlew, git init
  2. `docker-compose up -d`
  3. `cd backend && ./gradlew build`
  4. `./gradlew bootRun`, затем `curl http://localhost:8080/actuator/health`
  5. После успеха пометить TASK-001 как done и перейти к TASK-003 (jOOQ codegen)

---

## [TASK-003] jOOQ codegen: генерация Kotlin-классов из схемы БД
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - Применены все 9 Flyway-миграций напрямую через psql (таблицы ранее отсутствовали в DB)
  - Перезапущен docker-compose для активации port binding 5432:5432 (порт не был проброшен)
  - `./gradlew generateJooq` — успешная генерация 50 Kotlin-файлов в `backend/src/generated/jooq/`
  - Сгенерированы для каждой таблицы: Table, Record, DAO, POJO (9 таблиц + invite_links ✓)
  - Сгенерированы 9 enum-типов: ApplicationStatus, ClubAccessType, ClubCategory, EventStatus, FinalStatus, MembershipRole, MembershipStatus, TransactionType, VoteStatus
  - `./gradlew build` — BUILD SUCCESSFUL
  - `backend/src/generated/` добавлен в `.gitignore` (решение: не коммитить)
- **Проблемы:**
  - docker-compose был запущен без проброса портов (порт 5432 не слушался на localhost) — решено пересозданием контейнеров
  - Задача называется `generateJooq` (не `generateMainJooqSchemaSource`) — alias `jooqCodegen` → `generateJooq`

---

## [TASK-004] Аутентификация: Telegram initData, JWT, Security, RateLimit, GlobalExceptionHandler
- **Дата:** 2026-03-06
- **Статус:** in_progress (код написан, требуется запуск bootstrap для сборки и тестирования)
- **Что сделано:**
  - `auth/TelegramInitDataValidator.kt` — HMAC-SHA256 валидация Telegram initData (key=HMAC("WebAppData", botToken), compare data_check_string)
  - `auth/JwtService.kt` — генерация/валидация JWT (jjwt 0.12.6): user_id (subject), telegram_id (claim), iat, exp (24ч)
  - `auth/AuthDtos.kt` — AuthRequest, AuthResponse, AuthUserDto
  - `auth/UserRepository.kt` — jOOQ DSLContext (без codegen): findByTelegramId, createOrUpdate, findById
  - `auth/AuthController.kt` — POST /api/auth/telegram: валидация initData → upsert user → JWT
  - `auth/JwtAuthenticationFilter.kt` — OncePerRequestFilter: извлекает Bearer токен, создаёт UsernamePasswordAuthenticationToken
  - `auth/UserController.kt` — GET /api/users/me, GET /api/users/{id}
  - `config/RateLimitFilter.kt` — bucket4j 8.10.1: 100 req/min per IP глобально, 30 req/min per user на mutation (POST/PUT/DELETE/PATCH)
  - `config/GlobalExceptionHandler.kt` — @RestControllerAdvice: NotFoundException(404), AccessDeniedException(403), ValidationException(400), ConflictException(409), generic 500
  - `config/SecurityConfig.kt` — обновлён: добавлены JwtAuthenticationFilter и RateLimitFilter, HttpStatusEntryPoint(401)
- **Проблемы:**
  - Не удалось запустить сборку — `gradle-wrapper.jar` отсутствует, `chmod` и `curl` требуют ручного одобрения
  - Требуется bootstrap: `bash setup.sh` → `docker-compose up -d` → `cd backend && ./gradlew build`
- **Следующие шаги:**
  1. Запустить `bash setup.sh` (утвердить команды chmod, curl, git init)
  2. `docker-compose up -d`
  3. `cd backend && ./gradlew build` — убедиться в успешной сборке
  4. `./gradlew test` — запустить тесты
  5. После успешного прохождения тестов пометить TASK-004 как done
  6. При готовности TASK-003 (jOOQ codegen): UserRepository можно рефакторить на сгенерированные классы

---

## [TASK-036] Frontend: React + TypeScript + Vite + @telegram-apps/sdk v2
- **Дата:** 2026-03-06
- **Статус:** in_progress (код написан, требуется `npm install && npm run dev` для верификации)
- **Что сделано:**
  - `frontend/package.json` — React 19, Vite 6, TypeScript 5, @telegram-apps/sdk-react v3, @telegram-apps/telegram-ui v2, react-router-dom v7, zustand v5
  - `frontend/vite.config.ts` — Vite конфиг с proxy на `localhost:8080` для `/api`
  - `frontend/tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json` — TypeScript конфиги
  - `frontend/src/telegram/sdk.ts` — инициализация SDK v2 (`init()`, `viewport.expand()`, `miniApp.setHeaderColor()`), mock-режим при ошибке инициализации (вне Telegram), `getInitData()`, `getStartParam()`
  - `frontend/src/telegram/BackButtonHandler.tsx` — показ/скрытие BackButton кроме Discovery, navigate(-1) по нажатию
  - `frontend/src/telegram/DeepLinkHandler.tsx` — парсинг startapp параметра: `invite_X → /invite/:code`, `event_X → /events/:id`, `club_X → /clubs/:id`
  - `frontend/src/router.tsx` — все 8 роутов, code splitting через React.lazy + Suspense для /organizer, /clubs/:id/interior, /events/:id, /invite/:code
  - `frontend/src/App.tsx` — AppRoot (Telegram UI тема), BrowserRouter, BottomTabBar, BackButtonHandler, DeepLinkHandler
  - `frontend/src/main.tsx` — точка входа, импорт CSS telegram-ui, `initTelegram()`, `mountBackButton()`
  - `frontend/src/components/BottomTabBar.tsx` — Tabbar + TabbarItem из telegram-ui, 3 таба: Discovery / Мои клубы / Профиль
  - `frontend/src/store/` — Zustand сторы: useAuthStore, useClubsStore, useEventsStore
  - `frontend/src/pages/` — заглушки всех страниц
  - `frontend/src/vite-env.d.ts` — типы для VITE_ переменных
  - `frontend/.env.development` — mock initData для локальной разработки
  - `.gitignore` — добавлены frontend/node_modules/, frontend/dist/, frontend/.env.local
  - `backend/build.gradle.kts` — добавлен alias-таск `jooqCodegen` → `generateMainJooqSchemaSource`
- **Проблемы:**
  - `npm install` требует ручного одобрения — пользователь должен выполнить `npm install` в `frontend/`
  - `npm run dev` не запускался — нужна верификация в браузере
- **Следующие шаги:**
  1. `cd frontend && npm install`
  2. `npm run dev`
  3. Открыть http://localhost:5173 — проверить загрузку в mock-режиме
  4. Проверить переключение табов и роутинг
  5. Проверить deep-link: добавить `?startapp=event_123` в URL → редирект на /events/123
  6. После успешного прохождения всех тестов пометить TASK-036 как done
  7. Перейти к TASK-037 (API-клиент, AuthProvider, useAuth hook)

---

## Инициализация проекта
- **Дата:** 2026-03-05
- **Статус:** tasks.json сгенерирован
- **Что сделано:** PRD-Clubs.md декомпозирован на 43 атомарные задачи с acceptance criteria и test steps
- **Архитектурные решения:**
  - Бот НЕ создаёт Telegram-группы (ограничение Bot API) — организатор создаёт сам, бот привязывается
  - Stage 2 (гонка за места) — атомарный UPDATE ... WHERE confirmed_count < limit RETURNING для race condition protection
  - Membership flow до TASK-033 работает без оплаты (status=active напрямую)
  - PaymentService за интерфейсом для возможной замены Telegram Stars
  - Все миграции создаются одним пакетом в TASK-002 (9 таблиц включая invite_links), jOOQ codegen — один раз в TASK-003
  - Загрузка аватара/обложки опциональна — не блокирует создание клуба (TASK-010 не зависит от TASK-006)
  - Поле locked_subscription_price в memberships для grandfathering цены при продлении подписки
  - Rate limiting (bucket4j) и GlobalExceptionHandler включены в TASK-004 (security)
  - Frontend: @telegram-apps/sdk v2, bottom tab bar, BackButton, deep-links, code splitting
  - Real-time для MVP: optimistic updates + polling 30 сек (без WebSocket/SSE)
  - Определение города: navigator.geolocation → IP-based fallback → ручной ввод

---
