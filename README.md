---

# Lab 4: AOP (власні анотації та аспекти)

## 1. Мета і що зроблено
## Як працює код і за що відповідає (коротко)
## 2. Що таке AOP і як воно працює в проєкті

AOP виносить наскрізну логіку з бізнес-коду в окремі **аспекти**. Замість копіювання коду в методи їх позначають анотацією.

- **Aspect** (`@Aspect`): клас із наскрізною логікою.
- **Pointcut** (`@annotation(audited)`): вираз, що вибирає методи з нашою анотацією.
- **Advice**: код, який виконується (`@AfterReturning` після успіху, `@Around` навколо виклику).
- **JoinPoint / ProceedingJoinPoint**: опис виклику методу. У `@Around` виклик `proceed()` запускає оригінальний метод.

Spring AOP працює через **проксі**: бін обгортається об'єктом-посередником (CGLIB-підклас). Виклик ззовні потрапляє в проксі, який виконує advice, а потім оригінальний метод
AuditAspect (@AfterReturning): після успішного виконання бере користувача з SecurityContextHolder, формує AuditEntry, пише в AuditTrail і в лог AUDIT. Якщо метод кинув виняток (наприклад, @PreAuthorize заборонив видалення), аудиту немає.
RateLimitAspect (@Around): ключ це користувач плюс метод. Тримає чергу часу викликів у ковзному вікні. Якщо їх уже maxCalls, кидає виняток без виклику proceed() (429). Час береться з Clock, тому тести детерміновані.
RetryAspect (@Around): у циклі викликає proceed(). Повторює лише для винятків зі списку retryOn (за замовчуванням тимчасові помилки БД), для інших одразу кидає. Якщо спроби вичерпано, кидає останній виняток.

Порядок аспектів (@Order): Retry (1), RateLimit (2), Audit (3). Вони виконуються до безпеки та транзакцій (їхні порядки більші), тому кожна спроба повтору отримує нову транзакцію, а @PreAuthorize усередині аспектів.

Чому повтор лише для читання. Повтор безпечний для ідемпотентних операцій. Повторне створення могло б дублювати дані.

Self-invocation.

Виклик this.auditedAction() усередині того ж класу йде безпосередньо до об'єкта, повз проксі, тож аспект не спрацьовує, і жодних помилок немає.
Це обмеження проксі-моделі Spring AOP.
Обхід: винести метод в інший бін, інжектити самого себе, або використати AspectJ weaving (змінює байт-код на компіляції чи завантаженні).
Тут це зроблено свідомо: Postman 07 і SelfInvocationTest демонструють різницю між двома способами виклику.

Unit-тест аспекта. ProceedingJoinPoint замінено моком, Spring і @SpringBootTest не потрібні. Перевіряється рішення в advice: чи викликається proceed() (і скільки разів), що кидається і що повертається
**Ідея AOP.** Деякі речі потрібні багатьом методам (записати подію, обмежити частоту, повторити при збої). Замість копіювання коду в кожен метод ми пишемо це один раз в аспекті, а методи позначаємо анотацією.

**Як це працює технічно.** Spring не змінює `BookService`, а обгортає його проксі-об'єктом. Коли хтось викликає метод ззовні, виклик іде через проксі: той спершу виконує код аспекту, а потім (або замість цього) оригінальний метод.

### Три пари «анотація + аспект»

| Анотація | Аспект | Що робить | Приклад у коді |
|---|---|---|---|
| `@Audited(action)` | `AuditAspect` (`@AfterReturning`) | після **успішного** виконання записує в журнал, хто й що зробив | `BookService.create/update/delete` |
| `@RateLimited` | `RateLimitAspect` (`@Around`) | рахує виклики користувача у ковзному вікні часу. Якщо ліміт перевищено, кидає виняток і метод **не виконується** (429) | `BookService.create` |
| `@Retry` | `RetryAspect` (`@Around`) | якщо метод кинув тимчасову помилку БД, повторює виклик до `maxAttempts` разів | `BookService.findAll/findById` |

**Чим відрізняються типи advice.**
- `@AfterReturning` спрацьовує після методу і не керує його виконанням.
- `@Around` обгортає метод і сам вирішує, чи викликати його (`proceed()`), скільки разів і що повернути.

### Як аспект знаходить методи
Pointcut `@annotation(audited)` означає: «спрацюй на кожному методі, що має анотацію `@Audited`». Тому анотації мають `@Retention(RUNTIME)`: інакше під час роботи програми вони були б невидимі.

### За що відповідають допоміжні класи

| Клас | Роль |
|---|---|
| `AuditTrail` | пам'ять, де лежать записи аудиту (останні 1000) |
| `AuditEntry` | один запис: час, користувач, дія, метод, деталі |
| `CurrentUserName` | дає ім'я користувача з контексту безпеки (або `anonymous`) |
| `AopConfig` | створює `Clock`, щоб у тестах можна було підставити свій час |
| `RateLimitExceededException` | виняток, який перетворюється на відповідь 429 |
| `AopDemoService` | демонстраційні методи для показу ліміту, повтору й самовиклику |
| `AopDemoController` | ендпоінти `/api/admin/aop-demo/*` (лише ADMIN) для демонстрації |

### Приклад: створення книги
`POST /api/books` → `BookService.create` проходить через аспекти: `RateLimitAspect` перевіряє ліміт → метод створює книгу → `AuditAspect` записує `CREATE_BOOK` від користувача. Якщо метод кинув виняток (наприклад, дублікат ISBN), запису в аудит немає.

### Проблема самовиклику (свідома)
- Виклик ззовні (`контролер` → `проксі` → метод): аспект спрацьовує, `auditEntriesAdded = 1`.
- Виклик усередині того ж класу (`this.auditedAction()`): йде повз проксі, аспект **мовчки не спрацьовує**, помилки немає, `auditEntriesAdded = 0`.

Це обмеження Spring AOP і навмисна демонстрація. Обхід: винести метод в інший бін, інжектити самого себе або AspectJ weaving.

### Як це перевіряється
- `AuditAspectTest`, `RateLimitAspectTest`, `RetryAspectTest`: `ProceedingJoinPoint` замокано, без Spring. Перевіряється, чи викликається `proceed()` і скільки разів.
- `SelfInvocationTest`: проксі будується програмно, показує різницю між зовнішнім викликом і самовикликом.
- Postman-колекція Lab 4: запити 06 і 07 поруч демонструють самовиклик, 08 це повтор, 12 це 429.
Реалізовано три наскрізні функціональності у вигляді пари «власна анотація + аспект». Кожну застосовано в робочому коді (`BookService`) і додатково показано в демонстраційних ендпоінтах. Окремо продемонстровано проблему самовиклику (self-invocation).

| Анотація | Аспект | Тип advice | Призначення |
|---|---|---|---|
| `@Audited(action)` | `AuditAspect` | `@AfterReturning` | після успішного виконання записує в аудит: хто, що й над чим зробив |
| `@RateLimited(maxCalls, perSeconds)` | `RateLimitAspect` | `@Around` | обмежує частоту викликів для користувача, інакше кидає виняток (429) |
| `@Retry(maxAttempts, retryOn)` | `RetryAspect` | `@Around` | повторює виклик при тимчасовій помилці |

.

## 3. Усі нові та змінені файли

### Нові файли (`src/main/java/com/example/library/`)

| Файл | Призначення |
|---|---|
| `aop/Audited.java` | анотація аудиту, атрибут `action` |
| `aop/RateLimited.java` | анотація обмеження частоти, атрибути `maxCalls` (10) та `perSeconds` (60) |
| `aop/Retry.java` | анотація повтору, атрибути `maxAttempts` (3) та `retryOn` (за замовчуванням `TransientDataAccessException`) |
| `aop/AuditAspect.java` | аспект аудиту |
| `aop/RateLimitAspect.java` | аспект обмеження частоти |
| `aop/RetryAspect.java` | аспект повтору |
| `aop/AuditEntry.java` | `record` запису аудиту: `timestamp`, `user`, `action`, `method`, `details` |
| `aop/AuditTrail.java` | сховище записів аудиту в пам'яті |
| `aop/CurrentUserName.java` | допоміжний клас: ім'я поточного користувача |
| `aop/AopDemoService.java` | демонстраційний сервіс (самовиклик, ліміт, retry) |
| `controller/AopDemoController.java` | демонстраційні ендпоінти `/api/admin/aop-demo/*` |
| `exception/RateLimitExceededException.java` | виняток для 429 |
| `config/AopConfig.java` | бін `Clock` |

Всі анотації мають `@Retention(RUNTIME)` (інакше аспект їх не побачить) і `@Target(METHOD)`.

### Змінені файли

| Файл | Що змінилося |
|---|---|
| `service/BookService.java` | `@Retry(maxAttempts = 3)` на `findAll` і `findById`; `@RateLimited(maxCalls = 10, perSeconds = 60)` та `@Audited(action = "CREATE_BOOK")` на `create`; `@Audited(action = "UPDATE_BOOK")` на `update`; `@Audited(action = "DELETE_BOOK")` на `delete`. Тіла методів не змінено |
| `exception/RestExceptionHandler.java` | `AopDemoController` додано в `assignableTypes`, новий метод `handleRateLimit` для 429 |
| `RestExceptionHandlerTest` (з Lab 2) | додано тест `handleRateLimit_rateLimitExceeded_returns429WithMessage` |
| `pom.xml` (необов'язково) | явна залежність `spring-boot-starter-aop` (вона й так приходить через `spring-boot-starter-data-jpa`) |

## 4. Опис кожного класу та його логіки

### 4.1. `AuditAspect`: `@AfterReturning`
Залежності: `AuditTrail` та `Clock` (через конструктор).

Метод `audit(joinPoint, audited, result)` з pointcut `@annotation(audited)`:
1. бере користувача (`CurrentUserName.get()`);
2. бере сигнатуру методу (`joinPoint.getSignature().toShortString()`, наприклад `BookService.create(..)`);
3. формує деталі `describe(result, args)`:
   - якщо результат це `BookDto`: `bookId=<id>, isbn=<isbn>`;
   - якщо перший аргумент це `Long` (випадок `delete`): `bookId=<id>`;
   - інакше `-`;
4. створює `AuditEntry` з часом `clock.instant()`, додає в `AuditTrail`, пише рядок у лог `AUDIT`.

`@AfterReturning` виконується **лише якщо метод завершився без винятку**. Якщо `@PreAuthorize` заборонив `delete` або ISBN дубльований, у аудит нічого не потрапляє.

### 4.2. `RateLimitAspect`: `@Around`
Залежність: `Clock`. Внутрішній стан: `Map<String, Deque<Instant>> calls` (потокобезпечна `ConcurrentHashMap`).

Метод `limit(joinPoint, rateLimited)`:
1. ключ = `користувач:метод`, тож ліміт окремий для кожного користувача й методу;
2. `windowStart = now - perSeconds`;
3. у синхронізованому блоці по черзі видаляє з початку черги часи, які вже не потрапляють у вікно;
4. якщо в черзі вже `maxCalls` записів, кидає `RateLimitExceededException` з текстом `Rate limit exceeded: at most N calls per M seconds for <метод>` і **не** викликає `proceed()`;
5. інакше додає поточний час і викликає `joinPoint.proceed()`.

Це «ковзне вікно». Час береться з `Clock`, тому в тестах його можна підмінити.

### 4.3. `RetryAspect`: `@Around`
Метод `retry(joinPoint, retry)`:
1. кількість спроб `max(1, maxAttempts)`;
2. у циклі викликає `joinPoint.proceed()`; при успіху одразу повертає результат;
3. якщо кинуто виняток, перевіряє `isRetryable` (чи його тип є в `retryOn`). Якщо ні, виняток кидається **одразу** без повторів. Якщо так, пише в лог `Attempt N/M ... failed` і пробує знову;
4. коли спроби вичерпано, кидає останній виняток.

Затримок між спробами немає (щоб тести не потребували `Thread.sleep`).

### 4.4. `AuditTrail`
Колекція `ArrayDeque<AuditEntry>` з методами `add`, `entries`, `size` (всі `synchronized`). Зберігає максимум 1000 останніх записів: при перевищенні видаляє найстаріший. Дані в пам'яті, після перезапуску губляться.

### 4.5. `CurrentUserName`
Пакетно-приватний допоміжний клас. `get()` бере `Authentication` із `SecurityContextHolder`. Якщо його немає, повертає `anonymous`, інакше `authentication.getName()`.

### 4.6. `AopConfig`
Оголошує бін `Clock` (`Clock.systemUTC()`). Аспекти отримують годинник через конструктор, завдяки чому в тестах підставляється фіксований час.

### 4.7. `RateLimitExceededException`
`RuntimeException` з `@ResponseStatus(TOO_MANY_REQUESTS)`. У REST-контролерах її перехоплює `RestExceptionHandler.handleRateLimit` і повертає `ProblemDetail` зі статусом 429.

### 4.8. `AopDemoService`
| Метод | Призначення |
|---|---|
| `auditedAction()` з `@Audited(action = "DEMO_ACTION")` | анотований метод |
| `callsAuditedActionInternally()` | викликає `this.auditedAction()`: **самовиклик** |
| `limitedAction()` з `@RateLimited(maxCalls = 3, perSeconds = 30)` | показ ліміту (3 виклики за 30 с) |
| `flakyLookup(counter, failuresBeforeSuccess)` з `@Retry(maxAttempts = 3)` | імітує тимчасову помилку: перші N викликів кидають `TransientDataAccessResourceException`, далі успіх. Повертає номер успішного виклику |

### 4.9. `AopDemoController` (`/api/admin/aop-demo`, лише ADMIN)
Шлях під `/api/admin/**`, тому правило `SecurityFilterChain` пропускає тільки ADMIN.

| Ендпоінт | Що робить |
|---|---|
| `GET /audit` | список записів аудиту |
| `GET /audited-external` | викликає `demoService.auditedAction()`, повертає `auditEntriesAdded` (різниця розміру журналу): **1** |
| `GET /audited-self-invocation` | викликає `callsAuditedActionInternally()`: **0** |
| `GET /rate-limited` | викликає `limitedAction()` |
| `GET /retry?failures=N` | викликає `flakyLookup` і повертає `attempts` |

## 5. Порядок аспектів

`@Order`: Retry (1), RateLimit (2), Audit (3). Менше число означає зовнішній шар. Вони виконуються раніше за транзакції та перевірки безпеки, тому:
- кожна спроба retry отримує свою транзакцію;
- аудит записується лише після успішного завершення методу (після транзакції й перевірки `@PreAuthorize`).

`@Retry` застосовано лише до читання (`findAll`, `findById`): повтор ідемпотентний. Повторювати `create` не можна, це могло б створити дублікати.

## 6. Самовиклик (self-invocation)

У `AopDemoService` метод `auditedAction()` має `@Audited`.
- **Зовнішній виклик** (контролер → проксі → метод): аспект спрацьовує, `auditEntriesAdded = 1`.
- **Самовиклик** (`callsAuditedActionInternally()` викликає `this.auditedAction()` в тому ж класі): виклик іде безпосередньо до об'єкта, повз проксі. Метод виконується, але аспект **мовчки не спрацьовує**, помилки немає, `auditEntriesAdded = 0`.

Це обмеження проксі-моделі Spring AOP і свідомий задум вправи. Обхід: винести метод в інший бін, інжектити самого себе або використати AspectJ weaving.

## 7. Тести (15 нових)

Жоден тест не використовує Spring-контекст, БД, мережу чи `Thread.sleep`.

### `aop/AuditAspectTest` (3)
`JoinPoint`, `Signature`, `AuditTrail` замокано, `Clock` фіксований, користувач задається через `TestingAuthenticationToken` у `SecurityContextHolder` (очищується після кожного тесту).

| Тест | Що перевіряє |
|---|---|
| `audit_createReturnsBookDto_recordsUserActionAndBookDetails` | `ArgumentCaptor` ловить `AuditEntry`: користувач `alice`, дія `CREATE_BOOK`, метод, деталі `bookId=7, isbn=...`, час |
| `audit_voidDeleteWithIdArgument_recordsBookIdFromArgument` | результат `null`, id береться з аргументів: `bookId=5` |
| `audit_noAuthentication_recordsAnonymousUser` | без автентифікації користувач `anonymous`, деталі `-` |

### `aop/RateLimitAspectTest` (4)
Замокано **`ProceedingJoinPoint`**. Власний `MutableClock` дає змогу «перемотати» час викликом `advance(...)` замість `Thread.sleep`. Ліміт у тестовій анотації 2 виклики за 30 с.

| Тест | Що перевіряє |
|---|---|
| `limit_callsWithinLimit_proceedAndReturnResult` | два виклики: `proceed()` викликано 2 рази, повернуто результат |
| `limit_callOverLimit_throwsAndDoesNotProceed` | третій виклик кидає `RateLimitExceededException`, `proceed()` лишається 2 рази |
| `limit_afterWindowExpires_allowsCallsAgain` | після `advance(31 с)` виклик знову дозволено |
| `limit_differentUsers_haveIndependentCounters` | інший користувач (`bob`) має власний лічильник |

### `aop/RetryAspectTest` (4)
Замокано `ProceedingJoinPoint`, перевіряється, скільки разів викликається `proceed()`.

| Тест | Що перевіряє |
|---|---|
| `retry_succeedsOnFirstAttempt_proceedsOnlyOnce` | 1 виклик |
| `retry_failsTwiceThenSucceeds_returnsResultAfterThreeAttempts` | два збої, потім успіх: 3 виклики, результат повернуто |
| `retry_alwaysFails_throwsLastExceptionAfterMaxAttempts` | 3 збої: кинуто останній виняток (`fail 3`), 3 виклики |
| `retry_nonRetryableException_rethrowsImmediatelyWithoutRetry` | `IllegalStateException` не з переліку: кинуто одразу, 1 виклик |

### `aop/SelfInvocationTest` (3)
Проксі будується програмно через `AspectJProxyFactory` (без Spring-контексту) з реальним `AuditAspect` і `AuditTrail`.

| Тест | Що перевіряє |
|---|---|
| `externalCall_annotatedMethod_aspectRecordsAuditEntry` | зовнішній виклик: 1 запис з дією `DEMO_ACTION` |
| `selfInvocation_sameAnnotatedMethod_aspectIsBypassedSilently` | самовиклик: метод виконався, записів немає, помилки немає |
| `sameMethodTwoWays_onlyTheExternalCallIsAudited` | обидва способи підряд дають рівно 1 запис |

### `RestExceptionHandlerTest` (+1)
`handleRateLimit_rateLimitExceeded_returns429WithMessage`: статус 429 і текст винятку.

## 8. Відповідність сценаріям із завдання

| Сценарій | Як виконано |
|---|---|
| Анотація з `RUNTIME` і `@Target` пов'язана з аспектом | три анотації, у кожному аспекті pointcut `@annotation(...)` |
| Поведінка аспекту спрацьовує | запис в аудит і лог; виняток 429; повторний виклик |
| Зовнішній виклик активує аспект | контролер → бін → проксі → advice; Postman 02, 06, 08, 09-12; `SelfInvocationTest` |
| Самовиклик не активує аспект | `callsAuditedActionInternally()` → `this.auditedAction()`; Postman 07; `SelfInvocationTest` |
| Advice тестується unit-тестом з mock `ProceedingJoinPoint` | `RateLimitAspectTest` та `RetryAspectTest`, без Spring-контексту, перевіряється чи (і скільки разів) викликається `proceed()` |

## 9. Запуск і перевірка

```
mvn clean test          # очікується: Tests run: 88 (73 + 15), Failures: 0
```
Ручна перевірка: Postgres, Keycloak, `LibraryApplication`, імпорт `postman/Library.lab4.postman_collection.json`, Run.

| Запит | Очікування |
|---|---|
| 02 | 201: `create` пройшов через `@RateLimited` та `@Audited` |
| 03 | є запис `CREATE_BOOK` від `admin` |
| 05 | є запис `DELETE_BOOK` |
| 06 | `auditEntriesAdded = 1` |
| 07 | `auditEntriesAdded = 0`, але 200 (самовиклик) |
| 08 | `attempts = 3` |
| 09-11 | 200 (у межах ліміту) |
| 12 | 429 з `Rate limit exceeded` |

У логах IntelliJ видно рядки `AUDIT : user=admin action=CREATE_BOOK ...` і `Attempt 1/3 ... failed`. Між двома запусками колекції потрібно зачекати понад 30 с через ліміт `/rate-limited`.

Результат: тестів пройшло ___, запитів зелених ___.

## 10. Обмеження

- `AuditTrail` зберігає записи в пам'яті (останні 1000), після перезапуску вони губляться.
- Rate limit рахується в пам'яті одного екземпляра (для кластера потрібне спільне сховище, наприклад Redis).
- `AuditAspect.describe` знає лише про `BookDto` та id-аргумент, для інших сутностей його потрібно розширити.
- Retry не має затримки між спробами (спрощення для детермінованих тестів).
