package com.pulse.pulse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PulseApplicationTests {

	@Test
	void applicationClassLoads() {
		assertNotNull(new PulseApplication());
	}

}
