package com.github.sabomichal.immutablexjc.test;

import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import static junit.framework.Assert.assertNotNull;

/**
 * @author Michal Sabo
 */
public class TestImmutableXjc {
	@Test
	public void testUnmarshall() throws Exception {
		JAXBContext jc = JAXBContext.newInstance(Shiporder.class);
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		Shiporder orders = (Shiporder) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/orders.xml"));
		assertNotNull(orders.getItem());
		assertNotNull(orders);
	}

	@Test
	public void testMarshall() throws Exception {
		Shiporder orders = Shiporder.shiporderBuilder()
				.withOrderid("123")
				.withShipto(Shiporder.Shipto.shiptoBuilder()
						.withAddress("address")
						.withCity("city")
						.withCountry("country")
						.withName("name").build())
				.withOrderperson("person")
				.withItem(Arrays.asList(Shiporder.Item.itemBuilder().withNote("note").withPrice(BigDecimal.ONE).withQuantity(BigInteger.TEN).withTitle("title").build()))
				.build();
		assertNotNull(orders);

		JAXBContext jc = JAXBContext.newInstance(Shiporder.class);
		Marshaller marshaller = jc.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		marshaller.marshal(orders, System.out);
	}
}
