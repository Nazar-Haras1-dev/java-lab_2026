# Library: лабораторні роботи

Предметна область: бібліотека (книги). Стек: Java 17 (запуск на JDK 21), Spring Boot 3.3.5, Spring Data JPA,
Spring Security (OIDC + JWT, Keycloak), Liquibase, PostgreSQL, MapStruct, Thymeleaf, JUnit 5, Mockito, JaCoCo.

Документація кожної лаби лежить в окремому файлі:

| Лаба | Тема | README | Гілка |
|---|---|---|---|
| Lab 4 | AOP (власні анотації та аспекти) | [docs/lab4/README.md](docs/lab4/README.md) | `lab-4-aop` |
| Lab 5 | Асинхронна обробка (@Async, CompletableFuture) | [docs/lab5/README.md](docs/lab5/README.md) | `lab-5-async` |
| Lab 6 | Quartz Scheduler (JDBC job store, Liquibase) | [docs/lab6/README.md](docs/lab6/README.md) | `lab-6-quartz` |

Postman-колекції: `postman/Library.labN.postman_collection.json`.

## Загальний запуск

1. PostgreSQL на `localhost:5432` (БД, користувач і пароль `library`).
2. Keycloak 25 (JDK 21) на `8180`, realm `library` (користувачі `admin/admin`, `user/user`), імпорт `keycloak/realm-library.json`.
3. Застосунок (Lab 7: кожен сервіс окремо) і тести: `mvn clean test` (юніт-тести не потребують ні Postgres, ні Keycloak).
