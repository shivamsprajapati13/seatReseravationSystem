package seatReservationSystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReserveIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void reserveNewShow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        MvcResult create = mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admin-Token", "admin-secret")
                        .content("""
                                {"name":"it-show","seats":["Y1"],"price_paise":100}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String body = create.getResponse().getContentAsString();
        String showId = body.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-user-" + suffix)
                        .header("Idempotency-Key", "key-" + suffix)
                        .content("{\"seats\":[\"Y1\"]}"))
                .andDo(print())
                .andExpect(status().isCreated());
    }
}
