package com.github.sabomichal.immutablexjc.test;

import com.github.sabomichal.immutablexjc.test.optional.BaseEntity;
import com.github.sabomichal.immutablexjc.test.optional.DecimalExtensionType;
import com.github.sabomichal.immutablexjc.test.optional.Declaration;
import com.github.sabomichal.immutablexjc.test.optional.Metadata;
import com.github.sabomichal.immutablexjc.test.optional.Model;
import com.github.sabomichal.immutablexjc.test.optional.NameExpression;
import com.github.sabomichal.immutablexjc.test.optional.NoOptionalForPrimitive;
import com.github.sabomichal.immutablexjc.test.optional.StatusType;
import com.github.sabomichal.immutablexjc.test.optional.Task;
import com.github.sabomichal.immutablexjc.test.optional.TidyBedroom;
import com.github.sabomichal.immutablexjc.test.optional.Variable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests -Ximm -Ximm-builder -Ximm-cc -Ximm-optionalgetter.
 * Optional getters for non-required fields, copy constructor handles Optional unwrapping.
 */
public class TestOptionalGetter {

    @Test
    public void testOptionalGetterForNonRequiredElement() {
        // documentation is minOccurs="0" → Optional
        Declaration d1 = Declaration.declarationBuilder()
                .withType("t").withName("n").build();
        assertTrue(d1.getDocumentation().isEmpty());

        Declaration d2 = Declaration.declarationBuilder()
                .withType("t").withName("n").withDocumentation("doc").build();
        assertEquals("doc", d2.getDocumentation().orElse(null));
    }

    @Test
    public void testOptionalGetterForNonRequiredAttribute() {
        // comment is optional attribute → Optional
        Variable v = Variable.variableBuilder().withName("n").build();
        assertTrue(v.getComment().isEmpty());

        Variable v2 = Variable.variableBuilder().withName("n").withComment("c").build();
        assertEquals("c", v2.getComment().orElse(null));
    }

    @Test
    public void testOptionalGetterReturnType() throws Exception {
        Method getDoc = Declaration.class.getMethod("getDocumentation");
        assertEquals(Optional.class, getDoc.getReturnType());

        Method getComment = Variable.class.getMethod("getComment");
        assertEquals(Optional.class, getComment.getReturnType());

        Method getDesc = BaseEntity.class.getMethod("getDescription");
        assertEquals(Optional.class, getDesc.getReturnType());

        Method getModified = Metadata.class.getMethod("getModified");
        assertEquals(Optional.class, getModified.getReturnType());
    }

    @Test
    public void testRequiredFieldNotWrapped() {
        // type is use="required" → returns raw String
        Declaration d = Declaration.declarationBuilder()
                .withType("Double").withName("n").build();
        assertEquals("Double", d.getType());
    }

    @Test
    public void testRequiredFieldReturnType() throws Exception {
        Method getType = Declaration.class.getMethod("getType");
        assertEquals(String.class, getType.getReturnType());

        Method getName = BaseEntity.class.getMethod("getName");
        assertEquals(String.class, getName.getReturnType());
    }

    @Test
    public void testPrimitiveFieldNotWrapped() throws Exception {
        // Required primitive → raw type, not Optional
        Method getCost = Task.class.getMethod("getCost");
        assertEquals(int.class, getCost.getReturnType());

        Method getRevision = Metadata.class.getMethod("getRevision");
        assertEquals(int.class, getRevision.getReturnType());

        Method isActive = Metadata.class.getMethod("isActive");
        assertEquals(boolean.class, isActive.getReturnType());

        Method getPriority = Metadata.class.getMethod("getPriority");
        assertEquals(short.class, getPriority.getReturnType());
    }

    @Test
    public void testPrimitiveElementNotWrapped() throws Exception {
        // NoOptionalForPrimitive.index is a required xs:int element → raw int
        Method getIndex = NoOptionalForPrimitive.class.getMethod("getIndex");
        assertEquals(int.class, getIndex.getReturnType());
    }

    @Test
    public void testOptionalPrimitiveBecomesWrapperOptional() {
        // TidyBedroom.experiencePoints is optional xs:int → Optional<Integer>
        TidyBedroom tb = TidyBedroom.tidyBedroomBuilder().withCost(5).build();
        assertTrue(tb.getExperiencePoints().isEmpty());

        TidyBedroom tb2 = TidyBedroom.tidyBedroomBuilder().withCost(5).withExperiencePoints(10).build();
        assertEquals(10, tb2.getExperiencePoints().orElse(0));
    }

    @Test
    public void testXmlValueNotWrapped() throws Exception {
        // @XmlValue is always required → returns BigDecimal (not Optional)
        Method getValue = DecimalExtensionType.class.getMethod("getValue");
        assertEquals(BigDecimal.class, getValue.getReturnType());
    }

    @Test
    public void testSimpleContentOptionalAttribute() {
        // unit attribute is optional → Optional<String>
        DecimalExtensionType det = new DecimalExtensionType(new BigDecimal("1.23"), null);
        assertTrue(det.getUnit().isEmpty());

        DecimalExtensionType det2 = new DecimalExtensionType(new BigDecimal("1.23"), "s");
        assertEquals("s", det2.getUnit().orElse(null));
    }

    @Test
    public void testCopyConstructorHandlesOptionalInheritance() {
        // Copy constructor must use .orElse(null) for Optional getters on superclass (#142/#143)
        Declaration d1 = Declaration.declarationBuilder()
                .withType("t")
                .withName("n")
                .withDescription("desc")
                .withComment("comment")
                .withDocumentation("doc")
                .addBy(NameExpression.nameExpressionBuilder().withName("a").build())
                .build();

        Declaration d2 = Declaration.declarationBuilder(d1).build();
        assertNotNull(d2);
        assertEquals("n", d2.getName());
        assertEquals("t", d2.getType());
        assertEquals("desc", d2.getDescription().orElse(null));
        assertEquals("comment", d2.getComment().orElse(null));
        assertEquals("doc", d2.getDocumentation().orElse(null));
        assertEquals(1, d2.getBy().size());
    }

    @Test
    public void testCopyConstructorPreservesNullOptional() {
        // Optional field with null → Optional.empty() after copy
        Declaration d1 = Declaration.declarationBuilder()
                .withType("t").withName("n").build();
        assertTrue(d1.getComment().isEmpty());

        Declaration d2 = Declaration.declarationBuilder(d1).build();
        assertTrue(d2.getComment().isEmpty());
        assertTrue(d2.getDescription().isEmpty());
        assertTrue(d2.getDocumentation().isEmpty());
    }

    @Test
    public void testUnmarshalFullModel() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model.xml"));
        assertNotNull(model);
        Declaration decl = model.getParameters().getParameter().get(0);
        // populated optional fields → present
        assertTrue(decl.getDocumentation().isPresent());
        assertEquals("test documentation", decl.getDocumentation().orElse(null));
        assertTrue(decl.getComment().isPresent());
        assertEquals("a variable", decl.getComment().orElse(null));
        // modified is absent in model.xml → empty
        assertTrue(model.getMetadata().isPresent());
        assertTrue(model.getMetadata().get().getModified().isEmpty());
        // required fields remain unwrapped
        assertEquals("Double", decl.getType());
        assertEquals("x", decl.getName());
        // enum (status is optional attribute → Optional<StatusType>)
        assertEquals(StatusType.ACTIVE, model.getStatus().orElse(null));
        // TidyBedroom with experiencePoints present
        TidyBedroom tb = (TidyBedroom) model.getTasks().get().getDoLaundryOrWashCarOrTidyBedroom().get(2);
        assertTrue(tb.getExperiencePoints().isPresent());
        assertEquals(15, tb.getExperiencePoints().orElse(0));
    }

    @Test
    public void testUnmarshalOptionalEmpty() throws Exception {
        JAXBContext jc = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(this.getClass().getResourceAsStream("/model-optional-empty.xml"));
        assertNotNull(model);
        Declaration decl = model.getParameters().getParameter().get(0);
        // all optional fields omitted → empty
        assertTrue(decl.getDocumentation().isEmpty());
        assertTrue(decl.getComment().isEmpty());
        assertTrue(decl.getDescription().isEmpty());
        // TidyBedroom without experiencePoints → empty
        TidyBedroom tb = (TidyBedroom) model.getTasks().get().getDoLaundryOrWashCarOrTidyBedroom().get(1);
        assertTrue(tb.getExperiencePoints().isEmpty());
        assertEquals(2, tb.getCost());
    }

    @Test
    public void testCollectionFieldNotWrappedInOptional() throws Exception {
        // Collection getters return List (not Optional<List>)
        Method getBy = Variable.class.getMethod("getBy");
        assertEquals(java.util.List.class, getBy.getReturnType());

        Method getTags = BaseEntity.class.getMethod("getTags");
        assertEquals(java.util.List.class, getTags.getReturnType());
    }

    @Test
    public void testDecimalExtensionTypeBuilder() {
        DecimalExtensionType det = DecimalExtensionType.decimalExtensionTypeBuilder()
                .withValue(new BigDecimal("9.99"))
                .withUnit("kg")
                .build();
        assertEquals(new BigDecimal("9.99"), det.getValue());
        assertEquals("kg", det.getUnit().orElse(null));
    }

    @Test
    public void testDecimalExtensionTypeCopyConstructor() {
        DecimalExtensionType det1 = new DecimalExtensionType(new BigDecimal("1.23"), "s");
        DecimalExtensionType det2 = DecimalExtensionType.decimalExtensionTypeBuilder(det1).build();
        assertEquals(det1.getValue(), det2.getValue());
        assertEquals(det1.getUnit(), det2.getUnit());
    }

    @Test
    public void testCopyConstructorNullThrowsNPE() {
        assertThrows(NullPointerException.class, () -> Declaration.declarationBuilder(null));
    }
}
