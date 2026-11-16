# Lab 5: Асинхронна обробка (`@Async`, `CompletableFuture`)

## 1. Мета і що зроблено

Повільні побічні ефекти більше не блокують викликаючий код. Вони виконуються в **окремо налаштованому пулі потоків** з обмеженими ресурсами, а збої й переповнення обробляються передбачувано.

Три повільні операції (кожна імітує ~1 с, `library.async.simulated-delay-ms`):

| № | Операція | Клас | Тип | Як використовується |
|---|---|---|---|---|
| 1 | Надсилання листа про нову книгу | `EmailSender` | `void` (fire-and-forget) | викликається з `BookService.create`, ніхто не чекає |
| 2 | Генерація PDF-картки книги | `PdfGenerator` | `CompletableFuture<byte[]>` | частина звіту |
| 3 | Виклик зовнішнього сервісу (видавець за ISBN) | `IsbnLookupClient` | `CompletableFuture<String>` | частина звіту |

Операції 2 і 3 незалежні: `BookReportService` запускає їх **одразу обидві**, потім об'єднує `thenCombine`.

Трактування (де завдання неоднозначне):
- «Результат, потрібний викликаючому коду» реалізовано як `GET /api/books/{id}/report`; два виклики, тому `thenCombine` (а не `allOf`).
- Повільність імітується `SimulatedLatency` (сон на `library.async.simulated-delay-ms`); PDF і відповідь партнера теж імітовані, бо реальні SMTP/PDF/HTTP не потрібні для мети лаби.
- Збій Future або таймаут: звіт повертається як `degraded=true` із запасними значеннями (відповідь 200, не помилка). Переповнення пулу: явне **503 + `Retry-After: 5`** («спробуйте пізніше»).
- Листа при створенні книги пул, що переповнився, не скасовує: книга зберігається, лист пропускається з попередженням у лозі.

## Як працює код і за що відповідає (коротко)

1. `AsyncConfig` створює пул `library-async-*` (4 базових потоки, максимум 8, черга 10, `AbortPolicy`) і робить його виконавцем за замовчуванням для `@Async`.
2. Методи `@Async` повертають керування одразу; тіло виконується в потоці пулу. Для `void` помилки потрапляють у `RecordingAsyncExceptionHandler`, для `CompletableFuture` вони лежать усередині Future.
3. `BookReportService.buildReport` стартує PDF і запит видавця одночасно, склеює `thenCombine`, чекає **не довше** `report-timeout-ms`, а збої замінює запасними значеннями.
4. Якщо пул і черга заповнені, проксі кидає `TaskRejectedException`; `RestExceptionHandler` відповідає 503 з `Retry-After`, а не 500.

## 2. Нові та змінені файли

| Файл | Призначення | Що змінилось |
|---|---|---|
| `library/async/AsyncConfig.java` | `@EnableAsync`, `ThreadPoolTaskExecutor`, обробник | нове |
| `library/async/RecordingAsyncExceptionHandler.java` | `AsyncUncaughtExceptionHandler`: лог + пам'ять (до 100 записів) | нове |
| `library/async/SimulatedLatency.java` | імітація повільної дії, підміняється в тестах | нове |
| `library/async/EmailSender.java` | `@Async void` лист | нове |
| `library/async/PdfGenerator.java` | `@Async CompletableFuture<byte[]>` | нове |
| `library/async/IsbnLookupClient.java` | `@Async CompletableFuture<String>` | нове |
| `library/async/BookReport.java` | record результату звіту | нове |
| `library/async/BookReportService.java` | паралельний запуск, `thenCombine`, таймаут, запасні значення | нове |
| `controller/BookReportRestController.java` | `GET /api/books/{id}/report` | нове |
| `controller/AsyncDemoController.java` | ADMIN: `POST /api/admin/async-demo/fail-email`, `GET .../errors` | нове |
| `service/BookService.java` | `create` викликає `EmailSender`, `TaskRejectedException` перехоплюється | змінено (+конструктор) |
| `exception/RestExceptionHandler.java` | обробник `TaskRejectedException` -> 503 + `Retry-After`, нові контролери в `assignableTypes` | змінено |
| `resources/application.yml` | блок `library.async.*` | змінено |
| `test/.../async/*`, `BookReportRestControllerTest`, `AsyncDemoControllerTest` | нові тести | нове |
| `BookServiceTest`, `RestExceptionHandlerTest` | `@Mock EmailSender`, 2 + 1 новий тест | змінено |
| `postman/Library.lab5.postman_collection.json` | 10 запитів з тестами | нове |

## 3. Опис класів і логіки

- **`AsyncConfig`**: параметри пулу з `library.async.core-pool-size/max-pool-size/queue-capacity` (за замовчуванням 4/8/10), префікс потоків `library-async-`. Реалізує `AsyncConfigurer`, тому і `@Async` без імені, і `@Async(EXECUTOR_NAME)` ідуть у цей пул, а не в `SimpleAsyncTaskExecutor`.
- **`RecordingAsyncExceptionHandler`**: логує `ERROR` і зберігає `AsyncError(method, exception, message)`; зберігається не більше 100 останніх.
- **`SimulatedLatency.pause(op)`**: спить `delayMs`; при переривані відновлює прапорець `interrupt` і кидає `IllegalStateException`.
- **`EmailSender.sendBookCreatedEmail`**: порожній заголовок -> `IllegalArgumentException` (це і є «збій у фоні» для демонстрації обробника).
- **`BookReportService.buildReport`**: (1) знаходить книгу; (2) запускає PDF і видавця; якщо другий submit відхилено, скасовує перший і прокидає `TaskRejectedException`; (3) `exceptionally` на кожному Future; (4) `thenCombine`; (5) `get(timeout)`; таймаут/перерив./`ExecutionException` -> звіт `degraded=true`.
- **`RestExceptionHandler.handleTaskRejected`**: 503, `detail = "Server is busy, please try again later"`, заголовок `Retry-After: 5`.

## 4. Тести (33 нові; було 88, стало 121)

| Клас | Тест | Що перевіряє |
|---|---|---|
| `AsyncConfigTest` | `libraryTaskExecutor_defaultProperties_...` | розміри 4/8/10, префікс, `AbortPolicy` |
| | `getAsyncExecutor_...` | виконавець за замовчуванням = наш бін |
| | `getAsyncUncaughtExceptionHandler_...` | обробник = `RecordingAsyncExceptionHandler` |
| | `asyncMethod_called_returnsBeforeCompletion...` | виклик повертає керування до завершення; потік починається з `library-async-` |
| `AsyncBehaviourTest` | `buildReport_twoIndependentCalls_runConcurrently...` | `CyclicBarrier(2)` у обох викликах; послідовно бар'єр не відкрився б |
| | `sendBookCreatedEmail_voidMethodThrows_...` | `void` кидає -> обробник фіксує, викликач не падає |
| | `sendBookCreatedEmail_validArguments_...` | без помилки обробник порожній |
| | `buildReport_asyncCallsThrow_...` | збій Future -> `degraded`, без винятку |
| | `buildReport_asyncCallsTooSlow_...` | таймаут 100 мс -> перестає чекати |
| | `buildReport_executorSaturated_...` / `sendBookCreatedEmail_executorSaturated_...` | пул 1/черга 0 -> `TaskRejectedException` |
| | `generateBookCard_resultFuture_...` | результат Future містить дані книги |
| `BookReportServiceTest` (8) | успіх, збій PDF, збій видавця, обидва, таймаут + `cancel`, відхилення другого submit, невідома книга, переривання | логіка об'єднання й деградації |
| `RecordingAsyncExceptionHandlerTest` (3) | запис, межа 100, порожній стан | |
| `SimulatedLatencyTest` (2) | нульова затримка, переривання | |
| `BookReportRestControllerTest` (3) | 200, 404, **503 + Retry-After** | |
| `AsyncDemoControllerTest` (2) | 202, список помилок | |
| `RestExceptionHandlerTest` | `handleTaskRejected_...` | 503, заголовок, текст |
| `BookServiceTest` | `create_..._sendsCreatedEmailFireAndForget`, `create_executorSaturated_...` | лист викликається; відхилення не ламає `create` |

Без `Thread.sleep` у тестах: синхронізація через `CountDownLatch`, `CyclicBarrier`, Awaitility. Без `@SpringBootTest`: легкий `AnnotationConfigApplicationContext` з реальними `@Async`-проксі.

## 5. Відповідність Acceptance Criteria

| Сценарій | Як виконано | Де видно |
|---|---|---|
| Повільний метод не блокує викликача | `@Async`, метод повертається до завершення тіла | `asyncMethod_called_returnsBeforeCompletion...`; Postman 03 (< 900 мс) |
| Явний `ThreadPoolTaskExecutor`, обмежені ресурси, префікс | `AsyncConfig`: 4/8/10, `library-async-` | `AsyncConfigTest`; потік у тесті починається з префікса |
| Два незалежні виклики справді паралельні | старт обох до очікування + `thenCombine` | `...runConcurrently...` (бар'єр); Postman 04 (< 1900 мс) |
| Збій async-виклику не валить викликача | `exceptionally` -> `degraded`; для `void` обробник | `buildReport_asyncCallsThrow_...`, `sendBookCreatedEmail_voidMethodThrows_...`; Postman 07-08 |
| Очікування обмежене в часі | `get(timeout)` | `buildReport_asyncCallsTooSlow_...`, `..._futureNeverCompletes_...` |
| Переповнення пулу -> явне рішення, не 500 | `TaskRejectedException` -> 503 + `Retry-After` | `..._executorSaturated_...`, `BookReportRestControllerTest` |

## 6. Запуск і перевірка

```
mvn clean test        # очікується: Tests run: 121, Failures: 0, BUILD SUCCESS
```
Ручна перевірка: Postgres, Keycloak, `LibraryApplication`, імпорт `postman/Library.lab5.postman_collection.json`, Run (всі 10 запитів зелені). Перші запити після старту можуть бути трохи повільнішими через прогрів; поріг 1900 мс має запас.

Результат: тестів 121 (Failures 0, Errors 0).

## 7. Обмеження

- Лист, PDF і відповідь видавця **імітуються**; реального SMTP/PDF/HTTP немає.
- `RecordingAsyncExceptionHandler` зберігає помилки в пам'яті (до 100), після перезапуску вони губляться.
- Лист ставиться в чергу ще всередині транзакції `create`; якщо транзакцію відкотить після постановки, лист усе одно піде. Для продакшну варто відправляти після коміту (`@TransactionalEventListener`).
- Розмір пулу впливає на паралельність: `ThreadPoolExecutor` спочатку заповнює базові потоки, потім чергу і лише потім росте до `max`. Із базовим пулом 2 лист після `create` займав потік, і другий виклик звіту чекав у черзі (~2 с замість ~1 с), тому базовий пул 4.
- Відхилений лист не повторюється (рішення «пропустити й залогувати»).
