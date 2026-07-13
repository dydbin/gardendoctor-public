package com.project.farming;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "context-test"})
class FarmingApplicationTests {

    @Test
    void contextLoads() {
    }

}
