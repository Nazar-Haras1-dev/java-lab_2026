---

# Lab 2: Unit-тестування

## 1. Мета і що зроблено
## Як працює код і за що відповідає (коротко)

**Головна ідея.** У Lab 2 застосунок не змінювався. Додано тести, які перевіряють, що вже написаний код робить те, що від нього очікують.

**Як влаштований тест.**
1. Тест бере один клас (наприклад `BookService`).
2. Усе, від чого цей клас залежить (`BookRepository`, `BookMapper`), підміняється «підробками» (mock), які створює Mockito. Реальної бази немає.
3. Тест задає, що відповідають підробки (`when(...).thenReturn(...)`), викликає метод і перевіряє результат (`assertThat`) та те, що підробки викликали правильно (`verify`).

**За що відповідає кожен інструмент.**

| Інструмент | Роль |
|---|---|
| JUnit 5 | запускає методи з `@Test` |
| Mockito | створює підробки залежностей і перевіряє їх виклики |
| AssertJ | перевіряє результати й винятки |
| JaCoCo | показує, який відсоток коду виконано тестами |

**За що відповідає кожен тестовий клас.**

| Тестовий клас | Що перевіряє | Ключова ідея |
|---|---|---|
| `BookServiceTest` | усі методи сервісу: пошук, створення, оновлення, видалення, підрахунок | головний клас: правила унікальності ISBN, помилки «не знайдено» та «дублікат» |
| `BookRestControllerTest` | REST-контролер | лише передає виклики сервісу, формує статуси 201/204 і Location |
| `AdminRestControllerTest` | `/api/admin/stats` | повертає кількість книг із сервісу |
| `BookUiControllerTest` | веб-UI контролер | що кладеться в модель, redirect, помилки форми |
| `RestExceptionHandlerTest` | обробник помилок | виняток перетворюється на 404, 409, 400, 403 |
| `KeycloakRoleConverterTest` | конвертер ролей | `ADMIN` перетворюється на `ROLE_ADMIN` |
| `BookMapperTest` | мапер Entity і DTO | поля копіюються, `id` ігнорується |

**Типовий приклад.** Тест `create_duplicateIsbn_throwsDuplicateAndDoesNotSave`: репозиторій-підробка каже, що такий ISBN уже є, сервіс кидає `DuplicateResourceException`, а `verify(repository, never()).save(...)` доводить, що збереження не відбулось.

**Навіщо навмисно ламати код (AC4).** Якщо закоментувати перевірку дубліката в `BookService.create`, цей тест стає червоним. Це доводить, що тест справді перевіряє логіку, а не просто проходить.
Мета: переконатися, що бізнес-логіка з Lab 1 працює так, як задумано, і покрити код unit-тестами не менше ніж на 60%.

У `src/main` **нічого не змінено**. Додано:

| Що | Де | Навіщо |
|---|---|---|
| залежність `spring-boot-starter-test` | `pom.xml`, `<dependencies>` | містить JUnit 5, Mockito, AssertJ, Spring Test |
| плагін `jacoco-maven-plugin` 0.8.12 | `pom.xml`, `<build><plugins>` | вимірює покриття й будує HTML-звіт |
| 7 класів тестів (52 тести) | `src/test/java/com/example/library/...` | власне тести |

Класом бізнес-логіки визначено `BookService` (у ньому всі правила: унікальність ISBN, пошук, оновлення, видалення). Вимоги AC1, AC2 та AC8 виконані для всіх його 6 публічних методів. Решту класів покрито додатково для загального покриття.

## 2. Що таке unit-тест і як він працює в проєкті

Unit-тест перевіряє **один клас ізольовано**. Усе, від чого клас залежить, замінюється «підробкою» (mock). Тому тести:
- не потребують PostgreSQL, Keycloak, мережі чи файлів;
- не піднімають Spring-контекст (немає `@SpringBootTest`), виконуються за секунди;
- при падінні показують, який саме клас має помилку.

Схема для `BookService`:
```
BookServiceTest
  ├─ @Mock BookRepository      підробка (БД немає)
  ├─ @Mock BookMapper          підробка
  └─ @InjectMocks BookService  справжній клас, у конструктор якого Mockito підставив два моки
```

Кожен тест має три блоки (Arrange-Act-Assert):
1. **Arrange**: готуємо дані й задаємо відповіді моків (`when(...).thenReturn(...)`).
2. **Act**: викликаємо метод, який тестуємо.
3. **Assert**: перевіряємо результат (`assertThat`) і те, що моки викликали правильно (`verify`).

## 3. Інструменти

| Інструмент | Для чого |
|---|---|
| JUnit 5 (`@Test`) | запуск тестів |
| `@ExtendWith(MockitoExtension.class)` | підключає Mockito до JUnit 5. Працює в режимі strict stubs: якщо задано `when(...)`, який не використався, тест падає (не дає лишати зайві заглушки) |
| `@Mock` | створює підробку залежності |
| `@InjectMocks` | створює клас, що тестується, і підставляє в нього моки |
| `when(...).thenReturn/thenThrow` | задає, що відповість мок |
| `verify(mock).method(...)` | перевіряє, що метод мока викликано з потрібними аргументами |
| `verify(mock, never())` | перевіряє, що метод **не** викликано (побічного ефекту не було) |
| `verifyNoInteractions(mock)` | мок не використовувався взагалі |
| `ArgumentCaptor` | перехоплює об'єкт, переданий у мок, щоб перевірити його вміст |
| AssertJ (`assertThat`, `assertThatThrownBy`) | читабельні перевірки значень і винятків (тип і текст) |
| JaCoCo | рахує, які рядки та гілки виконались під час тестів |

Іменування: `метод_сценарій_очікуванийРезультат`, наприклад `create_duplicateIsbn_throwsDuplicateAndDoesNotSave`.

## 4. Опис кожного тестового класу

### 4.1. `service/BookServiceTest` (13 тестів): головний

У класі два допоміжні статичні методи: `book(id, isbn)` створює `Book`, `dto(id, isbn)` створює `BookDto` із типовими значеннями. Вони скорочують підготовку даних.

| Метод `BookService` | Тест | Що відбувається в тесті |
|---|---|---|
| `findAll` | `findAll_booksExist_returnsMappedDtos` | `repository.findAll()` повертає 2 книги, `mapper.toDto` для кожної повертає DTO. Перевіряється, що результат містить ці DTO в тому ж порядку |
| | `findAll_noBooks_returnsEmptyListWithoutMapping` | репозиторій повертає порожній список. Результат порожній, `verifyNoInteractions(mapper)`: мапер не викликався |
| `findById` | `findById_existingId_returnsMappedDto` | репозиторій знаходить книгу, мапер перетворює. Повернутий DTO той самий об'єкт (`isSameAs`) |
| | `findById_missingId_throwsResourceNotFound` | `findById` повертає `Optional.empty()`. Очікується `ResourceNotFoundException` з текстом `Book with id 99 not found`, мапер не викликано |
| `count` | `count_booksExist_returnsRepositoryCount` | повертає значення `repository.count()` (5) |
| | `count_noBooks_returnsZero` | граничний випадок: 0 |
| `create` | `create_newIsbn_savesMappedEntityAndReturnsDto` | `existsByIsbn` повертає false, мапер створює Entity, `save` повертає збережену. `ArgumentCaptor<Book>` ловить аргумент `save` і перевіряє ISBN, результат це DTO від мапера |
| | `create_duplicateIsbn_throwsDuplicateAndDoesNotSave` | `existsByIsbn` повертає true. Очікується `DuplicateResourceException` з текстом `Book with ISBN 111 already exists`, `save` не викликано, мапер не викликано |
| `update` | `update_existingBookUniqueIsbn_updatesEntityAndSaves` | книга знайдена, ISBN унікальний. Перевіряється `verify(mapper).updateEntity(input, existing)` і `verify(repository).save(existing)`, повернуто DTO |
| | `update_missingBook_throwsNotFoundAndDoesNotSave` | книги немає: виняток, перевірка ISBN і `save` не виконувались, мапер не викликано |
| | `update_isbnUsedByAnotherBook_throwsDuplicateAndDoesNotSave` | `existsByIsbnAndIdNot` повертає true: виняток, `updateEntity` і `save` не викликано |
| `delete` | `delete_existingId_deletesFoundEntity` | `verify(repository).delete(existing)`: видалено саме знайдену сутність |
| | `delete_missingId_throwsNotFoundAndDeletesNothing` | книги немає: виняток, `delete` не викликано |

### 4.2. `controller/BookRestControllerTest` (10 тестів)

`BookService` замінено моком, контролер створюється через `@InjectMocks`. Перевіряється, що контролер лише делегує виклики й формує відповідь.

| Тест | Що перевіряє |
|---|---|
| `getAll_booksExist_returnsListFromService` | повертає той самий список, що дав сервіс |
| `getAll_noBooks_returnsEmptyList` | порожній список |
| `getById_existingId_returnsDtoFromService` | повертає DTO із сервісу |
| `getById_serviceThrowsNotFound_propagatesException` | `ResourceNotFoundException` не гаситься в контролері (його перетворює на 404 `RestExceptionHandler`) |
| `create_validDto_returns201WithLocationAndBody` | статус 201, заголовок `Location: /api/books/7`, тіло дорівнює збереженому DTO, викликано `service.create` |
| `create_serviceThrowsDuplicate_propagatesException` | `DuplicateResourceException` пропускається далі (потім 409) |
| `update_validDto_returnsUpdatedDtoFromService` | викликано `service.update(3L, input)`, повернуто результат |
| `update_serviceThrowsNotFound_propagatesException` | виняток пропускається |
| `delete_existingId_callsServiceAndReturns204` | викликано `service.delete(5L)`, статус 204, тіла немає |
| `delete_serviceDeniesAccess_propagatesAccessDenied` | `AccessDeniedException` від сервісу не гаситься |

### 4.3. `controller/AdminRestControllerTest` (2 тести)

`stats_booksExist_returnsTotalBooksFromService` (3 книги дають `{"totalBooks": 3}`) та `stats_noBooks_returnsZero`.

### 4.4. `controller/BookUiControllerTest` (13 тестів)

UI-контролер викликається напряму. `Model` створюється як `ExtendedModelMap`, `BindingResult` як `BeanPropertyBindingResult`, `OidcUser` замокано.

| Тест | Що перевіряє |
|---|---|
| `home_anyRequest_redirectsToBooks` | `/` дає `redirect:/books` |
| `list_authenticatedUser_putsBooksUsernameAndOnlyAppRolesIntoModel` | у модель потрапляють книги, ім'я `admin` і ролі `["ROLE_ADMIN","ROLE_USER"]`. Службова `OIDC_USER` відфільтрована |
| `newForm_anyRequest_putsEmptyBookIntoModel` | у модель кладеться порожній `BookDto`, повертається `books/form` |
| `create_validBook_savesAndRedirects` | викликано `service.create`, redirect на `/books` |
| `create_bindingErrors_returnsFormAndDoesNotSave` | є помилка валідації: повертається форма, `service.create` не викликано |
| `create_duplicateIsbn_rejectsIsbnFieldAndReturnsForm` | при `DuplicateResourceException` помилка прив'язується до поля `isbn`, повертається форма |
| `editForm_existingId_putsBookIntoModel` | книга кладеться в модель |
| `editForm_missingId_propagatesNotFound` | виняток пропускається |
| `update_validBook_setsIdUpdatesAndRedirects` | `id` з URL записується в DTO, викликано `service.update`, redirect |
| `update_bindingErrors_returnsFormAndDoesNotUpdate` | форма, `update` не викликано |
| `update_duplicateIsbn_rejectsIsbnFieldAndReturnsForm` | помилка на поле `isbn` |
| `delete_existingId_callsServiceAndRedirects` | викликано `service.delete`, redirect |
| `delete_serviceDeniesAccess_propagatesAccessDenied` | виняток пропускається |

### 4.5. `exception/RestExceptionHandlerTest` (4 тести)

Без моків: обробник створюється через `new`, йому передаються винятки.

| Тест | Результат |
|---|---|
| `handleNotFound_resourceNotFound_returns404WithMessage` | статус 404, текст винятку |
| `handleDuplicate_duplicateResource_returns409WithMessage` | статус 409, текст винятку |
| `handleValidation_fieldErrors_returns400WithJoinedFieldMessages` | статус 400, текст `title: ...; author: ...` зібраний з помилок полів |
| `handleAccessDenied_accessDenied_returns403MentioningPreAuthorize` | статус 403, у тексті є `@PreAuthorize` |

### 4.6. `security/KeycloakRoleConverterTest` (4 тести)

| Тест | Що перевіряє |
|---|---|
| `extractAuthorities_rolesPresent_returnsRolePrefixedAuthorities` | `["ADMIN","USER"]` стає `ROLE_ADMIN`, `ROLE_USER` |
| `extractAuthorities_nullRealmAccess_returnsEmpty` | `null` дає порожній результат без помилки |
| `extractAuthorities_rolesKeyMissing_returnsEmpty` | немає ключа `roles` |
| `extractAuthorities_rolesNotACollection_returnsEmpty` | `roles` не колекція (рядок) |

### 4.7. `mapper/BookMapperTest` (6 тестів)

Тестується реальний клас `BookMapperImpl`, який MapStruct згенерував під час компіляції. Він створюється через `new`, Spring не потрібен.

| Тест | Що перевіряє |
|---|---|
| `toDto_book_mapsAllFields` | усі 5 полів Entity перенесено в DTO |
| `toEntity_dto_mapsFieldsAndIgnoresId` | поля перенесено, `id` лишається `null` |
| `updateEntity_dto_overwritesFieldsAndKeepsExistingId` | поля книги оновлено, її `id` не змінився |
| `toDto_null_returnsNull`, `toEntity_null_returnsNull` | `null` на вході дає `null` |
| `updateEntity_nullDto_leavesBookUnchanged` | `null` DTO не змінює книгу |

## 5. Відповідність Acceptance Criteria

| AC | Вимога | Як виконано |
|---|---|---|
| AC1 | тест звичайних даних із реальним assert | «щасливі» тести кожного методу `BookService`, кожен з `assertThat` над результатом |
| AC2 | тест невалідних, граничних даних, відмови | `missingId`, `duplicateIsbn`, `noBooks`, `count = 0`, `isbnUsedByAnotherBook`: перевіряються тип і текст винятку |
| AC3 | залежності через `@Mock`/`@InjectMocks`, без Spring | `@ExtendWith(MockitoExtension.class)`, немає `@SpringBootTest` |
| AC4 | навмисне ламання логіки, тест падає, логіку повернено | див. розділ 7 |
| AC5 | без БД, мережі, файлів, `Thread.sleep`, незалежність від порядку | лише об'єкти в пам'яті, кожен тест створює власні дані |
| AC6 | імена і Arrange-Act-Assert | формат `метод_сценарій_результат`, коментарі `// Arrange`, `// Act`, `// Assert` |
| AC7 | `verify` / `ArgumentCaptor` для побічних ефектів | `verify(repository).save` з `ArgumentCaptor`, `verify(mapper).updateEntity`, `verify(repository).delete`, `verify(..., never())` у негативних сценаріях |
| AC8 | AC1 і AC2 для кожного публічного методу | `BookService`: `findAll`, `findById`, `count`, `create`, `update`, `delete`, у кожного є і нормальний, і негативний чи граничний тест |

## 6. Запуск і покриття

```
mvn clean test
```
Або IntelliJ: Maven, Lifecycle, `test`. Postgres і Keycloak не потрібні.

Результат: `Tests run: 52, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

Звіт JaCoCo: `target/site/jacoco/index.html`. Total coverage: **___%** (заповнити після запуску).

Не покрито: `LibraryApplication`, `SecurityConfig`, `KeycloakOidcUserService`. Вони потребують Spring-контексту або живого Keycloak, що суперечить AC3 і AC5.

## 7. Перевірка якості тестів (AC4)

Логіку `BookService` навмисно ламали, тести падали, потім код повернули:
- в `create` закоментовано перевірку `existsByIsbn`. `create_duplicateIsbn_throwsDuplicateAndDoesNotSave` став червоним;
- в `delete` замість `repository.delete(getEntity(id))` лишили `getEntity(id)`. `delete_existingId_deletesFoundEntity` став червоним.

Після відкату всі 52 тести знову зелені. Зламаний код не комітився. Скріншоти збережено як доказ.

## 8. Обмеження

`@PreAuthorize` на `BookService.delete` у unit-тестах не діє: анотація працює через Spring-проксі, а контекст не піднімається. Ця поведінка перевіряється запитом 10 у Postman-колекції Lab 1.
