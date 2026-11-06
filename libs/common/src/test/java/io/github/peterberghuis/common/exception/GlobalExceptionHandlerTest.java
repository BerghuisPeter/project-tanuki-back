package io.github.peterberghuis.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void normalRequest_IsNotAffected() throws Exception {
        mockMvc.perform(get("/ok")).andExpect(status().isOk());
    }

    // ---- Spring MVC exceptions must keep their own status instead of becoming 500 ----

    @Test
    void unmappedRoute_Returns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/does-not-exist"));
    }

    @Test
    void noResourceFound_Returns404() throws Exception {
        // This is what a full application throws for an unmapped route (the static resource handler is the fallback)
        mockMvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/missing-resource"));
    }

    @Test
    void wrongHttpMethod_Returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void unsupportedMediaType_Returns415() throws Exception {
        mockMvc.perform(post("/payload").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void missingRequestParameter_Returns400() throws Exception {
        mockMvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("'q'")));
    }

    @Test
    void mistypedPathVariable_Returns400() throws Exception {
        mockMvc.perform(get("/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---- Behavior the handler already had must be unchanged ----

    @Test
    void invalidBody_Returns400WithFieldMessages() throws Exception {
        mockMvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed: name must not be blank"))
                .andExpect(jsonPath("$.path").value("/payload"));
    }

    @Test
    void malformedBody_Returns400() throws Exception {
        mockMvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void illegalArgument_Returns400WithMessage() throws Exception {
        mockMvc.perform(get("/bad-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad argument"));
    }

    @Test
    void badCredentials_Returns401() throws Exception {
        mockMvc.perform(get("/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void responseStatusException_UsesItsStatusAndReason() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("conflict reason"));
    }

    @Test
    void accessDenied_Returns403WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unexpectedException_Returns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret internals"))));
    }

    @RestController
    static class TestController {

        record Payload(@NotBlank(message = "name must not be blank") String name) {
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @PostMapping(value = "/payload", consumes = MediaType.APPLICATION_JSON_VALUE)
        String payload(@Valid @RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/param")
        String param(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/number/{n}")
        String number(@PathVariable int n) {
            return String.valueOf(n);
        }

        @GetMapping("/missing-resource")
        String missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-resource", "missing-resource");
        }

        @GetMapping("/bad-argument")
        String badArgument() {
            throw new IllegalArgumentException("bad argument");
        }

        @GetMapping("/bad-credentials")
        String badCredentials() {
            throw new BadCredentialsException("Invalid email or password");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "conflict reason");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("internal detail that must not leak");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
