package az.apitest.tests.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request/response modeli - REST Assured Jackson ilə avtomatik (de)serialize edir. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Post(Integer id, Integer userId, String title, String body) {
}
