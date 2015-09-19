package com.github.sabomichal.immutablexjc.test;

import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import static org.junit.Assert.assertNotNull;


/**
 * @author Michal Sabo
 */
public class TestImmutableXjc {

    @Test
    public void testUnmarshall() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        assertNotNull(model.getParameters());
    }

    @Test
    public void testMarshall() throws Exception {
        Model.ModelBuilder modelBuilder = Model.modelBuilder()
                .withParameters(Parameters.parametersBuilder()
                        .addParameter(Declaration.declarationBuilder()
                                .withType("type")
                                .withName("name")
                                .withDocumentation("doc")
                                .addBy(NameExpression.nameExpressionBuilder()
                                        .withName("x")
                                        .build())
                                .addBy(NameExpression.nameExpressionBuilder()
                                        .withName("y")
                                        .build())
                                .build())
                        .addParameter(Declaration.declarationBuilder()
                                .withType("type")
                                .withName("name")
                                .withDocumentation("doc")
                                .addBy(NameExpression.nameExpressionBuilder()
                                        .withName("x")
                                        .build())
                                .addBy(NameExpression.nameExpressionBuilder()
                                        .withName("y")
                                        .build())
                                .build())
                        .build());

        assertNotNull(modelBuilder.build());

        Model.ModelBuilder copy = Model.modelBuilder(modelBuilder.build());
        assertNotNull(copy.build());

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(modelBuilder.build(), System.out);
        marshaller.marshal(copy.build(), System.out);
    }
}
