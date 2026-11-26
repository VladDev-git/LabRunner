# ADR-0003: Kotlin Multiplatform для Android та iOS і єдиний Kotlin-стек із сервером

- **Статус:** Accepted
- **Дата:** 2026-11-26
- **Лабораторна:** №1

## Контекст

Застосунок має бути доступний і на Android, і на iOS, а команда має досвід мобільної розробки на Kotlin. Два нативні застосунки (Kotlin + Swift) подвоїли б обсяг розробки й тестування. Flutter чи React Native потребують іншої мови й не дозволяють перевикористати моделі даних Kotlin-сервера.

## Рішення

- Мобільний клієнт — Kotlin Multiplatform + Compose Multiplatform (`:composeApp`): спільні UI, ViewModel, мережевий шар (Ktor Client); платформні API через `expect` / `actual`.
- Сервер (Core API, Runner Orchestrator) — Kotlin + Ktor.
- DTO винесені в модуль `:shared:contracts` (kotlinx.serialization) і використовуються і клієнтом, і сервером.
- Один Gradle-монорепозиторій.

## Наслідки

- Більша частина коду клієнта пишеться один раз; контракти API не можуть розійтися між клієнтом і сервером.
- iOS-збірка можлива лише на macOS (окремий macOS-раннер у CI).
- Compose на iOS не є нативним UIKit / SwiftUI — платформні відмінності враховуються окремо.
