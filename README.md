# mail-agent — mini-ассистент «Коля»

Java 8/Maven агент, который опрашивает Outlook (COM/JACOB) на непрочитанные письма, прогоняет тело каждого письма через LLM tool-loop (Anthropic Messages API) и отвечает письмом отправителю. См. `задание.txt` для полного текста задания и `PLAN.md` для плана реализации.

## Архитектура

```
com.mailagent
├── Main                          — bootstrap, wiring, poll-loop, graceful shutdown
├── config/  AppConfig, ConfigLoader           — загрузка config.yaml (jackson-dataformat-yaml)
├── mail/    Msg, MailChannel, OutlookMailChannel (JACOB), MockMailChannel
├── llm/     LlmClient, ChatMessage, ContentBlock, ToolSpec, ChatResponse,
│            AnthropicLlmClient (OkHttp), MockLlmClient
├── tools/   Tool, ToolRegistry, CurrentDatetimeTool, AddReminderTool, FindItemsTool
├── store/   Reminder, ReminderStore (JSON-файл), SeenStore (идемпотентность)
├── audit/   AuditLogger (append-only, hash-chain / HMAC-SHA256)
└── agent/   ToolLoop, MailAgent
```

Границы:
- `MailChannel` — единственная точка входа/выхода почты. `OutlookMailChannel` (JACOB/COM, без юнит-тестов — см. ниже) и `MockMailChannel` (для тестов) реализуют один и тот же контракт: `List<Msg> fetchUnread()`, `void reply(Msg, String)`.
- `LlmClient` — единственная точка обращения к модели. `AnthropicLlmClient` (реальный HTTP через OkHttp) и `MockLlmClient` (скриптованные ответы) реализуют `chat(messages, tools)`.
- `MailAgent` — оркестрирует цикл: `fetchUnread → dedupe по SeenStore → ToolLoop → reply → mark seen`. Не знает, реальный ли `MailChannel`/`LlmClient` под ним или мок.
- `ToolLoop` — цикл `chat → tool_use? → ToolRegistry.dispatch → tool_result → chat → …`, с жёстким `maxSteps` и устойчивостью к кривым/галлюцинированным `tool_call` (ошибка возвращается модели как `tool_result`, а не бросается наружу).

## Сборка

```
./mvnw -q package
```

Собирает fat-jar `target/mail-agent.jar` (maven-shade-plugin, `Main-Class: com.mailagent.Main`).

## Тесты

```
./mvnw test
```

Проходит **на машине без Outlook** (в т.ч. на Linux/CI): `xyz.cofe:jacob` исключён из test-classpath через `maven-surefire-plugin` → `classpathDependencyExcludes` (по подсказке задания §5 — статический инициализатор JACOB на не-Windows вызывает `System.exit`). Покрыто: юниты инструментов, `ToolLoop` на моке (tool_call → финальный ответ, превышение `maxSteps`, кривой tool_call), `MailAgent` на `MockMailChannel` (все 4 golden-сценария §10 + LLM-фолбэк + идемпотентность полного цикла), `ConfigLoader`, `SeenStore` (переживает рестарт — новый инстанс на том же файле), `AuditLogger` (hash-chain, подделка записи рвёт цепочку), `AnthropicLlmClient` (через `MockWebServer`, без реального ключа/сети), и тест на отсутствие ПДн/тела письма в логах (`logback` `ListAppender`).

## Запуск

Требует Windows + Outlook (профиль по умолчанию) для реального `OutlookMailChannel`, и API-ключ LLM в переменной окружения, заданной в конфиге (`llm.apiKeyEnv`, по умолчанию `ANTHROPIC_API_KEY`).

```
set ANTHROPIC_API_KEY=sk-ant-...
java -jar target/mail-agent.jar config.yaml
```

Аргумент — путь к конфигу (по умолчанию `config.yaml` в текущей директории). Останов — `Ctrl+C`/`SIGTERM`: агент завершает текущий цикл, освобождает COM-поток и выходит.

### Конфиг (`config.yaml`)

```yaml
llm:
  endpoint: https://api.anthropic.com/v1/messages
  model: claude-sonnet-5
  apiKeyEnv: ANTHROPIC_API_KEY   # имя env-переменной с ключом, не сам ключ
  timeoutMs: 30000
agent:
  maxSteps: 6
store:
  path: ./data                  # reminders.json, seen.txt
mail:
  pollSeconds: 30
  profile: Outlook
  folder: Inbox
audit:
  path: ./data/audit.log
  hmacKeyEnv: AUDIT_HMAC_KEY     # опционально; без него — SHA-256 chain + WARN
```

Секретов в файле нет — только имена env-переменных, сами значения ключей нигде в репозитории не хранятся.

## Известный дефект зависимости (и его фикс)

Артефакт `xyz.cofe:jacob:1.20` из Maven Central (замена недоступной координаты `net.sf.jacob-project:jacob:1.20` из задания) не содержит `META-INF/JacobVersion.properties`, который статический инициализатор JACOB требует ещё до попытки загрузки native DLL. Без фикса `OutlookMailChannel` падал бы с `ExceptionInInitializerError` даже на машине с Outlook и DLL на PATH. Исправлено добавлением `src/main/resources/META-INF/JacobVersion.properties` (`version=1.20`, `build.date=2020-12-03`) — обнаружено и проверено сборкой и реальным запуском fat-jar (см. git-историю, коммит `feat: green — Main bootstrap`).

## Чек-лист готовности (§11 задания)

- [x] `mvn package` → fat-jar, запускается — `target/mail-agent.jar` собран и запущен (см. коммит-историю; на машине без Outlook падает ожидаемо на `UnsatisfiedLinkError` для native DLL, что и есть верная граница отказа без Windows-окружения).
- [x] `mvn test` зелёный без Outlook — `jacob` исключён из test-classpath, полный прогон зелёный.
- [x] `MailChannel`: JACOB-реализация (`OutlookMailChannel`) + мок (`MockMailChannel`).
- [x] ≥2 инструмента, tool-loop работает на моке — три инструмента (`current_datetime`, `add_reminder`, `find_items`), `ToolLoopTest` покрывает нормальный сценарий, превышение `maxSteps` и кривой `tool_call`.
- [x] Идемпотентность (seen) + переживает рестарт — `SeenStore`, ключ — стабильный `Msg.id` (Outlook `EntryID`), файл на диске, тест создаёт новый инстанс `SeenStore` на том же файле.
- [x] Конфиг-driven, секреты из env, в git ничего секретного — `ConfigLoader`/`AppConfig`, `apiKeyEnv`/`hmacKeyEnv` — только имена переменных.
- [x] Graceful-фолбэк на LLM и COM — LLM-таймаут/ошибка → фолбэк-ответ письмом + WARN (без stacktrace пользователю), ошибка COM в `fetchUnread`/`reply` → WARN в `Main.runPollLoop`, цикл не падает.
- [x] Структурные логи, без ПДн — event-keys (`agent_mail_seen`, `agent_tool_call`, `llm_failed`, ...), проверено отдельным тестом (`MailAgentPiiLoggingTest`), что тело письма/ПДн не попадают в логи ни на успешном, ни на фолбэк-пути.
- [x] Аудит-журнал действий — `AuditLogger`, append-only JSONL с hash-chain (HMAC-SHA256 при заданном `AUDIT_HMAC_KEY`, иначе SHA-256 chain + WARN); тест на подделку записи рвёт цепочку.
- [x] `PLAN.md` + экспорт сессии Claude Code + README — `PLAN.md` в репозитории, README — этот файл, экспорт сессии — см. ниже.

### Экспорт сессии Claude Code

Полная история сессии (промпты, планы, выполненные шаги) — в файлах `~/.claude/projects/d--ProjectsVS-PromptEng/*.jsonl`, либо через `/export` в Claude Code.

## Как я работал с ИИ

Всё решение написано в паре с Claude Code (модель Sonnet 5) в одну сессию, начиная с пустого репозитория.

**Стратегия промптов.** Сначала — план в plan-mode (без кода), результат — `PLAN.md`: архитектура, контракты (`MailChannel`, `LlmClient`, `Tool`), схема идемпотентности/аудита/логов, конфиг, список зависимостей и 17-шаговая TDD-последовательность с явным правилом «red-коммит → green-коммит» на каждый пункт. После согласования плана — строго TDD по списку: для каждого шага сначала просил (и получал) падающий тест, коммитил его отдельно как `test: red — ...`, затем реализацию и зелёный прогон — `feat: green — ...`. Переход red→green виден в `git log` как пара соседних коммитов почти на каждом шаге.

Исключения из паттерна red→green, сделанные осознанно и явно проговорённые в процессе: `OutlookMailChannel` (JACOB) и часть `Main`, отвечающая за JACOB — по требованию задания (§5) `jacob` исключён из test-classpath целиком (иначе на CI/Linux статический инициализатор JACOB вызывает `System.exit`), так что для этого кода структурно невозможен юнит-тест — эти шаги закоммичены как единая реализация с пометкой «без юнит-теста, см. §5», а верификация сделана вручную — реальной сборкой и запуском fat-jar.

**Что проверял у модели.** Формат tool-calling у Anthropic Messages API (структура `content`-блоков `tool_use`/`tool_result`, `stop_reason`, `input_schema`) — свежая проверка через веб-поиск перед реализацией `AnthropicLlmClient`, а не по памяти модели. Реальный API JACOB (`ComThread`, `ActiveXComponent`, `Dispatch.call/get/put`, метод `Namespace.GetItemFromID` для повторного получения письма по `EntryID` в `reply()`) — не поверил на слово, а декомпилировал (`javap -p -c`) реальный `.class` из зависимости в `~/.m2`, чтобы подтвердить точную сигнатуру методов и логику загрузки native-библиотеки (`LibraryLoader`/`JacobReleaseInfo`) до того, как писать код против него.

**Что отклонил / где не поверил модели на слово.** После того как весь код скомпилировался и все тесты прошли, не остановился на этом — собрал fat-jar и реально его запустил (`java -jar target/mail-agent.jar`). Это вскрыло реальный дефект: у артефакта `xyz.cofe:jacob:1.20` из Maven Central физически отсутствует `META-INF/JacobVersion.properties`, без которого JACOB падает ещё до попытки COM-инициализации — то есть дефект, который проявился бы и на реальной машине с Outlook на защите, а не только в этой среде разработки. Не принял «тесты зелёные ⇒ всё работает» и не сослался на «§5 требует исключить jacob из тестов, значит эту часть можно не проверять» — вместо этого добавил недостающий ресурс-файл и повторно прогнал fat-jar, подтвердив, что точка отказа сдвинулась туда, где ей и положено быть на машине без Windows/Outlook (`UnsatisfiedLinkError` на native DLL), а не в java-коде.

Также не согласился со скиллом `security-review` в его исходном виде: тот построен вокруг diff PR-против-базовой-ветки, а в этом проекте по явной инструкции каждый коммит уходит прямо в `main`, поэтому его автоматический git-diff был пуст. Вместо того чтобы принять это как «нет находок», сделал вручную полноценный обзор безопасности по тем же категориям (инъекции в tool-аргументах, секреты, ПДн в логах, десериализация) — обзор дал 0 находок, что и ожидалось, учитывая, что PII-safe логирование и секреты-только-из-env были заложены архитектурно с самого начала, а не добавлены поверх готового кода.

## Вне scope / stretch

Вне scope (см. §6 задания, не делалось): реальный Telegram, Confluence, календарь, DPAPI/cookies, RAG/эмбеддинги, БД сложнее JSON-файла, мультипользовательность, OAuth/SSO, веб-панель, деплой сверх fat-jar.

Что дальше (см. §7 задания, stretch, не делалось в этом проходе): retry/timeout/backoff для LLM-клиента; allow/deny-gate инструментов из конфига; память диалога по отправителю/треду; OpenTelemetry-спаны на цикл/tool_call; override значений конфига через env-переменные; устойчивость `OutlookMailChannel` к обрыву COM (reconnect); более широкий аудит (напр. полный snapshot решения модели, а не только вызовы инструментов).
