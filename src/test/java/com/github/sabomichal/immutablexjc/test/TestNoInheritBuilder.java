package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.noinheritbuilder.Declaration;
import com.github.sabomichal.immutablexjc.test.noinheritbuilder.Model;
import com.github.sabomichal.immutablexjc.test.noinheritbuilder.NameExpression;
import com.github.sabomichal.immutablexjc.test.noinheritbuilder.Parameters;
import org.junit.Test;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


/**
 * @author Michal Sabo
 */
public class TestNoInheritBuilder {

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

        Model model = modelBuilder.build();
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(modelBuilder.build(), System.out);

        try {
            model.getParameters().getParameter().add(0, Declaration.declarationBuilder().withType("type").build());
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }
}
