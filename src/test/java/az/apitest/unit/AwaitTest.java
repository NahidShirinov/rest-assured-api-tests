package az.apitest.unit;

import az.apitest.engine.Await;
import az.apitest.model.AwaitConfig;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class AwaitTest {

    private static AwaitConfig config(long timeoutMs, long intervalMs) {
        AwaitConfig c = new AwaitConfig();
        c.timeoutMs = timeoutMs;
        c.intervalMs = intervalMs;
        return c;
    }

    @Test
    public void withoutConfigRunsOnce() {
        AtomicInteger calls = new AtomicInteger();
        expectThrows(AssertionError.class, () -> Await.until(null, () -> {
            calls.incrementAndGet();
            throw new AssertionError("x");
        }));
        assertEquals(calls.get(), 1);
    }

    @Test
    public void retriesUntilAssertionPasses() {
        AtomicInteger calls = new AtomicInteger();
        String result = Await.until(config(2000, 10), () -> {
            if (calls.incrementAndGet() < 3) throw new AssertionError("hələ yox");
            return "ok";
        });
        assertEquals(result, "ok");
        assertEquals(calls.get(), 3);
    }

    @Test
    public void timeoutReportsAttemptsAndLastError() {
        AtomicInteger calls = new AtomicInteger();
        AssertionError e = expectThrows(AssertionError.class, () -> Await.until(config(200, 50), () -> {
            throw new AssertionError("status RUNNING #" + calls.incrementAndGet());
        }));
        assertTrue(e.getMessage().startsWith("await: " + calls.get() + " cəhddən sonra da (200 ms)"), e.getMessage());
        assertTrue(e.getMessage().endsWith("status RUNNING #" + calls.get()), e.getMessage());
        assertTrue(calls.get() >= 3, "bir neçə cəhd olmalıdır: " + calls.get());
    }

    @Test
    public void nonAssertionErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        expectThrows(IllegalStateException.class, () -> Await.until(config(2000, 10), () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("connection refused");
        }));
        assertEquals(calls.get(), 1);
    }
}
