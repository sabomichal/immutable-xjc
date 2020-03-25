package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.inheritbuilder.*;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import static org.junit.Assert.*;


/**
 * @author Michal Sabo
 */
public class TestInheritBuilder {

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
        Model.Builder modelBuilder = Model.builder()
                .withParameters(Parameters.builder()
                        .addParameter(Declaration.builder()
                                .withType("type")
                                .withName("name")
                                .withDocumentation("doc")
                                .addBy(NameExpression.builder()
                                        .withName("x")
                                        .build())
                                .addBy(NameExpression.builder()
                                        .withName("y")
                                        .build())
                                .build())
                        .addParameter(Declaration.builder()
                                .withType("type")
                                .withName("name")
                                .withDocumentation("doc")
                                .addBy(NameExpression.builder()
                                        .withName("x")
                                        .build())
                                .addBy(NameExpression.builder()
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
            model.getParameters().getParameter().add(0, Declaration.builder().withType("type").build());
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testSuperclassBuilderIsInherited() {
        // This doesn't really test all that much. It will mostly cause compile errors if the builders do not use inheritance
        Variable.Builder builder = Declaration.builder();
        Variable variable = builder
                .withName("name")
                .addBy(NameExpression.builder()
                        .withName("x")
                        .build())
                .build();
        assertTrue(variable instanceof Declaration);
    }

    @Test
    public void testSubclassBuilderNarrowsSuperclassBuilderMethods() {
        // This doesn't really test all that much. It will mostly cause compile errors if builders do not properly narrow the return type of inherited fluent methods.
        // This only works if the inherited method's return type has been narrowed from Variable.Builder to Declaration.Builder:
        Declaration.builder()
                .withName("name") // inherited Variable.Builder method
                .withType("type") // declared builder method
                .build();
    }

    @Test
    public void testSubclassBuilderCopiesSuperclassProperties() {
        Declaration d1 = Declaration.builder()
                .withType("type")
                .withName("name")
                .withDocumentation("doc")
                .addBy(NameExpression.builder()
                        .withName("x")
                        .build())
                .addBy(NameExpression.builder()
                        .withName("y")
                        .build())
                .build();
        Declaration d2 = Declaration.builder(d1).build();

        assertNotNull(d2);
        assertEquals(d1.getName(), d2.getName());
        assertEquals(d1.getType(), d2.getType());
        assertEquals(d1.getDocumentation(), d2.getDocumentation());
        assertNotNull(d2.getBy());
        assertEquals(d1.getBy(), d2.getBy());
    }
}
