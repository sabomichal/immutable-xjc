/*
package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.misc.Declaration;
import com.github.sabomichal.immutablexjc.test.misc.Model;
import com.github.sabomichal.immutablexjc.test.misc.NameExpression;
import com.github.sabomichal.immutablexjc.test.misc.Parameters;
import org.junit.Test;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

import static org.junit.Assert.*;


*/
/**
 * @author Michal Sabo
 *//*

public class TestMiscOptions {

    @Test
    public void testCopyConstructor() throws Exception {
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
        assertFalse(p1.getParameter().contains(d2));
    }

    @Test
    public void testOptionalGetter() {
        assertFalse(new com.github.sabomichal.immutablexjc.test.optionalgetter.Declaration(null, null, null, null)
                .getDocumentation()
                .isPresent());

        assertTrue(new com.github.sabomichal.immutablexjc.test.optionalgetter.Declaration(null, null, "documentation", null)
                .getDocumentation()
                .isPresent());
    }
}
*/
