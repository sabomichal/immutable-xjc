package com.github.sabomichal.immutablexjc.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.sabomichal.immutablexjc.test.basic.Declaration;
import com.github.sabomichal.immutablexjc.test.basic.Model;
import com.github.sabomichal.immutablexjc.test.basic.NameExpression;
import com.github.sabomichal.immutablexjc.test.basic.Parameters;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Michal Sabo
 */
public class TestBasic {

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
        List<Declaration> parameter = new ArrayList<>();

        List<NameExpression> by1 = new ArrayList<>();
        NameExpression x1 = new NameExpression("x");
        by1.add(x1);
        NameExpression y1 = new NameExpression("y");
        by1.add(y1);
        Declaration declaration1 = new Declaration(by1, "name", new HashMap<>(),"doc", "type");
        parameter.add(declaration1);

        List<NameExpression> by2 = new ArrayList<>();
        NameExpression x2 = new NameExpression("x");
        by2.add(x2);
        NameExpression y2 = new NameExpression("y");
        by2.add(y2);
        Declaration declaration2 = new Declaration(by2, "name", new HashMap<>(), "doc", "type");
        parameter.add(declaration2);

        Parameters parameters = new Parameters(parameter);
        Model model = new Model(parameters);
        assertNotNull(model);

        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(model, System.out);

        try {
            model.getParameters().getParameter().add(0, declaration1);
            fail("Expected an UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }
    }
}
