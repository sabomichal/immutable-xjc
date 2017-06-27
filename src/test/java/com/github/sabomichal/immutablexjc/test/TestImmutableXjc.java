package com.github.sabomichal.immutablexjc.test;

import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


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

        Model model = modelBuilder.build();
        assertNotNull(model);

        Model.ModelBuilder copy = Model.modelBuilder(modelBuilder.build());
        assertNotNull(copy.build());

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(modelBuilder.build(), System.out);
        marshaller.marshal(copy.build(), System.out);

        try {
            model.getParameters().getParameter().add(0, new Declaration.DeclarationBuilder().withType("type").build());
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testCollectionsAreImmutable() {
        Declaration d1 = Declaration.declarationBuilder()
                .withType("type")
                .withName("name")
                .withDocumentation("doc")
                .addBy(NameExpression.nameExpressionBuilder()
                        .withName("x")
                        .build())
                .addBy(NameExpression.nameExpressionBuilder()
                        .withName("y")
                        .build())
                .build();
        assertNotNull(d1);

        Parameters p1 = Parameters.parametersBuilder()
                        .addParameter(d1)
                        .build();
        assertNotNull(p1);

        assertTrue(p1.getParameter().contains(d1));

        Parameters.ParametersBuilder b1 = Parameters.parametersBuilder(p1);

        Declaration d2 = Declaration.declarationBuilder()
                .withType("type")
                .withName("name")
                .withDocumentation("doc")
                .addBy(NameExpression.nameExpressionBuilder()
                        .withName("x")
                        .build())
                .addBy(NameExpression.nameExpressionBuilder()
                        .withName("y")
                        .build())
                .build();
        assertNotNull(d2);

        b1.addParameter(d2);
        Parameters p2 = b1.build();
        assertNotNull(p2);

        assertTrue(p2.getParameter().contains(d2));
        assertTrue(!p1.getParameter().contains(d2));
    }
}
