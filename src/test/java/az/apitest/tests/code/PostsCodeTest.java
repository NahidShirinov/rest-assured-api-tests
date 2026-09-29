package az.apitest.tests.code;

import az.apitest.core.ApiClient;
import az.apitest.tests.pojo.Post;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * JSON-la ifadə etmək çətin olan mürəkkəb məntiq üçün klassik REST Assured stili.
 * Eyni config, auth, logging istifadə olunur (ApiClient vasitəsilə).
 */
public class PostsCodeTest {

    private ApiClient api;

    @BeforeClass
    public void setUp() {
        api = new ApiClient();
    }

    @Test(groups = "smoke")
    public void getPostById_returnsTypedModel() {
        Post post = api.get("/posts/{id}", 1)
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/post-schema.json"))
                .extract().as(Post.class);

        assertEquals(post.id(), 1);
        assertTrue(post.title() != null && !post.title().isBlank());
    }

    @Test
    public void filterPostsByUser_allBelongToUser() {
        api.get("/posts", Map.of("userId", 2))
                .then()
                .statusCode(200)
                .body("size()", equalTo(10))
                .body("userId", everyItem(equalTo(2)));
    }

    @Test
    public void createPost_withPojoBody() {
        Post request = new Post(null, 7, "Framework test", "POJO body ilə göndərildi");

        Post created = api.post("/posts", request)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract().as(Post.class);

        assertEquals(created.title(), request.title());
        assertEquals(created.userId(), request.userId());
    }

    @Test
    public void commentsOfPost_customLogic() {
        Response res = api.get("/posts/{id}/comments", 1);
        res.then().statusCode(200).body("$", hasSize(5));

        List<String> emails = res.jsonPath().getList("email");
        assertTrue(emails.stream().allMatch(e -> e.contains("@")), "Bütün email-lər düzgün olmalıdır");
    }
}
