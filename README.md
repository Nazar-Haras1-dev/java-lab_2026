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

## 3. Як працює код і за що відповідає (простими словами)

1. `AsyncConfig` створює пул `library-async-*` (4 базових потоки, максимум 8, черга 10, `AbortPolicy`) і робить його виконавцем за замовчуванням для `@Async`.
2. Методи `@Async` повертають керування одразу; тіло виконується в потоці пулу. Для `void` помилки потрапляють у `RecordingAsyncExceptionHandler`, для `CompletableFuture` вони лежать усередині Future.
3. `BookReportService.buildReport` стартує PDF і запит видавця одночасно, склеює `thenCombine`, чекає **не довше** `report-timeout-ms`, а збої замінює запасними значеннями.
4. Якщо пул і черга заповнені, проксі кидає `TaskRejectedException`; `RestExceptionHandler` відповідає 503 з `Retry-After`, а не 500.

## 4. Детальний опис кожного класу й методу

- **`AsyncConfig`**: параметри пулу з `library.async.core-pool-size/max-pool-size/queue-capacity` (за замовчуванням 4/8/10), префікс потоків `library-async-`. Реалізує `AsyncConfigurer`, тому і `@Async` без імені, і `@Async(EXECUTOR_NAME)` ідуть у цей пул, а не в `SimpleAsyncTaskExecutor`.
- **`RecordingAsyncExceptionHandler`**: логує `ERROR` і зберігає `AsyncError(method, exception, message)`; зберігається не більше 100 останніх.
- **`SimulatedLatency.pause(op)`**: спить `delayMs`; при переривані відновлює прапорець `interrupt` і кидає `IllegalStateException`.
- **`EmailSender.sendBookCreatedEmail`**: порожній заголовок -> `IllegalArgumentException` (це і є «збій у фоні» для демонстрації обробника).
- **`BookReportService.buildReport`**: (1) знаходить книгу; (2) запускає PDF і видавця; якщо другий submit відхилено, скасовує перший і прокидає `TaskRejectedException`; (3) `exceptionally` на кожному Future; (4) `thenCombine`; (5) `get(timeout)`; таймаут/перерив./`ExecutionException` -> звіт `degraded=true`.
- **`RestExceptionHandler.handleTaskRejected`**: 503, `detail = "Server is busy, please try again later"`, заголовок `Retry-After: 5`.

- **`PdfGenerator.generateBookCard(BookDto)`** (`@Async`, `CompletableFuture<byte[]>`): імітує повільну генерацію PDF (`SimulatedLatency`) і повертає текстовий «PDF» з даними книги.
- **`IsbnLookupClient.fetchPublisher(isbn)`** (`@Async`, `CompletableFuture<String>`): імітує виклик зовнішнього сервісу, повертає `Publisher-<isbn>`.
- **`BookReport`**: record `(bookId, title, pdfBytes, publisher, degraded)`.
- **`BookReportRestController.report(id)`**: `GET /api/books/{id}/report`, делегує в `BookReportService.buildReport`.
- **`AsyncDemoController`**: `POST /api/admin/async-demo/fail-email` (запускає лист із порожнім заголовком, 202) і `GET /api/admin/async-demo/errors` (записи обробника); лише для ADMIN.
- **`BookService.create` / `notifyBookCreated`**: після збереження ставить лист у чергу (`EmailSender`); `TaskRejectedException` перехоплюється, книга все одно створюється.

## 5. Тести

Усього 121 тест (88 було + 33 нових). Нижче всі нові тести. Без `@SpringBootTest`: легкий `AnnotationConfigApplicationContext` з реальними `@Async`-проксі, без `Thread.sleep` (`CountDownLatch`, `CyclicBarrier`, Awaitility).

### AsyncConfigTest

| Тест | Що перевіряє |
|---|---|
| `libraryTaskExecutor_defaultProperties_hasExplicitPoolSizesQueueAndPrefix` | пул має явні розміри 4/8/10, чергу, префікс потоків `library-async-` і політику `AbortPolicy` |
| `getAsyncExecutor_called_returnsSameLibraryExecutorNotSpringDefault` | виконавець за замовчуванням для `@Async` - наш бін, а не стандартний Spring |
| `getAsyncUncaughtExceptionHandler_called_returnsRecordingHandlerBean` | обробник помилок `void`-методів - наш `RecordingAsyncExceptionHandler` |
| `asyncMethod_called_returnsBeforeCompletionAndRunsInPrefixedThread` | виклик повертає керування до завершення методу; метод виконується в потоці з префіксом `library-async-` |

### AsyncBehaviourTest

| Тест | Що перевіряє |
|---|---|
| `buildReport_twoIndependentCalls_runConcurrentlyAndResultsAreCombined` | два виклики справді одночасні (бар'єр на двох учасників) і результати об'єднані |
| `sendBookCreatedEmail_voidMethodThrows_callerContinuesAndHandlerRecordsError` | помилка `void`-методу не ламає викликача, а `AsyncUncaughtExceptionHandler` її фіксує |
| `sendBookCreatedEmail_validArguments_handlerRecordsNothing` | за правильних аргументів обробник помилок нічого не записує |
| `buildReport_asyncCallsThrow_returnsDegradedReportInsteadOfFailing` | збій обох асинхронних викликів дає звіт `degraded`, а не виняток |
| `buildReport_asyncCallsTooSlow_givesUpAfterTimeout` | за надто повільних викликів очікування припиняється через таймаут |
| `buildReport_executorSaturated_throwsTaskRejectedExplicitlyInsteadOfHanging` | пул 1/черга 0: переповнення дає явний `TaskRejectedException`, а не зависання |
| `sendBookCreatedEmail_executorSaturated_secondCallIsRejectedWithTaskRejected` | другий лист при зайнятому пулі відхиляється з `TaskRejectedException` |
| `generateBookCard_resultFuture_completesWithContentOfTheBook` | `Future` PDF завершується вмістом із даними книги |

### BookReportServiceTest

| Тест | Що перевіряє |
|---|---|
| `buildReport_bothCallsSucceed_combinesResultsNotDegraded` | обидва виклики успішні: результати об'єднано, `degraded=false` |
| `buildReport_pdfFails_returnsDegradedReportWithoutThrowing` | збій PDF: звіт `degraded`, видавець збережено, виняток не кидається |
| `buildReport_publisherFails_returnsDegradedReportWithUnknownPublisher` | збій видавця: `publisher=unknown`, `degraded=true` |
| `buildReport_bothFail_returnsFullyDegradedReport` | збій обох: повністю деградований звіт |
| `buildReport_futureNeverCompletes_stopsWaitingAfterTimeoutAndCancelsBoth` | `Future` ніколи не завершується: очікування обривається таймаутом, обидва `Future` скасовано |
| `buildReport_secondSubmitRejected_cancelsFirstAndPropagatesTaskRejected` | другий submit відхилено: перший скасовано, `TaskRejectedException` прокинуто далі |
| `buildReport_unknownBook_throwsNotFoundAndStartsNoAsyncWork` | невідома книга: `ResourceNotFoundException`, асинхронні виклики не стартують |
| `buildReport_interruptedCaller_returnsDegradedAndKeepsInterruptFlag` | перерваний потік: деградований звіт і збережений прапорець `interrupt` |

### RecordingAsyncExceptionHandlerTest

| Тест | Що перевіряє |
|---|---|
| `handleUncaughtException_oneError_isRecordedWithMethodAndExceptionName` | помилка записується з ім'ям методу та типом винятку |
| `handleUncaughtException_moreThanLimit_keepsOnlyLatestEntries` | зберігаються лише останні 100 записів |
| `errors_nothingHappened_isEmpty` | без помилок список порожній |

### SimulatedLatencyTest

| Тест | Що перевіряє |
|---|---|
| `pause_zeroDelay_returnsImmediately` | нульова затримка: повертається одразу |
| `pause_interruptedThread_throwsAndKeepsInterruptFlag` | перерваний потік: виняток і збережений прапорець `interrupt` |

### BookReportRestControllerTest

| Тест | Що перевіряє |
|---|---|
| `report_existingBook_returns200WithCombinedReport` | `GET /api/books/{id}/report`: 200 і об'єднаний звіт |
| `report_unknownBook_returns404` | невідома книга: 404 |
| `report_executorSaturated_returns503WithRetryAfterNot500` | переповнений пул: 503 із `Retry-After`, а не 500 |

### AsyncDemoControllerTest

| Тест | Що перевіряє |
|---|---|
| `failEmail_called_returns202AndStartsBlankTitleEmail` | `POST fail-email`: 202 і запуск листа з порожнім заголовком |
| `errors_handlerHasRecords_returnsThem` | `GET errors` повертає записи обробника |

### BookServiceTest (додано 2 тести)

| Тест | Що перевіряє |
|---|---|
| `create_newIsbn_sendsCreatedEmailFireAndForget` | `BookService.create` викликає `EmailSender` (fire-and-forget) |
| `create_executorSaturated_stillReturnsSavedBookWithoutError` | пул переповнений: книга все одно створюється, без помилки |

### RestExceptionHandlerTest (додано 1 тест)

| Тест | Що перевіряє |
|---|---|
| `handleTaskRejected_executorSaturated_returns503WithRetryAfterHeader` | `TaskRejectedException` -> 503, заголовок `Retry-After: 5`, текст «спробуйте пізніше» |

## 6. Відповідність Acceptance Criteria

| Сценарій | Як виконано | Де видно |
|---|---|---|
| Повільний метод не блокує викликача | `@Async`, метод повертається до завершення тіла | `asyncMethod_called_returnsBeforeCompletion...`; Postman 03 (< 900 мс) |
| Явний `ThreadPoolTaskExecutor`, обмежені ресурси, префікс | `AsyncConfig`: 4/8/10, `library-async-` | `AsyncConfigTest`; потік у тесті починається з префікса |
| Два незалежні виклики справді паралельні | старт обох до очікування + `thenCombine` | `...runConcurrently...` (бар'єр); Postman 04 (< 1900 мс) |
| Збій async-виклику не валить викликача | `exceptionally` -> `degraded`; для `void` обробник | `buildReport_asyncCallsThrow_...`, `sendBookCreatedEmail_voidMethodThrows_...`; Postman 07-08 |
| Очікування обмежене в часі | `get(timeout)` | `buildReport_asyncCallsTooSlow_...`, `..._futureNeverCompletes_...` |
| Переповнення пулу -> явне рішення, не 500 | `TaskRejectedException` -> 503 + `Retry-After` | `..._executorSaturated_...`, `BookReportRestControllerTest` |

## 7. Запуск і перевірка

```
mvn clean test        # очікується: Tests run: 121, Failures: 0, BUILD SUCCESS
```
Ручна перевірка: Postgres, Keycloak, `LibraryApplication`, імпорт `postman/Library.lab5.postman_collection.json`, Run (всі 10 запитів зелені). Перші запити після старту можуть бути трохи повільнішими через прогрів; поріг 1900 мс має запас.

Результат: тестів 121 (Failures 0, Errors 0).

## 8. Обмеження та примітки

- Лист, PDF і відповідь видавця **імітуються**; реального SMTP/PDF/HTTP немає.
- `RecordingAsyncExceptionHandler` зберігає помилки в пам'яті (до 100), після перезапуску вони губляться.
- Лист ставиться в чергу ще всередині транзакції `create`; якщо транзакцію відкотить після постановки, лист усе одно піде. Для продакшну варто відправляти після коміту (`@TransactionalEventListener`).
- Розмір пулу впливає на паралельність: `ThreadPoolExecutor` спочатку заповнює базові потоки, потім чергу і лише потім росте до `max`. Із базовим пулом 2 лист після `create` займав потік, і другий виклик звіту чекав у черзі (~2 с замість ~1 с), тому базовий пул 4.
- Відхилений лист не повторюється (рішення «пропустити й залогувати»).
