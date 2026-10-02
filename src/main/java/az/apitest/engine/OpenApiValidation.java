package az.apitest.engine;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.RestAssuredRequest;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import io.restassured.filter.Filter;
import io.restassured.response.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * "openapi": true - cavabı servisin OpenAPI (Swagger) spesifikasiyasına qarşı yoxlayır.
 *
 * Yalnız CAVAB yoxlanılır: neqativ testlər qəsdən yanlış sorğu göndərir, sorğu yoxlaması onları pozardı.
 * Spesifikasiyada olmayan status kodları (məs. sənədləşdirilməmiş 400) üçün xəta verilmir.
 * Spesifikasiya: URL (http...) və ya src/test/resources-a nisbətən fayl.
 */
public final class OpenApiValidation {

    private static final Map<String, OpenApiInteractionValidator> CACHE = new ConcurrentHashMap<>();

    private OpenApiValidation() {
    }

    /** Sorğuya əlavə olunan filter: cavabı yoxlayır, nəticəni holder-ə yazır (testi özü dayandırmır). */
    public static Filter filter(String spec, AtomicReference<ValidationReport> holder) {
        OpenApiInteractionValidator validator = validator(spec);
        return (request, response, ctx) -> {
            Response r = ctx.next(request, response);
            holder.set(validator.validate(RestAssuredRequest.of(request), RestAssuredResponse.of(r)));
            return r;
        };
    }

    /** Hesabatda xəta varsa AssertionError. */
    public static void assertValid(String spec, ValidationReport report) {
        if (report != null && report.hasErrors()) {
            String errors = report.getMessages().stream()
                    .filter(m -> m.getLevel() == ValidationReport.Level.ERROR)
                    .map(m -> "  - " + m.getMessage())
                    .collect(Collectors.joining("\n"));
            throw new AssertionError("Cavab OpenAPI spesifikasiyasına uyğun deyil (" + spec + "):\n" + errors);
        }
    }

    static OpenApiInteractionValidator validator(String spec) {
        return CACHE.computeIfAbsent(spec, location -> {
            OpenApiInteractionValidator.Builder builder = location.startsWith("http://") || location.startsWith("https://")
                    ? OpenApiInteractionValidator.createForSpecificationUrl(location)
                    : OpenApiInteractionValidator.createForInlineApiSpecification(Resources.read(location));
            return builder.withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            .withLevel("validation.response.status.unknown", ValidationReport.Level.IGNORE)
                            .build())
                    .build();
        });
    }
}
