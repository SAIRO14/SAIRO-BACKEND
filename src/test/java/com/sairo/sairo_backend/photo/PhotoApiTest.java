package com.sairo.sairo_backend.photo;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhotoApiTest extends IntegrationTestBase {

    @Test
    void getPhotos_returns200WithArray() throws Exception {
        mockMvc.perform(get("/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
