package com.github.sabomichal.immutablexjc.test;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Michal Sabo
 */
public class TestImmutableXjc {

	@Test
	public void test() {
		Shiporder s = Shiporder.newBuilder()
				.withOrderid("123")
				.withShipto(Shiporder.Shipto.newBuilder()
						.withAddress("address")
						.withCity("city")
						.withCountry("country")
						.withName("name").build())
				.withOrderperson("person")
				.build();
		assertNotNull(s);
	}
}
