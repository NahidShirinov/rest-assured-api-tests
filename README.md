# API Test Framework (REST Assured + TestNG)

[![CI](https://github.com/NahidShirinov/restassured-testing/actions/workflows/ci.yml/badge.svg)](https://github.com/NahidShirinov/restassured-testing/actions/workflows/ci.yml)

Yeni API testi üçün **Java yazmağa ehtiyac yoxdur**: `src/test/resources/testdata/` qovluğuna JSON fayl əlavə et, `mvn test` işlət.
Mürəkkəb məntiq lazım olanda eyni infrastrukturla klassik REST Assured testi yazmaq olar.

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
    │   ├── engine/Placeholders.java    # ${var}, ${random.*}, ${config.*}, ${env.*}
    │   └── matchers/MatcherFactory.java# "notNull", "gt:5", "regex:..." və s.
    └── test
        ├── java/az/apitest
        │   ├── tests/datadriven/JsonDrivenApiTest.java  # bütün JSON-ları işlədən tək test
        │   └── examples/PostsCodeTest.java              # klassik REST Assured nümunəsi
        └── resources
            ├── config/dev.properties, test.properties   # base.url buradadır
            ├── api-test.schema.json    # test fayllarının formatı (IDE autocomplete)
            ├── testdata/*.json         # <-- SƏNİN TESTLƏRİN (avtomatik işləyir)
            ├── examples/*.json         # nümunələr (avtomatik İŞLƏMİR)
            └── schemas/*.json          # JSON schema-lar
```

**Framework** = `src/main/java` — buna toxunmursan.
**Sənin işin** = `config/dev.properties` içində `base.url` + `testdata/` qovluğuna JSON fayllar.

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

Hesabat: `target/surefire-reports/index.html` və `emailable-report.html`.

> JSON fayllar birbaşa `src/test/resources/testdata/`-dan oxunur: fayl əlavə etdikdə, dəyişdikdə
> və ya sildikdə növbəti `mvn test` bunu dərhal görür — `mvn clean` lazım deyil.

### Nümunələri işlətmək

`examples/` qovluğundakı testlər açıq demo API-si (jsonplaceholder.typicode.com) üzərində işləyir
və framework-ün imkanlarını göstərir:

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

`${ad}` (variables/extract), `${config.base.url}`, `${env.API_TOKEN}`, `${random.uuid}`, `${random.int}`, `${random.email}`, `${random.string}`, `${random.digits:16}`, `${timestamp}`.

## Framework testləri

`src/test/java/az/apitest/unit/` — framework-ün özünü yoxlayır (matcher-lər, dəyişənlər, zəncir/skip,
test fayllarının formatı). API lazım deyil. Adi `mvn test` bunları **işlətmir** — yalnız API testləri işləyir.
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
