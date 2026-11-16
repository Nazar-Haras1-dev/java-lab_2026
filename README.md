# Lab 7: Multi-module Maven (розділення на сервіси)

## 1. Мета і що зроблено

Один репозиторій і один процес збірки (`mvn verify` з кореня) охоплюють кілька незалежних сервісів. Кожен сервіс має власний `main`, `application.yml`, порт, тести й Dockerfile. Кореневий POM лише обʼєднує модулі та містить спільні елементи.

| Модуль | Роль | Порт | Main-клас |
|---|---|---|---|
| `common` | спільна бібліотека: `BookDto` і обмеження `@ValidIsbn` (без Spring Boot плагіна, без `main`) | - | - |
| `library-service` | книги (REST + UI), Keycloak-безпека, AOP, асинхронний звіт (Lab 1-5) | 8080 | `LibraryApplication` |
| `scheduler-service` | Quartz: щоденний знімок каталогу та очищення знімків (Lab 6) | 8081 | `SchedulerApplication` |

Трактування (де завдання неоднозначне):
- Сервіси виділено за межами, які вже були в коді: Quartz-частина з Lab 6 стала окремим `scheduler-service`, решта лишилась у `library-service`.
- `scheduler-service` раніше рахував книги через `BookRepository`. Щоб не залежати від `library-service`, він читає кількість через порт `BookCountProvider`; реалізація `JdbcBookCountProvider` виконує `SELECT count(*) FROM books` (лише читання, без дубля сутності). Обидва сервіси використовують одну БД `library` (спрощення для навчальної роботи; у реальному середовищі краще HTTP-виклик).
- Невелика утиліта `KeycloakRoleConverter` (~15 рядків) **скопійована** в `scheduler-service`, а не винесена у `common`, бо `common` має містити лише спільні об'єкти (DTO), а не код безпеки. Це свідомий компроміс автономності сервісів.
- До `common` потрапили `BookDto` та його анотація `@ValidIsbn` з валідатором, бо обмеження є частиною опису DTO. Пакети лишились `com.example.library.dto` і `com.example.library.validation`, тож імпорти в коді не змінились. Поки що використовує лише `library-service`.
- Liquibase: кожен сервіс має власний `db.changelog-master.yaml` (library: `001`, scheduler: `002` і `003`); шляхи й id changeset не змінились, тому існуючу БД перезаливати не треба.
- Dockerfile складено, але образи в цій роботі не збирались і не запускались (Docker на машині не використовується).

## 2. Нові та змінені файли

| Файл | Призначення | Що змінилось |
|---|---|---|
| `pom.xml` | батьківський POM: `<modules>`, спільні залежності, JaCoCo | переписано (було один модуль) |
| `common/pom.xml` | jar-модуль спільних об'єктів | нове |
| `common/src/main/java/com/example/library/dto/BookDto.java` | DTO книги | перенесено (`git mv`) |
| `common/src/main/java/com/example/library/validation/*` | `@ValidIsbn`, `IsbnValidator` | перенесено |
| `common/src/test/.../IsbnValidationTest.java` | тест валідації ISBN | перенесено |
| `library-service/pom.xml`, `Dockerfile` | залежності сервісу, Spring Boot плагін, `finalName` | нове |
| `library-service/src/**` | увесь код Lab 1-5 | перенесено (`git mv src library-service/src`) |
| `library-service/src/main/resources/application.yml` | без блоку `spring.quartz` | змінено |
| `library-service/src/main/resources/db/changelog/db.changelog-master.yaml` | лише `001` | змінено |
| `library-service/src/test/.../ApplicationConfigTest.java` | порт 8080, немає quartz, `@SpringBootApplication` | нове |
| `scheduler-service/pom.xml`, `Dockerfile` | залежності (quartz, jpa, security...), Spring Boot плагін | нове |
| `scheduler-service/src/main/java/com/example/scheduler/*` | код Lab 6 (пакет `com.example.library.scheduling` -> `com.example.scheduler`) | перенесено |
| `.../SchedulerApplication.java`, `SchedulerConfig.java` | main-клас, бін `Clock` (UTC) | нове |
| `.../BookCountProvider.java`, `JdbcBookCountProvider.java` | порт і реалізація підрахунку книг | нове |
| `.../security/SecurityConfig.java`, `KeycloakRoleConverter.java` | JWT-безпека сервісу, `/api/admin/**` для ADMIN | нове |
| `scheduler-service/src/main/resources/application.yml` | порт 8081, quartz, БД, JWT | нове |
| `scheduler-service/src/main/resources/db/changelog/**` | `002`, `003` + власний master | перенесено/нове |
| `scheduler-service/src/test/**` | тести Lab 6 + `JdbcBookCountProviderTest`, `ApplicationConfigTest`, `KeycloakRoleConverterTest` | перенесено/нове |
| `postman/Library.lab6.postman_collection.json` | запити до планувальника йдуть на `{{schedulerUrl}}` (8081) | змінено |
| `postman/Library.lab7.postman_collection.json` | перевірка обох сервісів | нове |

## 3. Як працює код і за що відповідає (простими словами)

1. Кореневий `pom.xml` (`packaging=pom`) у `<modules>` перелічує `common`, `library-service`, `scheduler-service`. У ньому ж: `spring-boot-starter-test` у `<dependencies>` (потрібен усім) і плагін JaCoCo.
2. Кожен модуль має `<parent>` на кореневий POM. Плагін Spring Boot оголошено у власних POM обох сервісів (у `common` його немає).
3. `library-service` залежить тільки від `common`. `scheduler-service` не залежить від жодного внутрішнього модуля.
4. Сервіси запускаються й тестуються окремо: `mvn -pl scheduler-service -am test`, `java -jar library-service/target/library-service.jar`.

## 4. Детальний опис кожного класу й методу

- **Кореневий POM**: не має коду, `packaging=pom`; `spring-boot-starter-test` оголошено тут один раз (модулі його не дублюють); Spring Boot parent версії 3.3.5, Java 17.
- **`common`**: `BookDto` (поля та обмеження `@NotBlank/@Size/@Min/@Max/@ValidIsbn`), `ValidIsbn` (`RUNTIME`, `FIELD`) та `IsbnValidator` (контрольна сума ISBN-10/13). Залежить лише від `spring-boot-starter-validation`.
- **`library-service`**: той самий код, що й у Lab 5, нічого не знає про Quartz.
- **`scheduler-service`**: `CatalogSnapshotService` бере кількість книг із `BookCountProvider`, а не з `BookRepository`; решта логіки (ідемпотентність, зона `Europe/Kyiv`, misfire, `@DisallowConcurrentExecution`) без змін.
- **Dockerfile**: `FROM eclipse-temurin:17-jre`, копіює `target/<service>.jar`, `EXPOSE` порту сервісу.

- **`SchedulerApplication`**: main-клас `scheduler-service` (`@SpringBootApplication`, порт 8081).
- **`SchedulerConfig.clock()`**: бін `Clock.systemUTC()` (у `library-service` свій такий бін в `AopConfig`).
- **`security/SecurityConfig.apiFilterChain`**: stateless JWT (resource server), `/api/admin/**` лише для ADMIN, решта - автентифікований користувач.
- **`security/KeycloakRoleConverter.extractAuthorities`**: `realm_access.roles` -> `ROLE_*` (копія з `library-service`).
- **`BookCountProvider.countBooks()`** і **`JdbcBookCountProvider`**: порт і реалізація (`SELECT count(*) FROM books`), щоб не залежати від коду `library-service`.

## 5. Тести

Усього 173 тести: `common` 9, `library-service` 115, `scheduler-service` 49. Тести лаб 2-6 переїхали разом із кодом без змін (крім `CatalogSnapshotServiceTest`, який тепер мокає `BookCountProvider`, а не `BookRepository`); `IsbnValidationTest` (9) тепер у модулі `common`. Нижче нові тести цієї лаби.

### ApplicationConfigTest (library-service)

| Тест | Що перевіряє |
|---|---|
| `applicationYml_ownFile_definesPort8080` | власний `application.yml` із портом 8080 |
| `applicationYml_libraryService_hasNoQuartzSettings` | у `library-service` немає налаштувань Quartz |
| `mainClass_libraryApplication_isSpringBootApplication` | `LibraryApplication` - власний main-клас (`@SpringBootApplication`) |

### ApplicationConfigTest (scheduler-service)

| Тест | Що перевіряє |
|---|---|
| `applicationYml_ownFile_definesPort8081DifferentFromLibraryService` | власний `application.yml` із портом 8081 (не 8080) |
| `applicationYml_quartz_usesJdbcStoreAndNeverInitializesSchema` | `job-store-type: jdbc`, `initialize-schema: never` |
| `mainClass_schedulerApplication_isSpringBootApplication` | `SchedulerApplication` - власний main-клас (`@SpringBootApplication`) |

### JdbcBookCountProviderTest (scheduler-service)

| Тест | Що перевіряє |
|---|---|
| `countBooks_tableHasRows_returnsCountFromReadOnlySelect` | один `SELECT count(*) FROM books`, повертає кількість |
| `countBooks_driverReturnsNull_returnsZero` | `null` від драйвера -> 0 |

### KeycloakRoleConverterTest (копія в scheduler-service)

| Тест | Що перевіряє |
|---|---|
| `extractAuthorities_rolesPresent_returnsRolePrefixedAuthorities` | ролі `ADMIN/USER` стають `ROLE_ADMIN/ROLE_USER` |
| `extractAuthorities_nullRealmAccess_returnsEmpty` | `realm_access = null`: порожній список |
| `extractAuthorities_rolesKeyMissing_returnsEmpty` | ключа `roles` немає: порожній список |
| `extractAuthorities_rolesNotACollection_returnsEmpty` | `roles` не колекція: порожній список |

## 6. Відповідність Acceptance Criteria

| Сценарій | Як виконано | Де видно |
|---|---|---|
| 1. Кореневий POM `packaging=pom`, `<modules>` перелічує всі сервіси, `<parent>` модулів вказує на корінь, `mvn verify` зелений | `pom.xml`; `<parent>` у трьох модулях на `library-parent` | `mvn clean verify`: Reactor Summary, усі модулі SUCCESS, BUILD SUCCESS |
| 2. Спільна залежність у кореневому POM, не в модулях | `spring-boot-starter-test` лише в кореневому `<dependencies>` | `grep -l spring-boot-starter-test */pom.xml pom.xml` -> лише `pom.xml` |
| 3. Власний main, `application.yml`, порт; Spring Boot плагін у POM модуля | `LibraryApplication` (8080), `SchedulerApplication` (8081); плагін у `library-service/pom.xml` і `scheduler-service/pom.xml` | `ApplicationConfigTest` в обох; `java -jar` обох jar |
| 4. `dependency:tree`: сервіси не залежать один від одного, спільний лише `common` | `library-service` -> `common`; `scheduler-service` -> нічого внутрішнього; `common` без внутрішніх | `mvn dependency:tree -pl <module>`, див. нижче |

## 7. Запуск і перевірка

```
mvn clean verify                                   # з кореня: очікується Tests run: 9 + 115 + 49, BUILD SUCCESS
mvn dependency:tree -pl library-service            # серед com.example лише com.example:common
mvn dependency:tree -pl scheduler-service          # жодного com.example-модуля (окрім самого себе)
mvn -pl scheduler-service -am test                 # окрема збірка й тест одного сервісу (+ common)
grep -l spring-boot-starter-test */pom.xml pom.xml # лише pom.xml
```
Запуск: Postgres, Keycloak, потім
```
java -jar library-service/target/library-service.jar      # 8080
java -jar scheduler-service/target/scheduler-service.jar  # 8081
```
Postman: `Library.lab7.postman_collection.json` (9 запитів), `Library.lab6...` (планувальник на 8081), `Library.lab5...` (8080).

Результат: тестів 173 (Failures 0), `mvn verify` BUILD SUCCESS.

## 8. Обмеження та примітки

- Обидва сервіси ділять одну БД `library` (`scheduler-service` читає таблицю `books`). Для справжніх мікросервісів краще HTTP-виклик або події.
- `KeycloakRoleConverter` продубльовано в `scheduler-service` (свідомо, щоб не тягнути код безпеки в `common`).
- Dockerfile не збирались і не запускались; `docker-compose.yml` у корені, як і раніше, лише для Postgres і Keycloak.
- `common` зараз використовує один сервіс; він залишено, бо це єдине місце для спільного DTO.
- Тести Lab 1-6 переїхали разом із кодом; їх склад змінився лише там, де треба (`CatalogSnapshotServiceTest`).
