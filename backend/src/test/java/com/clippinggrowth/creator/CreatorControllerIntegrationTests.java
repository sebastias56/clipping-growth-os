package com.clippinggrowth.creator;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@Testcontainers
@SpringBootTest
class CreatorControllerIntegrationTests {

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
    void createsCreatorWithGeneratedUuidPopulatedTimestampsAndNormalizedName() throws Exception {
        mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   MrBeast   "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/creators/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("MrBeast"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        var persisted = jdbcTemplate.queryForMap("""
                SELECT id, name, created_at, updated_at FROM creators
                """);
        assertThat(persisted.get("id")).isInstanceOf(UUID.class);
        assertThat(persisted.get("name")).isEqualTo("MrBeast");
        assertThat(persisted.get("created_at")).isNotNull();
        assertThat(persisted.get("updated_at")).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("invalidBlankNames")
    void rejectsMissingNullEmptyAndBlankNames(String requestBody) throws Exception {
        expectInvalidRequest(
                mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)),
                "Name is required");

        assertThat(countCreators()).isZero();
    }

    static Stream<Arguments> invalidBlankNames() {
        return Stream.of(
                Arguments.of("{}"),
                Arguments.of("{\"name\":null}"),
                Arguments.of("{\"name\":\"\"}"),
                Arguments.of("{\"name\":\"   \"}"));
    }

    @Test
    void rejectsNameLongerThan120Characters() throws Exception {
        String oversizedName = "a".repeat(121);

        expectInvalidRequest(
                mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + oversizedName + "\"}")),
                "Name must be at most 120 characters");

        assertThat(countCreators()).isZero();
    }

    @Test
    void returnsProblemDetailForMalformedJson() throws Exception {
        expectInvalidRequest(
                mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":")),
                "Request body is malformed");

        assertThat(countCreators()).isZero();
    }

    @Test
    void returnsProblemDetailForMalformedCreatorUuid() throws Exception {
        expectInvalidRequest(
                mockMvc.perform(get("/api/creators/not-a-uuid")),
                "Invalid value for creatorId");
    }

    @Test
    void acceptsDuplicateNames() throws Exception {
        String body = "{\"name\":\"Same Creator\"}";

        mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/creators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(countCreators()).isEqualTo(2L);
    }

    @Test
    void getsExistingCreator() throws Exception {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "Lex Fridman", Instant.parse("2026-08-13T12:00:00Z"));

        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creatorId.toString()))
                .andExpect(jsonPath("$.name").value("Lex Fridman"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-13T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-13T12:00:00Z"));
    }

    @Test
    void returnsProblemDetailWhenCreatorDoesNotExist() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Creator not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(creatorId.toString())));
    }

    @Test
    void listsEmptyCreators() throws Exception {
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listsCreatorsByCreatedAtAndThenIdDescending() throws Exception {
        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID lowerRecentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherRecentId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        insertCreator(olderId, "Older", Instant.parse("2026-08-12T12:00:00Z"));
        insertCreator(lowerRecentId, "Recent lower UUID", Instant.parse("2026-08-13T12:00:00Z"));
        insertCreator(higherRecentId, "Recent higher UUID", Instant.parse("2026-08-13T12:00:00Z"));

        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(higherRecentId.toString()))
                .andExpect(jsonPath("$[1].id").value(lowerRecentId.toString()))
                .andExpect(jsonPath("$[2].id").value(olderId.toString()));
    }

    private void insertCreator(UUID id, String name, Instant timestamp) {
        jdbcTemplate.update("""
                INSERT INTO creators (id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, id, name, Timestamp.from(timestamp), Timestamp.from(timestamp));
    }

    private long countCreators() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM creators", Long.class);
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
