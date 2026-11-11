package com.bakery;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BakeryApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource(value = {
        "/|Something sweet for every occasion.",
        "/users|User Management",
        "/products|Bakery Product Management",
        "/orders|Order Management",
        "/cake-bookings|Custom Cake Booking",
        "/feedback|Feedback, Review and Admin Management"
    }, delimiter = '|')
    void pagesRenderSuccessfully(String path, String expectedHeading) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expectedHeading)))
                .andExpect(content().string(containsString("href=\"/cake-bookings\"")));
    }

    /** Payment pages hold personal payment records, so they now require sign-in. */
    @org.junit.jupiter.api.Test
    void paymentPagesRequireSignIn() throws Exception {
        mockMvc.perform(get("/payments"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("**/login"));
    }
}
