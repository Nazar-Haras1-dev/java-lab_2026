---

# Lab 3: Кастомні анотації

## 1. Мета і що зроблено

Реалізовано три кастомні анотації трьох різних типів, кожна з яких демонструє окремий механізм Spring. Повторення механізмів немає.

| Тип | Анотація | Призначення |
|---|---|---|
| Bean Validation constraint | `@ValidIsbn` | коректний ISBN-10 або ISBN-13 з перевіркою контрольної цифри |
| Композитна (мета-анотація) | `@DeleteNoContent` | `@RequestMapping(method = DELETE)` + `@ResponseStatus(NO_CONTENT)` в одній анотації |
| Резолвер аргументів MVC | `@CurrentUsername` | підставляє ім'я автентифікованого користувача в параметр методу контролера |

## 2. Нові і змінені файли

| Файл | Роль |
|---|---|
| `validation/ValidIsbn` | оголошення constraint-анотації, посилається на `IsbnValidator` через `@Constraint(validatedBy)` |
| `validation/IsbnValidator` | логіка перевірки. `isValid` нормалізує ISBN (прибирає дефіси та пробіли), перевіряє довжину, символи й контрольну цифру. Для зрозумілих повідомлень вимикає стандартне й додає своє |
| `annotation/DeleteNoContent` | композитна анотація. Атрибути `value` та `path` перенаправлені на `@RequestMapping` через `@AliasFor` |
| `annotation/CurrentUsername` | анотація-маркер параметра, `@Target(PARAMETER)` |
| `resolver/CurrentUsernameArgumentResolver` | `supportsParameter` повертає true лише для параметра `String` з `@CurrentUsername`. `resolveArgument` повертає `Principal.getName()` |
| `config/WebConfig` | реєструє резолвер у Spring MVC (`addArgumentResolvers`) |
| `controller/ProfileRestController` | `GET /api/profile`, показує, що значення з резолвера реально використовується в тілі методу |
| `dto/BookDto` (зміна) | на поле `isbn` додано `@ValidIsbn` |
| `controller/BookRestController` (зміна) | метод `delete` тепер з `@DeleteNoContent("/{id}")`, повертає `void`. Поведінка та сама: 204 |

## 3. Як це працює

**`@ValidIsbn`.** Для ISBN-13 цифри множаться по черзі на 1 і 3, контрольна цифра доповнює суму до кратної 10. Для ISBN-10 цифри множаться на ваги 10...1, сума має ділитися на 11, останній символ може бути `X`. Повідомлення розрізняє причини: довжина, недопустимі символи, неправильна контрольна цифра. `null` і порожнє значення валідатор пропускає, їх ловить `@NotBlank`. Валідатор створює Bean Validation, у коді застосунку він вручну не створюється. Помилка у REST дає 400 (повідомлення збирає `RestExceptionHandler`), в UI показується біля поля.

**`@DeleteNoContent`.** Spring шукає `@RequestMapping` і `@ResponseStatus` з урахуванням мета-анотацій, тому нова анотація дає ту саму поведінку, що й дві оригінальні. Це видно з того, що Postman-запит DELETE і далі повертає 204.

**`@CurrentUsername`.** Spring MVC для кожного параметра перебирає резолвери. Наш повертає true для `@CurrentUsername String`, бере `Principal` із запиту. Для JWT ім'я береться з claim `preferred_username` (налаштовано в `SecurityConfig`), для UI з OIDC-користувача.

## 4. Тести (21 новий)

| Клас | Тестів | Що перевіряє |
|---|---|---|
| `IsbnValidationTest` | 9 | коректні ISBN-13/10 (також з дефісами та `X`), `null`, чотири види порушень із текстом повідомлення. Використовується справжній `Validator` без Spring-контексту |
| `DeleteNoContentTest` | 4 | MockMvc standalone: DELETE дає 204 з порожнім тілом, DELETE на колекцію дає 405, GET того ж шляху не зламаний, merged-атрибути збігаються з оригінальними анотаціями |
| `CurrentUsernameArgumentResolverTest` | 5 | `supportsParameter` (з анотацією, без неї, не String), `resolveArgument` (є `Principal`, немає) |
| `ProfileRestControllerTest` | 3 | реальні запити через MockMvc з резолвером. Ім'я потрапляє у відповідь |

Тест `delete_existingId_callsService` у `BookRestControllerTest` змінено під тип `void`. Статус 204 тепер перевіряє `DeleteNoContentTest`.

Спосіб тестування: `standaloneSetup`, а не `@WebMvcTest`, бо `@WebMvcTest` піднімає OAuth2-автоналаштування, яке ходить у Keycloak.

## 5. Відповідність Acceptance Criteria

| AC | Виконання |
|---|---|
| 1 | `@Constraint(validatedBy = IsbnValidator.class)`, валідатор не створюється вручну |
| 2 | `IsbnValidationTest`: валідні ISBN дають 0 порушень |
| 3 | `IsbnValidationTest`: невалідні дають порушення з поясненням причини |
| 4 | `DeleteNoContent` позначена `@RequestMapping` і `@ResponseStatus` |
| 5 | `DeleteNoContentTest`: статус 204, routing, merged-атрибути |
| 6 | `CurrentUsernameArgumentResolverTest`: `supportsParameter` true/false |
| 7 | `CurrentUsernameArgumentResolverTest`: `resolveArgument` повертає `String` з `Principal` |
| 8 | `ProfileRestControllerTest` та Postman-запити 03 і 04 |

## 6. Запуск і тестування

```
mvn clean test           # очікується: Tests run: 73, Failures: 0
```
Для ручної перевірки запустити Postgres, Keycloak та застосунок, імпортувати `postman/Library.lab3.postman_collection.json` і виконати **Run** (очікується 10 зелених запитів). У UI форма книги показує повідомлення `@ValidIsbn` біля поля ISBN.

## 7. Примітки

- Після додавання `@ValidIsbn` старі прикладові ISBN (`111`) недійсні. Postman-колекція Lab 1 оновлена: ISBN генерується у pre-request скрипті.
- ISBN зберігається так, як введений (з дефісами або без): нормалізація лише для перевірки.
- `@CurrentUsername` повертає `null`, якщо запит неавтентифікований. У застосунку це неможливо, бо `/api/**` вимагає автентифікації.
