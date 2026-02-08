package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.inheritbuilder.BaseEntity;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Declaration;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Model;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.NameExpression;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Parameters;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.StatusType;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Task;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.TidyBedroom;
import com.github.sabomichal.immutablexjc.test.inheritbuilder.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests -Ximm -Ximm-inheritbuilder -Ximm-cc -Ximm-simplebuildername.
 * Builder inheritance, copy constructor, simple builder naming.
 */
public class TestInheritBuilder {

    @Test
    public void testUnmarshal() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        assertNotNull(model.getParameters());
    }

    @Test
    public void testMarshalWithBuilder() throws Exception {
        Model model = Model.builder()
                .withParameters(Parameters.builder()
                        .addParameter(Declaration.builder()
                                .withType("Double")
                                .withName("x")
                                .withDocumentation("doc")
                                .addBy(NameExpression.builder().withName("a").build())
                                .addBy(NameExpression.builder().withName("b").build())
                                .build())
                        .build())
                .withStatus(StatusType.ACTIVE)
                .build();
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        marshaller.marshal(model, sw);
        assertTrue(sw.toString().contains("Double"));
    }

    @Test
    public void testSimpleBuilderNaming() throws Exception {
        // Method is builder(), not declarationBuilder()
        var method = Declaration.class.getMethod("builder");
        assertNotNull(method);
        // Inner class is Builder, not DeclarationBuilder
        assertTrue(Arrays.stream(Declaration.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("Builder")));
    }

    @Test
    public void testBuilderInheritance() {
        // Declaration.Builder extends Variable.Builder
        assertTrue(Variable.Builder.class.isAssignableFrom(Declaration.Builder.class));
    }

    @Test
    public void testSuperclassBuilderMethodsAvailable() {
        // Can call withName (inherited from Variable.Builder) on Declaration.Builder
        Declaration d = Declaration.builder()
                .withName("x")  // inherited
                .withType("t")  // own
                .addBy(NameExpression.builder().withName("a").build())  // inherited
                .build();
        assertEquals("x", d.getName());
        assertEquals("t", d.getType());
        assertEquals(1, d.getBy().size());
    }

    @Test
    public void testReturnTypeNarrowing() {
        // Inherited methods return Declaration.Builder (not Variable.Builder)
        // so we can chain without casting
        Declaration.Builder builder = Declaration.builder()
                .withName("name")  // inherited, returns Declaration.Builder
                .withType("type"); // own method
        assertNotNull(builder);
        Declaration d = builder.build();
        assertNotNull(d);
    }

    @Test
    public void testCopyConstructor() {
        Declaration d1 = Declaration.builder()
                .withType("Double")
                .withName("x")
                .withDescription("desc")
                .withComment("comment")
                .withDocumentation("doc")
                .addBy(NameExpression.builder().withName("a").build())
                .addTags("tag1")
                .build();

        Declaration d2 = Declaration.builder(d1).build();
        assertNotNull(d2);
        assertEquals(d1.getName(), d2.getName());
        assertEquals(d1.getType(), d2.getType());
        assertEquals(d1.getDocumentation(), d2.getDocumentation());
        assertEquals(d1.getDescription(), d2.getDescription());
        assertEquals(d1.getComment(), d2.getComment());
        assertEquals(d1.getBy().size(), d2.getBy().size());
        assertEquals(d1.getTags().size(), d2.getTags().size());
    }

    @Test
    public void testCopyConstructorWithUppercaseFields() {
        Declaration d1 = Declaration.builder()
                .withType("t")
                .withName("n")
                .withCid("cid-123")
                .addUri("http://example.com/1")
                .addUri("http://example.com/2")
                .build();

        Declaration d2 = Declaration.builder(d1).build();
        assertNotNull(d2);
        assertEquals("cid-123", d2.getCID());
        assertEquals(2, d2.getURI().size());
        assertEquals("http://example.com/1", d2.getURI().get(0));
        assertEquals("http://example.com/2", d2.getURI().get(1));
    }

    @Test
    public void testCopyConstructorWithAbstractSuperclass() {
        // TidyBedroom extends abstract Task — copy must preserve Task's cost
        TidyBedroom tb1 = TidyBedroom.builder()
                .withCost(5)
                .withExperiencePoints(10)
                .build();
        TidyBedroom tb2 = TidyBedroom.builder(tb1).build();

        assertNotNull(tb2);
        assertEquals(5, tb2.getCost());
        assertEquals(10, tb2.getExperiencePoints());
    }

    @Test
    public void testRequiredFieldsAreMandatory() {
        assertThrows(NullPointerException.class, () ->
            Declaration.builder()
                .withName("name")
                // missing required 'type'
                .build()
        );
    }

    @Test
    public void testNoBuilderForAbstractTypes() {
        assertEquals(0, Arrays.stream(BaseEntity.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().contains("Builder")).count());
        assertEquals(0, Arrays.stream(Task.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().contains("Builder")).count());
    }

    @Test
    public void testBuilderFieldsAreProtected() throws Exception {
        // With inherit builder, builder fields should be protected for subclass access
        Field nameField = Variable.Builder.class.getDeclaredField("name");
        assertTrue(Modifier.isProtected(nameField.getModifiers()));
    }

    @Test
    public void testCopyConstructorNullThrowsNPE() {
        assertThrows(NullPointerException.class, () -> Declaration.builder(null));
    }

    @Test
    public void testCopyConstructorCollectionIndependence() {
        Declaration d1 = Declaration.builder()
                .withName("n").withType("t")
                .addBy(NameExpression.builder().withName("a").build())
                .build();

        Declaration d2 = Declaration.builder(d1)
                .addBy(NameExpression.builder().withName("b").build())
                .build();

        assertEquals(1, d1.getBy().size());
        assertEquals(2, d2.getBy().size());
    }
}
