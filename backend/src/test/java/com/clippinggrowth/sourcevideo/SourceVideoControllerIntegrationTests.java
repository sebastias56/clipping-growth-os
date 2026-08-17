package com.clippinggrowth.sourcevideo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@Testcontainers
@SpringBootTest
class SourceVideoControllerIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(applicationContext).build();
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM source_videos");
        jdbcTemplate.update("DELETE FROM creators");
    }

    @Test
    void createsSourceVideoWithOwnershipUuidTimestampsNormalizationAndLocation() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        MvcResult result = mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Donald Knuth Interview  ",
                                  "originUrl": "  https://example.com/video  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/source-videos/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.creatorId").value(creatorId.toString()))
                .andExpect(jsonPath("$.title").value("Donald Knuth Interview"))
                .andExpect(jsonPath("$.originUrl").value("https://example.com/video"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.creator").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String sourceVideoId = JsonPath.read(responseBody, "$.id");
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/source-videos/" + sourceVideoId);

        var persisted = jdbcTemplate.queryForMap("""
                SELECT id, creator_id, title, origin_url, created_at, updated_at
                FROM source_videos
                """);
        assertThat(persisted.get("id")).isInstanceOf(UUID.class);
        assertThat(persisted.get("creator_id")).isEqualTo(creatorId);
        assertThat(persisted.get("title")).isEqualTo("Donald Knuth Interview");
        assertThat(persisted.get("origin_url")).isEqualTo("https://example.com/video");
        assertThat(persisted.get("created_at")).isNotNull();
        assertThat(persisted.get("updated_at")).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("requestsWithAbsentOriginUrl")
    void normalizesOmittedNullAndBlankOriginUrlToNull(String requestBody) throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originUrl").value((Object) null));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT origin_url FROM source_videos", String.class))
                .isNull();
    }

    static Stream<Arguments> requestsWithAbsentOriginUrl() {
        return Stream.of(
                Arguments.of("{\"title\":\"Omitted URL\"}"),
                Arguments.of("{\"title\":\"Null URL\",\"originUrl\":null}"),
                Arguments.of("{\"title\":\"Empty URL\",\"originUrl\":\"\"}"),
                Arguments.of("{\"title\":\"Blank URL\",\"originUrl\":\"   \"}"));
    }

    @ParameterizedTest
    @MethodSource("validOriginUrls")
    void acceptsHttpAndHttpsOriginUrls(String originUrl, String expectedOriginUrl) throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid URL\",\"originUrl\":\""
                                + originUrl + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originUrl").value(expectedOriginUrl));
    }

    static Stream<Arguments> validOriginUrls() {
        return Stream.of(
                Arguments.of("http://example.com/video", "http://example.com/video"),
                Arguments.of("  https://example.com/video  ", "https://example.com/video"));
    }

    @ParameterizedTest
    @MethodSource("invalidTitles")
    void rejectsMissingNullEmptyAndBlankTitles(String requestBody) throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)),
                "Title is required");

        assertThat(countSourceVideos()).isZero();
    }

    static Stream<Arguments> invalidTitles() {
        return Stream.of(
                Arguments.of("{}"),
                Arguments.of("{\"title\":null}"),
                Arguments.of("{\"title\":\"\"}"),
                Arguments.of("{\"title\":\"   \"}"));
    }

    @Test
    void rejectsTitleLongerThan300Characters() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "a".repeat(301) + "\"}")),
                "Title must be at most 300 characters");

        assertThat(countSourceVideos()).isZero();
    }

    @Test
    void rejectsOriginUrlLongerThan2048Characters() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");
        String oversizedUrl = "https://example.com/" + "a".repeat(2049);

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Oversized URL\",\"originUrl\":\""
                                + oversizedUrl + "\"}")),
                "Origin URL must be at most 2048 characters");

        assertThat(countSourceVideos()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidOriginUrls")
    void rejectsMalformedRelativeNonHttpAndHostlessOriginUrls(String originUrl) throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invalid URL\",\"originUrl\":\""
                                + originUrl + "\"}")),
                "Origin URL must be an absolute HTTP or HTTPS URL with a valid host");

        assertThat(countSourceVideos()).isZero();
    }

    static Stream<Arguments> invalidOriginUrls() {
        return Stream.of(
                Arguments.of("http://exa mple.com/video"),
                Arguments.of("/relative/video"),
                Arguments.of("ftp://example.com/video"),
                Arguments.of("https:/video"),
                Arguments.of("http://[broken"));
    }

    @Test
    void returnsProblemDetailForMalformedJson() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":")),
                "Request body is malformed");

        assertThat(countSourceVideos()).isZero();
    }

    @Test
    void returnsProblemDetailForMalformedCreatorUuidInNestedEndpoint() throws Exception {
        expectInvalidRequest(
                mockMvc.perform(post("/api/creators/not-a-uuid/source-videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Interview\"}")),
                "Invalid value for creatorId");
    }

    @Test
    void returnsProblemDetailForMalformedSourceVideoUuid() throws Exception {
        expectInvalidRequest(
                mockMvc.perform(get("/api/source-videos/not-a-uuid")),
                "Invalid value for sourceVideoId");
    }

    @Test
    void returnsProblemDetailWhenCreatingUnderMissingCreator() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Missing Creator\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Creator not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(creatorId.toString())));

        assertThat(countSourceVideos()).isZero();
    }

    @Test
    void getsExistingSourceVideo() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID sourceVideoId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-13T12:00:00Z");
        insertCreator(creatorId, "Creator");
        insertSourceVideo(
                sourceVideoId, creatorId, "Interview", "https://example.com/video", timestamp);

        mockMvc.perform(get("/api/source-videos/{sourceVideoId}", sourceVideoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sourceVideoId.toString()))
                .andExpect(jsonPath("$.creatorId").value(creatorId.toString()))
                .andExpect(jsonPath("$.title").value("Interview"))
                .andExpect(jsonPath("$.originUrl").value("https://example.com/video"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-13T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-13T12:00:00Z"));
    }

    @Test
    void returnsProblemDetailWhenSourceVideoDoesNotExist() throws Exception {
        UUID sourceVideoId = UUID.randomUUID();

        mockMvc.perform(get("/api/source-videos/{sourceVideoId}", sourceVideoId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("SourceVideo not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(sourceVideoId.toString())));
    }

    @Test
    void listsEmptyPageForExistingCreatorWithDefaultPagination() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listOnlyContainsSourceVideosOwnedByRequestedCreator() throws Exception {
        UUID requestedCreatorId = UUID.randomUUID();
        UUID otherCreatorId = UUID.randomUUID();
        UUID requestedVideoId = UUID.randomUUID();
        insertCreator(requestedCreatorId, "Requested Creator");
        insertCreator(otherCreatorId, "Other Creator");
        insertSourceVideo(
                requestedVideoId,
                requestedCreatorId,
                "Requested",
                null,
                Instant.parse("2026-08-13T12:00:00Z"));
        insertSourceVideo(
                UUID.randomUUID(),
                otherCreatorId,
                "Other",
                null,
                Instant.parse("2026-08-13T13:00:00Z"));

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", requestedCreatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(requestedVideoId.toString()))
                .andExpect(jsonPath("$.items[0].creatorId").value(requestedCreatorId.toString()));
    }

    @Test
    void listsByCreatedAtAndThenIdDescending() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID lowerRecentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherRecentId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        insertCreator(creatorId, "Creator");
        insertSourceVideo(
                olderId, creatorId, "Older", null, Instant.parse("2026-08-12T12:00:00Z"));
        insertSourceVideo(
                lowerRecentId,
                creatorId,
                "Recent lower UUID",
                null,
                Instant.parse("2026-08-13T12:00:00Z"));
        insertSourceVideo(
                higherRecentId,
                creatorId,
                "Recent higher UUID",
                null,
                Instant.parse("2026-08-13T12:00:00Z"));

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(higherRecentId.toString()))
                .andExpect(jsonPath("$.items[1].id").value(lowerRecentId.toString()))
                .andExpect(jsonPath("$.items[2].id").value(olderId.toString()));
    }

    @Test
    void appliesDefaultPageSizeOfThirty() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");
        for (int index = 0; index < 31; index++) {
            insertSourceVideo(
                    UUID.randomUUID(),
                    creatorId,
                    "Video " + index,
                    null,
                    Instant.parse("2026-08-13T12:00:00Z").plusSeconds(index));
        }

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(30))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.totalElements").value(31))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void appliesExplicitPagination() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID oldestId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");
        insertSourceVideo(
                oldestId, creatorId, "Oldest", null, Instant.parse("2026-08-11T12:00:00Z"));
        insertSourceVideo(
                UUID.randomUUID(),
                creatorId,
                "Middle",
                null,
                Instant.parse("2026-08-12T12:00:00Z"));
        insertSourceVideo(
                UUID.randomUUID(),
                creatorId,
                "Newest",
                null,
                Instant.parse("2026-08-13T12:00:00Z"));

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId)
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldestId.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void rejectsInvalidPagination(String page, String size, String detail) throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");

        expectInvalidRequest(
                mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId)
                        .queryParam("page", page)
                        .queryParam("size", size)),
                detail);
    }

    static Stream<Arguments> invalidPagination() {
        return Stream.of(
                Arguments.of("-1", "30", "Page must be at least 0"),
                Arguments.of("0", "0", "Size must be at least 1"),
                Arguments.of("0", "101", "Size must be at most 100"),
                Arguments.of("abc", "30", "Invalid value for page"),
                Arguments.of("0", "abc", "Invalid value for size"));
    }

    @Test
    void returnsProblemDetailWhenListingForMissingCreator() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(get("/api/creators/{creatorId}/source-videos", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Creator not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(creatorId.toString())));
    }

    @Test
    void acceptsDuplicateTitlesAndOriginUrls() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Creator");
        String body = """
                {"title":"Duplicate","originUrl":"https://example.com/video"}
                """;

        mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/creators/{creatorId}/source-videos", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(countSourceVideos()).isEqualTo(2L);
    }

    private void insertCreator(UUID id, String name) {
        jdbcTemplate.update("""
                INSERT INTO creators (id, name, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, name);
    }

    private void insertSourceVideo(
            UUID id,
            UUID creatorId,
            String title,
            String originUrl,
            Instant timestamp) {
        jdbcTemplate.update("""
                INSERT INTO source_videos
                    (id, creator_id, title, origin_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                creatorId,
                title,
                originUrl,
                Timestamp.from(timestamp),
                Timestamp.from(timestamp));
    }

    private long countSourceVideos() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_videos", Long.class);
    }

    private ResultActions expectInvalidRequest(ResultActions result, String detail)
            throws Exception {
        return result
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("tools.jackson"))))
                .andExpect(content().string(not(containsString("com.clippinggrowth"))));
    }
}
