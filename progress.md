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
- **Статус:** in_progress (код реализован, unit-тесты проходят, требуется интеграционное тестирование)
- **Что сделано:**
  - `auth/TelegramInitDataValidator.kt` — HMAC-SHA256 валидация Telegram initData (key=HMAC("WebAppData", botToken), compare data_check_string)
  - `auth/JwtService.kt` — генерация/валидация JWT (jjwt 0.12.6): user_id (subject), telegram_id (claim), iat, exp (24ч)
  - `auth/AuthDtos.kt` — AuthRequest, AuthResponse, AuthUserDto
  - `auth/UserRepository.kt` — jOOQ DSLContext: findByTelegramId, createOrUpdate, findById
  - `auth/AuthController.kt` — POST /api/auth/telegram: валидация initData → upsert user → JWT
  - `auth/JwtAuthenticationFilter.kt` — OncePerRequestFilter: извлекает Bearer токен, создаёт UsernamePasswordAuthenticationToken
  - `auth/UserController.kt` — GET /api/users/me, GET /api/users/{id}
  - `config/RateLimitFilter.kt` — bucket4j 8.10.1: 100 req/min per IP глобально, 30 req/min per user на mutation (POST/PUT/DELETE/PATCH)
  - `config/GlobalExceptionHandler.kt` — @RestControllerAdvice: NotFoundException(404), AccessDeniedException(403), ValidationException(400), ConflictException(409), generic 500
  - `config/SecurityConfig.kt` — JwtAuthenticationFilter и RateLimitFilter, HttpStatusEntryPoint(401)
  - **Unit-тесты**: `JwtServiceTest.kt` (8 тестов), `TelegramInitDataValidatorTest.kt` (6 тестов), `GlobalExceptionHandlerTest.kt` (6 тестов) — все 23 теста проходят
  - `./gradlew build` — BUILD SUCCESSFUL
  - Исправлен баг в тесте: `claims["telegram_id", Long::class.java]` → `(claims["telegram_id"] as? Number)?.toLong()` (JJWT хранит маленькие Long как Integer)
- **Проблемы:**
  - Интеграционные test_steps (end-to-end с запущенным сервером) требуют `docker-compose up` + `./gradlew bootRun`
- **Следующие шаги:**
  1. `docker-compose up -d` + `cd backend && ./gradlew bootRun`
  2. Выполнить интеграционные test_steps (curl к /api/auth/telegram, /api/users/me, rate limiting)
  3. После подтверждения — пометить TASK-004 как done
  4. Перейти к TASK-007 (UserRepository с jOOQ codegen) или TASK-009 (ClubRepository)

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

## [TASK-014] Membership: jOOQ-репозиторий + MembershipService
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `membership/MembershipDto.kt` — data class: id, userId, clubId, role, status, joinedAt, subscriptionExpiresAt, lockedSubscriptionPrice, createdAt, updatedAt
  - `membership/MembershipRepository.kt` — jOOQ DSLContext репозиторий:
    - `create(userId, clubId, role, status, lockedSubscriptionPrice)` — вставка в MEMBERSHIPS
    - `findByUserAndClub(userId, clubId) -> MembershipDto?`
    - `findByClub(clubId) -> List<MembershipDto>`
    - `findActiveCountByClub(clubId) -> Int` — кол-во active участников
    - `updateStatus(userId, clubId, status)` — смена статуса
  - `membership/MembershipService.kt` — Spring @Service:
    - `joinClub(userId, clubId)` — проверки: клуб существует, не участник, не заполнен; создаёт membership со status=active, lockedSubscriptionPrice из клуба
    - `isActiveMember(userId, clubId)` — active/grace_period → true; cancelled → true если subscriptionExpiresAt > now(); expired/null → false
    - `leaveClub(userId, clubId)` — updateStatus → cancelled
  - `MembershipServiceTest.kt` — 14 unit-тестов через mockito-kotlin: все проходят
  - `./gradlew build` — BUILD SUCCESSFUL
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-019 — EventRepository (deps: TASK-003 ✅, TASK-009 ✅)
  2. TASK-015 — Membership flow REST (deps: TASK-011 ❌, TASK-014 ✅, TASK-013 ❌)
  3. TASK-016 — Application репозиторий + сервис (dep: TASK-014 ✅)

---

## [TASK-008] User: REST-контроллер — PUT /api/users/me, GET /api/geo/city
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `user/UserDto.kt` — добавлен `UpdateUserDto(city, firstName, lastName, avatarUrl)`
  - `user/UserRepository.kt` — добавлен `updateProfile(id, dto)`: частичное обновление (только непустые поля), возвращает обновлённый `UserDto?`
  - `user/UserService.kt` — добавлен `updateProfile(id, dto)`: вызывает репозиторий, бросает NotFoundException если пользователь не найден
  - `auth/UserController.kt` — добавлен `PUT /api/users/me`: извлекает userId из JWT, вызывает `updateProfile`
  - `user/GeoController.kt` — `GET /api/geo/city`: определяет IP из `X-Forwarded-For` или `remoteAddr`, запрашивает `ip-api.com/json/{ip}`, возвращает `{city: "..."}`; для локальных IP (127.0.0.1, 192.168.x, 10.x) возвращает `{city: null}`; ошибки логируются, не бросают исключений
  - `./gradlew build` — BUILD SUCCESSFUL
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-013 — Invite-ссылки для приватных клубов (deps: TASK-010 ✅)
  2. TASK-015 — Membership flow REST (deps: TASK-011 ✅, TASK-014 ✅, TASK-013 ❌)
  3. TASK-016 — Application репозиторий + сервис (dep: TASK-014 ✅)
  4. TASK-021 — EventResponse репозиторий + сервис (deps: TASK-019 ✅, TASK-014 ✅)

---

## [TASK-015] Membership: flow вступления — POST /join, POST /invite/{code}/join, POST /leave
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `membership/MembershipController.kt` — Spring @RestController на `/api/clubs`:
    - `POST /api/clubs/{id}/join` — проверяет существование клуба, `accessType == "open"` (иначе 400 с советом использовать /apply), вызывает `MembershipService.joinClub()`, возвращает `JoinClubResponse(membership, telegramInviteLink=null)`
    - `POST /api/clubs/invite/{code}/join` — валидирует код через `InviteLinkService.validateAndGetClub()`, создаёт membership, деактивирует одноразовую ссылку через `consumeLink()`
    - `POST /api/clubs/{id}/leave` — вызывает `leaveClub()`, возвращает обновлённый `MembershipDto` со статусом cancelled
  - `membership/MembershipService.kt` — добавлен метод `getMembership(userId, clubId)` для получения membership после выхода
  - `JoinClubResponse` — data class: `membership: MembershipDto`, `telegramInviteLink: String?` (null до TASK-030)
  - Обработка конфликтов: `ConflictException(409)` при повторном вступлении, `ValidationException(400)` при полном клубе или не-открытом типе доступа, `NotFoundException(404)` при невалидной/использованной invite-ссылке
  - `./gradlew build` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-016 — Application репозиторий + сервис (dep: TASK-014 ✅)
  2. TASK-021 — EventResponse репозиторий + сервис (deps: TASK-019 ✅, TASK-014 ✅)

---

## [TASK-016] Application: репозиторий + сервис для заявок в закрытые клубы
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `application/ApplicationDto.kt` — `ApplicationDto`, `SubmitApplicationRequest` data classes
  - `application/ApplicationRepository.kt` — jOOQ DSLContext репозиторий:
    - `create(userId, clubId, answerText) -> ApplicationDto` — статус pending
    - `findById(id) -> ApplicationDto?`
    - `findByUserAndClub(userId, clubId) -> ApplicationDto?` — последняя заявка
    - `findByClubId(clubId, status?) -> List<ApplicationDto>` — с опциональным фильтром по статусу
    - `findPendingByUserAndClub(userId, clubId) -> ApplicationDto?` — активная pending заявка
    - `updateStatus(id, status, rejectionReason?)` — смена статуса
    - `findAllByUser(userId) -> List<ApplicationDto>` — все заявки пользователя
    - `findPendingOlderThan(cutoff) -> List<ApplicationDto>` — для автоотклонения (TASK-018)
  - `application/ApplicationService.kt` — Spring @Service:
    - `submitApplication(userId, clubId, answerText)` — проверки: клуб существует, не участник, нет pending заявки
    - `approveApplication(applicationId, organizerId)` — проверка организатора, создаёт membership через `MembershipService.joinClub`, затем статус approved
    - `rejectApplication(applicationId, organizerId, reason?)` — проверка организатора, статус rejected
    - `getClubApplications(clubId, requesterId, status?)` — только для организатора
    - `getMyApplications(userId)` — все заявки пользователя
  - `ApplicationServiceTest.kt` — 10 unit-тестов через mockito-kotlin: все проходят
  - `./gradlew build && ./gradlew test --rerun-tasks` — BUILD SUCCESSFUL
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-017 — Application REST-контроллер (deps: TASK-004 in_progress, TASK-016 ✅)
  2. TASK-018 — Application scheduler автоотклонения 48ч (dep: TASK-016 ✅)
  3. TASK-021 — EventResponse репозиторий + сервис (deps: TASK-019 ✅, TASK-014 ✅)

---

## [TASK-013] Club: invite-ссылки для приватных клубов
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `invite/InviteLinkDto.kt` — `InviteLinkDto`, `GenerateInviteLinkRequest`, `InviteLinkResponse`
  - `invite/InviteLinkRepository.kt` — jOOQ DSLContext репозиторий:
    - `create(clubId, code, isSingleUse, createdBy) -> InviteLinkDto`
    - `findByCode(code) -> InviteLinkDto?`
    - `markUsed(code)` — устанавливает `is_used=true`, `used_at=now()`
  - `invite/InviteLinkService.kt` — Spring @Service:
    - `generateLink(clubId, userId, isSingleUse)` — проверяет роль организатора через MembershipRepository, генерирует UUID-код, сохраняет ссылку, возвращает `InviteLinkResponse` с полным `t.me/...` URL
    - `validateAndGetClub(code)` — проверяет существование и неиспользованность одноразовых ссылок, возвращает ClubDto
    - `consumeLink(code)` — деактивирует одноразовую ссылку (вызывается при вступлении в TASK-015)
  - `club/ClubController.kt` — добавлены два эндпоинта:
    - `GET /api/clubs/invite/{code}` — получение данных клуба по invite-коду (404 для невалидных/использованных)
    - `POST /api/clubs/{id}/invite-link` → 201: генерация ссылки (только организатор, 403 для других)
  - `app.bot-username` конфигурируется через `application.yml` (дефолт: `clubsapp`)
  - `./gradlew build && ./gradlew test` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-015 — Membership flow REST (POST /join, /leave, /invite/{code}/join) — все зависимости ✅ (TASK-011 ✅, TASK-014 ✅, TASK-013 ✅)
  2. TASK-016 — Application репозиторий + сервис (dep: TASK-014 ✅)
  3. TASK-021 — EventResponse репозиторий + сервис (deps: TASK-019 ✅, TASK-014 ✅)

---

## [TASK-020] Event: сервис + контроллер + scheduler
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `event/EventService.kt` — Spring @Service:
    - `createEvent(clubId, userId, ...)` — проверка организатора, валидация полей, создаёт событие через EventRepository
    - `updateEvent(eventId, userId, dto)` — проверка организатора, проверка статуса (upcoming/stage_1), частичное обновление
    - `cancelEvent(eventId, userId)` — проверка организатора, статус → cancelled
    - `getEvent(eventId)` — детальная информация
    - `getClubEvents(clubId, userId, upcoming)` — список событий, только для активных участников
    - `requireOrganizer()` — private: проверяет membership.role == organizer через MembershipRepository
    - `requireActiveMember()` — private: проверяет status active/grace_period
    - `validateEventFields()` — private: event_datetime > now(), participantLimit > 0, votingDaysBefore 1-14, voting period не в прошлом
  - `event/EventController.kt` — Spring @RestController:
    - `POST /api/clubs/{clubId}/events` → 201: создание события (только организатор)
    - `GET /api/clubs/{clubId}/events?upcoming=true` → 200: список событий (только участники)
    - `GET /api/events/{id}` → 200/404: детальная информация
    - `PUT /api/events/{id}` → 200/403/400/404: обновление (только организатор, только upcoming/stage_1)
    - `DELETE /api/events/{id}/cancel` → 204/403/404: отмена
  - `event/EventScheduler.kt` — Spring @Component со @Scheduled:
    - `transitionUpcomingToStage1()` — каждые 15 минут (`fixedDelay = 15 * 60 * 1000L`)
    - Находит все upcoming события где `event_datetime - voting_opens_days_before * 24h <= now()`
    - Меняет статус на stage_1, логирует количество переходов
  - `./gradlew build` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-021 — EventResponse: репозиторий + сервис голосования (deps: TASK-019 ✅, TASK-014 ✅)
  2. TASK-008 — User REST-контроллер (PUT /api/users/me, GET /api/geo/city) — deps: TASK-004 in_progress, TASK-007 ✅
  3. TASK-013 — Invite-ссылки для приватных клубов — deps: TASK-010 ✅

---

## [TASK-019] Event: jOOQ-репозиторий + DTO
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `event/EventDto.kt` — data classes: `EventDto` (все поля таблицы events), `CreateEventDto`, `UpdateEventDto`
  - `event/EventRepository.kt` — Spring @Repository с jOOQ DSLContext:
    - `create(dto: CreateEventDto) -> EventDto` — вставка с gen_random_uuid
    - `findById(id: UUID) -> EventDto?`
    - `findByClubId(clubId, upcoming: Boolean) -> List<EventDto>` — upcoming: ASC по event_datetime, past: DESC
    - `findUpcomingByClub(clubId)` — алиас для `findByClubId(upcoming=true)`
    - `updateStatus(id, EventStatus)` — смена статуса события
    - `update(id, UpdateEventDto)` — частичное обновление
    - `toDto()` — private extension function для маппинга jOOQ Record → EventDto
  - `EventRepositoryTest.kt` — 4 unit-теста: поля EventDto, дефолты CreateEventDto, null-поля UpdateEventDto, частичный UpdateEventDto
  - `./gradlew build` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-020 — Event сервис + контроллер (deps: TASK-004 in_progress, TASK-019 ✅, TASK-014 ✅)
  2. TASK-008 — User REST (PUT /api/users/me, GET /api/geo/city) — deps: TASK-004, TASK-007

---

## [TASK-011] Club: REST-контроллер — CRUD + каталог + список участников
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `club/ClubController.kt` — Spring @RestController:
    - `POST /api/clubs` → 201: создание клуба через ClubService (только авторизованные)
    - `GET /api/clubs` → 200: каталог с фильтрами (city, category, accessType, priceMin, priceMax, sizeMin, sizeMax, search), пагинация (page, size), сортировка (newest, price_asc, price_desc), ответ `PagedClubsResponse`
    - `GET /api/clubs/{id}` → 200/404: детальная карточка клуба
    - `PUT /api/clubs/{id}` → 200/403/404: обновление (только владелец)
    - `DELETE /api/clubs/{id}` → 204/403/404: деактивация soft-delete (только владелец)
    - `GET /api/clubs/{id}/members` → 200/403/404: список участников с именем, аватаром, joined_at, reliabilityIndex (только для участников)
  - `club/ClubDtos.kt` — добавлены `ClubMemberDto` (userId, username, firstName, lastName, avatarUrl, role, joinedAt, reliabilityIndex) и `PagedClubsResponse`
  - `club/ClubRepository.kt` — `findAll` обновлён: поддержка пагинации (limit/offset) и сортировки; добавлен `countAll` для total count
  - `membership/MembershipRepository.kt` — добавлен `findMembersWithUsers(clubId)`: JOIN MEMBERSHIPS + USERS + LEFT JOIN USER_CLUB_REPUTATION
  - `./gradlew build` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-008 — User REST-контроллер (PUT /api/users/me, GET /api/geo/city) — deps: TASK-004 (in_progress), TASK-007 (done)
  2. TASK-015 — Membership flow REST (POST /join, /leave, invite join) — deps: TASK-011 ✅, TASK-014 ✅, TASK-013 ❌
  3. TASK-013 — Invite-ссылки для приватных клубов — deps: TASK-010 ✅

---

## [TASK-010] Club: ClubService — бизнес-логика создания клуба
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `storage/FileStorageService.kt` — интерфейс `uploadFile`, `getFileUrl`, `deleteFile` (используется в TASK-006)
  - `club/ClubDtos.kt` — добавлен `MonthlyRevenueDto(totalRevenue, organizerShare, platformShare)`
  - `club/ClubService.kt` — Spring @Service:
    - `createClub()` — валидация (name <=60, description <=500, memberLimit 10-80), проверка лимита (max 10 клубов), опциональная загрузка аватара через `FileStorageService?`, создание клуба, автосоздание organizer membership через jOOQ DSLContext
    - `calculateRevenue(price, memberLimit)` — 80/20 split: organizer=total*0.8, platform=total*0.2
    - `createOrganizerMembership()` — private, вставляет в MEMBERSHIPS role=organizer, status=active, locked_subscription_price
  - `build.gradle.kts` — добавлена зависимость `org.mockito.kotlin:mockito-kotlin:5.4.0`
  - `ClubServiceTest.kt` — 17 unit-тестов через mockito-kotlin: все проходят
  - `./gradlew build` — BUILD SUCCESSFUL
- **Проблемы:**
  - Kotlin + Mockito: `any()` вызывает `NullPointerException` для non-null Kotlin параметров — решено через `mockito-kotlin:5.4.0`
- **Следующие шаги:**
  1. TASK-011 — Club REST-контроллер (зависит от TASK-004 in_progress и TASK-010 done)
  2. TASK-014 — MembershipRepository + сервис

---

## [TASK-007] User: jOOQ-репозиторий, UserDto, UserService
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `user/UserDto.kt` — data class с полными полями: id (UUID), telegramId, username, firstName, lastName, avatarUrl, city, createdAt, updatedAt
  - `user/UserRepository.kt` — jOOQ DSLContext с использованием generated `USERS` table: `findByTelegramId`, `createOrUpdate`, `findById`, private `toDto()` extension
  - `user/UserService.kt` — Spring @Service: `findByTelegramId`, `createOrUpdate`, `findById`, `getById` (throws NotFoundException)
  - `auth/AuthDtos.kt` — AuthResponse теперь использует UserDto из user пакета, AuthUserDto удалён
  - `auth/AuthController.kt` — инжектирует UserService вместо UserRepository, user.id теперь UUID напрямую
  - `auth/UserController.kt` — инжектирует UserService, возвращает UserDto
  - `auth/UserRepository.kt` — удалён (заменён на user/UserRepository.kt)
  - `./gradlew build` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-008 — добавить PUT /api/users/me и GET /api/geo/city (зависит от TASK-004 → нужен завершённый статус)
  2. TASK-010 — ClubService (валидация, лимит 10 клубов, авто-membership organizer)

---

## [TASK-009] Club: jOOQ-репозиторий с фильтрами, поиском, CRUD
- **Дата:** 2026-03-06
- **Статус:** done
- **Что сделано:**
  - `club/ClubDtos.kt` — `ClubDto`, `CreateClubDto`, `UpdateClubDto`, `ClubFilters` data classes
  - `club/ClubRepository.kt` — jOOQ DSLContext репозиторий:
    - `create(dto) -> ClubDto`, `findById(id) -> ClubDto?`, `findAll(filters)`, `search(query)`, `update(id, dto)`, `softDelete(id)`, `countByOwner(ownerId)`
    - `findAll` исключает private и inactive клубы, фильтры комбинируются по AND
    - `search` — ILIKE по name OR description
    - `buildFilterConditions(filters)` — internal, возвращает `List<Condition>`
  - `ClubRepositoryTest.kt` — 11 unit-тестов для filter logic (без DB), все проходят
  - `./gradlew build` — BUILD SUCCESSFUL
- **Использованные jOOQ классы:** `CLUBS`, `ClubCategory`, `ClubAccessType` из `com.clubs.generated.jooq`
- **Следующие шаги:**
  1. TASK-010 — ClubService (валидация, лимит 10 клубов, аватар S3, калькулятор дохода, auto-membership organizer)
  2. TASK-007 — добавить UserService как wrapper для UserRepository

---

## [TASK-017] Application: REST-контроллер — apply, applications, approve, reject, my
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `application/ApplicationDto.kt` — добавлен `RejectApplicationRequest(reason: String?)`
  - `application/ApplicationController.kt` — Spring @RestController с полными путями:
    - `POST /api/clubs/{id}/apply` → 201: подача заявки, тело `SubmitApplicationRequest`
    - `GET /api/clubs/{id}/applications` → 200: список заявок (только организатор, фильтр по `?status=pending|approved|rejected`)
    - `PUT /api/applications/{id}/approve` → 200/403/404: одобрение заявки
    - `PUT /api/applications/{id}/reject` → 200/403/404: отклонение, тело `RejectApplicationRequest`
    - `GET /api/applications/my` → 200: мои заявки
  - Извлечение userId из `Authentication.principal` (паттерн из других контроллеров)
  - `./gradlew build && ./gradlew test` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-018 — Application scheduler автоотклонения 48ч (dep: TASK-016 ✅)
  2. TASK-023 — Event Stage 2 scheduler (deps: TASK-005 pending, TASK-021 ✅)
  3. TASK-005 — Redis конфигурация (dep: TASK-001 ✅)

---

## [TASK-018] Application: scheduler автоотклонения заявок старше 48 часов
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `application/ApplicationScheduler.kt` — Spring @Component со @Scheduled(fixedDelay = 1ч):
    - `autoRejectStalePendingApplications()` — каждый час находит все pending заявки с `created_at < now() - 48h`
    - Меняет статус каждой на `auto_rejected` через `ApplicationRepository.updateStatus()`
    - Уменьшает `activity_rating` клуба на -0.5 через jOOQ UPDATE `CLUBS SET activity_rating = activity_rating - 0.5 WHERE id = clubId`
    - Логирует количество автоотклонённых заявок
  - `ApplicationRepository.findPendingOlderThan()` уже существовал — использован напрямую
  - `./gradlew build && ./gradlew test --rerun-tasks` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-012 — Club sorting algorithm (deps: TASK-011 ✅)
  2. TASK-005 — Redis config (deps: TASK-001 ✅ by build)
  3. TASK-023 — Event Stage 2 scheduler (deps: TASK-005 pending, TASK-021 ✅)

---

## [TASK-005] Redis конфигурация: RedisConfig, RedisTemplate<String, Any>
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `config/RedisConfig.kt` — Spring @Configuration:
    - `redisTemplate(connectionFactory)` bean: `RedisTemplate<String, Any>` с `StringRedisSerializer` для ключей и `GenericJackson2JsonRedisSerializer` для значений и hash-значений
    - Использует auto-configured `LettuceConnectionFactory` от Spring Boot (конфиг из application.yml: `spring.data.redis.host/port`)
  - `config/RedisConfigTest.kt` — 4 unit-теста: проверка сериализаторов ключей, значений, hash-ключей и connection factory; все проходят
  - `application.yml` уже содержал `spring.data.redis.host/port` для dev и prod профилей
  - `./gradlew build && ./gradlew test --rerun-tasks` — BUILD SUCCESSFUL
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-023 — Event Stage 2 scheduler (deps: TASK-005 ✅, TASK-021 ✅) — все зависимости выполнены
  2. TASK-012 — Club sorting algorithm (deps: TASK-011 ✅)
  3. TASK-028 — Telegram Bot initialization (deps: TASK-001 ✅)

---

## [TASK-023] Event Stage 2: scheduler — Сценарий А/Б, FIFO, waitlist
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `event/EventRepository.kt` — добавлены методы:
    - `findEventsReadyForStage2()`: находит stage_1 события с `event_datetime - 24h <= now()` и `stage_2_triggered = false`
    - `markStage2Triggered(id, confirmedCount)`: атомарный UPDATE — `stage_2_triggered=true`, `status=stage_2`, `confirmed_count`
    - `atomicIncrementConfirmedCount(id, limit)`: SQL `UPDATE ... WHERE confirmed_count < limit RETURNING confirmed_count` для TASK-024
  - `event/EventResponseRepository.kt` — добавлены методы:
    - `findGoingByEvent(eventId)`: going-ответы по `responded_at ASC` (FIFO)
    - `findMaybeByEvent(eventId)`: maybe-ответы по `responded_at ASC`
    - `updateFinalStatus(eventId, userId, finalStatus, waitlistPosition?)`: обновляет `final_status`, `confirmed_at`, `waitlist_position`
  - `event/EventScheduler.kt` — добавлен `triggerStage2ForEligibleEvents()` (каждые 15 минут):
    - **Сценарий А** (going > limit): первые N → `confirmed` (FIFO), остальные → `waitlisted` с позицией
    - **Сценарий Б** (going <= limit): все going → `confirmed`, остаток мест заполняется maybe (FIFO); при дефиците — warn-лог "Organizer notification needed"
    - Ошибки в одном событии не останавливают обработку остальных (try/catch)
    - `EventResponseRepository` инжектируется в `EventScheduler`
  - `EventStage2SchedulerTest.kt` — 5 unit-тестов (mockito-kotlin): no events, Scenario A, Scenario B with deficit, Scenario B exact, multi-event + exception isolation
  - `./gradlew build && ./gradlew test --rerun-tasks` — BUILD SUCCESSFUL, все тесты проходят
- **Проблемы:**
  - Decline-flow (при отказе confirmed → первый из waitlist становится confirmed) реализован в TASK-024
  - Реальные уведомления (ЛС и Telegram-группа) реализуются в TASK-032
- **Следующие шаги:**
  1. TASK-024 — Event Stage 2 контроллер confirm/decline (deps: TASK-004 in_progress, TASK-023 ✅)
  2. TASK-012 — Club sorting algorithm (deps: TASK-011 ✅)
  3. TASK-028 — Telegram Bot init (deps: TASK-001 ✅ by build)

---

## [TASK-022] EventResponse: REST-контроллер — vote, stats, responses
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `event/EventResponseDto.kt` — добавлен `EventStatsDto(going, maybe, notGoing, confirmed, limit)`
  - `event/EventResponseService.kt` — добавлен `getStats(eventId)`: агрегирует `countByStatus` + `event.confirmedCount` + `event.participantLimit`
  - `event/EventResponseController.kt` — Spring @RestController:
    - `POST /api/events/{id}/vote` → 200: голосование (going/maybe/not_going), валидация + проверка stage_1 в сервисе
    - `GET /api/events/{id}/stats` → 200/404: сводка {going, maybe, notGoing, confirmed, limit}
    - `GET /api/events/{id}/responses` → 200/403/404: список ответов (только для участников клуба)
  - `./gradlew build` — BUILD SUCCESSFUL
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-017 — Application REST-контроллер (deps: TASK-004 in_progress, TASK-016 ✅)
  2. TASK-018 — Application scheduler автоотклонения 48ч (dep: TASK-016 ✅)

---

## [TASK-021] EventResponse: репозиторий + сервис голосования Этапа 1
- **Дата:** 2026-03-07
- **Статус:** done
- **Что сделано:**
  - `event/EventResponseDto.kt` — data classes: `EventResponseDto` (все поля таблицы event_responses), `VoteCountsDto(going, maybe, notGoing)`
  - `event/EventResponseRepository.kt` — jOOQ DSLContext репозиторий:
    - `createOrUpdate(eventId, userId, stage1Status: VoteStatus) -> EventResponseDto` — INSERT если нет, UPDATE если есть
    - `findByEventAndUser(eventId, userId) -> EventResponseDto?`
    - `findByEvent(eventId) -> List<EventResponseDto>` — отсортированный по responded_at ASC
    - `countByStatus(eventId) -> VoteCountsDto` — подсчёт по статусам
  - `event/EventResponseService.kt` — Spring @Service:
    - `vote(userId, eventId, status)` — проверки: событие существует, статус = stage_1, пользователь активный участник; создаёт/обновляет голос через репозиторий
    - `countByStatus(eventId)` — возвращает `VoteCountsDto`
    - `getResponses(eventId, requesterId)` — список ответов, только для участников клуба
  - `EventResponseServiceTest.kt` — 10 unit-тестов через mockito-kotlin: все проходят
  - `./gradlew build && ./gradlew test --rerun-tasks` — BUILD SUCCESSFUL, 10/10 тестов
- **Проблемы:** нет
- **Следующие шаги:**
  1. TASK-022 — EventResponse REST-контроллер (deps: TASK-004 in_progress, TASK-021 ✅)
  2. TASK-018 — Application scheduler автоотклонения 48ч (dep: TASK-016 ✅)
  3. TASK-017 — Application REST-контроллер (deps: TASK-004 in_progress, TASK-016 ✅)

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
