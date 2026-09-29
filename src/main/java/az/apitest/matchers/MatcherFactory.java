package az.apitest.matchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * JSON-dakı gözlənti dəyərini Hamcrest matcher-ə çevirir.
 *
 *   "abc"              bərabərdir (string)
 *   123 / true / {...} bərabərdir (tipə uyğun)
 *   null / "isNull"    null-dır
 *   "notNull"          null deyil
 *   "notEmpty"         boş deyil (string, list, map)
 *   "contains:x"       string x-i ehtiva edir və ya list-də x elementi var
 *   "startsWith:x"     "endsWith:x"
 *   "regex:^\\d+$"     regex-ə uyğundur
 *   "gt:5" "gte:5" "lt:5" "lte:5"   ədəd müqayisəsi
 *   "size:3"           list/map/string ölçüsü
 *   "type:string|number|boolean|array|object"
 *   "oneOf:a|b|c"      dəyərlərdən biridir
 *   "not:x"            x-ə bərabər deyil
 */
public final class MatcherFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MatcherFactory() {
    }

    public static Matcher<?> from(JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return nullValue();
        }
        if (expected.isNumber()) {
            return number("==", expected.decimalValue());
        }
        if (expected.isBoolean()) {
            return equalTo(expected.booleanValue());
        }
        if (expected.isTextual()) {
            return fromString(expected.textValue());
        }
        // array / object -> struktur bərabərliyi
        Object value = MAPPER.convertValue(expected, Object.class);
        return describe("equal to " + expected, actual -> deepEquals(value, actual));
    }

    public static Matcher<?> fromString(String s) {
        switch (s) {
            case "notNull": return notNullValue();
            case "isNull": return nullValue();
            case "notEmpty": return describe("not empty", a -> a != null && size(a) > 0);
            default: break;
        }
        int idx = s.indexOf(':');
        if (idx > 0) {
            String op = s.substring(0, idx);
            String arg = s.substring(idx + 1);
            switch (op) {
                case "contains":
                    return describe("contains '" + arg + "'", a -> a instanceof Collection<?> c
                            ? c.stream().anyMatch(item -> looseEquals(arg, item))
                            : a != null && a.toString().contains(arg));
                case "startsWith":
                    return describe("starts with '" + arg + "'", a -> a != null && a.toString().startsWith(arg));
                case "endsWith":
                    return describe("ends with '" + arg + "'", a -> a != null && a.toString().endsWith(arg));
                case "regex":
                    Pattern p = Pattern.compile(arg);
                    return describe("matches /" + arg + "/", a -> a != null && p.matcher(a.toString()).matches());
                case "gt": return number(">", new BigDecimal(arg));
                case "gte": return number(">=", new BigDecimal(arg));
                case "lt": return number("<", new BigDecimal(arg));
                case "lte": return number("<=", new BigDecimal(arg));
                case "size":
                    int n = Integer.parseInt(arg);
                    return describe("size " + n, a -> a != null && size(a) == n);
                case "type":
                    return describe("type " + arg, a -> typeOf(a).equals(arg));
                case "oneOf":
                    List<String> options = List.of(arg.split("\\|"));
                    return describe("one of " + options, a -> options.stream().anyMatch(o -> looseEquals(o, a)));
                case "not":
                    return not(describe("equal to '" + arg + "'", a -> looseEquals(arg, a)));
                default:
                    break; // ':' adi mətnin bir hissəsidir, məs. "http://..."
            }
        }
        return equalTo(s);
    }

    // ---------------------------------------------------------------- köməkçilər

    private static Matcher<Object> describe(String text, Predicate<Object> predicate) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return predicate.test(actual);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(text);
            }
        };
    }

    private static Matcher<Object> number(String op, BigDecimal expected) {
        return describe((op.equals("==") ? "" : op + " ") + expected.toPlainString(), actual -> {
            BigDecimal a = toDecimal(actual);
            if (a == null) {
                return false;
            }
            int cmp = a.compareTo(expected);
            return switch (op) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                default -> cmp == 0;
            };
        });
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof Number || value instanceof String) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int size(Object value) {
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        return value.toString().length();
    }

    private static String typeOf(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Collection<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        return value.getClass().getSimpleName();
    }

    /** "1" ilə 1, "true" ilə true bərabər sayılır. */
    private static boolean looseEquals(String expected, Object actual) {
        if (actual == null) return "null".equals(expected);
        BigDecimal a = toDecimal(actual);
        BigDecimal e = toDecimal(expected);
        if (a != null && e != null && actual instanceof Number) return a.compareTo(e) == 0;
        return expected.equals(actual.toString());
    }

    /** Ədədləri tipindən asılı olmayaraq (Integer/Long/Double) müqayisə edir. */
    private static boolean deepEquals(Object expected, Object actual) {
        if (expected instanceof Number && actual instanceof Number) {
            return toDecimal(expected).compareTo(toDecimal(actual)) == 0;
        }
        if (expected instanceof Map<?, ?> em && actual instanceof Map<?, ?> am) {
            return em.size() == am.size()
                    && em.entrySet().stream().allMatch(e -> am.containsKey(e.getKey())
                    && deepEquals(e.getValue(), am.get(e.getKey())));
        }
        if (expected instanceof List<?> el && actual instanceof List<?> al) {
            if (el.size() != al.size()) return false;
            for (int i = 0; i < el.size(); i++) {
                if (!deepEquals(el.get(i), al.get(i))) return false;
            }
            return true;
        }
        return Objects.equals(expected, actual);
    }
}
