# mail-agent — mini-ассистент «Коля»

## Сборка

```
./mvnw -q package
```

Собирает fat-jar `target/mail-agent.jar` (`Main-Class: com.mailagent.Main`).

## Тесты

```
./mvnw test
```

Проходит на машине без Outlook (jacob исключён из test-classpath).

## Запуск

Требует Windows + Outlook и API-ключ LLM в переменной окружения, заданной в конфиге (`llm.apiKeyEnv`, по умолчанию `ANTHROPIC_API_KEY`).

```
set ANTHROPIC_API_KEY=sk-ant-...
java -jar target/mail-agent.jar config.yaml
```

Аргумент — путь к конфигу (по умолчанию `config.yaml` в текущей директории). Останов — `Ctrl+C`/`SIGTERM`.

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
