# API Test Framework (REST Assured + TestNG)

[![CI](https://github.com/NahidShirinov/restassured-testing/actions/workflows/ci.yml/badge.svg)](https://github.com/NahidShirinov/restassured-testing/actions/workflows/ci.yml)

İstənilən REST API üçün test framework-ü. Repo-nu çək, `src/test/resources/testdata/` qovluğuna JSON fayl əlavə et,
`mvn test` işlət — **Java yazmağa ehtiyac yoxdur**. Mürəkkəb məntiq lazım olanda eyni infrastrukturla klassik
REST Assured testi yazmaq olar.

Nə edə bilir: status/body/header/schema yoxlaması, testlər arası dəyişən ötürmə, **DB yoxlaması**, **await**
(asinxron proseslər), **OpenAPI contract yoxlaması**, **OpenAPI-dən test generatoru**, **parametrli testlər**
(dataSets / CSV), **body faylları**, **Faker** ilə realistik data, **qlobal dəyişənlər**, **Allure hesabatı**.

## Struktur

```
api-test-framework/
├── pom.xml
├── testng.xml                          # hansı test class-ları işləyəcək
└── src
    ├── main/java/az/apitest
    │   ├── config/Config.java          # env + properties + -D / ENV override
    │   ├── core/SpecFactory.java       # base URL, auth, timeout, logging
    │   ├── core/ApiClient.java         # kodla yazılan testlər üçün client
    │   ├── model/                      # JSON test modelləri (Suite, TestCase, Expectation)
    │   ├── engine/SuiteLoader.java     # testdata/*.json oxuyur, suite/tag filtri
    │   ├── engine/ApiTestExecutor.java # sorğu göndər -> yoxla -> dəyişən çıxar
    │   ├── engine/Placeholders.java    # ${var}, ${random.*}, ${faker.*}, ${date.*}, ${config.*}, ${env.*}
    │   ├── engine/DbChecker.java       # "db": SQL -> sətirlər -> matcher-lər
    │   ├── engine/OpenApiValidation.java # "openapi": true
    │   ├── engine/BodyBuilder.java     # bodyFile + bodyOverrides + bodyRemove
    │   ├── engine/Await.java           # "await": şərt ödənənə qədər təkrar
    │   ├── tools/OpenApiTestGenerator.java # OpenAPI -> test qaralaması
    │   └── matchers/MatcherFactory.java# "notNull", "gt:5", "regex:..." və s.
    └── test
        ├── java/az/apitest
        │   ├── tests/datadriven/JsonDrivenApiTest.java  # bütün JSON-ları işlədən tək test
        │   └── examples/PostsCodeTest.java              # klassik REST Assured nümunəsi
        └── resources
            ├── config/dev.properties, test.properties   # base.url buradadır
            ├── api-test.schema.json    # test fayllarının formatı (IDE autocomplete)
            ├── testdata/*.json         # <-- SƏNİN TESTLƏRİN (avtomatik işləyir)
            ├── testdata/_globals.json  # bütün suite-lər üçün ortaq dəyişənlər/header-lər (istəyə görə)
            ├── testdata/_files/        # body, CSV və s. köməkçi fayllar ("_" = suite deyil)
            ├── examples/*.json         # nümunələr (avtomatik İŞLƏMİR) - hər imkan üçün işlək nümunə
            └── schemas/*.json          # JSON schema-lar
```

**Framework** = `src/main/java` — buna toxunmursan.
**Sənin işin** = `config/dev.properties` içində `base.url` + `testdata/` qovluğuna JSON fayllar.

> **"_" qaydası:** `testdata/` içində adı `_` ilə başlayan fayl və qovluqlar test kimi işləmir
> (`_globals.json`, `_files/`, generatorun yaratdığı `_<ad>.json` qaralamaları).

## İşlətmək

Card Status API (`localhost:8090`) üçün testlər: `testdata/10-card-status.json`.

```bash
mvn test                                  # hamısı (env=dev)
mvn test -Denv=test                       # başqa mühit (config/test.properties)
mvn test -Dbase.url=https://my-api.com    # URL-i birbaşa dəyiş
mvn test -Dtags=smoke                     # yalnız smoke tag-lı testlər
mvn test -Dsuite=users                    # yalnız adında "users" olan JSON fayllar
mvn test -Dlog.all=true                   # bütün request/response-ları göstər
mvn test -Dsuite.xml=testng-unit.xml      # yalnız framework unit testləri (API lazım deyil)
AUTH_TOKEN=xxx mvn test -Dauth.type=bearer
```

### Hesabat (Allure)

```bash
mvn clean test         # clean: köhnə nəticələr yeni hesabata qarışmasın
mvn allure:serve       # hesabatı yaradıb brauzerdə açır
mvn allure:report      # yalnız yaradır: target/site/allure-maven-plugin/index.html
```

Hər testdə request, response və DB sorğusu/nəticəsi əlavə kimi görünür; suite-lər JSON-dakı `suite` adı ilə,
data sətirləri parametr kimi qruplaşır. Sadə TestNG hesabatı da var: `target/surefire-reports/index.html`.

> JSON fayllar birbaşa `src/test/resources/testdata/`-dan oxunur: fayl əlavə etdikdə, dəyişdikdə
> və ya sildikdə növbəti `mvn test` bunu dərhal görür — `mvn clean` lazım deyil.

### Nümunələri işlətmək

`examples/` qovluğundakı testlər açıq demo API-lər (jsonplaceholder, Swagger Petstore) və H2 demo bazası
üzərində işləyir — yeni bir imkanı istifadə etməzdən əvvəl ora bax:

| Fayl | Göstərir |
|---|---|
| `01-posts-crud.json`, `02-users.json` | CRUD, matcher-lər, extract/zəncir, query/path param |
| `03-petstore-openapi.json` | `openapi`, `bodyFile` + `bodyOverrides`, Faker, CSV `dataFile` |
| `04-db-check.json` | `db` (API cavabı ilə bazanı müqayisə), `await`, `dataSets` + `"${status}"` |
| `_globals.json` | qlobal dəyişən və header |


```bash
mvn test -Dsuite.xml=testng-examples.xml -Dtestdata.dir=examples
```

## JSON test formatı

Faylın əvvəlinə `"$schema": "../api-test.schema.json"` yaz — IntelliJ / VS Code sahələri
avtomatik tamamlayır və yazı səhvlərini (`"expcet"`, `"method": "GTE"`) dərhal qırmızı göstərir.
Bütün test fayllarını bu formata qarşı yoxlamaq üçün: `mvn test -Dsuite.xml=testng-unit.xml`.

```json
{
  "$schema": "../api-test.schema.json",
  "suite": "Posts",
  "baseUrl": "https://... (istəyə görə)",
  "headers":   { "X-Client": "tests" },
  "variables": { "userId": 1 },
  "tests": [
    {
      "name": "Post yarat",
      "tags": ["smoke"],
      "enabled": true,
      "method": "POST",
      "path": "/posts/{id}",
      "pathParams":  { "id": 1 },
      "queryParams": { "page": 1 },
      "headers":     { "Authorization": "Bearer ${token}" },
      "body":        { "userId": "${userId}", "title": "${random.string}" },
      "formParams":  { },
      "expect": {
        "status": 201,
        "maxTimeMs": 2000,
        "schema": "schemas/post-schema.json",
        "headers": { "Content-Type": "contains:json" },
        "body": { "id": "notNull", "title": "startsWith:str_" }
      },
      "extract": { "postId": "id", "location": "header:Location" }
    }
  ]
}
```

Bir fayldakı testlər **ardıcıl** işləyir və dəyişənləri paylaşır: `extract` ilə çıxarılan `postId` sonrakı testlərdə `${postId}` kimi istifadə olunur.

Dəyişəni çıxarmalı olan test uğursuz olsa (və ya `enabled: false` olsa), ondan asılı testlər **SKIP** olur və səbəbi yazılır:
`${postId} yoxdur, çünki 'Post yarat' uğursuz oldu - bu test keçildi`.

### `expect.body` matcher-ləri

| Dəyər | Mənası |
|---|---|
| `"abc"`, `123`, `true`, `{...}`, `[...]` | bərabərdir |
| `null` / `"isNull"` / `"notNull"` | null yoxlaması |
| `"notEmpty"` | boş deyil (string/list/map) |
| `"contains:x"` | string x-i ehtiva edir / list-də x var |
| `"startsWith:x"`, `"endsWith:x"` | |
| `"regex:^\\d+$"` | regex |
| `"gt:5"`, `"gte:5"`, `"lt:5"`, `"lte:5"` | ədəd müqayisəsi |
| `"size:3"` | list/map/string ölçüsü |
| `"type:string"` | string, number, boolean, array, object |
| `"oneOf:a\|b\|c"` | dəyərlərdən biri |
| `"not:x"` | x deyil |

Açarlar REST Assured GPath-dır: `id`, `data.items[0].name`, `size()`, `items.findAll { it.active }.size()`,
`find { it.id == '${myId}' }.status`. Bir neçə sahəni müqayisə etmək üçün `with { }`: `"with { a + b == total }": true`.

### Dəyişənlər

| Dəyişən | Nəticə |
|---|---|
| `${ad}` | `variables`, `extract`, `_globals.json` və ya data sətrindən |
| `${config.base.url}`, `${env.API_TOKEN}` | config faylı / OS environment |
| `${random.uuid}` `${random.int}` `${random.email}` `${random.string}` `${random.digits:16}` `${timestamp}` | təsadüfi dəyərlər |
| `${faker.name.fullName}` `${faker.internet.emailAddress}` `${faker.phoneNumber.cellPhone}` `${faker.address.city}` `${faker.number.numberBetween '1','100'}` | realistik data ([Datafaker](https://www.datafaker.net/documentation/providers/) - istənilən provider.metod), dil: `faker.locale` |
| `${date.today}` `${date.today+5d}` `${date.today-1m}` `${date.today+1y\|dd.MM.yyyy}` | tarix (d, w, m=ay, y), default `yyyy-MM-dd` |
| `${datetime.now}` `${datetime.now+2h}` `${datetime.now-30min\|yyyy-MM-dd HH:mm}` | UTC vaxt (s, min, h, d), default ISO-8601 |

`variables`-dakı dəyər suite başlayanda **bir dəfə** hesablanır (bütün testlərdə eyni); testin içində birbaşa
yazılan `${random.*}` / `${faker.*}` isə hər istifadədə yenidir.

## Əlavə imkanlar

### DB yoxlaması

API cavabından sonra bazada nəyin dəyişdiyini yoxla (istənilən JDBC bazası):

```json
"db": {
  "query": "SELECT status, amount FROM orders WHERE id = ?",
  "params": ["${orderId}"],
  "expect": { "size()": 1, "[0].status": "CREATED", "[0].amount": "gt:0" },
  "extract": { "orderStatus": "[0].status" }
}
```

- Nəticə sətirlərin siyahısıdır; sütun adları **kiçik hərflə** (`db.lowercaseColumns=false` ilə söndürmək olar),
  tarixlər ISO mətn kimi. `expect`-də bütün matcher-lər işləyir.
- Dəyərləri `?` + `params` ilə ver (SQL injection-dan qorunur). Bir neçə yoxlama üçün `"db": [ {...}, {...} ]`.
- Bağlantı: config-də `db.url` / `db.user` / `db.password` (şifrə ENV ilə: `DB_PASSWORD=...`);
  bir neçə baza üçün `db.<ad>.url` + `"datasource": "<ad>"`; və ya suite-də `"datasources": { "<ad>": {"url": ...} }`.
- Driver-lər: PostgreSQL, MySQL, H2 hazırdır; Oracle / MSSQL üçün `pom.xml`-ə driver əlavə et.

### Await (asinxron proseslər)

Növbə, background job, event kimi gec baş verən işlər üçün - şərt ödənənə qədər təkrar yoxla:

```json
"await": { "timeoutMs": 10000, "intervalMs": 500 }
```

Testdə olsa sorğu + gözləntilər təkrarlanır (GET üçün; POST-u təkrar göndərir!), `db` içində olsa yalnız SQL.

### OpenAPI contract yoxlaması

Schema faylı yazmadan cavabı servisin OpenAPI (Swagger) spesifikasiyasına qarşı yoxla:

```json
{ "suite": "...", "openapi": true, "openapiSpec": "http://localhost:8080/v3/api-docs", "tests": [ ... ] }
```

`openapiSpec` (və ya config-də `openapi.spec`) URL və ya `src/test/resources`-a nisbətən fayl ola bilər.
Testdə `"expect": { "openapi": false }` ilə söndürülür. Yalnız **cavab** yoxlanılır (neqativ testlər qəsdən yanlış
sorğu göndərir); spesifikasiyada olmayan status kodları üçün xəta verilmir.

### OpenAPI-dən test generatoru

```bash
mvn -q compile exec:java -Dopenapi=http://localhost:8080/v3/api-docs -Dname=my-api
```

`testdata/_my-api.json` yaranır (`_` ilə başladığı üçün işləmir): hər endpoint üçün pozitiv test (nümunə body,
ilk 2xx status) və neqativ testlər (hər məcburi body sahəsi / query parametri olmadan -> 400). Dəyərləri yoxla,
düzəlt, sonra adından `_` işarəsini sil.

### Parametrli testlər

Bir şablon, çox data — neqativ və boundary testlər üçün:

```json
{
  "name": "Qeydiyyat: ${case}",
  "method": "POST", "path": "/users",
  "body": { "name": "Ali", "email": "${email}" },
  "expect": { "status": "${status}" },
  "dataSets": [
    { "case": "düzgün email", "email": "a@b.com", "status": 201 },
    { "case": "email boş",    "email": "",        "status": 400 }
  ]
}
```

Və ya CSV-dən: `"dataFile": "testdata/_files/users.csv"` (birinci sətir başlıq; `42`, `true`, `null` tipləri tanınır).
Hər sətir hesabatda ayrıca test kimi görünür.

### Body faylları

```json
"bodyFile": "testdata/_files/create-user.json",
"bodyOverrides": { "role": "ADMIN", "address.city": "Baku", "items[0].qty": 2 },
"bodyRemove": ["email"]
```

Yollar `src/test/resources`-a nisbətəndir. `bodyOverrides`/`bodyRemove` adi `body` ilə də işləyir —
`bodyRemove` "məcburi sahə olmadan" neqativ testləri üçün rahatdır.

### Qlobal dəyişənlər

`testdata/_globals.json` — bütün suite-lərə tətbiq olunur, suite-in öz dəyəri üstündür:

```json
{
  "$schema": "../api-test-globals.schema.json",
  "variables": { "adminId": 1 },
  "headers": { "X-Client": "api-tests" },
  "datasources": { "main": { "url": "jdbc:postgresql://localhost:5432/app", "user": "app", "password": "${env.DB_PASSWORD}" } },
  "openapi": true,
  "openapiSpec": "http://localhost:8080/v3/api-docs"
}
```

## Framework testləri

`src/test/java/az/apitest/unit/` — framework-ün özünü yoxlayır (matcher-lər, dəyişənlər, zəncir/skip,
test fayllarının formatı, DB, await, OpenAPI, generator, CSV, body). Lokal HTTP server və H2 ilə işləyir. API lazım deyil. Adi `mvn test` bunları **işlətmir** — yalnız API testləri işləyir.
Framework koduna dəyişiklik etsən və ya test JSON-u yazanda şübhən olsa, ayrıca işlət:
`mvn test -Dsuite.xml=testng-unit.xml`

## CI (GitHub Actions)

`.github/workflows/ci.yml` hər push və pull request zamanı framework-ün **unit testlərini** işlədir
(API lazım deyil). Nəticə README-nin yuxarısındakı nişanda görünür; uğursuz olsa GitHub email göndərir,
test hesabatı isə **Artifacts → `unit-test-reports`** bölməsindədir.

## Öz API-ni qoşmaq

1. `config/dev.properties` içində `base.url`-i dəyiş (auth lazımdırsa `auth.type`).
2. `testdata/_TEMPLATE.json.example`-i kopyala → `testdata/10-my-api.json` (və ya `examples/` fayllarına bax).
3. `mvn test`.
4. Kodla test yazmaq istəsən: `src/test/java/az/apitest/tests/code/` altında class yarat və `testng.xml`-ə əlavə et.
