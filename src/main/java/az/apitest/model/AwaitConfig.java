package az.apitest.model;

/**
 * "await": şərt ödənənə qədər təkrar yoxla (asinxron proseslər üçün: növbə, background job, event).
 *
 *   "await": { "timeoutMs": 10000, "intervalMs": 500 }
 */
public class AwaitConfig {

    /** Ən çox nə qədər gözlənilsin. */
    public long timeoutMs = 10_000;

    /** Cəhdlər arasında fasilə. */
    public long intervalMs = 500;
}
