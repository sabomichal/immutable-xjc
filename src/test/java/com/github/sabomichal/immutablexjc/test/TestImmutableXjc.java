package com.github.sabomichal.immutablexjc.test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.github.sabomichal.immutablexjc.test.inheritbuilder.Declaration;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Model;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.NameExpression;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Parameters;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Variable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

        Model.Builder copy = Model.builder(modelBuilder.build());
        assertNotNull(copy.build());

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(modelBuilder.build(), System.out);
        marshaller.marshal(copy.build(), System.out);

        try {
            model.getParameters().getParameter().add(0, new Declaration.Builder().withType("type").build());
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testCollectionsAreImmutable() {
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
        assertNotNull(d1);

        Parameters p1 = Parameters.builder()
                        .addParameter(d1)
                        .build();
        assertNotNull(p1);

        assertTrue(p1.getParameter().contains(d1));

        Parameters.Builder b1 = Parameters.builder(p1);

        Declaration d2 = Declaration.builder()
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
        assertNotNull(d2);

        b1.addParameter(d2);
        Parameters p2 = b1.build();
        assertNotNull(p2);

        assertTrue(p2.getParameter().contains(d2));
        assertTrue(!p1.getParameter().contains(d2));
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

    @Test
    public void optionalGetter() {
        assertFalse(new com.github.sabomichal.immutablexjc.test.optionalgetter.Declaration(null, null, null, null)
                .getDocumentation()
                .isPresent());

        assertTrue(new com.github.sabomichal.immutablexjc.test.optionalgetter.Declaration(null, null, "documentation", null)
                .getDocumentation()
                .isPresent());
    }
}
