# LabRunner

Сервіс автоматизованої перевірки програмних завдань з мобільним клієнтом для Android та iOS (Kotlin Multiplatform).
Навчальний проєкт з дисципліни «Життєвий цикл ПЗ та DevOps-технології» (ТНТУ, кафедра програмної інженерії).

Студент здає рішення (Git-репозиторій або код) із застосунку, сервер ставить здачу в чергу, Runner Orchestrator перевіряє її та зберігає звіт, а студент бачить статус і бал.

## Архітектура

![C4 Container](docs/architecture/c4-container.png)

| Компонент | Технології | Опис |
|---|---|---|
| `composeApp` | Kotlin Multiplatform, Compose Multiplatform, Ktor Client | Мобільний застосунок Android / iOS |
| `server/api` | Kotlin, Ktor 3, JDBC + HikariCP | Core API `/api/v1`, міграції БД, постановка задач у чергу |
| `server/orchestrator` | Kotlin, Lettuce, MinIO SDK | Споживач черги, перевірка здач, звіти в S3 |
| `shared/contracts` | Kotlin Multiplatform, kotlinx.serialization | DTO, спільні для клієнта й сервера |
| `db` | PostgreSQL 16 | Завдання, здачі, бали |
| `redis` | Redis 7 Streams (AOF) | Черга перевірок `labrunner:submissions` |
| `storage` | RustFS (S3-сумісне сховище) | JSON-звіти перевірок |

Архітектурні рішення: [docs/adr](docs/adr).

## Швидкий старт (локальне середовище)

Потрібні Docker Desktop (Compose v2+) та вільний порт 8080.

```bash
cp .env.example .env        # змініть паролі
docker compose up -d --build
docker compose ps           # усі сервіси мають статус (healthy)
curl http://localhost:8080/health
```

Зупинка: `docker compose down` (дані зберігаються в томах), повне очищення: `docker compose down -v`.

### Мережі та безпека

- `public-net` — лише `api`, єдиний порт на хості: `8080`.
- `backend-net` (`internal: true`) — `db`, `redis`, `storage`, `api`, `orchestrator`; без виходу в інтернет і без опублікованих портів.
- Власні образи працюють від непривілейованого користувача `labrunner` (uid 10001).

## API

| Метод | Шлях | Опис |
|---|---|---|
| GET | `/health` | Стан сервісу (БД + Redis) |
| GET | `/api/v1/assignments` | Список завдань |
| POST | `/api/v1/assignments` | Створити завдання |
| POST | `/api/v1/submissions` | Здати рішення → `202 Accepted`, статус `QUEUED` |
| GET | `/api/v1/submissions?student=...` | Здачі студента |
| GET | `/api/v1/submissions/{id}` | Статус і бал |
| GET | `/api/v1/submissions/{id}/report` | JSON-звіт перевірки |

```bash
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{"assignmentId":1,"studentName":"Владислав Петрик","sourceCode":"fun main() { println(42) }"}'
```

## Мобільний застосунок

```bash
./gradlew :composeApp:installDebug                                # емулятор: API на http://10.0.2.2:8080
./gradlew :composeApp:assembleDebug -Plabrunner.apiUrl=http://192.168.0.10:8080   # реальний пристрій
```

iOS: див. [iosApp/README.md](iosApp/README.md) (збірка на macOS).

## Структура репозиторію

```
composeApp/          KMP-клієнт (androidMain, iosMain, commonMain)
iosApp/              SwiftUI-обгортка для iOS
server/api/          Ktor Core API + Dockerfile
server/orchestrator/ Runner Orchestrator + Dockerfile
shared/contracts/    спільні DTO
docs/adr/            Architecture Decision Records
docs/architecture/   діаграми C4 / Deployment
docker-compose.yml   локальне середовище
.env.example         шаблон конфігурації
```

## Стан за лабораторними

- [x] Лаба 1 — аналіз, вимоги, C4, ADR-0001…0003
- [x] Лаба 2 — контейнеризація, Docker Compose, 12-Factor, ADR-0004
- [ ] Лаба 3 — CI/CD
- [ ] Лаба 4 — Kubernetes, IaC, sandbox у gVisor
- [ ] Лаба 5 — моніторинг та спостережуваність
