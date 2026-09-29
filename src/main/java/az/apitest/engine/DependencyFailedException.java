package az.apitest.engine;

/**
 * Test əvvəlki testin çıxarmalı olduğu dəyişəndən (${...}) istifadə edir,
 * amma həmin test uğursuz olub və ya söndürülüb. Test runner bunu "skip" kimi göstərir.
 */
public class DependencyFailedException extends RuntimeException {

    /** vars xəritəsində dəyər əvəzinə saxlanılan işarə: bu dəyişəni kim yaratmalı idi və niyə yaratmadı. */
    record Unavailable(String producer, String reason) {
    }

    public DependencyFailedException(String variable, Unavailable unavailable) {
        super("${" + variable + "} yoxdur, çünki '" + unavailable.producer() + "' " + unavailable.reason()
                + " - bu test keçildi");
    }
}
