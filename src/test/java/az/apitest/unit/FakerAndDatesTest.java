package az.apitest.unit;

import az.apitest.engine.Placeholders;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class FakerAndDatesTest {

    private final Map<String, Object> vars = new HashMap<>();

    private String resolve(String text) {
        return Placeholders.resolve(text, vars);
    }

    @Test
    public void fakerProviders() {
        assertTrue(resolve("${faker.internet.emailAddress}").matches(".+@.+\\..+"));
        assertFalse(resolve("${faker.name.fullName}").isBlank());
        assertTrue(resolve("${faker.number.digits '6'}").matches("\\d{6}"));
        assertTrue(resolve("${faker.Name.firstName}").matches("\\S+"), "provider adı böyük hərflə də işləyir");
    }

    @Test
    public void unknownFakerExpressionFailsWithHint() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> resolve("${faker.nope.nothing}"));
        assertTrue(e.getMessage().contains("${faker.nope.nothing}"), e.getMessage());
    }

    @Test
    public void dates() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertEquals(resolve("${date.today}"), today.toString());
        assertEquals(resolve("${date.today+5d}"), today.plusDays(5).toString());
        assertEquals(resolve("${date.today-2w}"), today.minusWeeks(2).toString());
        assertEquals(resolve("${date.today+1m}"), today.plusMonths(1).toString());
        assertEquals(resolve("${date.today-1y|dd.MM.yyyy}"), today.minusYears(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }

    @Test
    public void dateTimes() {
        Instant now = Instant.parse(resolve("${datetime.now}"));
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(Instant.now(), now)) < 5);
        Instant later = Instant.parse(resolve("${datetime.now+2h}"));
        assertEquals(ChronoUnit.MINUTES.between(now, later), 120, 1);
        assertTrue(resolve("${datetime.now-30min|yyyy-MM-dd HH:mm}").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }
}
