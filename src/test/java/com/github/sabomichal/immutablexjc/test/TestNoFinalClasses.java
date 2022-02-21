package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.nofinalclasses.Declaration;
import com.github.sabomichal.immutablexjc.test.nofinalclasses.Model;
import com.github.sabomichal.immutablexjc.test.nofinalclasses.ObjectFactory;
import com.github.sabomichal.immutablexjc.test.nofinalclasses.Parameters;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRegistry;
import jakarta.xml.bind.annotation.XmlType;
import org.glassfish.jaxb.runtime.v2.runtime.unmarshaller.UnmarshallerImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class TestNoFinalClasses {

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "MyDeclaration", namespace = "https://x.y.z.com/w", propOrder = {
            "myAdditionalElement"
    })
    public static class MyDeclaration extends Declaration {

        @XmlElement
        private final String myAdditionalElement;

        public MyDeclaration(String myAdditionalElement) {
            super(new ArrayList<>(), "name", new HashMap<>(), "doc", "type");
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
    public void testUnmarshall() throws Exception {
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
    public void testMarshall() throws Exception {
        List<Declaration> parameter = new ArrayList<>();

        MyDeclaration myDeclaration = new MyDeclaration("myAdditionalElement");
        parameter.add(myDeclaration);

        Parameters parameters = new Parameters(parameter);
        Model model = new Model(parameters);
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class, MyDeclaration.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(model, System.out);

        try {
            model.getParameters().getParameter().add(0, myDeclaration);
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }
}
