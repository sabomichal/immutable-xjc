package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.allflags.Configuration;
import com.github.sabomichal.immutablexjc.test.allflags.Declaration;
import com.github.sabomichal.immutablexjc.test.allflags.DoLaundry;
import com.github.sabomichal.immutablexjc.test.allflags.TidyBedroom;
import com.github.sabomichal.immutablexjc.test.allflags.Model;
import com.github.sabomichal.immutablexjc.test.allflags.NameExpression;
import com.github.sabomichal.immutablexjc.test.allflags.StatusType;
import com.github.sabomichal.immutablexjc.test.allflags.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests -Ximm -Ximm-builder -Ximm-cc -Ximm-ifnotnull -Ximm-nopubconstructor
 * -Ximm-skipcollections -Ximm-constructordefaults.
 */
public class TestAllFlags {

    @Test
    public void testUnmarshal() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        assertNotNull(model.getParameters());
        Declaration decl = model.getParameters().getParameter().get(0);
        assertEquals("Double", decl.getType());
        assertEquals("x", decl.getName());
        assertEquals("test documentation", decl.getDocumentation());
        assertEquals(StatusType.ACTIVE, model.getStatus());
        // verify mutable collections work after unmarshal (skipcollections)
        int sizeBefore = decl.getBy().size();
        decl.getBy().add(NameExpression.nameExpressionBuilder().withName("z").build());
        assertEquals(sizeBefore + 1, decl.getBy().size());
    }

    @Test
    public void testWithIfNotNullMethodExists() throws Exception {
        Method m = Declaration.DeclarationBuilder.class.getMethod("withDocumentationIfNotNull", String.class);
        assertNotNull(m);
        Method m2 = Declaration.DeclarationBuilder.class.getMethod("withTypeIfNotNull", String.class);
        assertNotNull(m2);
    }

    @Test
    public void testWithIfNotNullSkipsNull() {
        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .withDocumentation("original")
                .withDocumentationIfNotNull(null)  // should not overwrite
                .build();
        assertEquals("original", d.getDocumentation());
    }

    @Test
    public void testWithIfNotNullSetsValue() {
        Declaration d = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .withDocumentationIfNotNull("updated")
                .build();
        assertEquals("updated", d.getDocumentation());
    }

    @Test
    public void testWithIfNotNullNotOnPrimitives() {
        // Primitives can't be null, so no withCostIfNotNull should exist
        boolean hasIfNotNull = Arrays.stream(DoLaundry.DoLaundryBuilder.class.getMethods())
                .anyMatch(m -> m.getName().equals("withCostIfNotNull"));
        assertFalse(hasIfNotNull, "Primitive field should not have withIfNotNull method");
    }

    @Test
    public void testAllConstructorsArePackagePrivate() throws Exception {
        // -Ximm-nopubconstructor: all all-args constructors should be package-private
        for (Class<?> cls : new Class[]{Declaration.class, Variable.class, NameExpression.class,
                Configuration.class, DoLaundry.class}) {
            for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 0) continue; // skip no-arg (protected for JAXB)
                assertFalse(Modifier.isPublic(ctor.getModifiers()),
                        cls.getSimpleName() + " all-args constructor should not be public");
                assertFalse(Modifier.isProtected(ctor.getModifiers()),
                        cls.getSimpleName() + " all-args constructor should not be protected");
                assertFalse(Modifier.isPrivate(ctor.getModifiers()),
                        cls.getSimpleName() + " all-args constructor should not be private");
            }
        }
    }

    @Test
    public void testCollectionsAreMutable() {
        // -Ximm-skipcollections: collection fields are mutable (live list)
        Declaration d = Declaration.declarationBuilder()
                .withType("t").withName("n").build();
        d.getBy().add(NameExpression.nameExpressionBuilder().withName("z").build());
        assertEquals(1, d.getBy().size());
        assertEquals("z", d.getBy().get(0).getName());
    }

    @Test
    public void testNonCollectionFieldsStillFinal() throws Exception {
        // Non-collection fields remain final despite -Ximm-skipcollections
        Field nameField = Variable.class.getSuperclass().getDeclaredField("name");
        assertTrue(Modifier.isFinal(nameField.getModifiers()), "name should still be final");

        Field commentField = Variable.class.getDeclaredField("comment");
        assertTrue(Modifier.isFinal(commentField.getModifiers()), "comment should still be final");

        Field typeField = Declaration.class.getDeclaredField("type");
        assertTrue(Modifier.isFinal(typeField.getModifiers()), "type should still be final");
    }

    @Test
    public void testCollectionFieldsNotFinal() throws Exception {
        // -Ximm-skipcollections: collection fields are NOT final
        Field byField = Variable.class.getDeclaredField("by");
        assertFalse(Modifier.isFinal(byField.getModifiers()), "by (collection) should not be final with skipcollections");
    }

    @Test
    public void testConstructorDefaultString() throws Exception {
        // -Ximm-constructordefaults: no-arg constructor sets documentation to "test"
        Constructor<?> ctor = Declaration.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Declaration d = (Declaration) ctor.newInstance();
        assertEquals("test", d.getDocumentation());
    }

    @Test
    public void testConstructorDefaultStringDescription() throws Exception {
        // Configuration.description has default="default config"
        Constructor<?> ctor = Configuration.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Configuration c = (Configuration) ctor.newInstance();
        assertEquals("default config", c.getDescription());
    }

    @Test
    public void testCopyConstructorWorks() {
        Declaration d1 = Declaration.declarationBuilder()
                .withType("Double")
                .withName("x")
                .withDocumentation("doc")
                .withComment("comment")
                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
                .build();

        Declaration d2 = Declaration.declarationBuilder(d1).build();
        assertNotNull(d2);
        assertEquals(d1.getName(), d2.getName());
        assertEquals(d1.getType(), d2.getType());
        assertEquals(d1.getDocumentation(), d2.getDocumentation());
        assertEquals(d1.getComment(), d2.getComment());
        assertEquals(d1.getBy().size(), d2.getBy().size());
    }

    @Test
    public void testIndependentCollectionCopies() {
        // Modifying copy's collection doesn't affect original (#152)
        Declaration d1 = Declaration.declarationBuilder()
                .withType("t").withName("n")
                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
                .build();

        Declaration d2 = Declaration.declarationBuilder(d1).build();
        d2.getBy().add(NameExpression.nameExpressionBuilder().withName("z").build());
        assertEquals(1, d1.getBy().size());
        assertEquals(2, d2.getBy().size());
    }

    @Test
    public void testConstructorDefaultPrimitives() throws Exception {
        // -Ximm-constructordefaults: no-arg constructor sets primitive defaults
        Constructor<?> ctor = TidyBedroom.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        TidyBedroom tb = (TidyBedroom) ctor.newInstance();
        assertEquals(0, tb.getCost());  // int defaults to 0
        assertNull(tb.getExperiencePoints());  // Integer defaults to null
    }

    @Test
    public void testWithIfNotNullOnWrapperType() {
        // withExperiencePointsIfNotNull(null) should not overwrite existing value
        TidyBedroom tb = TidyBedroom.tidyBedroomBuilder()
                .withCost(5)
                .withExperiencePoints(10)
                .withExperiencePointsIfNotNull(null)
                .build();
        assertEquals(Integer.valueOf(10), tb.getExperiencePoints());
    }

    @Test
    public void testCopyConstructorNullThrowsNPE() {
        assertThrows(NullPointerException.class, () -> Declaration.declarationBuilder(null));
    }

    @Test
    public void testUppercaseCollectionMutable() {
        // -Ximm-skipcollections: URI collection should be mutable
        Declaration d = Declaration.declarationBuilder()
                .withType("t").withName("n").build();
        d.getURI().add("http://example.com");
        assertEquals(1, d.getURI().size());
        assertEquals("http://example.com", d.getURI().get(0));
    }
}
