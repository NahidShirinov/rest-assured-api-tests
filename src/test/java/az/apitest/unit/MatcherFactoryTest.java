package az.apitest.unit;

import az.apitest.matchers.MatcherFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Matcher-lərin həm "keçməli", həm də "uğursuz olmalı" hallarını yoxlayır.
 * Buradakı bir bug bütün API testlərinin yalandan keçməsinə səbəb ola bilər.
 */
public class MatcherFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DataProvider
    public Object[][] cases() {
        return new Object[][]{
                // gözlənti (JSON)       faktiki dəyər                  uyğun olmalıdır?
                {"\"abc\"",              "abc",                         true},
                {"\"abc\"",              "abd",                         false},
                {"1",                    1,                             true},
                {"1",                    1L,                            true},
                {"1",                    1.0d,                          true},
                {"1",                    2,                             false},
                {"1",                    "1",                           true},
                {"true",                 true,                          true},
                {"true",                 false,                         false},
                {"null",                 null,                          true},
                {"null",                 "x",                           false},

                {"\"notNull\"",          "x",                           true},
                {"\"notNull\"",          null,                          false},
                {"\"isNull\"",           null,                          true},
                {"\"isNull\"",           0,                             false},
                {"\"notEmpty\"",         "x",                           true},
                {"\"notEmpty\"",         "",                            false},
                {"\"notEmpty\"",         List.of(),                     false},
                {"\"notEmpty\"",         null,                          false},

                {"\"contains:ell\"",     "hello",                       true},
                {"\"contains:xyz\"",     "hello",                       false},
                {"\"contains:2\"",       List.of(1, 2, 3),              true},
                {"\"contains:5\"",       List.of(1, 2, 3),              false},
                {"\"startsWith:he\"",    "hello",                       true},
                {"\"startsWith:lo\"",    "hello",                       false},
                {"\"endsWith:lo\"",      "hello",                       true},
                {"\"endsWith:he\"",      "hello",                       false},
                {"\"regex:^\\\\d{3}$\"", "123",                         true},
                {"\"regex:^\\\\d{3}$\"", "1234",                        false},

                {"\"gt:5\"",             6,                             true},
                {"\"gt:5\"",             5,                             false},
                {"\"gte:5\"",            5,                             true},
                {"\"gte:5\"",            4.99d,                         false},
                {"\"lt:5\"",             4,                             true},
                {"\"lt:5\"",             5,                             false},
                {"\"lte:5\"",            5L,                            true},
                {"\"lte:5\"",            6,                             false},
                {"\"gt:5\"",             "abc",                         false},
                {"\"gt:5\"",             null,                          false},

                {"\"size:2\"",           List.of(1, 2),                 true},
                {"\"size:2\"",           List.of(1),                    false},
                {"\"size:2\"",           Map.of("a", 1, "b", 2),        true},
                {"\"size:3\"",           "abc",                         true},

                {"\"type:string\"",      "x",                           true},
                {"\"type:string\"",      1,                             false},
                {"\"type:number\"",      1.5d,                          true},
                {"\"type:boolean\"",     false,                         true},
                {"\"type:boolean\"",     "false",                       false},
                {"\"type:array\"",       List.of(),                     true},
                {"\"type:object\"",      Map.of(),                      true},

                {"\"oneOf:A|B\"",        "B",                           true},
                {"\"oneOf:A|B\"",        "C",                           false},
                {"\"not:A\"",            "B",                           true},
                {"\"not:A\"",            "A",                           false},

                // ':' olan adi mətn operator kimi başa düşülməməlidir
                {"\"http://x.com\"",     "http://x.com",                true},

                // struktur bərabərliyi, ədəd tipləri fərqli olsa da
                {"[1, 2]",               List.of(1L, 2.0d),             true},
                {"[1, 2]",               List.of(2, 1),                 false},
                {"{\"a\": 1}",           Map.of("a", 1),                true},
                {"{\"a\": 1}",           Map.of("a", 1, "b", 2),        false},
        };
    }

    @Test(dataProvider = "cases")
    public void matcher(String expectedJson, Object actual, boolean shouldMatch) throws Exception {
        JsonNode expected = MAPPER.readTree(expectedJson);
        assertEquals(MatcherFactory.from(expected).matches(actual), shouldMatch,
                expectedJson + " vs " + actual);
    }
}
