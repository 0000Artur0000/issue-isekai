# IssueIsekai

IssueIsekai принимает баг-репорты из Minecraft Paper через Dialog API, надёжно доставляет их в отдельную Java-панель, объединяет заявки нескольких серверов и отправляет уведомления в Telegram.

В репозитории два Gradle-модуля:

- `paper-plugin` — плагин Paper 26.1.2 для Java 25;
- `panel` — Spring Boot панель с PostgreSQL, ролями и Telegram worker.

Frontend панели — React/TypeScript SPA, собранная Vite и встроенная как статика в panel JAR. Отдельный Node.js runtime в production не используется.

## Возможности

- `/bug`, `/bugreport` и `/bugreprt` открывают нативный Minecraft Dialog;
- категория, описание, игрок, мир, координаты, режим игры и версия Paper попадают в заявку;
- дисковая очередь переживает недоступность панели и restart сервера;
- разные Minecraft-серверы используют отдельные отзываемые API-ключи;
- SPA содержит kanban-доску, timeline, detail со снимком инвентаря и admin-страницы пользователей, серверов и resource packs;
- панель поддерживает поиск, фильтры, статусы, приоритеты, ответственного, участников, дубликаты и аудит;
- роли `ADMIN` и `OPERATOR` ограничивают доступ к управлению;
- Telegram worker повторяет неудачную отправку и отмечает только успешную;
- Denizen необязателен и добавляет три события и script-команду.

## Снимок инвентаря

Новый плагин отправляет inventory schema `2` с едиными logical slots: `hotbar_0..8`, `storage_9..35`, `boots`, `leggings`, `chestplate`, `helmet`, `offhand`. Panel продолжает принимать schema `1` из старых очередей и нормализует прежние `storage_0..26`, `armor_*` и `off_hand` при записи и чтении.

Adventure name/lore выводятся безопасными React nodes и allowlisted CSS-классами Minecraft-цветов без inline styles и HTML. Для custom-моделей администратор загружает ZIP на странице сервера и делает нужную ревизию активной; UUID из `server.properties` не требуется. При ingest панель закрепляет текущую активную ревизию за отчётом. Deepslate 0.26.0 прошёл mesh-proof на vanilla 2D, custom 2D и custom 3D parent chain, но не добавлен в production без WebGL pixel proof на реальном pack;

## Права Minecraft

- `bugreport.submit` (`true`) — использовать `/bug`, `/bugreport` и `/bugreprt`;
- `bugreport.open.others` (`op`) — открыть форму другому онлайн-игроку;
- `bugreport.cooldown.bypass` (`op`) — отправлять репорты без cooldown.

## Требования

- Paper `26.1.2`;
- Java `25` для Paper и runtime панели;
- Node.js `22` только для сборки frontend из исходников;
- Docker с Compose для рекомендуемого запуска панели;
- PostgreSQL `16+` для запуска панели без Compose.

Gradle `8.14.3` запускается на Java 21 и компилирует модули toolchain JDK 25. Docker и CI настраивают это автоматически.

## Сборка

```shell
npm --prefix panel/frontend test
./gradlew clean check :paper-plugin:jar :panel:bootJar --no-daemon
```

Артефакты появятся здесь:

- `paper-plugin/build/libs/paper-plugin-0.1.0-SNAPSHOT.jar`;
- `panel/build/libs/panel-0.1.0-SNAPSHOT.jar`.

## Запуск панели через Docker Compose

```shell
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Перед первым запуском замените в `.env` как минимум `POSTGRES_PASSWORD` и `BOOTSTRAP_ADMIN_PASSWORD`. `APP_LOCALE=ru|en` задаёт язык всей панели; неизвестное значение останавливает запуск. Панель по умолчанию доступна на `http://127.0.0.1:8080`.

Первый администратор создаётся только в пустой базе из:

- `BOOTSTRAP_ADMIN_USERNAME`;
- `BOOTSTRAP_ADMIN_PASSWORD`.

Повторный запуск не меняет пароль существующего администратора. Данные PostgreSQL сохраняются в Docker volume `postgres-data`.

Проверка готовности:

```shell
curl http://127.0.0.1:8080/actuator/health/readiness
```

Остановка:

```shell
docker compose down
```

Команда `docker compose down -v` также удалит базу данных.

## Запуск panel JAR

```shell
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/issue_isekai \
DATABASE_USERNAME=issue_isekai \
DATABASE_PASSWORD=change-me \
BOOTSTRAP_ADMIN_USERNAME=admin \
BOOTSTRAP_ADMIN_PASSWORD=change-me \
APP_LOCALE=ru \
java -jar panel/build/libs/panel-0.1.0-SNAPSHOT.jar
```

Порт можно изменить через `SERVER_PORT`.

## Подключение Minecraft-сервера

1. Войдите в панель как `ADMIN`.
2. Откройте `/servers` и создайте сервер.
3. Сразу сохраните показанный API-ключ: повторно он не отображается.
4. Скопируйте plugin JAR в `plugins/` Paper-сервера.
5. Запустите Paper один раз и откройте `plugins/IssueIsekai/config.yml`.
6. Укажите URL панели и API-ключ.
7. Перезапустите Paper.

Минимальная конфигурация плагина:

```yaml
panel-url: "http://127.0.0.1:8080"
api-key: "replace-me"
language: "ru_RU"
categories:
  - id: gameplay
    title: "Gameplay"
  - id: performance
    title: "Performance"
  - id: exploit
    title: "Exploit"
  - id: other
    title: "Other"
request-timeout-seconds: 10
retry-interval-seconds: 30
max-deliveries-per-run: 20
max-queued-reports: 1000
cooldown-seconds: 60
```

Доступны `ru_RU` и `en_US`. При первом запуске плагин копирует редактируемые файлы в `plugins/IssueIsekai/lang/`; сообщения поддерживают MiniMessage. Неизвестный или повреждённый язык откатывается на встроенный `ru_RU`. Поле `title` категории используется как fallback, если в lang-файле нет `category.<id>`.

Если Paper и панель находятся на разных машинах, в `panel-url` нужен адрес панели, доступный именно с Paper-сервера. HTTP следует использовать только в доверенной сети или VPN.

Плагин создаёт `plugins/IssueIsekai/queue/` и `plugins/IssueIsekai/dead-letter/`. Не удаляйте `queue/` при недоступной панели: worker доставит заявки после восстановления соединения.

## Telegram

Добавьте бота в нужный чат, узнайте chat ID и задайте в `.env`:

```dotenv
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=replace-me
TELEGRAM_CHAT_ID=replace-me
TELEGRAM_POLL_INTERVAL_MS=30000
```

После изменения перезапустите panel-контейнер. Worker выбирает до 20 новых заявок за проход. При ошибке Bot API заявка остаётся неотмеченной и будет отправлена повторно. Семантика уведомлений — at-least-once.

## Denizen

Denizen не требуется для обычной работы. Если `Denizen.jar` загружен и включён, IssueIsekai регистрирует:

- `bugreport player submits`;
- `bugreport submission queued`;
- `bugreport submission delivered`.

Pre-submit событие принимает determinations `cancelled`, `category:<id>` и `description:<text>`.

Script-команда использует тот же pipeline, что и обычная команда:

```denizen
- bugreport open player:<player>
- bugreport submit category:gameplay "description:Текст ошибки" player:<player>
```

Это Denizen script action, а не Bukkit-команда: Bukkit permissions к нему не применяются, доступ задаёт сам Denizen-скрипт.

Runtime совместимость проверена с Denizen `1.3.3-b7290-DEV`. Depenizen нужен только тестовому серверу и не является зависимостью IssueIsekai.

## Проверка Docker

После запуска Compose можно выполнить интеграционный smoke-тест:

```shell
PANEL_URL=http://127.0.0.1:8080 \
BOOTSTRAP_ADMIN_USERNAME=admin \
BOOTSTRAP_ADMIN_PASSWORD=change-me \
./scripts/smoke.sh
```

Сценарий входит в панель, создаёт Minecraft-сервер, получает одноразовый ключ и проверяет `201 → 200` для повторного `submission_id` с тем же `report_id`.

## Ручная приёмка

1. Выполните `/bugreport`, отправьте описание длиннее 10 символов и найдите заявку на `/board`.
2. Повторите через алиасы `/bug` и `/bugreprt`.
3. Остановите панель, отправьте заявку, перезапустите Paper и затем панель. Заявка должна появиться один раз.
4. Измените статус, приоритет и ответственного. В карточке должны появиться записи аудита.
5. Проверьте, что `OPERATOR` видит заявки, но получает `403` на `/users` и `/servers`.
6. Включите Telegram и убедитесь, что новый report приходит в заданный чат.
7. Запустите Paper без Denizen и с Denizen, затем проверьте три события и actions `open`/`submit`.

## CI

`.github/workflows/ci.yml` запускается на `push`, `pull_request` и вручную через `workflow_dispatch`. Workflow устанавливает JDK 25 для toolchain, запускает Gradle на JDK 21, выполняет все проверки и сохраняет два артефакта:

- `IssueIsekai-plugin`;
- `IssueIsekai-panel`.

Тег строгого вида `vX.Y.Z`, отправленный владельцем проекта, после успешного build-job автоматически:

- собирает JAR с версией из тега и публикует `ghcr.io/0000artur0000/issue-isekai-panel:X.Y.Z`;
- создаёт `compose.yaml` с image digest, install guide, installer, `.env.example` и `SHA256SUMS`;
- создаёт provenance attestations для image и файлов, проверяет анонимное чтение image и публикует GitHub Release.

Существующий Release не перезаписывается. CI ничего не развёртывает на серверы. Исполнитель не создаёт commit, push или tag: после проверки изменений это делает владелец проекта.
