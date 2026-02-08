package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.wsdl.CreateDeclarationRequest;
import com.github.sabomichal.immutablexjc.test.wsdl.GetMetadataRequest;
import com.github.sabomichal.immutablexjc.test.wsdl.ProcessTasksRequest;
import com.github.sabomichal.immutablexjc.test.wsdl.TestServicePortType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CXF wsdl2java with -xjc-Ximm -xjc-Ximm-builder -xjc-Ximm-cc.
 * Verifies immutability and builders on WSDL-generated classes.
 */
public class TestWsdl {

    @Test
    public void testCxfGeneratedClassesAreImmutable() {
        // Fields should be private and final
        for (Class<?> cls : new Class[]{CreateDeclarationRequest.class, GetMetadataRequest.class}) {
            for (Field f : cls.getDeclaredFields()) {
                assertTrue(Modifier.isPrivate(f.getModifiers()),
                        cls.getSimpleName() + "." + f.getName() + " should be private");
                assertTrue(Modifier.isFinal(f.getModifiers()),
                        cls.getSimpleName() + "." + f.getName() + " should be final");
            }
        }
        // No setters
        for (Class<?> cls : new Class[]{CreateDeclarationRequest.class, GetMetadataRequest.class}) {
            for (Method m : cls.getMethods()) {
                assertFalse(m.getName().startsWith("set"),
                        cls.getSimpleName() + " should not have setter: " + m.getName());
            }
        }
        // Classes are final
        assertTrue(Modifier.isFinal(CreateDeclarationRequest.class.getModifiers()));
        assertTrue(Modifier.isFinal(GetMetadataRequest.class.getModifiers()));
    }

    @Test
    public void testCxfBuildersExist() {
        assertTrue(Arrays.stream(CreateDeclarationRequest.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("CreateDeclarationRequestBuilder")));
        assertTrue(Arrays.stream(GetMetadataRequest.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("GetMetadataRequestBuilder")));
        assertTrue(Arrays.stream(ProcessTasksRequest.class.getDeclaredClasses())
                .anyMatch(c -> c.getSimpleName().equals("ProcessTasksRequestBuilder")));
    }

    @Test
    public void testCxfCopyConstructor() {
        CreateDeclarationRequest req1 = CreateDeclarationRequest.createDeclarationRequestBuilder()
                .withName("x")
                .withType("Double")
                .withDocumentation("doc")
                .build();

        CreateDeclarationRequest req2 = CreateDeclarationRequest.createDeclarationRequestBuilder(req1).build();
        assertNotNull(req2);
        assertEquals("x", req2.getName());
        assertEquals("Double", req2.getType());
        assertEquals("doc", req2.getDocumentation());
    }

    @Test
    public void testMarshalUnmarshalRoundtrip() throws Exception {
        CreateDeclarationRequest req = CreateDeclarationRequest.createDeclarationRequestBuilder()
                .withName("x")
                .withType("Double")
                .withDocumentation("doc")
                .build();

        JAXBContext jc = JAXBContext.newInstance(CreateDeclarationRequest.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        marshaller.marshal(req, sw);
        String xml = sw.toString();
        assertTrue(xml.contains("Double"));

        Unmarshaller unmarshaller = jc.createUnmarshaller();
        CreateDeclarationRequest req2 = (CreateDeclarationRequest) unmarshaller.unmarshal(new StringReader(xml));
        assertNotNull(req2);
        assertEquals("x", req2.getName());
        assertEquals("Double", req2.getType());
        assertEquals("doc", req2.getDocumentation());
    }

    @Test
    public void testCxfServiceInterfaceGenerated() {
        assertTrue(TestServicePortType.class.isInterface());
        assertTrue(Arrays.stream(TestServicePortType.class.getMethods())
                .anyMatch(m -> m.getName().equals("createDeclaration")));
        assertTrue(Arrays.stream(TestServicePortType.class.getMethods())
                .anyMatch(m -> m.getName().equals("getMetadata")));
        assertTrue(Arrays.stream(TestServicePortType.class.getMethods())
                .anyMatch(m -> m.getName().equals("processTasks")));
    }

    @Test
    public void testCopyConstructorNullThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
            CreateDeclarationRequest.createDeclarationRequestBuilder(null));
    }
}
