# Yeni test necə əlavə olunur

Bu bələdçi repo-nu ilk dəfə clone edən və öz API-si üçün test yazmaq istəyən hər kəs üçündür.
Java bilmək lazım deyil: testlər **JSON faylında** yazılır.

---

## 1. Hazırlıq (bir dəfə)

**Lazım olanlar:** Git, **Java 17+**, **Maven 3.9+**, istənilən redaktor (IntelliJ IDEA və ya VS Code tövsiyə olunur).

```bash
java -version      # 17 və ya daha yeni olmalıdır
mvn -version
```

```bash
git clone https://github.com/NahidShirinov/rest-assured-api-tests.git
cd rest-assured-api-tests
mvn test -Dsuite.xml=testng-unit.xml      # framework-ün öz testləri: API lazım deyil
```

Sonda `Failures: 0` və `BUILD SUCCESS` görünürsə, mühit hazırdır.

> 💡 Repo-da əvvəlki nümunə testlər varsa və onlar sənin servisinə aid deyilsə, `testdata/`-dan sil
> və ya yalnız öz faylını işlət: `mvn test -Dsuite=<faylın adı>` (aşağıda).

---

## 2. Testlər harada yaşayır

```
src/test/resources/
├── testdata/                   ← TESTLƏR BURADADIR (hər .json fayl = bir suite)
│   ├── 20-orders.json          ← sənin test faylın (istənilən qədər fayl)
│   ├── _globals.json           bütün suite-lər üçün ortaq header/dəyişən
│   └── _files/                 body faylları, CSV data ("_" = test deyil)
├── config/dev.properties       base.url, DB, OpenAPI ayarları
├── schemas/                    response JSON schema-ları (istəyə görə)
└── examples/                   hər imkan üçün işlək nümunə - BURAYA BAX
```

Qaydalar:
- **Fayl adı:** `<nömrə>-<ad>.json` (məs. `20-orders.json`). Nömrə işləmə sırasını müəyyən edir.
- **`_` ilə başlayan** fayl və qovluqlar test deyil (`_globals.json`, `_files/`, `_draft.json`).
- Bir fayldakı testlər **yuxarıdan aşağı, ardıcıl** işləyir və dəyişənləri paylaşır.

---

## 3. İlk testin (5 dəqiqə)

`src/test/resources/testdata/20-ilk-testim.json` faylı yarat:

```json
{
  "$schema": "../api-test.schema.json",
  "suite": "İlk testim",
  "baseUrl": "https://jsonplaceholder.typicode.com",
  "tests": [
    {
      "name": "İstifadəçini al",
      "tags": ["smoke"],
      "method": "GET",
      "path": "/users/{id}",
      "pathParams": { "id": 1 },
      "expect": {
        "status": 200,
        "body": { "id": 1, "name": "notEmpty", "email": "contains:@" }
      },
      "extract": { "userId": "id" }
    },
    {
      "name": "Həmin istifadəçi üçün post yarat",
      "method": "POST",
      "path": "/posts",
      "body": { "userId": "${userId}", "title": "${faker.book.title}", "body": "Test" },
      "expect": {
        "status": 201,
        "body": { "id": "notNull", "userId": "${userId}" }
      }
    },
    {
      "name": "Olmayan istifadəçi -> 404",
      "tags": ["negative"],
      "path": "/users/99999",
      "expect": { "status": 404 }
    }
  ]
}
```

İşlət:

```bash
mvn test -Dsuite=20-ilk-testim
```

Gözlənilən: `Tests run: 3, Failures: 0`. Bu nümunə açıq demo API-yə qarşı işləyir — dərhal sına.

Nə baş verdi:
1. Birinci test istifadəçini aldı və cavabdan `id`-ni **`userId` dəyişəninə çıxardı** (`extract`).
2. İkinci test `${userId}`-ni body-də istifadə etdi, başlığı isə Faker yaratdı.
3. Üçüncü test neqativ halı yoxladı.

> 💡 Faylın birinci sətri (`"$schema"`) sayəsində IntelliJ / VS Code bütün sahələri təklif edir
> (**Ctrl+Space**) və yazı səhvini dərhal qırmızı göstərir.

---

## 4. Öz API-nə keçmək

Servisinin ünvanını `config/dev.properties`-ə yaz — bütün test faylları onu istifadə edir:

```properties
base.url=http://localhost:8080
```

Digər variantlar:

| Yol | Nə vaxt |
|---|---|
| Suite-də `"baseUrl": "http://..."` | Bir faylın testləri başqa ünvana gedir (məs. ikinci servis) |
| `config/test.properties`, `config/stage.properties` ... yarat, işlət: `mvn test -Denv=test` | Eyni testləri müxtəlif mühitlərdə işlətmək |
| `mvn test -Dbase.url=http://...` | Bir dəfəlik başqa ünvan |

Faylda servisinə aid olmayan ayarlar varsa (`db.*`, `openapi.spec`), onları sil və ya öz dəyərlərinlə əvəz et.

Auth lazımdırsa (config faylında):

```properties
auth.type=bearer          # none | bearer | basic | apikey
```

Token-i fayla yazma, işə salanda ver: `AUTH_TOKEN=xxx mvn test -Denv=<mühit> -Dsuite=...`

**Swagger varsa** — testləri əl ilə yazmağa ehtiyac yoxdur, qaralama yarat:

```bash
mvn -q compile exec:java -Dopenapi=http://localhost:8080/v3/api-docs -Dname=orders
```

`testdata/_orders.json` yaranır (hər endpoint üçün pozitiv + neqativ testlər). Dəyərləri yoxla, düzəlt,
sonra faylın adından `_` sil.

---

## 5. Bir testin anatomiyası

```json
{
  "name": "Sifariş yarat",             // hesabatda görünən ad
  "tags": ["smoke"],                   // -Dtags=smoke ilə filtr
  "method": "POST",                    // GET (default), POST, PUT, PATCH, DELETE
  "path": "/orders/{id}",              // {id} -> pathParams-dan
  "pathParams":  { "id": 1 },
  "queryParams": { "page": 0 },
  "headers":     { "X-Trace": "abc" },
  "body":        { "item": "book", "qty": 2 },
  "expect": {
    "status": 201,                     // gözlənilən HTTP status
    "maxTimeMs": 2000,                 // cavab müddəti limiti
    "body": { "id": "notNull", "qty": 2 }
  },
  "extract": { "orderId": "id" }       // sonrakı testlər üçün ${orderId}
}
```

(`//` şərhləri burada yalnız izah üçündür — JSON faylında şərh yazmaq olmaz.)

**`expect.body`** — açar JSON yoludur, dəyər gözlənti:

| Yol nümunəsi | Mənası |
|---|---|
| `"id"` | kökdəki `id` |
| `"address.city"` | iç-içə sahə |
| `"items[0].name"` | massivin birinci elementi |
| `"size()"` | kök massivin uzunluğu |
| `"items.find { it.sku == 'A1' }.qty"` | şərtə uyğun elementin sahəsi |

| Gözlənti | Mənası |
|---|---|
| `"abc"`, `5`, `true`, `null` | dəqiq bərabərlik |
| `"notNull"`, `"notEmpty"` | boş deyil |
| `"contains:x"`, `"startsWith:x"`, `"regex:^\\d+$"` | mətn yoxlaması |
| `"gt:0"`, `"lte:100"` | ədəd müqayisəsi |
| `"type:string"`, `"oneOf:NEW\|PAID"`, `"size:3"` | tip / siyahı / ölçü |

Tam siyahı: [README.md](README.md#expectbody-matcher-ləri).

---

## 6. Hazır reseptlər

### Təsadüfi / realistik data (testlər bir-birinə mane olmasın)

```json
"body": {
  "email": "${faker.internet.emailAddress}",
  "name":  "${faker.name.fullName}",
  "code":  "${random.uuid}",
  "date":  "${date.today+7d}"
}
```

### Çox neqativ hal — bir şablon

```json
{
  "name": "Qeydiyyat: ${case}",
  "method": "POST", "path": "/users",
  "body": { "email": "${email}" },
  "expect": { "status": "${status}" },
  "dataSets": [
    { "case": "düzgün",    "email": "a@b.com", "status": 201 },
    { "case": "boş email", "email": "",        "status": 400 }
  ]
}
```

Cədvəl böyükdürsə — CSV: `"dataFile": "testdata/_files/users.csv"`.

### Böyük body faylda, testdə yalnız fərq

```json
"bodyFile": "testdata/_files/order.json",
"bodyOverrides": { "qty": 0 },
"bodyRemove": ["item"]
```

### Bazanı yoxla

Config-ə bir dəfə: `db.url`, `db.user`, `db.password` (şifrə ENV ilə). Testdə:

```json
"db": {
  "query": "SELECT status FROM orders WHERE id = ?",
  "params": ["${orderId}"],
  "expect": { "[0].status": "CREATED" }
}
```

### Nəticə gec gəlir (növbə, job, Kafka)

```json
"await": { "timeoutMs": 15000, "intervalMs": 500 }
```

`db` içində və ya testin özündə. Testdə olsa sorğu təkrarlanır — yalnız GET üçün istifadə et.

### Cavab Swagger-ə uyğundurmu

Suite-in yuxarısına: `"openapi": true, "openapiSpec": "http://localhost:8080/v3/api-docs"`.

> Hər reseptin işlək nümunəsi `src/test/resources/examples/` qovluğundadır.

---

## 7. İşlətmək və nəticəyə baxmaq

| Məqsəd | Əmr |
|---|---|
| Yalnız mənim faylım | `mvn test -Dsuite=20-ilk-testim` |
| Bir neçə fayl | `mvn test -Dsuite=20-orders,21-payments` |
| Yalnız smoke | `mvn test -Dsuite=20-orders -Dtags=smoke` |
| Request/response-ları konsolda gör | `... -Dlog.all=true` |
| Faylımın formatını yoxla (API-siz) | `mvn test -Dsuite.xml=testng-unit.xml` |
| Hesabat (brauzerdə) | `mvn clean test -Dsuite=...` sonra `mvn allure:serve` |

Uğursuz testdə request və response avtomatik konsola yazılır. Allure hesabatında hər testin request, response
və DB sorğusu ayrıca görünür.

---

## 8. Tez-tez rast gəlinən xətalar

| Mesaj | Səbəb | Həll |
|---|---|---|
| `Connection refused` | API işləmir və ya ünvan səhvdir | Servisi qaldır, `base.url` / `baseUrl`-i yoxla |
| `JSON oxunmadı: ... Unrecognized field "expcet"` | Sahə adında yazı səhvi | Adı düzəlt (IDE-də `$schema` sayəsində qırmızı görünür) |
| `Naməlum dəyişən ${x}. Mövcud olanlar: [...]` | Dəyişən təyin olunmayıb | `variables`-a əlavə et və ya əvvəlki testdə `extract` et |
| `${x} yoxdur, çünki '...' uğursuz oldu - bu test keçildi` | Dəyişəni verən test qırılıb | Əvvəl həmin testi düzəlt — bu test avtomatik **skip** olunub |
| `Config açarı tələb olunur: base.url` | URL yoxdur | Suite-də `baseUrl` və ya config-də `base.url` yaz |
| `Expected status code <201> but was <400>` | API sorğunu qəbul etmədi | `-Dlog.all=true` ilə cavabın body-sinə bax — səbəb orada yazılır |
| `Fayl tapılmadı: testdata/_files/...` | `bodyFile` / `dataFile` yolu səhvdir | Yol `src/test/resources`-a nisbətəndir |
| `Baza bağlantısı tapılmadı` | DB ayarı yoxdur | Config-də `db.url` / `db.user` / `db.password` |

---

## 9. Testini repoya göndərmək

```bash
git checkout -b test/orders-api              # öz branch-ın
git add src/test/resources/testdata/20-orders.json src/test/resources/testdata/_files/
git commit -m "test(orders): create, read and validation cases"
git push -u origin test/orders-api
```

Sonra GitHub-da **Pull Request** aç. CI avtomatik olaraq framework testlərini və **sənin JSON faylının formatını**
yoxlayır (API çağırmadan). PR-da qeyd et: hansı API, hansı mühitdə işlədir, nə lazımdır (məs. "lokal Docker stack").

Yoxlama siyahısı:
- [ ] `mvn test -Dsuite=<faylım>` lokalda yaşıldır
- [ ] `mvn test -Dsuite.xml=testng-unit.xml` yaşıldır
- [ ] Şifrə / token faylda **yoxdur** (ENV ilə verilir)
- [ ] Test datası təsadüfidir (`${random.*}`, `${faker.*}`) — təkrar işlədəndə toqquşmur
- [ ] Hər testin aydın `name`-i var, neqativ testlərdə `"tags": ["negative"]`
