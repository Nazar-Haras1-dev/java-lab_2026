# Lab 6: Quartz Scheduler (JDBC job store, Liquibase)

## 1. Мета і що зроблено

Регулярні завдання виконуються за розкладом, який **зберігається в PostgreSQL** і переживає перезапуск; нова версія застосунку не скасовує й не дублює завдання.

Трактування (де завдання неоднозначне):
- У проєкті немає замовлень, тому «рутинні задачі» обрано з предметної області Library: **щоденний знімок каталогу** (скільки книг у бібліотеці, таблиця `catalog_snapshots`) і **очищення старих знімків**.
- Завдання з двома тригерами: знімок каталогу (щоденний cron + страхувальний simple-тригер «кожні 6 годин і при старті»).
- Стратегія збою: **фіксація помилки й очікування наступного запланованого запуску** (`JobExecutionException(e, false)`), без повторів і без нескінченного циклу.
- Невідомий/відсутній параметр: ключа немає -> документоване значення за замовчуванням; значення неправильного типу/формату -> явний виняток (завдання не виконується).
- Для ідемпотентності тест рівня сервісу з Mockito (без БД); справжню БД перевірено живим запуском і Postman.
- `Clock` береться із існуючого `AopConfig` (UTC) і переводиться в `Europe/Kyiv` усередині сервісу, щоб дата знімка збігалась з часовим поясом cron.

## 2. Нові та змінені файли

| Файл | Призначення | Що змінилось |
|---|---|---|
| `pom.xml` | залежність `spring-boot-starter-quartz` | змінено |
| `resources/application.yml` | `spring.quartz.*` (jdbc, `initialize-schema: never`, `overwrite-existing-jobs`, PostgreSQL delegate) | змінено |
| `db/changelog/db.changelog-master.yaml` | підключення двох нових changeset | змінено |
| `db/changelog/changes/002-create-quartz-tables.yaml` + `002-quartz-tables.sql` | 11 таблиць `QRTZ_*` (офіційний скрипт Quartz 2.3.2 для PostgreSQL, без DROP) + rollback | нове |
| `db/changelog/changes/003-create-catalog-snapshots-table.yaml` | таблиця `catalog_snapshots`, `snapshot_date` UNIQUE | нове |
| `library/scheduling/CatalogSnapshot.java`, `CatalogSnapshotRepository.java` | сутність і репозиторій знімків | нове |
| `library/scheduling/CatalogSnapshotService.java` | уся логіка: `takeSnapshot`, `purgeOlderThan`, `findAll` | нове |
| `library/scheduling/JobParams.java` | читання параметрів: default або явний виняток | нове |
| `library/scheduling/CatalogSnapshotJob.java`, `SnapshotRetentionJob.java` | два легкі завдання | нове |
| `library/scheduling/QuartzConfig.java` | `JobDetail` + `Trigger` бінів в одному `@Configuration` | нове |
| `controller/QuartzAdminController.java` | ADMIN: `GET /api/admin/quartz/jobs`, `POST .../jobs/catalog-snapshot/run`, `GET .../snapshots` | нове |
| `test/.../scheduling/*`, `QuartzAdminControllerTest` | нові тести | нове |
| `postman/Library.lab6.postman_collection.json` | 9 запитів з тестами | нове |

## 3. Як працює код і за що відповідає (простими словами)

1. `QuartzConfig` в одному місці оголошує 2 `JobDetail` і 3 `Trigger`. Кожен тригер вказує на завдання через `JobKey`, усі мають імʼя та групу `library-maintenance`.
2. Liquibase (changeset 002) створює таблиці `QRTZ_*`; Spring Boot налаштований `initialize-schema: never`, тобто сам таблиць не створює. `job-store-type: jdbc` кладе розклад у БД.
3. Тригер у момент запуску **перекриває** значення `JobDataMap` завдання: `catch-up` замість `daily`, `180` днів замість `90`. Решта ключів лишається за замовчуванням.
4. `CatalogSnapshotJob` і `SnapshotRetentionJob` «легкі»: читають параметри й **один раз** викликають `CatalogSnapshotService`. Транзакція проходить на рівні методу сервісу.
5. `@DisallowConcurrentExecution`: наступний запуск чекає завершення попереднього.
6. Сервіс ідемпотентний: другий запуск за ту саму дату нічого не змінює (унікальний ключ `snapshot_date` + перевірка).

## 4. Детальний опис кожного класу й методу

- **`QuartzConfig`**: `catalogSnapshotJobDetail` (дефолти `source=daily`, `triggeredBy=scheduler`), `catalogSnapshotDailyTrigger` (cron `0 0 2 * * ?`, `Europe/Kyiv`, misfire `FIRE_ONCE_NOW`), `catalogSnapshotCatchUpTrigger` (кожні 6 годин, `startNow`, `source=catch-up`, misfire `RESCHEDULE_NEXT_WITH_REMAINING_COUNT`), `snapshotRetentionJobDetail` (`retentionDays=90`), `snapshotRetentionWeeklyTrigger` (неділя 03:00 Kyiv, `retentionDays=180`, misfire `DO_NOTHING`).
- **`CatalogSnapshotService.takeSnapshot(source)`**: дата за Києвом; якщо знімок на цю дату є, повертає `false` і нічого не міняє; інакше зберігає кількість книг. `purgeOlderThan(days)` видаляє знімки старші за `days` (days <= 0 -> `IllegalArgumentException`).
- **`JobParams`**: `string` і `positiveInt`. Немає ключа (зокрема помилка в імені ключа) -> default; не той тип, порожній рядок, нечисло, <= 0 -> `IllegalArgumentException`.
- **Завдання**: `execute` бере `getMergedJobDataMap()` (дані тригера поверх даних завдання), викликає сервіс один раз, `RuntimeException` -> лог + `JobExecutionException(e, false)`.
- **Страхувальний тригер**: якщо вночі застосунок не працював, знімок усе одно зʼявиться. Дубля не буде, бо сервіс ідемпотентний.

- **`CatalogSnapshot`** (сутність, таблиця `catalog_snapshots`): `snapshotDate` (UNIQUE), `bookCount`, `source`, `createdAt`.
- **`CatalogSnapshotRepository`**: `existsBySnapshotDate`, `findAllByOrderBySnapshotDateDesc`, `deleteOlderThan(cutoff)` (`@Modifying`).
- **`CatalogSnapshotJob.execute` / `SnapshotRetentionJob.execute`**: описані вище (читання `getMergedJobDataMap()`, один виклик сервісу, `JobExecutionException(e, false)` при збої).
- **`QuartzAdminController`**: `GET /api/admin/quartz/jobs` (завдання, тригери, misfire, часовий пояс, `JobDataMap`), `POST /api/admin/quartz/jobs/catalog-snapshot/run[?source=]` (негайний запуск, 202), `GET /api/admin/quartz/snapshots`; лише для ADMIN.
- **Liquibase**: `002-create-quartz-tables.yaml` (+ `002-quartz-tables.sql`, офіційна схема Quartz 2.3.2 для PostgreSQL, `rollback` видаляє таблиці) і `003-create-catalog-snapshots-table.yaml`.

## 5. Тести

Усього 161 тест (121 було + 40 нових). Нижче всі нові тести. Без `@SpringBootTest`, без `Thread.sleep`, час через `Clock.fixed`.

### JobParamsTest

| Тест | Що перевіряє |
|---|---|
| `string_keyMissing_returnsDocumentedDefault` | ключа немає: повертається значення за замовчуванням |
| `string_keyPresent_returnsValue` | ключ є: повертається його значення |
| `string_keyWithTypoInName_isTreatedAsMissingAndFallsBackToDefault` | помилка в імені ключа = ключа немає: значення за замовчуванням |
| `string_wrongTypeOrBlank_throwsIllegalArgument` | неправильний тип або порожній рядок: `IllegalArgumentException` |
| `positiveInt_keyMissing_returnsDefault` | число: ключа немає - значення за замовчуванням |
| `positiveInt_integerOrNumericString_isParsed` | `Integer` і рядок-число розбираються правильно |
| `positiveInt_notANumber_throwsIllegalArgument` | не число: `IllegalArgumentException` |
| `positiveInt_zeroOrNegative_throwsIllegalArgument` | 0 і від'ємні значення: `IllegalArgumentException` |

### CatalogSnapshotServiceTest

| Тест | Що перевіряє |
|---|---|
| `takeSnapshot_noSnapshotForToday_savesCountWithKyivDateAndReturnsTrue` | знімка немає: зберігає кількість книг із київською датою (23:30 UTC = наступний день), повертає `true` |
| `takeSnapshot_snapshotForTodayExists_changesNothingAndReturnsFalse` | знімок за сьогодні вже є: нічого не змінює, повертає `false` |
| `takeSnapshot_runTwiceForSameDay_secondRunChangesNothing` | **ідемпотентність**: два запуски за день дають рівно один `save` |
| `purgeOlderThan_validDays_deletesBeforeCutoffAndReturnsCount` | видаляє знімки старші за межу і повертає кількість |
| `purgeOlderThan_runTwice_secondRunRemovesNothing` | повторне очищення видаляє 0 |
| `purgeOlderThan_nonPositiveDays_throwsAndDeletesNothing` | днів <= 0: виняток, нічого не видаляється |
| `findAll_snapshotsExist_returnsRepositoryOrder` | повертає знімки в порядку репозиторію |

### CatalogSnapshotJobTest

| Тест | Що перевіряє |
|---|---|
| `execute_sourceProvided_callsServiceExactlyOnceWithIt` | завдання викликає сервіс рівно один раз із переданим `source` |
| `execute_sourceMissing_usesDocumentedDefaultDaily` | `source` немає: використовується `daily` |
| `execute_sourceHasWrongType_failsWithoutRefireAndDoesNotCallService` | `source` неправильного типу: `JobExecutionException` без refire, сервіс не викликано |
| `execute_serviceThrows_errorIsRecordedWithoutRefireAndServiceCalledOnlyOnce` | збій сервісу: помилка фіксується, refire немає, сервіс викликано один раз (нескінченного циклу немає) |

### SnapshotRetentionJobTest

| Тест | Що перевіряє |
|---|---|
| `execute_retentionDaysProvided_callsServiceOnceWithIt` | викликає `purgeOlderThan` один раз із переданим числом днів |
| `execute_retentionDaysMissing_usesDocumentedDefault90` | `retentionDays` немає: використовується 90 |
| `execute_retentionDaysMalformed_failsWithoutCallingService` | `"ninety"`: виняток, сервіс не викликано |
| `execute_serviceThrows_noRefireAndServiceCalledOnce` | збій сервісу: без refire, один виклик |

### QuartzConfigTest

| Тест | Що перевіряє |
|---|---|
| `jobDetails_declared_haveExplicitNameAndGroup` | завдання мають явні `name` і `group`, правильні класи |
| `triggers_declared_haveExplicitUniqueNamesAndPointToExistingJobsByJobKey` | тригери мають унікальні імена й групу та вказують на існуючі завдання через `JobKey` |
| `snapshotJob_hasTwoTriggers` | у завдання знімка рівно два тригери |
| `mergedJobDataMap_triggerOverridesOneKey_otherJobDefaultsStay` | злиття `JobDataMap` як у Quartz: тригер перекриває `source`, `triggeredBy` лишається за замовчуванням |
| `mergedJobDataMap_triggerWithoutOverride_usesJobDefaults` | тригер без перекриття: значення завдання |
| `mergedJobDataMap_retentionTrigger_overridesDefault90With180` | `retentionDays` 90 перекрито тригером до 180 |
| `jobDetails_declared_disallowConcurrentExecution` | `@DisallowConcurrentExecution` на обох завданнях |
| `dailyTrigger_misfireInstruction_isFireOnceNow` | misfire щоденного тригера: `FIRE_ONCE_NOW` |
| `catchUpTrigger_misfireInstruction_isRescheduleNextWithRemainingCount` | misfire страхувального тригера: `RESCHEDULE_NEXT_WITH_REMAINING_COUNT` |
| `weeklyRetentionTrigger_misfireInstruction_isDoNothing` | misfire тижневого тригера: `DO_NOTHING` |
| `cronTriggers_declared_haveExplicitKyivTimeZoneNotGmt` | ідентифікатор часового поясу cron-тригерів: `Europe/Kyiv` |
| `timeZone_unknownId_isSilentlyReplacedByGmtSoConfiguredIdMustBeVerified` | невідомий ідентифікатор замінюється на `GMT`, тому справжній ID перевіряється |
| `dailyTrigger_nextFireTime_is0200KyivRegardlessOfServerZone` | наступний запуск о 02:00 за Києвом влітку (UTC+3) і взимку (UTC+2) |

### QuartzAdminControllerTest

| Тест | Що перевіряє |
|---|---|
| `runSnapshot_noSource_triggersJobWithoutOverrideAndReturns202` | `POST .../run`: 202, завдання запущено без перекриття |
| `runSnapshot_withSource_passesSourceOverrideInJobDataMap` | `?source=manual`: перекриття передано в `JobDataMap` |
| `snapshots_snapshotsExist_returnsThemAsJson` | `GET snapshots`: знімки в JSON (дата як ISO-рядок) |
| `jobs_cronTriggerScheduled_returnsJobWithTriggerMisfireAndTimeZone` | `GET jobs`: завдання з тригером, misfire та часовим поясом |

## 6. Відповідність Acceptance Criteria

| Сценарій | Як виконано | Де видно |
|---|---|---|
| Завдання й тригери в одному `@Configuration`, `JobKey`, name+group | `QuartzConfig` | `QuartzConfigTest` (імена/групи, `JobKey`); Postman 03 |
| Параметри тригера мають пріоритет | `usingJobData` у тригері, `getMergedJobDataMap()` | `mergedJobDataMap_*` тести; Postman 03 (`data.source`, `retentionDays`) |
| Завдання «легкі» | читання параметрів + один виклик сервісу | `CatalogSnapshotJobTest`, `verifyNoMoreInteractions(service)` |
| Неправильний/відсутній ключ | default або `IllegalArgumentException` | `JobParamsTest`, `execute_sourceMissing_...`, `execute_sourceHasWrongType_...` |
| Заборона паралельного виконання | `@DisallowConcurrentExecution` | `jobDetails_declared_disallowConcurrentExecution`; у БД `is_nonconcurrent = t` |
| Misfire визначено для кожного тригера | три різні інструкції | `*_misfireInstruction_*` тести; Postman 03; `qrtz_triggers.misfire_instr` |
| Cron не залежить від часового поясу сервера | `inTimeZone(Europe/Kyiv)` | `cronTriggers_declared_haveExplicitKyivTimeZoneNotGmt`; `qrtz_cron_triggers.time_zone_id` |
| Повторний запуск безпечний | перевірка за датою + UNIQUE | `takeSnapshot_runTwiceForSameDay_...`; Postman 04-07 |
| Невдале виконання обробляється | лог + `JobExecutionException(e, false)`, без повторів | `execute_serviceThrows_...` (сервіс викликано 1 раз) |
| Розклад у БД через Liquibase | changeset 002 + `initialize-schema: never` | `qrtz_*` у БД (11 таблиць), `DATABASECHANGELOG` |

## 7. Запуск і перевірка

```
mvn clean test        # очікується: Tests run: 161, Failures: 0, BUILD SUCCESS
```
Живий запуск: Postgres, Keycloak, `LibraryApplication` (Liquibase створить `QRTZ_*` і `catalog_snapshots`), потім `postman/Library.lab6.postman_collection.json` (Run; всі 9 запитів зелені, 20 перевірок). Перевірка в БД:
```
select job_name, is_nonconcurrent from qrtz_job_details;
select trigger_name, trigger_type, misfire_instr from qrtz_triggers;
select cron_expression, time_zone_id from qrtz_cron_triggers;
```
Очікується 2 завдання, 3 тригери (cron/cron/simple), misfire 1/2/4, часовий пояс `Europe/Kyiv`. Після перезапуску застосунку кількість завдань і тригерів не змінюється (`overwrite-existing-jobs: true`). У логах: `CatalogSnapshotJob: source=catch-up ... created=true`, а наступні запуски `created=false`.

Результат: тестів 161 (Failures 0, Errors 0), Postman 9/9.

## 8. Обмеження та примітки

- Ідемпотентність у юніт-тесті перевірена з Mockito; реальну БД перевірено живим запуском і Postman, окремого інтеграційного тесту з БД немає (за умовою без Postgres юніт-тести працюють).
- Кластерного режиму немає (`isClustered=false`), один екземпляр застосунку.
- Cron-розклад (02:00, неділя 03:00) у Postman не чекається; завдання запускаються вручну через `POST .../run`, а розклад видно в `GET .../jobs`.
- Страхувальний тригер із `startNow` спрацьовує при кожному старті застосунку (безпечно через ідемпотентність).
- Єдиний `Clock` проєкту (UTC з `AopConfig`) не змінювався; київська зона застосовується тільки в `CatalogSnapshotService`.
