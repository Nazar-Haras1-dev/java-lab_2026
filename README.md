---

# Lab 3: Кастомні анотації


## 1. Мета і що зроблено

Реалізовано три кастомні анотації трьох **різних** механізмів Spring (без повторів).

| Тип за завданням | Анотація | Що робить |
|---|---|---|
| 1. Bean Validation constraint | `@ValidIsbn` | перевіряє, що ISBN коректний (ISBN-10 або ISBN-13 з вірною контрольною цифрою), і пояснює, що саме не так |
| 2. Композитна (мета-анотація) | `@DeleteNoContent` | об'єднує `@RequestMapping(method = DELETE)` та `@ResponseStatus(NO_CONTENT)` |
| 3. Резолвер аргументів MVC | `@CurrentUsername` | підставляє ім'я автентифікованого користувача в параметр методу контролера |
## Як працює код і за що відповідає (коротко)

У Lab 3 додано три власні анотації. Анотація сама нічого не робить: це мітка, а поведінку забезпечує окремий код, який цю мітку знаходить.

### `@ValidIsbn`: перевірка ISBN
- **Де стоїть:** на полі `isbn` у `BookDto`.
- **Хто виконує перевірку:** `IsbnValidator`. З анотацією його пов'язує `@Constraint(validatedBy = IsbnValidator.class)`.
- **Коли спрацьовує:** коли контролер отримує `@Valid BookDto`. Spring (Hibernate Validator) сам знаходить і створює валідатор.
- **Що перевіряє:** довжину (10 або 13 цифр), допустимі символи та контрольну цифру за формулою.
- **Результат:** якщо ISBN неправильний, у REST буде відповідь 400 з поясненням, в UI текст з'явиться біля поля. Повідомлення розрізняє причини (довжина, символи, контрольна цифра).

### `@DeleteNoContent`: об'єднання двох анотацій в одну
- **Де стоїть:** на методі `delete` у `BookRestController`.
- **Що замінює:** `@DeleteMapping` + `@ResponseStatus(NO_CONTENT)`.
- **Як працює:** Spring при пошуку анотацій дивиться і всередину анотацій (мета-анотації). Тому `@DeleteNoContent("/{id}")` дає ту саму поведінку: метод обробляє HTTP DELETE і відповідає статусом 204.
- **`@AliasFor`:** передає значення `"/{id}"` з нашої анотації в `@RequestMapping`.

### `@CurrentUsername`: ім'я користувача в параметрі методу
- **Де використовується:** параметр `String username` у `ProfileRestController.profile`.
- **Хто підставляє значення:** `CurrentUsernameArgumentResolver`.
- **Як працює:** перед викликом методу Spring MVC питає кожен резолвер: «ти обробляєш цей параметр?» (`supportsParameter`). Наш каже «так», якщо є `@CurrentUsername` і тип `String`. Тоді `resolveArgument` бере користувача із запиту й повертає його ім'я.
- **Реєстрація:** `WebConfig` додає резолвер у Spring MVC. Без цього він не працював би.
- **Звідки ім'я:** з JWT-токена, claim `preferred_username` (налаштовано в `SecurityConfig` ще в Lab 1).

### Схема

| Запит | Що відбувається |
|---|---|
| `POST /api/books` з поганим ISBN | `@Valid` → `IsbnValidator` → 400 з поясненням |
| `DELETE /api/books/5` | `@DeleteNoContent` → `service.delete(5)` → 204 |
| `GET /api/profile` | резолвер бере ім'я з токена → метод повертає `Hello, <ім'я>` |

### Як це перевіряється
- `IsbnValidationTest`: справжній `Validator`, коректні й некоректні ISBN, перевірка тексту повідомлень.
- `DeleteNoContentTest`: MockMvc перевіряє статус 204 і routing.
- `CurrentUsernameArgumentResolverTest` та `ProfileRestControllerTest`: `supportsParameter`, `resolveArgument` і те, що значення потрапляє у відповідь.
## 2. Усі нові та змінені файли

### Нові файли (`src/main/java/com/example/library/`)

| Файл | Призначення |
|---|---|
| `validation/ValidIsbn.java` | оголошення constraint-анотації |
| `validation/IsbnValidator.java` | логіка перевірки ISBN |
| `annotation/DeleteNoContent.java` | композитна анотація |
| `annotation/CurrentUsername.java` | маркер параметра для резолвера |
| `resolver/CurrentUsernameArgumentResolver.java` | резолвер аргументу |
| `config/WebConfig.java` | реєстрація резолвера в Spring MVC |
| `controller/ProfileRestController.java` | ендпоінт `GET /api/profile`, де використано `@CurrentUsername` |

### Змінені файли

| Файл | Що змінилося |
|---|---|
| `dto/BookDto.java` | імпорт `ValidIsbn` та анотація `@ValidIsbn` на полі `isbn` |
| `controller/BookRestController.java` | метод `delete` тепер з `@DeleteNoContent("/{id}")`, повертає `void` (раніше `@DeleteMapping` і `ResponseEntity<Void>`) |
| `BookRestControllerTest` (тест з Lab 2) | тест видалення переписано під `void`, перевірка статусу 204 перенесена в `DeleteNoContentTest` |
| `postman/Library.postman_collection.json` (Lab 1) | ISBN `{{$randomInt}}-...` замінено на `{{isbn13}}`, додано pre-request скрипт генерації валідного ISBN-13 |

## 3. Тип 1: `@ValidIsbn` (Bean Validation)

### `ValidIsbn`
Анотація з `@Constraint(validatedBy = IsbnValidator.class)`. Це і є зв'язок із валідатором: Hibernate Validator сам знаходить клас і створює його, ніде в коді немає `new IsbnValidator()`.
- `@Target({FIELD, PARAMETER})`: застосовується до полів і параметрів;
- `@Retention(RUNTIME)`: доступна під час виконання;
- обов'язкові атрибути Bean Validation: `message`, `groups`, `payload`.

### `IsbnValidator` (реалізує `ConstraintValidator<ValidIsbn, String>`)
Метод `isValid(value, context)`:
1. `null` або порожній рядок дає `true`. Їх перевіряє `@NotBlank`, щоб не було двох повідомлень про одну помилку.
2. Прибирає дефіси та пробіли (`978-0-13-235088-4` перетворюється на `9780132350884`).
3. Викликає `validate(...)`. Якщо повернувся `null`, ISBN коректний.
4. Інакше вимикає стандартне повідомлення (`disableDefaultConstraintViolation`) і додає своє (`buildConstraintViolationWithTemplate`). Так користувач бачить причину, а не загальне «Invalid ISBN».

Допоміжні методи:
- `validate(isbn)`: якщо довжина не 10 і не 13, повертає `ISBN must contain 10 or 13 digits, but contains N`; інакше викликає перевірку для ISBN-10 або ISBN-13.
- `checkIsbn13(isbn)`: перші 12 цифр множаться по черзі на 1 і 3 та сумуються. Очікувана контрольна цифра: `(10 - сума % 10) % 10`. Якщо символ не цифра: повідомлення про дозволені лише цифри. Якщо цифра не збігається: `ISBN-13 check digit is incorrect (expected N)`.
- `checkIsbn10(isbn)`: перші 9 цифр множаться на ваги 10...2, десятий символ це цифра або `X` (дорівнює 10). Сума має ділитися на 11. Інакше: `ISBN-10 check digit is incorrect`.

Приклади: `9780132350884` коректний; `9780132350885` дає помилку контрольної цифри з `expected 4`; `12345` дає помилку довжини.

### Як це спрацьовує
`BookDto.isbn` має `@NotBlank`, `@Size(max = 20)` і `@ValidIsbn`. У REST контролер приймає `@Valid @RequestBody BookDto`: при помилці виникає `MethodArgumentNotValidException`, `RestExceptionHandler` перетворює її на **400** і збирає текст `isbn: <повідомлення>`. В UI контролер має `@Valid` та `BindingResult`: повідомлення показується біля поля ISBN у формі.

## 4. Тип 2: `@DeleteNoContent` (композитна анотація)

`DeleteNoContent` позначена двома мета-анотаціями:
- `@RequestMapping(method = RequestMethod.DELETE)`: обробка HTTP DELETE;
- `@ResponseStatus(HttpStatus.NO_CONTENT)`: відповідь 204.

Атрибути `value` та `path` мають `@AliasFor(annotation = RequestMapping.class)`: значення, яке передано в `@DeleteNoContent("/{id}")`, перенаправляється в `RequestMapping.path`. Так працюють вбудовані `@GetMapping` та `@RestController`.

Spring при пошуку анотацій на методі враховує мета-анотації, тому `@DeleteNoContent("/{id}")` поводиться так само, як окремі `@DeleteMapping("/{id}")` та `@ResponseStatus(NO_CONTENT)`. У `BookRestController` метод став:
```java
@DeleteNoContent("/{id}")
public void delete(@PathVariable Long id) {
    service.delete(id);
}
```
Метод `void`, а статус 204 забезпечує анотація. `@PreAuthorize` у `BookService.delete` працює як раніше.

## 5. Тип 3: `@CurrentUsername` (HandlerMethodArgumentResolver)

### `CurrentUsername`
Анотація-маркер: `@Target(PARAMETER)`, `@Retention(RUNTIME)`, без атрибутів.

### `CurrentUsernameArgumentResolver` (`@Component`)
- `supportsParameter(parameter)`: `true`, тільки якщо параметр має `@CurrentUsername` **і** його тип `String`. Параметр без анотації або з іншим типом дає `false`.
- `resolveArgument(...)`: бере `Principal` із запиту (`webRequest.getUserPrincipal()`). Якщо користувач є, повертає `principal.getName()`, інакше `null`.

### `WebConfig` (реалізує `WebMvcConfigurer`)
Отримує резолвер через конструктор і додає його у `addArgumentResolvers`. Без цієї реєстрації Spring MVC резолвер не використовує.

### `ProfileRestController`
`GET /api/profile` з параметром `@CurrentUsername String username` повертає `{"username": "...", "message": "Hello, ..."}`. Це доводить, що значення з резолвера реально використовується в тілі методу.

### Звідки береться ім'я
Для `/api/**` працює JWT. У `SecurityConfig` задано `setPrincipalClaimName("preferred_username")`, тому `Principal.getName()` повертає `admin` або `user` із токена Keycloak.

## 6. Як усе пов'язано

```
Запит POST /api/books ── @Valid ──> BookDto ──> @ValidIsbn ──> IsbnValidator ──> 400 або далі в сервіс
Запит DELETE /api/books/5 ──> BookRestController.delete (@DeleteNoContent) ──> service.delete ──> 204
Запит GET /api/profile ──> CurrentUsernameArgumentResolver ──> String username ──> ProfileRestController
```

## 7. Тести (21 новий)

### `validation/IsbnValidationTest` (9)
Використовує справжній `Validator` (`Validation.buildDefaultValidatorFactory()`), без Spring-контексту. Метод `validateProperty(dto, "isbn")` перевіряє лише поле `isbn`.

| Тест | Що перевіряє |
|---|---|
| `isbn_validIsbn13_noViolations` | `9780132350884`: порушень немає |
| `isbn_validIsbn13WithHyphens_noViolations` | `978-0-13-235088-4`: дефіси допускаються |
| `isbn_validIsbn10_noViolations` | `0132350882` |
| `isbn_validIsbn10EndingWithX_noViolations` | `080442957X` |
| `isbn_null_noIsbnViolationBecauseNotBlankHandlesIt` | для `null` немає порушення саме від `@ValidIsbn` |
| `isbn_wrongIsbn13CheckDigit_violationExplainsCheckDigit` | текст містить `ISBN-13 check digit is incorrect` та `expected 4` |
| `isbn_wrongIsbn10CheckDigit_violationExplainsCheckDigit` | текст містить `ISBN-10 check digit is incorrect` |
| `isbn_wrongLength_violationMentionsAllowedLengths` | текст містить `10 or 13 digits` та `contains 5` |
| `isbn_lettersInsideIsbn13_violationMentionsOnlyDigits` | текст містить `only digits` |

### `annotation/DeleteNoContentTest` (4)
`MockMvc` у режимі `standaloneSetup` (без Spring-контексту) з реальним `BookRestController` і замоканим `BookService`.

| Тест | Що перевіряє |
|---|---|
| `delete_existingId_returns204WithEmptyBodyAndCallsService` | DELETE `/api/books/5` дає 204, порожнє тіло, викликано `service.delete(5L)` |
| `delete_collectionPathWithoutId_returns405` | DELETE `/api/books` дає 405, сервіс не викликається |
| `get_sameBookPath_isStillRoutedToGetById` | GET `/api/books/5` досі іде в `getById` (routing не зіпсовано) |
| `composedAnnotation_mergedAttributes_matchOriginalAnnotations` | `AnnotatedElementUtils.findMergedAnnotation` показує DELETE, шлях `/{id}`, статус `NO_CONTENT` |

### `resolver/CurrentUsernameArgumentResolverTest` (5)
Тестовий метод `sampleEndpoint` має три параметри: з анотацією (`String`), без анотації, з анотацією але типу `Long`. `NativeWebRequest` замокано.

| Тест | Що перевіряє |
|---|---|
| `supportsParameter_annotatedString_returnsTrue` | true |
| `supportsParameter_parameterWithoutAnnotation_returnsFalse` | false |
| `supportsParameter_annotatedButNotString_returnsFalse` | false |
| `resolveArgument_authenticatedRequest_returnsPrincipalNameAsString` | `Principal` з ім'ям `alice` дає `String "alice"` |
| `resolveArgument_noPrincipal_returnsNull` | без користувача `null` |

### `controller/ProfileRestControllerTest` (3)
| Тест | Що перевіряє |
|---|---|
| `profile_requestAsAlice_bodyContainsResolvedUsername` | через MockMvc із резолвером у тілі `username=alice`, `message=Hello, alice` |
| `profile_requestAsBob_bodyContainsBobNotAlice` | для іншого користувача інше ім'я |
| `profile_directCall_usesPassedUsernameInResponse` | прямий виклик `profile("carol")` |

Чому `standaloneSetup`, а не `@WebMvcTest`: `@WebMvcTest` піднімає OAuth2-автоналаштування, яке при старті звертається до Keycloak, а це порушило б незалежність тестів від мережі.

## 8. Відповідність Acceptance Criteria

| AC | Як виконано |
|---|---|
| 1 | `@Constraint(validatedBy = IsbnValidator.class)`, валідатор не створюється вручну |
| 2 | `IsbnValidationTest`: коректні дані дають 0 порушень |
| 3 | `IsbnValidationTest`: некоректні дані дають порушення з поясненням причини |
| 4 | `DeleteNoContent` позначена `@RequestMapping` та `@ResponseStatus` |
| 5 | `DeleteNoContentTest`: статус 204, routing, merged-атрибути |
| 6 | `CurrentUsernameArgumentResolverTest`: `supportsParameter` true/false |
| 7 | `CurrentUsernameArgumentResolverTest`: `resolveArgument` повертає `String` з `Principal` |
| 8 | `ProfileRestControllerTest` і Postman-запити 03, 04 |

## 9. Запуск і перевірка

```
mvn clean test          # очікується: Tests run: 73 (52 + 21), Failures: 0
```
Ручна перевірка: запустити Postgres, Keycloak, `LibraryApplication`.
- **UI:** додати книгу з ISBN `9780132350885` (помилка контрольної цифри), `12345` (помилка довжини), `978-0-13-235088-4` (успіх).
- **Postman:** імпортувати `postman/Library.lab3.postman_collection.json`, виконати Run (10 запитів: 03 і 04 повертають `admin` та `user`, 07 і 08 дають 400, 09 дає 204).

Результат: тестів пройшло ___, запитів зелених ___.

## 10. Примітки

- Після `@ValidIsbn` старі ISBN на кшталт `111` недійсні, тому колекцію Lab 1 оновлено.
- ISBN зберігається так, як введений, нормалізація лише для перевірки.
- `@CurrentUsername` повертає `null` без автентифікації. У застосунку це неможливо, бо `/api/**` вимагає токен.
