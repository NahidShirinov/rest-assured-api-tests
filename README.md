# API Test Framework (REST Assured + TestNG)

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
            ├── testdata/*.json         # <-- SƏNİN TESTLƏRİN (avtomatik işləyir)
            ├── examples/*.json         # nümunələr (avtomatik İŞLƏMİR)
            └── schemas/*.json          # JSON schema-lar
```

**Framework** = `src/main/java` — buna toxunmursan.
**Sənin işin** = `config/dev.properties` içində `base.url` + `testdata/` qovluğuna JSON fayllar.

## İşlətmək

```bash
mvn test                                  # hamısı (env=dev)
mvn test -Denv=test                       # başqa mühit (config/test.properties)
mvn test -Dbase.url=https://my-api.com    # URL-i birbaşa dəyiş
mvn test -Dtags=smoke                     # yalnız smoke tag-lı testlər
mvn test -Dsuite=users                    # yalnız adında "users" olan JSON fayllar
mvn test -Dlog.all=true                   # bütün request/response-ları göstər
AUTH_TOKEN=xxx mvn test -Dauth.type=bearer
```

Hesabat: `target/surefire-reports/index.html` və `emailable-report.html`.

> `testdata/`-dan fayl silmisən və ya adını dəyişmisənsə `mvn clean test` işlət —
> əks halda `target/` qovluğunda köhnə kopyası qalır və yenə işləyir.

### Nümunələri işlətmək

`examples/` qovluğundakı testlər açıq demo API-si (jsonplaceholder.typicode.com) üzərində işləyir
və framework-ün imkanlarını göstərir:

```bash
mvn clean test -Dsuite.xml=testng-examples.xml -Dtestdata.dir=examples
```

## JSON test formatı

```json
{
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

Açarlar REST Assured GPath-dır: `id`, `data.items[0].name`, `size()`, `items.findAll { it.active }.size()`.

### Dəyişənlər

`${ad}` (variables/extract), `${config.base.url}`, `${env.API_TOKEN}`, `${random.uuid}`, `${random.int}`, `${random.email}`, `${random.string}`, `${timestamp}`.

## Öz API-ni qoşmaq

1. `config/dev.properties` içində `base.url`-i dəyiş (auth lazımdırsa `auth.type`).
2. `testdata/_TEMPLATE.json.example`-i kopyala → `testdata/10-my-api.json` (və ya `examples/` fayllarına bax).
3. `mvn test`.
4. Kodla test yazmaq istəsən: `src/test/java/az/apitest/tests/code/` altında class yarat və `testng.xml`-ə əlavə et.
