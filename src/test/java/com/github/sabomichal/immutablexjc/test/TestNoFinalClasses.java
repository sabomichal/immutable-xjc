package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.nofinal.Declaration;
import com.github.sabomichal.immutablexjc.test.nofinal.Metadata;
import com.github.sabomichal.immutablexjc.test.nofinal.Model;
import com.github.sabomichal.immutablexjc.test.nofinal.NameExpression;
import com.github.sabomichal.immutablexjc.test.nofinal.ObjectFactory;
import com.github.sabomichal.immutablexjc.test.nofinal.Parameters;
import com.github.sabomichal.immutablexjc.test.nofinal.StatusType;
import com.github.sabomichal.immutablexjc.test.nofinal.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.*;
import org.glassfish.jaxb.runtime.v2.runtime.unmarshaller.UnmarshallerImpl;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests -Ximm -Ximm-nofinalclasses.
 * Classes are not final, can be subclassed, custom ObjectFactory for unmarshalling.
 */
public class TestNoFinalClasses {

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "MyDeclaration", namespace = "https://x.y.z.com/w", propOrder = {
            "myAdditionalElement"
    })
    public static class MyDeclaration extends Declaration {

        @XmlElement
        private final String myAdditionalElement;

        public MyDeclaration(String myAdditionalElement) {
            super(new ArrayList<>(), "name", null, null, new HashMap<>(),
                    new ArrayList<>(), new ArrayList<>(), null, "doc", "type");
            this.myAdditionalElement = myAdditionalElement;
        }

        protected MyDeclaration() {
            myAdditionalElement = null;
        }

        public String getMyAdditionalField() {
            return myAdditionalElement;
        }
    }

    @XmlRegistry
    public static class MyFactory extends ObjectFactory {

        @Override
        public Declaration createDeclaration() {
            return new MyDeclaration();
        }
    }

    @Test
    public void testClassesAreNotFinal() {
        assertFalse(Modifier.isFinal(Declaration.class.getModifiers()));
        assertFalse(Modifier.isFinal(Variable.class.getModifiers()));
        assertFalse(Modifier.isFinal(NameExpression.class.getModifiers()));
        assertFalse(Modifier.isFinal(Metadata.class.getModifiers()));
        assertFalse(Modifier.isFinal(Model.class.getModifiers()));
    }

    @Test
    public void testCanSubclass() {
        MyDeclaration myDecl = new MyDeclaration("extra");
        assertNotNull(myDecl);
        assertEquals("extra", myDecl.getMyAdditionalField());
        assertEquals("name", myDecl.getName());
        assertTrue(myDecl instanceof Declaration);
    }

    @Test
    public void testUnmarshalWithCustomFactory() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        unmarshaller.setProperty(UnmarshallerImpl.FACTORY, new MyFactory());
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        assertNotNull(model.getParameters());
        assertTrue(model.getParameters().getParameter().get(0) instanceof MyDeclaration);
        assertNull(((MyDeclaration) model.getParameters().getParameter().get(0)).myAdditionalElement);
    }

    @Test
    public void testMarshalWithSubclass() throws Exception {
        MyDeclaration myDecl = new MyDeclaration("myExtra");
        Parameters params = new Parameters(Collections.singletonList(myDecl));
        Model model = new Model(params, null, null, null, StatusType.ACTIVE);
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class, MyDeclaration.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        marshaller.marshal(model, sw);
        assertTrue(sw.toString().contains("model"));
    }

    @Test
    public void testCollectionsStillUnmodifiable() {
        Declaration d = new Declaration(
                Collections.emptyList(), "n", null, null, new HashMap<>(),
                Collections.singletonList(new NameExpression("a")),
                Collections.emptyList(), null, "doc", "t");
        assertThrows(UnsupportedOperationException.class, () ->
                d.getBy().add(new NameExpression("z")));
    }

    @Test
    public void testFieldsStillPrivateFinal() {
        for (Field f : Declaration.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(f.getModifiers()), "Field " + f.getName() + " should be private");
            assertTrue(Modifier.isFinal(f.getModifiers()), "Field " + f.getName() + " should be final");
        }
    }

    @Test
    public void testProtectedNoArgConstructor() throws Exception {
        Constructor<?> ctor = Declaration.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()));
    }
}
