---

# Lab 2: Unit-тестування

## 1. Мета і що зроблено

Мета: переконатися, що бізнес-логіка, написана в Lab 1, поводиться так, як задумано, і покрити код unit-тестами не менше ніж на 60%.

Нового коду в `src/main` не додавалося. Додано:
- залежність `spring-boot-starter-test` та плагін `jacoco-maven-plugin` у `pom.xml`;
- папку `src/test/java/com/example/library/` із 7 класами тестів (52 тести).

Класом бізнес-логіки є `BookService`. Для нього виконано вимоги AC1, AC2 та AC8 для всіх 6 публічних методів. Решту класів покрито додатково, щоб набрати загальне покриття.

## 2. Що таке unit-тест і як він працює тут

Unit-тест перевіряє **один клас ізольовано**. Усе, від чого клас залежить, замінюється «підробкою» (mock). Тому тест:
- не потребує PostgreSQL, Keycloak, мережі чи файлів;
- не запускає Spring-контекст (немає `@SpringBootTest`), тому виконується за секунди;
- при падінні одразу показує, який саме клас має помилку.

Приклад на `BookService`. Реально він залежить від `BookRepository` (БД) і `BookMapper`. У тесті обидва замінені моками:

```
BookServiceTest
├─ @Mock BookRepository  (підробка, БД немає)
├─ @Mock BookMapper      (підробка)
└─ @InjectMocks BookService  (справжній клас, у який підставлено два моки)
```

Тест кожного разу працює за схемою **Arrange-Act-Assert**:
1. **Arrange**: готуємо дані й задаємо, що відповідатимуть моки (`when(...).thenReturn(...)`).
2. **Act**: викликаємо метод, який тестуємо.
3. **Assert**: перевіряємо результат (`assertThat`) і те, що мок викликали правильно (`verify`).

## 3. Стек тестування

| Інструмент | Для чого |
|---|---|
| JUnit 5 (`@Test`) | запуск тестів |
| Mockito (`@Mock`, `@InjectMocks`, `when`, `verify`, `never`, `ArgumentCaptor`) | підробки залежностей і перевірка їх викликів |
| `MockitoExtension` | підключає Mockito до JUnit 5. Має strict stubs: заглушка `when`, яка не використалась, ламає тест |
| AssertJ (`assertThat`, `assertThatThrownBy`) | читабельні перевірки результатів і винятків |
| JaCoCo 0.8.12 | вимірює покриття коду тестами, будує HTML-звіт |

Іменування тестів: `метод_сценарій_очікуванийРезультат`, наприклад `create_duplicateIsbn_throwsDuplicateAndDoesNotSave`.

## 4. Які тести за що відповідають

### 4.1. `BookServiceTest` (13 тестів): головний клас завдання

Перевіряє `BookService` з підробленими `BookRepository` та `BookMapper`.

| Метод сервісу | Тест | Що перевіряє |
|---|---|---|
| `findAll` | `findAll_booksExist_returnsMappedDtos` | кожна Entity з репозиторію перетворена мапером на DTO, порядок збережено |
| | `findAll_noBooks_returnsEmptyListWithoutMapping` | порожня БД дає порожній список, мапер взагалі не викликався |
| `findById` | `findById_existingId_returnsMappedDto` | знайдена книга повертається як DTO |
| | `findById_missingId_throwsResourceNotFound` | відсутній id дає `ResourceNotFoundException` з текстом `Book with id 99 not found` |
| `count` | `count_booksExist_returnsRepositoryCount` | повертає значення з `repository.count()` |
| | `count_noBooks_returnsZero` | граничний випадок: 0 книг |
| `create` | `create_newIsbn_savesMappedEntityAndReturnsDto` | новий ISBN: DTO мапиться в Entity, `save` викликано, перехоплений `ArgumentCaptor` об'єкт має потрібний ISBN, повернуто DTO |
| | `create_duplicateIsbn_throwsDuplicateAndDoesNotSave` | дублікат ISBN дає `DuplicateResourceException`, `save` не викликано |
| `update` | `update_existingBookUniqueIsbn_updatesEntityAndSaves` | викликано `mapper.updateEntity(dto, існуюча книга)` і `save` |
| | `update_missingBook_throwsNotFoundAndDoesNotSave` | книги немає: виняток, жодних перевірок ISBN і збережень |
| | `update_isbnUsedByAnotherBook_throwsDuplicateAndDoesNotSave` | ISBN належить іншій книзі: виняток, `updateEntity` і `save` не викликано |
| `delete` | `delete_existingId_deletesFoundEntity` | `repository.delete` викликано саме з тією сутністю, яку знайшли |
| | `delete_missingId_throwsNotFoundAndDeletesNothing` | книги немає: виняток, `delete` не викликано |

### 4.2. `BookRestControllerTest` (10 тестів)

`BookService` замінений моком. Перевіряється, що контролер правильно делегує виклики й формує відповідь.

| Тест | Що перевіряє |
|---|---|
| `getAll_booksExist_...`, `getAll_noBooks_...` | повертає список із сервісу, у тому числі порожній |
| `getById_existingId_...`, `getById_serviceThrowsNotFound_...` | повертає DTO із сервісу, виняток `ResourceNotFoundException` не гаситься |
| `create_validDto_returns201WithLocationAndBody` | статус 201, заголовок `Location: /api/books/7`, тіло відповіді |
| `create_serviceThrowsDuplicate_...` | `DuplicateResourceException` пропускається далі (його перетворює на 409 `RestExceptionHandler`) |
| `update_validDto_...`, `update_serviceThrowsNotFound_...` | делегування в `service.update`, проброс винятку |
| `delete_existingId_callsServiceAndReturns204` | викликано `service.delete`, статус 204, тіла немає |
| `delete_serviceDeniesAccess_...` | `AccessDeniedException` від сервісу не гаситься |

### 4.3. `AdminRestControllerTest` (2 тести)

`stats_booksExist_...` та `stats_noBooks_returnsZero` перевіряють, що `GET /api/admin/stats` повертає `{"totalBooks": N}` із значенням `service.count()`.

### 4.4. `BookUiControllerTest` (13 тестів)

Перевіряє UI-контролер без браузера: методи викликаються напряму, `Model` і `BindingResult` створюються вручну.

| Тест | Що перевіряє |
|---|---|
| `home_...` | `/` перенаправляє на `redirect:/books` |
| `list_authenticatedUser_...` | у модель потрапляють книги, ім'я користувача і лише ролі `ROLE_ADMIN`/`ROLE_USER` (службова `OIDC_USER` відфільтрована) |
| `newForm_...` | у модель кладеться порожній `BookDto`, повертається `books/form` |
| `create_validBook_...` | викликається `service.create`, redirect на `/books` |
| `create_bindingErrors_...` | є помилки валідації: повертається форма, `service.create` не викликається |
| `create_duplicateIsbn_...` | при `DuplicateResourceException` помилка прив'язується до поля `isbn`, повертається форма |
| `editForm_existingId_...`, `editForm_missingId_...` | книга кладеться в модель, або `ResourceNotFoundException` пропускається |
| `update_validBook_...` | `id` з URL записується в DTO, викликається `service.update`, redirect |
| `update_bindingErrors_...`, `update_duplicateIsbn_...` | те саме, що й для створення |
| `delete_existingId_...`, `delete_serviceDeniesAccess_...` | делегування в `service.delete` і redirect, `AccessDeniedException` пропускається |

### 4.5. `RestExceptionHandlerTest` (4 тести)

Без моків: обробник створюється через `new`, йому передаються винятки.

| Тест | Очікуваний результат |
|---|---|
| `handleNotFound_...` | статус 404 і текст винятку |
| `handleDuplicate_...` | статус 409 і текст винятку |
| `handleValidation_...` | статус 400, повідомлення `title: ...; author: ...` зібране з помилок полів |
| `handleAccessDenied_...` | статус 403, у тексті є `@PreAuthorize` |

### 4.6. `KeycloakRoleConverterTest` (4 тести)

Перевіряє, що claim `realm_access` з Keycloak перетворюється на ролі Spring Security.

| Тест | Що перевіряє |
|---|---|
| `extractAuthorities_rolesPresent_...` | `["ADMIN","USER"]` стає `ROLE_ADMIN`, `ROLE_USER` |
| `extractAuthorities_nullRealmAccess_...` | `null` дає порожній результат без помилки |
| `extractAuthorities_rolesKeyMissing_...` | немає ключа `roles`: порожній результат |
| `extractAuthorities_rolesNotACollection_...` | `roles` не колекція: порожній результат |

### 4.7. `BookMapperTest` (6 тестів)

Тестується згенерований MapStruct клас `BookMapperImpl` (створюється через `new`, без Spring).

| Тест | Що перевіряє |
|---|---|
| `toDto_book_mapsAllFields` | усі 5 полів Entity перенесені в DTO |
| `toEntity_dto_mapsFieldsAndIgnoresId` | поля перенесені, а `id` лишається `null` (ігнорується в мапері) |
| `updateEntity_dto_overwritesFieldsAndKeepsExistingId` | поля книги оновлені, її `id` не змінився |
| `toDto_null_...`, `toEntity_null_...`, `updateEntity_nullDto_...` | `null` на вході не ламає мапер |

## 5. Відповідність Acceptance Criteria

| AC | Вимога | Як виконано |
|---|---|---|
| AC1 | тест для звичайних даних із реальним assert | тести «щасливого шляху» в кожному методі `BookService`, кожен має `assertThat` над результатом |
| AC2 | тест для невалідних, граничних даних, відмови | `missingId`, `duplicateIsbn`, `noBooks`, `count = 0`: перевіряються тип і текст винятку |
| AC3 | залежності через `@Mock`/`@InjectMocks`, без Spring | `@ExtendWith(MockitoExtension.class)`, немає `@SpringBootTest` |
| AC4 | навмисно зламати логіку, тест падає, повернути назад |  |
| AC5 | без БД, мережі, файлів, `Thread.sleep`, незалежність від порядку | лише об'єкти в пам'яті, кожен тест створює власні дані |
| AC6 | імена тестів і Arrange-Act-Assert | формат `метод_сценарій_результат` і коментарі `// Arrange`, `// Act`, `// Assert` |
| AC7 | `verify` / `ArgumentCaptor` для побічних ефектів | `verify(repository).save(...)` із `ArgumentCaptor<Book>`, `verify(mapper).updateEntity(...)`, `verify(repository).delete(...)`, `verify(..., never())` у негативних сценаріях |
| AC8 | AC1 і AC2 для кожного публічного методу класу | `BookService`: `findAll`, `findById`, `count`, `create`, `update`, `delete`, у кожного є і нормальний, і негативний чи граничний тест |

