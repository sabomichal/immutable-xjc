package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.basic.BaseEntity;
import com.github.sabomichal.immutablexjc.test.basic.DecimalExtensionType;
import com.github.sabomichal.immutablexjc.test.basic.Declaration;
import com.github.sabomichal.immutablexjc.test.basic.Metadata;
import com.github.sabomichal.immutablexjc.test.basic.Model;
import com.github.sabomichal.immutablexjc.test.basic.NameExpression;
import com.github.sabomichal.immutablexjc.test.basic.Parameters;
import com.github.sabomichal.immutablexjc.test.basic.StatusType;
import com.github.sabomichal.immutablexjc.test.basic.Task;
import com.github.sabomichal.immutablexjc.test.basic.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests core -Ximm immutability (final classes, private final fields, no setters,
 * constructors, unmodifiable collections, enum, abstract types).
 */
public class TestBasic {

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
        assertEquals("a variable", decl.getComment());
        assertEquals(2, decl.getBy().size());
        assertEquals("test documentation", decl.getDocumentation());
        // uppercase property fields
        assertEquals(2, decl.getURI().size());
        assertEquals("http://example.com/1", decl.getURI().get(0));
        assertEquals("cid-123", decl.getCID());
        // tasks
        assertNotNull(model.getTasks());
        // metadata
        assertNotNull(model.getMetadata());
        assertEquals("Test Author", model.getMetadata().getAuthor());
        // enum default
        assertEquals(StatusType.ACTIVE, model.getStatus());
    }

    @Test
    public void testMarshal() throws Exception {
        List<NameExpression> byList = new ArrayList<>();
        byList.add(new NameExpression("a"));
        byList.add(new NameExpression("b"));

        Declaration decl = new Declaration(
                Collections.emptyList(), "x", null, null, new HashMap<>(),
                byList, Collections.emptyList(), null, "doc", "Double");

        Parameters params = new Parameters(Collections.singletonList(decl));
        Model model = new Model(params, null, null, null, StatusType.ACTIVE);
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        marshaller.marshal(model, sw);
        String xml = sw.toString();
        assertTrue(xml.contains("Double"));
    }

    @Test
    public void testClassesAreFinal() {
        // Leaf concrete classes are final
        assertTrue(Modifier.isFinal(Declaration.class.getModifiers()));
        assertTrue(Modifier.isFinal(NameExpression.class.getModifiers()));
        assertTrue(Modifier.isFinal(Metadata.class.getModifiers()));
        assertTrue(Modifier.isFinal(Model.class.getModifiers()));
        // Superclasses of JAXB-bound subclasses are NOT final
        assertFalse(Modifier.isFinal(Variable.class.getModifiers()));
        // Abstract classes are NOT final
        assertFalse(Modifier.isFinal(BaseEntity.class.getModifiers()));
        assertFalse(Modifier.isFinal(Task.class.getModifiers()));
    }

    @Test
    public void testFieldsArePrivateFinal() {
        for (Field f : Declaration.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(f.getModifiers()), "Field " + f.getName() + " should be private");
            assertTrue(Modifier.isFinal(f.getModifiers()), "Field " + f.getName() + " should be final");
        }
        for (Field f : Metadata.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(f.getModifiers()), "Field " + f.getName() + " should be private");
            assertTrue(Modifier.isFinal(f.getModifiers()), "Field " + f.getName() + " should be final");
        }
    }

    @Test
    public void testNoSetters() {
        for (Class<?> cls : new Class[]{Declaration.class, Variable.class, NameExpression.class, Metadata.class, Model.class}) {
            for (Method m : cls.getMethods()) {
                assertFalse(m.getName().startsWith("set"), "Class " + cls.getSimpleName() + " should not have setter: " + m.getName());
                assertFalse(m.getName().startsWith("unset"), "Class " + cls.getSimpleName() + " should not have unsetter: " + m.getName());
            }
        }
    }

    @Test
    public void testProtectedNoArgConstructor() throws Exception {
        Constructor<?> ctor = Declaration.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()));
    }

    @Test
    public void testPublicAllArgsConstructor() throws Exception {
        Constructor<?> ctor = Declaration.class.getConstructor(
                List.class, String.class, String.class, String.class, Map.class,
                List.class, List.class, String.class, String.class, String.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void testListCollectionIsUnmodifiable() {
        List<NameExpression> byList = new ArrayList<>();
        byList.add(new NameExpression("a"));
        Variable v = new Variable(Collections.emptyList(), "n", null, null, new HashMap<>(), byList, Collections.emptyList(), null);
        assertThrows(UnsupportedOperationException.class, () -> v.getBy().add(new NameExpression("z")));
    }

    @Test
    public void testMapCollectionIsUnmodifiable() {
        Map<QName, String> attrs = new HashMap<>();
        attrs.put(new QName("test"), "value");
        Variable v = new Variable(Collections.emptyList(), "n", null, null, attrs, Collections.emptyList(), Collections.emptyList(), null);
        assertThrows(UnsupportedOperationException.class, () -> v.getOtherAttributes().put(new QName("x"), "y"));
    }

    @Test
    public void testEmptyCollectionReturnedForNull() {
        Variable v = new Variable(null, "n", null, null, null, null, null, null);
        assertNotNull(v.getBy());
        assertTrue(v.getBy().isEmpty());
        assertNotNull(v.getOtherAttributes());
        assertTrue(v.getOtherAttributes().isEmpty());
        assertNotNull(v.getTags());
        assertTrue(v.getTags().isEmpty());
        assertNotNull(v.getURI());
        assertTrue(v.getURI().isEmpty());
    }

    @Test
    public void testAbstractTypeHandling() {
        assertTrue(Modifier.isAbstract(BaseEntity.class.getModifiers()));
        assertTrue(Modifier.isAbstract(Task.class.getModifiers()));
        assertFalse(Modifier.isFinal(BaseEntity.class.getModifiers()));
        assertFalse(Modifier.isFinal(Task.class.getModifiers()));
    }

    @Test
    public void testNoBuilderExists() {
        for (Class<?> cls : new Class[]{Declaration.class, Variable.class, NameExpression.class, Metadata.class, Model.class, BaseEntity.class, Task.class}) {
            long builderCount = Arrays.stream(cls.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().contains("Builder"))
                    .count();
            assertEquals(0, builderCount, cls.getSimpleName() + " should not have a Builder inner class");
        }
    }

    @Test
    public void testSimpleContentExtension() {
        DecimalExtensionType det = new DecimalExtensionType(new BigDecimal("1.234"), "s");
        assertEquals(new BigDecimal("1.234"), det.getValue());
        assertEquals("s", det.getUnit());
    }

    @Test
    public void testEnumTypeGenerated() {
        StatusType[] values = StatusType.values();
        assertEquals(3, values.length);
        assertEquals(StatusType.ACTIVE, StatusType.valueOf("ACTIVE"));
        assertEquals(StatusType.INACTIVE, StatusType.valueOf("INACTIVE"));
        assertEquals(StatusType.PENDING, StatusType.valueOf("PENDING"));
    }

    @Test
    public void testDefensiveCopyForList() {
        List<NameExpression> original = new ArrayList<>();
        original.add(new NameExpression("a"));
        Variable v = new Variable(Collections.emptyList(), "n", null, null, new HashMap<>(), original, Collections.emptyList(), null);
        original.add(new NameExpression("z"));  // modify original after construction
        assertEquals(1, v.getBy().size());  // object unaffected
    }

    @Test
    public void testDefensiveCopyForMap() {
        Map<QName, String> original = new HashMap<>();
        original.put(new QName("test"), "value");
        Variable v = new Variable(Collections.emptyList(), "n", null, null, original, Collections.emptyList(), Collections.emptyList(), null);
        original.put(new QName("extra"), "extra");  // modify original after construction
        assertEquals(1, v.getOtherAttributes().size());  // object unaffected
    }

    @Test
    public void testUppercaseCollectionFieldIsUnmodifiable() {
        List<String> uris = new ArrayList<>();
        uris.add("http://example.com");
        Variable v = new Variable(Collections.emptyList(), "n", null, null, new HashMap<>(), Collections.emptyList(), uris, null);
        assertThrows(UnsupportedOperationException.class, () -> v.getURI().add("http://other.com"));
    }

    @Test
    public void testUppercaseAttributeFieldWorks() {
        Variable v = new Variable(Collections.emptyList(), "n", null, "cid-abc", new HashMap<>(), Collections.emptyList(), Collections.emptyList(), null);
        assertEquals("cid-abc", v.getCID());
    }
}
