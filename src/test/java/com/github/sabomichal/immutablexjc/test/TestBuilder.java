package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.builder.BaseEntity;
import com.github.sabomichal.immutablexjc.test.builder.Declaration;
import com.github.sabomichal.immutablexjc.test.builder.DoLaundry;
import com.github.sabomichal.immutablexjc.test.builder.Metadata;
import com.github.sabomichal.immutablexjc.test.builder.Model;
import com.github.sabomichal.immutablexjc.test.builder.NameExpression;
import com.github.sabomichal.immutablexjc.test.builder.Parameters;
import com.github.sabomichal.immutablexjc.test.builder.StatusType;
import com.github.sabomichal.immutablexjc.test.builder.Task;
import com.github.sabomichal.immutablexjc.test.builder.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests -Ximm -Ximm-builder -Ximm-pubconstructormaxargs=2.
 * Builder pattern without inheritance, plus maxargs threshold.
 */
public class TestBuilder {

    @Test
    public void testUnmarshal() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        assertNotNull(model.getParameters());
        assertFalse(model.getParameters().getParameter().isEmpty());
        Declaration decl = model.getParameters().getParameter().get(0);
        assertEquals("Double", decl.getType());
        assertEquals("x", decl.getName());
        assertEquals(2, decl.getBy().size());
        // tasks
        assertNotNull(model.getTasks());
        // metadata
        assertNotNull(model.getMetadata());
        assertEquals("Test Author", model.getMetadata().getAuthor());
        assertEquals(StatusType.ACTIVE, model.getStatus());
    }

    @Test
    public void testBuilderExists() {
        assertTrue(Arrays.stream(Declaration.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("DeclarationBuilder")));
        assertTrue(Arrays.stream(Variable.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("VariableBuilder")));
        assertTrue(Arrays.stream(Metadata.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("MetadataBuilder")));
    }

    @Test
    public void testNoBuilderForAbstractTypes() {
        assertEquals(0, Arrays.stream(BaseEntity.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().contains("Builder")).count());
        assertEquals(0, Arrays.stream(Task.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().contains("Builder")).count());
    }

    @Test
    public void testBuilderNaming() throws Exception {
        // Default naming: declarationBuilder() returns DeclarationBuilder
        var method = Declaration.class.getMethod("declarationBuilder");
        assertNotNull(method);
        assertEquals(Declaration.DeclarationBuilder.class, method.getReturnType());
    }

    @Test
    public void testBuilderWithMethods() {
        Declaration d = Declaration.declarationBuilder()
                .withType("Double")
                .withName("x")
                .withDocumentation("doc")
                .withComment("c")
                .build();
        assertEquals("Double", d.getType());
        assertEquals("x", d.getName());
        assertEquals("doc", d.getDocumentation());
        assertEquals("c", d.getComment());
    }

    @Test
    public void testBuilderAddMethodForList() {
        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
                .addBy(NameExpression.nameExpressionBuilder().withName("b").build())
                .build();
        assertEquals(2, d.getBy().size());
        assertEquals("a", d.getBy().get(0).getName());
        assertEquals("b", d.getBy().get(1).getName());
    }

    @Test
    public void testBuilderAddMethodForMap() {
        QName key = new QName("http://test", "attr");
        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .addOtherAttributes(key, "value")
                .build();
        assertEquals("value", d.getOtherAttributes().get(key));
    }

    @Test
    public void testBuilderWithMethodForListClearsAndAdds() {
        List<NameExpression> list1 = new ArrayList<>();
        list1.add(NameExpression.nameExpressionBuilder().withName("a").build());

        List<NameExpression> list2 = new ArrayList<>();
        list2.add(NameExpression.nameExpressionBuilder().withName("x").build());
        list2.add(NameExpression.nameExpressionBuilder().withName("y").build());

        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .withBy(list1)
                .withBy(list2) // should replace list1 contents
                .build();
        assertEquals(2, d.getBy().size());
        assertEquals("x", d.getBy().get(0).getName());
    }

    @Test
    public void testBuilderWithMethodNullCollection() {
        // Regression: #161 - withBy(null) should not throw NPE
        assertDoesNotThrow(() ->
            Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .withBy(null)
                .build()
        );
    }

    @Test
    public void testBuilderRequiredFieldValidation() {
        assertThrows(NullPointerException.class, () ->
            Declaration.declarationBuilder()
                .withName("x")
                // missing required 'type'
                .build()
        );
    }

    @Test
    public void testBuilderRequiredPrimitiveSkipsNullCheck() {
        // Regression: #107 - required int field (Task.cost) should not cause NPE
        // cost defaults to 0 in builder, no null-check for primitives
        DoLaundry dl = DoLaundry.doLaundryBuilder().build();
        assertNotNull(dl);
        assertEquals(0, dl.getCost());
    }

    @Test
    public void testBuilderBuildCreatesImmutableObject() {
        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
                .build();
        assertThrows(UnsupportedOperationException.class, () -> d.getBy().add(
                NameExpression.nameExpressionBuilder().withName("z").build()));
    }

    @Test
    public void testPubConstructorMaxArgsPublic() throws Exception {
        // NameExpression has 1 field (≤ 2 maxargs) → public constructor
        Constructor<?> ctor = NameExpression.class.getConstructor(String.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void testPubConstructorMaxArgsPackagePrivate() throws Exception {
        // Metadata has 7 fields (> 2 maxargs) → package-private constructor
        Constructor<?> ctor = Metadata.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class,
                int.class, boolean.class, short.class);
        assertFalse(Modifier.isPublic(ctor.getModifiers()));
        assertFalse(Modifier.isProtected(ctor.getModifiers()));
        assertFalse(Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    public void testMarshalWithBuilder() throws Exception {
        Model model = Model.modelBuilder()
                .withParameters(Parameters.parametersBuilder()
                        .addParameter(Declaration.declarationBuilder()
                                .withType("Double")
                                .withName("x")
                                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
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
    public void testBuilderFieldsArePrivate() throws Exception {
        // Non-inherit builder fields should be private (not protected)
        Field docField = Declaration.DeclarationBuilder.class.getDeclaredField("documentation");
        assertTrue(Modifier.isPrivate(docField.getModifiers()));
        Field typeField = Declaration.DeclarationBuilder.class.getDeclaredField("type");
        assertTrue(Modifier.isPrivate(typeField.getModifiers()));
    }

    @Test
    public void testNoCopyConstructorWithoutCcFlag() {
        // -Ximm-cc not enabled → no copy constructor builder method
        assertThrows(NoSuchMethodException.class, () ->
            Declaration.class.getMethod("declarationBuilder", Declaration.class));
    }
}
