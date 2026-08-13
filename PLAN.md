# PLAN.md — mini-ассистент «Коля» (mail-agent)

Короткий план/issue-лист до кода, по заданию (`задание.txt` / `задание.pdf`).

## Цель
Java 8/Maven агент: опрашивает Outlook (COM/JACOB) на непрочитанные письма → тело письма как запрос в LLM tool-loop (Anthropic Messages API) → ответ письмом отправителю. Идемпотентно, с graceful-фолбэками, аудитом и структурными логами без ПДн.

## Стек
Java 8, Maven (через Maven Wrapper — `mvnw`/`mvnw.cmd`, система без Maven), okhttp 3.14.9, jackson-databind + jackson-dataformat-yaml, JACOB `net.sf.jacob-project:jacob:1.20`, slf4j + logback, JUnit 4, maven-shade-plugin (fat-jar).

## Issue-лист (в порядке выполнения, TDD: red-коммит → green-коммит на каждый пункт)

- [x] #1 Скелет проекта: `pom.xml`, Maven Wrapper, `.gitignore`, пакеты, `config.yaml`, `logback.xml` — инфраструктурный коммит.
- [x] #2 `CurrentDatetimeTool` — детерминированно через инжектируемый `java.time.Clock`.
- [x] #3 `ReminderStore` (JSON-файл на диске) + `AddReminderTool`, `FindItemsTool`.
- [x] #4 `ToolRegistry` — реестр инструментов, диспетчеризация по имени, устойчивость к неизвестному инструменту и к невалидным (кривым/галлюцинированным) JSON-аргументам: без падения, с понятной ошибкой обратно в tool-loop.
- [x] #5 Модель сообщений/тулов для LLM (`ChatMessage`, `ContentBlock`, `ToolSpec`, `ChatResponse`) + `LlmClient` интерфейс + `MockLlmClient` со скриптованными ответами для тестов.
- [x] #6 `ToolLoop` — цикл `chat → tool_call → execute → chat → …` с ограничением `maxSteps`; при превышении лимита или "кривом" tool_call — graceful-завершение с понятным ответом, не exception наружу.
- [x] #7 `ConfigLoader`/`AppConfig` — загрузка `llm.*`, `agent.maxSteps`, `store.path`, `mail.*`, `audit.*` из YAML.
- [x] #8 `SeenStore` — идемпотентность по стабильному id письма (Outlook `EntryID`), персистентно на диске, переживает рестарт процесса.
- [x] #9 `AuditLogger` — append-only журнал с hash-chain (HMAC-SHA256 по ключу из env, иначе SHA-256 chain + WARN); фиксирует обработанные письма и tool_call.
- [x] #10 `MockMailChannel` — для тестов канала без реального Outlook.
- [x] #11 `MailAgent` — сборка цикла (`fetchUnread → dedupe by SeenStore → ToolLoop → reply → mark seen`); тесты на все 4 golden-сценария из §10 задания + фолбэк при недоступном LLM + идемпотентность полного цикла (повторный опрос не даёт второй ответ на то же письмо).
- [x] #12 `AnthropicLlmClient` — реальный HTTP-клиент (okhttp) на `POST /v1/messages`, `tools[].input_schema`, разбор `tool_use`/`text`/`stop_reason`; тест через `MockWebServer` (без реального ключа).
- [x] #13 Тест на отсутствие ПДн в логах (logback `ListAppender`, полный цикл `MailAgent`, тело письма не встречается ни в одном лог-евенте).
- [x] #14 `OutlookMailChannel` (JACOB) — `fetchUnread`/`reply` через COM; без юнит-теста (JACOB исключён из test-classpath, см. §5 задания); проверяется вручную на защите на живом Outlook. По пути обнаружен и исправлен дефект зависимости `xyz.cofe:jacob:1.20` (отсутствовал `META-INF/JacobVersion.properties`) — см. README.
- [x] #15 `Main` — wiring всех компонентов из конфига, poll-loop с `mail.pollSeconds`, graceful shutdown hook, ловит и логирует (WARN, без stacktrace пользователю) ошибки LLM/COM за цикл, не давая упасть всему процессу.
- [x] #16 security-review перед сдачей: ручной обзор (skill `security-review` не применим — нет base-branch diff, весь коммит-поток идёт прямо в `main`), покрыты секреты (только из env, grep по репозиторию — 0 находок), ПДн в логах (отдельный тест + ручной обзор), инъекции в tool-аргументах (обзор `AddReminderTool`/`FindItemsTool`/`CurrentDatetimeTool`), десериализация (`ConfigLoader`/`Json` — plain `ObjectMapper`, без polymorphic typing). Находок нет.
- [x] #17 `README.md` (build/run/test + «Как я работал с ИИ») + финализация этого файла + самопроверка по чек-листу §11 задания.

## Вне scope (см. §6 задания)
Telegram, Confluence, календарь, DPAPI/cookies, RAG/эмбеддинги, БД сложнее JSON-файла, мультипользовательность, OAuth/SSO, веб-панель, деплой сверх fat-jar. Один инстанс, один ящик.

## Stretch (см. §7 задания, опционально)
retry/backoff для LLM, allow/deny-gate инструментов из конфига, память диалога по треду, OpenTelemetry-спаны, override конфига через env, reconnect при сбое COM, расширенный аудит — не делаются в этом проходе; см. README, раздел «Что дальше».
