package az.apitest.engine;

import az.apitest.model.AwaitConfig;

import java.util.function.Supplier;

/**
 * Şərt ödənənə qədər təkrarla: AssertionError olduqca intervalMs sonra yenidən cəhd edir,
 * timeoutMs bitəndə sonuncu xətanı (neçə cəhd olduğu ilə birlikdə) atır.
 * Asılılıq xətaları (DependencyFailedException) və digər xətalar dərhal ötürülür.
 */
public final class Await {

    private Await() {
    }

    public static <T> T until(AwaitConfig config, Supplier<T> attempt) {
        if (config == null) {
            return attempt.get();
        }
        long deadline = System.currentTimeMillis() + config.timeoutMs;
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return attempt.get();
            } catch (AssertionError e) {
                if (System.currentTimeMillis() + config.intervalMs > deadline) {
                    AssertionError error = new AssertionError("await: " + attempts + " cəhddən sonra da ("
                            + config.timeoutMs + " ms) şərt ödənmədi. Son xəta:\n" + e.getMessage());
                    error.setStackTrace(e.getStackTrace());
                    throw error;
                }
                sleep(config.intervalMs);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("await dayandırıldı", e);
        }
    }
}
