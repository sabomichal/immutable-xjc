package com.github.sabomichal.immutablexjc;

import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.xml.sax.ErrorHandler;

import java.beans.Introspector;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * IMMUTABLE-XJC plugin implementation.
 *
 * @author <a href="mailto:sabo.michal@gmail.com">Michal Sabo</a>
 */
public final class PluginImpl extends Plugin {

    private static final String OPTION_NAME = "Ximm";

    private static final String BUILDER_OPTION_NAME = "-Ximm-builder";
    private static final String SIMPLEBUILDERNAME_OPTION_NAME = "-Ximm-simplebuildername";
    private static final String INHERIT_BUILDER_OPTION_NAME = "-Ximm-inheritbuilder";
    private static final String CCONSTRUCTOR_OPTION_NAME = "-Ximm-cc";
    private static final String WITHIFNOTNULL_OPTION_NAME = "-Ximm-ifnotnull";
    private static final String NOPUBLICCONSTRUCTOR_OPTION_NAME = "-Ximm-nopubconstructor";
    private static final String PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME = "-Ximm-pubconstructormaxargs";
    private static final String SKIPCOLLECTIONS_OPTION_NAME = "-Ximm-skipcollections";
    private static final String CONSTRUCTORDEFAULTS_OPTION_NAME = "-Ximm-constructordefaults";
    private static final String OPTIONAL_GETTER_OPTION_NAME = "-Ximm-optionalgetter";
    private static final String NOFINALCLASSES_OPTION_NAME = "-Ximm-nofinalclasses";

    private static final String UNSET_PREFIX = "unset";
    private static final String SET_PREFIX = "set";
    private static final String MESSAGE_PREFIX = "IMMUTABLE-XJC";
    private static final JType[] NO_ARGS = new JType[0];

    private final ResourceBundle resourceBundle = ResourceBundle.getBundle(PluginImpl.class.getCanonicalName());

    private boolean createBuilder;
    private boolean builderInheritance;
    private boolean createCConstructor;
    private boolean createWithIfNotNullMethod;
    private boolean createBuilderWithoutPublicConstructor;
    private int publicConstructorMaxArgs = Integer.MAX_VALUE;
    private boolean leaveCollectionsMutable;
    private boolean setDefaultValuesInConstructor;
    private boolean useSimpleBuilderName;
    private boolean optionalGetter;
    private boolean noFinalClasses;
    private Options options;

    @Override
    public boolean run(final Outline model, final Options options, final ErrorHandler errorHandler) {
        boolean success = true;
        this.options = options;

        this.log(Level.INFO, "title");

        List<? extends ClassOutline> classes = new ArrayList<ClassOutline>(model.getClasses());
        if (builderInheritance) {
            classes.sort(new Comparator<ClassOutline>() {
                @Override
                public int compare(ClassOutline o1, ClassOutline o2) {
                    return Integer.compare(getDepth(o1), getDepth(o2));
                }

                private int getDepth(ClassOutline outline) {
                    int depth = 0;
                    while ((outline = outline.getSuperClass()) != null) {
                        ++depth;
                    }
                    return depth;
                }
            });
        }
        for (ClassOutline clazz : classes) {
            JDefinedClass implClass = clazz.implClass;

            JFieldVar[] declaredFields = getDeclaredFields(implClass);
            ClassField[] superclassFieldsWithOwners = getSuperclassFields(implClass);
            JFieldVar[] superclassFields = Arrays.stream(superclassFieldsWithOwners).map(ClassField::getField).toArray(JFieldVar[]::new);

            makePropertiesPrivate(implClass);
            makePropertiesFinal(implClass, declaredFields);

            int declaredFieldsLength = declaredFields.length;
            int superclassFieldsLength = superclassFields.length;
            JMethod propertyContructor = null;
            if (declaredFieldsLength + superclassFieldsLength > 0) {
                int mod;
                if (createBuilderWithoutPublicConstructor
                        || (createBuilder && declaredFieldsLength + superclassFieldsLength > publicConstructorMaxArgs)) {
                    mod = JMod.NONE;
                } else {
                    mod = JMod.PUBLIC;
                }
                propertyContructor = addPropertyContructor(implClass, declaredFields, superclassFields, mod);
                if (propertyContructor == null) {
                    log(Level.WARNING, "couldNotAddPropertyCtor", implClass.binaryName());
                }
            }
            if (propertyContructor == null || !propertyContructor.params().isEmpty()) {
                addStandardConstructor(implClass, declaredFields, superclassFields);
            }

            makeClassFinal(implClass);
            removeSetters(implClass);
            replaceCollectionGetters(implClass, declaredFields);

            if (optionalGetter) {
                replaceOptionalGetters(implClass, declaredFields);
            }

            if (createBuilder) {
                if (!clazz.implClass.isAbstract()) {
                    JFieldVar[] unhandledSuperclassFields = getUnhandledSuperclassFields(superclassFieldsWithOwners);
                    JDefinedClass builderClass;
                    if ((builderClass = addBuilderClass(clazz, declaredFields, unhandledSuperclassFields, superclassFields)) == null) {
                        log(Level.WARNING, "couldNotAddClassBuilder", implClass.binaryName());
                    }

                    if (createCConstructor && builderClass != null) {
                        addCopyConstructor(clazz.implClass, builderClass, declaredFields, unhandledSuperclassFields);
                    }
                }
            }
        }

        // if superclass is a JAXB bound class or an abstract class, revert setting it final
        for (ClassOutline clazz : model.getClasses()) {
            if (clazz.getSuperClass() != null) {
                clazz.getSuperClass().implClass.mods().setFinal(false);
            } else if (clazz.implClass.isAbstract()) {
                clazz.implClass.mods().setFinal(false);
            }
        }

        this.options = null;

        return success;
    }

    @Override
    public String getOptionName() {
        return OPTION_NAME;
    }

    @Override
    public String getUsage() {
        final String n = System.getProperty("line.separator", "\n");
        final int maxOptionLength = PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME.length();
        StringBuilder retval = new StringBuilder();
        appendOption(retval, "-" + OPTION_NAME, getMessage("usage"), n, maxOptionLength);
        appendOption(retval, BUILDER_OPTION_NAME, getMessage("builderUsage"), n, maxOptionLength);
        appendOption(retval, SIMPLEBUILDERNAME_OPTION_NAME, getMessage("simpleBuilderNameUsage"), n, maxOptionLength);
        appendOption(retval, INHERIT_BUILDER_OPTION_NAME, getMessage("inheritBuilderUsage"), n, maxOptionLength);
        appendOption(retval, CCONSTRUCTOR_OPTION_NAME, getMessage("cConstructorUsage"), n, maxOptionLength);
        appendOption(retval, WITHIFNOTNULL_OPTION_NAME, getMessage("withIfNotNullUsage"), n, maxOptionLength);
        appendOption(retval, NOPUBLICCONSTRUCTOR_OPTION_NAME, getMessage("builderWithoutPublicConstructor"), n, maxOptionLength);
        appendOption(retval, SKIPCOLLECTIONS_OPTION_NAME, getMessage("leaveCollectionsMutable"), n, maxOptionLength);
        appendOption(retval, PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME, getMessage("publicConstructorMaxArgs"), n, maxOptionLength);
        appendOption(retval, CONSTRUCTORDEFAULTS_OPTION_NAME, getMessage("setDefaultValuesInConstructor"), n, maxOptionLength);
        appendOption(retval, OPTIONAL_GETTER_OPTION_NAME, getMessage("optionalGetterUsage"), n, maxOptionLength);
        appendOption(retval, NOFINALCLASSES_OPTION_NAME, getMessage("noFinalClassesUsage"), n, maxOptionLength);
        return retval.toString();
    }

    private void appendOption(StringBuilder retval, String option, String description, String n, int optionColumnWidth) {
        retval.append("  ");
        retval.append(option);
        retval.append(" ".repeat(Math.max(0, optionColumnWidth - option.length())));
        retval.append(" :  ");
        retval.append(description);
        retval.append(n);
    }

    @Override
    public int parseArgument(final Options opt, final String[] args, final int i) {
        if (args[i].startsWith(BUILDER_OPTION_NAME)) {
            this.createBuilder = true;
            return 1;
        }
        if (args[i].startsWith(SIMPLEBUILDERNAME_OPTION_NAME)) {
            this.useSimpleBuilderName = true;
            return 1;
        }
        if (args[i].startsWith(INHERIT_BUILDER_OPTION_NAME)) {
            this.createBuilder = true;
            this.builderInheritance = true;
            return 1;
        }
        if (args[i].startsWith(CCONSTRUCTOR_OPTION_NAME)) {
            this.createCConstructor = true;
            return 1;
        }
        if (args[i].startsWith(WITHIFNOTNULL_OPTION_NAME)) {
            this.createWithIfNotNullMethod = true;
            return 1;
        }
        if (args[i].startsWith(NOPUBLICCONSTRUCTOR_OPTION_NAME)) {
            this.createBuilderWithoutPublicConstructor = true;
            return 1;
        }
        if (args[i].startsWith(SKIPCOLLECTIONS_OPTION_NAME)) {
            this.leaveCollectionsMutable = true;
            return 1;
        }
        if (args[i].startsWith(PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME)) {
            this.publicConstructorMaxArgs = Integer.parseInt(args[i].substring(PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME.length() + 1));
            return 1;
        }
        if (args[i].startsWith(CONSTRUCTORDEFAULTS_OPTION_NAME)) {
            this.setDefaultValuesInConstructor = true;
            return 1;
        }
        if (args[i].startsWith(OPTIONAL_GETTER_OPTION_NAME)) {
            this.optionalGetter = true;
            return 1;
        }
        if (args[i].startsWith(NOFINALCLASSES_OPTION_NAME)) {
            this.noFinalClasses = true;
            return 1;
        }
        return 0;
    }

    private String getMessage(final String key, final Object... args) {
        return MessageFormat.format(resourceBundle.getString(key), args);
    }

    private JDefinedClass addBuilderClass(ClassOutline clazz, JFieldVar[] declaredFields, JFieldVar[] unhandledSuperclassFields, JFieldVar[] allSuperclassFields) {
        JDefinedClass builderClass = generateBuilderClass(clazz.implClass);
        if (builderClass == null) {
            return null;
        }
        addBuilderMethodsForFields(builderClass, declaredFields);
        // handle all superclass fields not handled by any superclass builder
        addBuilderMethodsForFields(builderClass, unhandledSuperclassFields);
        if (builderInheritance) {
            // re-type inherited builder methods
            for (int i = 0; i < allSuperclassFields.length - unhandledSuperclassFields.length; i++) {
                JFieldVar inheritedField = allSuperclassFields[i];
                JMethod unconditionalWithMethod = addWithMethod(builderClass, inheritedField, true);
                if (createWithIfNotNullMethod) {
                    addWithIfNotNullMethod(builderClass, inheritedField, unconditionalWithMethod, true);
                }
                if (isCollection(inheritedField)) {
                    addAddMethod(builderClass, inheritedField, true);
                }
            }
        }
        addNewBuilder(clazz, builderClass);
        if (createCConstructor) {
            addNewBuilderCc(clazz, builderClass);
        }
        addBuildMethod(clazz.implClass, builderClass, declaredFields, allSuperclassFields);
        return builderClass;
    }

    private void addBuilderMethodsForFields(JDefinedClass builderClass, JFieldVar[] declaredFields) {
        for (JFieldVar field : declaredFields) {
            addProperty(builderClass, field);
            JMethod unconditionalWithMethod = addWithMethod(builderClass, field, false);
            if (createWithIfNotNullMethod) {
                addWithIfNotNullMethod(builderClass, field, unconditionalWithMethod, false);
            }
            if (isCollection(field)) {
                addAddMethod(builderClass, field, false);
            }
        }
    }

    private JVar addProperty(JDefinedClass clazz, JFieldVar field) {
        JType jType = getJavaType(field);
        int builderFieldVisibility = builderInheritance ? JMod.PROTECTED : JMod.PRIVATE;
        if (isCollection(field)) {
            return clazz.field(builderFieldVisibility, jType, field.name(),
                    getNewCollectionExpression(field.type().owner(), jType));
        } else {
            return clazz.field(builderFieldVisibility, jType, field.name());
        }
    }

    private JMethod addBuildMethod(JDefinedClass clazz, JDefinedClass builderClass, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        JMethod method = builderClass.method(JMod.PUBLIC, clazz, "build");
        if (hasSuperClass(builderClass)) {
            method.annotate(Override.class);
        }
        JInvocation constructorInvocation = JExpr._new(clazz);
        for (JFieldVar field : superclassFields) {
            if (mustAssign(field)) {
                constructorInvocation.arg(JExpr.ref(field.name()));
            }
        }
        for (JFieldVar field : declaredFields) {
            if (mustAssign(field)) {
                if (isRequired(field) && !field.type().isPrimitive()) {
                    JBlock block = method.body();
                    JConditional conditional = block._if(field.eq(JExpr._null()));
                    conditional._then()._throw(JExpr._new(builderClass.owner().ref(NullPointerException.class))
                            .arg("Required field '" + field.name() + "' have to be assigned a value."));
                }
                constructorInvocation.arg(JExpr.ref(field.name()));
            }
        }
        method.body()._return(constructorInvocation);
        return method;
    }

    private void addNewBuilder(ClassOutline clazz, JDefinedClass builderClass) {
        if (builderInheritance || !hasSuperClassWithSameName(clazz)) {
            String builderMethodName = generateBuilderMethodName(clazz);
            JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, builderMethodName);
            method.body()._return(JExpr._new(builderClass));
        }
    }

    private void addNewBuilderCc(ClassOutline clazz, JDefinedClass builderClass) {
        if (builderInheritance || !hasSuperClassWithSameName(clazz)) {
            String builderMethodName = generateBuilderMethodName(clazz);
            JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, builderMethodName);
            JVar param = method.param(JMod.FINAL, clazz.implClass, "o");
            method.body()._return(JExpr._new(builderClass).arg(param));
        }
    }

    private String generateBuilderMethodName(ClassOutline clazz) {
        if (isUseSimpleBuilderName()) {
            return "builder";
        }
        return Introspector.decapitalize(clazz.implClass.name()) + "Builder";
    }

    private boolean isUseSimpleBuilderName() {
        return useSimpleBuilderName;
    }

    private boolean hasSuperClassWithSameName(ClassOutline clazz) {
        ClassOutline superclass = clazz.getSuperClass();
        while (superclass != null) {
            if (superclass.implClass.name().equals(clazz.implClass.name())) {
                return true;
            }
            superclass = superclass.getSuperClass();
        }
        return false;
    }

    private JMethod addPropertyContructor(JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields, int constAccess) {
        JMethod ctor = clazz.getConstructor(getFieldTypes(declaredFields, superclassFields));
        if (ctor == null) {
            ctor = this.generatePropertyConstructor(clazz, declaredFields, superclassFields, constAccess);
        } else {
            this.log(Level.WARNING, "standardCtorExists");
        }
        return ctor;
    }

    private JMethod addStandardConstructor(final JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        JMethod ctor = clazz.getConstructor(NO_ARGS);
        if (ctor == null) {
            ctor = this.generateStandardConstructor(clazz, declaredFields, superclassFields);
        } else {
            this.log(Level.WARNING, "standardCtorExists");
        }
        return ctor;
    }

    private JMethod addCopyConstructor(final JDefinedClass clazz, final JDefinedClass builderClass, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        JMethod ctor = generateCopyConstructor(clazz, builderClass, declaredFields, superclassFields);
        createConstructor(builderClass, JMod.PUBLIC);
        return ctor;
    }

    private JMethod addWithMethod(JDefinedClass builderClass, JFieldVar field, boolean inherit) {
        String fieldName = StringUtils.capitalize(field.name());
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName);
        if (inherit) {
            generateMethodParameter(method, field);
            generateSuperCall(method);
        } else {
            generatePropertyAssignment(method, field);
        }
        method.body()._return(JExpr._this());
        return method;
    }

    private JMethod addWithIfNotNullMethod(JDefinedClass builderClass, JFieldVar field, JMethod unconditionalWithMethod, boolean inherit) {
        if (field.type().isPrimitive())
            return null;
        String fieldName = StringUtils.capitalize(field.name());
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName + "IfNotNull");
        JVar param = generateMethodParameter(method, field);
        JBlock block = method.body();
        if (inherit) {
            generateSuperCall(method);
            method.body()._return(JExpr._this());
        } else {
            JConditional conditional = block._if(param.eq(JExpr._null()));
            conditional._then()._return(JExpr._this());
            conditional._else()._return(JExpr.invoke(unconditionalWithMethod).arg(param));
        }
        return method;
    }

    private JMethod addAddMethod(JDefinedClass builderClass, JFieldVar field, boolean inherit) {
        List<JClass> typeParams = ((JClass) getJavaType(field)).getTypeParameters();
        if (typeParams.isEmpty()) {
            return null;
        }
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "add" + StringUtils.capitalize(field.name()));
        JBlock block = method.body();
        String fieldName = field.name();

        List<JVar> params = createAddParameters(method, typeParams, fieldName);

        if (inherit) {
            generateSuperCall(method);
        } else if(isCollection(field)) {
            method.body().add(field.invoke("clear"));
            String methodName = isMap(field) ? "putAll" : "addAll";
            JVar param = generateMethodParameter(method, field);
            JInvocation invocation = field.invoke(methodName).arg(param);
            method.body().add(invocation);
        } else {
            String methodName = isMap(field) ? "put" : "add";
            JInvocation invocation = JExpr.refthis(fieldName).invoke(methodName);
            params.forEach(invocation::arg);
            block.add(invocation);
        }
        block._return(JExpr._this());
        return method;
    }

    private List<JVar> createAddParameters(JMethod method, List<JClass> typeParams, String fieldName) {
        return IntStream.range(0, typeParams.size())
                .mapToObj(i -> createAddParameter(method, typeParams, fieldName, i))
                .collect(Collectors.toList());
    }

    private JVar createAddParameter(JMethod method, List<JClass> typeParams, String fieldName, int index) {
        String name = fieldName;
        if (typeParams.size() > 1) {
            name += index;
        }
        return method.param(JMod.FINAL, typeParams.get(index), name);
    }

    private void generateSuperCall(JMethod method) {
        method.annotate(Override.class);
        JBlock block = method.body();
        JInvocation superInvocation = block.invoke(JExpr._super(), method);
        for (JVar param : method.params()) {
            superInvocation.arg(param);
        }
    }

    private JDefinedClass generateBuilderClass(JDefinedClass clazz) {
        JDefinedClass builderClass = null;
        String builderClassName = getBuilderClassName(clazz);
        try {
            builderClass = clazz._class(JMod.PUBLIC | JMod.STATIC, builderClassName);
            if (builderInheritance) {
                for (JClass superClass = clazz._extends(); superClass != null; superClass = superClass._extends()) {
                    JClass superClassBuilderClass = getBuilderClass(superClass);
                    if (superClassBuilderClass != null) {
                        builderClass._extends(superClassBuilderClass);
                        break;
                    }
                }
            }
        } catch (JClassAlreadyExistsException e) {
            this.log(Level.WARNING, "builderClassExists", builderClassName);
        }
        return builderClass;
    }

    private String getBuilderClassName(JClass clazz) {
        if (isUseSimpleBuilderName()) {
            return "Builder";
        }
        return clazz.name() + "Builder";
    }

    private JClass getBuilderClass(JClass clazz) {
        //Current limitation: this only works for classes from this model / outline, i.e. that are part of this generator run
        if (!createBuilder || clazz.isAbstract()) {
            return null;
        }
        String builderClassName = getBuilderClassName(clazz);
        if (clazz instanceof JDefinedClass) {
            JDefinedClass definedClass = (JDefinedClass) clazz;
            for (Iterator<JDefinedClass> i = definedClass.classes(); i.hasNext(); ) {
                JDefinedClass innerClass = i.next();
                if (builderClassName.equals(innerClass.name())) {
                    return innerClass;
                }
            }
        }
        return null;
    }

    private void replaceOptionalGetters(JDefinedClass implClass, JFieldVar[] declaredFields) {
        for (JFieldVar field : declaredFields) {
            if (isCollection(field)) {
                continue;
            }

            if (!isRequired(field)) {
                JMethod getterMethod = getGetterProperty(field, implClass);
                if (getterMethod != null) {
                    replaceOptionalGetter(implClass, field, getterMethod);
                }
            }
        }
    }

    private void replaceOptionalGetter(JDefinedClass ownerClass, JFieldVar field, final JMethod getter) {
        // remove the old getter
        ownerClass.methods().remove(getter);

        JCodeModel codeModel = field.type().owner();

        final JClass optionalWrappedReturnType = codeModel.ref(Optional.class).narrow(field.type());

        // and create a new one
        JMethod newGetter = ownerClass.method(getter.mods().getValue(), optionalWrappedReturnType, getter.name());
        JBlock block = newGetter.body();

        JVar param = generateMethodParameter(getter, field);

        block._return(getOptionalWrappedExpression(codeModel, param));

        getter.javadoc().append("Returns optional attribute/element.");
    }

    private void replaceCollectionGetters(JDefinedClass implClass, JFieldVar[] declaredFields) {
        for (JFieldVar field : declaredFields) {
            if (isCollection(field) && !leaveCollectionsMutable) {
                JMethod getterMethod = getGetterProperty(field, implClass);
                if (getterMethod != null) {
                    replaceCollectionGetter(implClass, field, getterMethod);
                }
            }
        }
    }

    private void replaceCollectionGetter(JDefinedClass ownerClass, JFieldVar field, final JMethod getter) {
        // remove the old getter
        ownerClass.methods().remove(getter);
        // and create a new one
        JMethod newGetter = ownerClass.method(getter.mods().getValue(), getter.type(), getter.name());
        JBlock block = newGetter.body();

        JVar ret = block.decl(getJavaType(field), "ret");
        JCodeModel codeModel = field.type().owner();
        JVar param = generateMethodParameter(getter, field);
        JConditional conditional = block._if(param.eq(JExpr._null()));
        conditional._then().assign(ret, getEmptyCollectionExpression(codeModel, param));
        conditional._else().assign(ret, getUnmodifiableWrappedExpression(codeModel, param));
        block._return(ret);

        getter.javadoc().append("Returns unmodifiable collection.");
    }

    private void generatePropertyAssignment(final JMethod method, JFieldVar field) {
        generatePropertyAssignment(method, field, false);
    }

    private void generatePropertyAssignment(final JMethod method, JFieldVar field, boolean wrapUnmodifiable) {
        JBlock block = method.body();
        JCodeModel codeModel = field.type().owner();
        String fieldName = field.name();
        JVar param = generateMethodParameter(method, field);
        if (isCollection(field) && !leaveCollectionsMutable && wrapUnmodifiable) {
            JConditional conditional = block._if(param.eq(JExpr._null()));
            conditional._then().assign(JExpr.refthis(fieldName), JExpr._null());
            conditional._else().assign(JExpr.refthis(fieldName),
                    getDefensiveCopyExpression(codeModel, getJavaType(field), param));
        } else {
            block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
        }
    }

    private JVar generateMethodParameter(final JMethod method, JFieldVar field) {
        String fieldName = field.name();
        JType javaType = getJavaType(field);
        return method.param(JMod.FINAL, javaType, fieldName);
    }

    private JExpression getDefensiveCopyExpression(JCodeModel codeModel, JType jType, JVar param) {
        List<JClass> typeParams = ((JClass) jType).getTypeParameters();

        JClass newClass = null;
        if (param.type().erasure().equals(codeModel.ref(Collection.class))) {
            newClass = codeModel.ref(ArrayList.class);
        } else if (param.type().erasure().equals(codeModel.ref(List.class))) {
            newClass = codeModel.ref(ArrayList.class);
        } else if (param.type().erasure().equals(codeModel.ref(Map.class))) {
            newClass = codeModel.ref(HashMap.class);
        } else if (param.type().erasure().equals(codeModel.ref(Set.class))) {
            newClass = codeModel.ref(HashSet.class);
        } else if (param.type().erasure().equals(codeModel.ref(SortedMap.class))) {
            newClass = codeModel.ref(TreeMap.class);
        } else if (param.type().erasure().equals(codeModel.ref(SortedSet.class))) {
            newClass = codeModel.ref(TreeSet.class);
        }
        if (newClass != null && !typeParams.isEmpty()) {
            newClass = newClass.narrow(typeParams);
        }
        return newClass == null ? JExpr._null() : JExpr._new(newClass).arg(param);
    }

    private JExpression getUnmodifiableWrappedExpression(JCodeModel codeModel, JVar param) {
        if (param.type().erasure().equals(codeModel.ref(Collection.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableCollection").arg(param);
        } else if (param.type().erasure().equals(codeModel.ref(List.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableList").arg(param);
        } else if (param.type().erasure().equals(codeModel.ref(Map.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableMap").arg(param);
        } else if (param.type().erasure().equals(codeModel.ref(Set.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableSet").arg(param);
        } else if (param.type().erasure().equals(codeModel.ref(SortedMap.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableSortedMap").arg(param);
        } else if (param.type().erasure().equals(codeModel.ref(SortedSet.class))) {
            return codeModel.ref(Collections.class).staticInvoke("unmodifiableSortedSet").arg(param);
        }
        return param;
    }

    private JExpression getEmptyCollectionExpression(JCodeModel codeModel, JVar param) {
        if (param.type().erasure().equals(codeModel.ref(Collection.class))) {
            return codeModel.ref(Collections.class).staticInvoke("emptyList");
        } else if (param.type().erasure().equals(codeModel.ref(List.class))) {
            return codeModel.ref(Collections.class).staticInvoke("emptyList");
        } else if (param.type().erasure().equals(codeModel.ref(Map.class))) {
            return codeModel.ref(Collections.class).staticInvoke("emptyMap");
        } else if (param.type().erasure().equals(codeModel.ref(Set.class))) {
            return codeModel.ref(Collections.class).staticInvoke("emptySet");
        } else if (param.type().erasure().equals(codeModel.ref(SortedMap.class))) {
            return JExpr._new(codeModel.ref(TreeMap.class));
        } else if (param.type().erasure().equals(codeModel.ref(SortedSet.class))) {
            return JExpr._new(codeModel.ref(TreeSet.class));
        }
        return param;
    }

    private JExpression getNewCollectionExpression(JCodeModel codeModel, JType jType) {
        List<JClass> typeParams = ((JClass) jType).getTypeParameters();

        JClass newClass = null;
        if (jType.erasure().equals(codeModel.ref(Collection.class))) {
            newClass = codeModel.ref(ArrayList.class);
        } else if (jType.erasure().equals(codeModel.ref(List.class))) {
            newClass = codeModel.ref(ArrayList.class);
        } else if (jType.erasure().equals(codeModel.ref(Map.class))) {
            newClass = codeModel.ref(HashMap.class);
        } else if (jType.erasure().equals(codeModel.ref(Set.class))) {
            newClass = codeModel.ref(HashSet.class);
        } else if (jType.erasure().equals(codeModel.ref(SortedMap.class))) {
            newClass = codeModel.ref(TreeMap.class);
        } else if (jType.erasure().equals(codeModel.ref(SortedSet.class))) {
            newClass = codeModel.ref(TreeSet.class);
        }
        if (newClass != null && !typeParams.isEmpty()) {
            newClass = newClass.narrow(typeParams);
        }

        return newClass == null ? JExpr._null() : JExpr._new(newClass);
    }

    private JExpression getOptionalWrappedExpression(JCodeModel codeModel, JVar param) {
        return codeModel.ref(Optional.class).staticInvoke("ofNullable").arg(param);
    }

    private void generateDefaultPropertyAssignment(JMethod method, JFieldVar field) {
        JBlock block = method.body();
        String propertyName = field.name();
        block.assign(JExpr.refthis(propertyName), defaultValue(field));
    }

    private JExpression defaultValue(JFieldVar field) {
        JType javaType = field.type();
        if (setDefaultValuesInConstructor) {
            Optional<JAnnotationUse> xmlElementAnnotation = getAnnotation(field.annotations(), XmlElement.class.getCanonicalName());
            if (xmlElementAnnotation.isPresent()) {
                JAnnotationValue annotationValue = xmlElementAnnotation.get().getAnnotationMembers().get("defaultValue");
                if (annotationValue != null) {
                    StringWriter sw = new StringWriter();
                    JFormatter f = new JFormatter(sw);
                    annotationValue.generate(f);
                    return JExpr.lit(sw.toString().replaceAll("\"", ""));
                }
            }
        }
        if (javaType.isPrimitive()) {
            if (field.type().owner().BOOLEAN.equals(javaType)) {
                return JExpr.lit(false);
            } else if (javaType.owner().SHORT.equals(javaType)) {
                return JExpr.cast(javaType.owner().SHORT, JExpr.lit(0));
            } else {
                return JExpr.lit(0);
            }
        }
        return JExpr._null();
    }

    private Optional<JAnnotationUse> getAnnotation(Collection<JAnnotationUse> annotations, String clazz) {
        return annotations.stream().filter(ann -> ann.getAnnotationClass().fullName().equals(clazz)).findFirst();
    }

    private JMethod generatePropertyConstructor(JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields, int constAccess) {
        final JMethod ctor = createConstructor(clazz, constAccess);
        if (superclassFields.length > 0) {
            JInvocation superInvocation = ctor.body().invoke("super");
            for (JFieldVar field : superclassFields) {
                if (mustAssign(field)) {
                    superInvocation.arg(JExpr.ref(field.name()));
                    generateMethodParameter(ctor, field);
                }
            }
        }

        for (JFieldVar field : declaredFields) {
            if (mustAssign(field)) {
                generatePropertyAssignment(ctor, field, true);
            }
        }
        return ctor;
    }

    private boolean mustAssign(JFieldVar field) {
        // we have to assign final field, except filled collection fields, since we might lose the collection type upon marshal
        return !isFinal(field) || !isCollection(field) || getInitJExpression(field) == null;
    }

    private boolean shouldAssign(JFieldVar field) {
        // we don't want to clear filled collection fields in default constructor, since we might lose the collection type upon marshal
        return !isCollection(field) || getInitJExpression(field) == null;
    }

    private JMethod generateStandardConstructor(final JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        final JMethod ctor = createConstructor(clazz, JMod.PROTECTED);
        ctor.javadoc().add("Used by JAX-B");
        if (superclassFields.length > 0) {
            JInvocation superInvocation = ctor.body().invoke("super");
            for (JFieldVar field : superclassFields) {
                if (mustAssign(field)) {
                    superInvocation.arg(defaultValue(field));
                }
            }
        }
        for (JFieldVar field : declaredFields) {
            if (shouldAssign(field)) {
                generateDefaultPropertyAssignment(ctor, field);
            }
        }
        return ctor;
    }

    private JMethod generateCopyConstructor(final JDefinedClass clazz, final JDefinedClass builderClass, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        final JMethod ctor = createConstructor(builderClass, JMod.PUBLIC);
        final JVar o = ctor.param(JMod.FINAL, clazz, "o");
        if (hasSuperClass(builderClass)) {
            ctor.body().invoke("super").arg(o);
        } else {
            String builderName = isUseSimpleBuilderName() ? String.format("%s.%s", clazz.name(), builderClass.name()) : builderClass.name();
            ctor.body()._if(o.eq(JExpr._null()))._then()
                    ._throw(JExpr._new(builderClass.owner().ref(NullPointerException.class))
                            .arg("Cannot create a copy of '" + builderName + "' from 'null'."));
        }
        JCodeModel codeModel = clazz.owner();

        for (JFieldVar field : superclassFields) {
            String propertyName = field.name();
            JMethod getter = getGetterProperty(field, clazz);
            if (isCollection(field)) {
                JVar tmpVar = ctor.body().decl(0, getJavaType(field), "_" + propertyName, JExpr.invoke(o, getter));
                JConditional conditional = ctor.body()._if(tmpVar.eq(JExpr._null()));
                conditional._then().assign(JExpr.refthis(propertyName), getNewCollectionExpression(codeModel, getJavaType(field)));
                conditional._else().assign(JExpr.refthis(propertyName), getDefensiveCopyExpression(codeModel, getJavaType(field), tmpVar));
            } else if (optionalGetter && !isRequired(field)) {
                ctor.body().assign(JExpr.refthis(propertyName), JExpr.invoke(o, getter).invoke("orElse").arg(JExpr._null()));
            } else {
                ctor.body().assign(JExpr.refthis(propertyName), JExpr.invoke(o, getter));
            }
        }
        for (JFieldVar field : declaredFields) {
            String propertyName = field.name();

            if (isCollection(field)) {
                JVar tmpVar = ctor.body().decl(0, getJavaType(field), "_" + propertyName, JExpr.ref(o, propertyName));
                JConditional conditional = ctor.body()._if(tmpVar.eq(JExpr._null()));
                conditional._then().assign(JExpr.refthis(propertyName), getNewCollectionExpression(codeModel, getJavaType(field)));
                conditional._else().assign(JExpr.refthis(propertyName), getDefensiveCopyExpression(codeModel, getJavaType(field), tmpVar));
            } else {
                ctor.body().assign(JExpr.refthis(propertyName), JExpr.ref(o, propertyName));
            }
        }
        return ctor;
    }

    private boolean hasSuperClass(final JDefinedClass builderClass) {
        // we have to account for java.lang.Object, which we don't care about...
        return builderClass._extends() != null && builderClass._extends()._extends() != null;
    }

    private JMethod createConstructor(final JDefinedClass clazz, final int visibility) {
        return clazz.constructor(visibility);
    }

    private JType getJavaType(JFieldVar field) {
        return field.type();
    }

    private JType[] getFieldTypes(JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
        JType[] fieldTypes = new JType[declaredFields.length + superclassFields.length];
        int i = 0;
        for (JFieldVar field : superclassFields) {
            fieldTypes[i++] = field.type();
        }
        for (JFieldVar field : declaredFields) {
            fieldTypes[i++] = field.type();
        }
        return fieldTypes;
    }

    private JMethod getGetterProperty(final JFieldVar field, final JDefinedClass clazz) {
        JMethod getter = clazz.getMethod("get" + StringUtils.capitalize(field.name()), NO_ARGS);
        if (getter == null) {
            getter = clazz.getMethod("is" + StringUtils.capitalize(field.name()), NO_ARGS);
        }

        if (getter == null) {
            List<JDefinedClass> superClasses = getSuperClasses(clazz);
            for (JDefinedClass definedClass : superClasses) {
                getter = getGetterProperty(field, definedClass);

                if (getter != null) {
                    break;
                }
            }
        }
        if (getter == null) {
            //XJC does not work conform Introspector.decapitalize when multiple upper-case letter are in field name
            Optional<JAnnotationUse> xmlElementAnnotation = getAnnotation(field.annotations(), XmlElement.class.getCanonicalName());
            if (xmlElementAnnotation.isPresent()) {
                JAnnotationValue annotationValue = xmlElementAnnotation.get().getAnnotationMembers().get("name");
                if (annotationValue != null) {
                    StringWriter sw = new StringWriter();
                    JFormatter f = new JFormatter(sw);
                    annotationValue.generate(f);
                    getter = clazz.getMethod("get" + sw.toString().replaceAll("\"", ""), NO_ARGS);
                }
            }
        }
        return getter;
    }

    private void makeClassFinal(JDefinedClass clazz) {
        clazz.mods().setFinal(!noFinalClasses);
    }

    private void makePropertiesPrivate(JDefinedClass clazz) {
        for (JFieldVar field : clazz.fields().values()) {
            field.mods().setPrivate();
        }
    }

    private void makePropertiesFinal(JDefinedClass clazz, JFieldVar[] declaredFields) {
        for (JFieldVar field : declaredFields) {
            String fieldName = field.name();
            clazz.fields().get(fieldName).mods().setFinal(!(leaveCollectionsMutable && isCollection(field)));
            clazz.fields().get(fieldName).init(null); // remove field assignment
        }
    }

    private boolean isCollection(JFieldVar field) {
        if (field.type() instanceof JClass) {
            return isCollection((JClass) field.type());
        }
        return false;
    }

    private boolean isCollection(JClass clazz) {
        return clazz.owner().ref(Collection.class).isAssignableFrom(clazz) ||
                isMap(clazz);
    }

    private boolean isMap(JFieldVar field) {
        if (field.type() instanceof JClass) {
            return isMap((JClass) field.type());
        }
        return false;
    }

    private boolean isMap(JClass clazz) {
        return clazz.equals(clazz.owner().ref(Map.class).narrow(clazz.getTypeParameters()));
    }

    private boolean isRequired(JFieldVar field) {
        if (field.type().isPrimitive()) {
            return true;
        }

        if (getAnnotation(field.annotations(), XmlValue.class.getCanonicalName()).isPresent()) {
            return true;
        }

        return Stream.of(XmlElement.class, XmlAttribute.class)
                .map(annotationType ->
                        getAnnotation(field.annotations(), annotationType.getCanonicalName())
                                .map(JAnnotationUse::getAnnotationMembers)
                                .map(annotationValues -> annotationValues.get("required"))
                                .filter(annotationValue -> {
                                    StringWriter sw = new StringWriter();
                                    JFormatter f = new JFormatter(sw);
                                    annotationValue.generate(f);
                                    return sw.toString().equals("true");
                                })
                ).anyMatch(Optional::isPresent);
    }

    private void removeSetters(JDefinedClass clazz) {
        Collection<JMethod> methods = clazz.methods();
        Iterator<JMethod> it = methods.iterator();
        while (it.hasNext()) {
            JMethod method = it.next();
            String methodName = method.name();
            if (methodName.startsWith(SET_PREFIX) || methodName.startsWith(UNSET_PREFIX)) {
                it.remove();
            }
        }
    }

    private JFieldVar[] getDeclaredFields(JDefinedClass clazz) {
        return clazz.fields().values().stream().filter(f -> !(isFinal(f) && isStatic(f))).toArray(JFieldVar[]::new);
    }

    private ClassField[] getSuperclassFields(JDefinedClass clazz) {
        List<JDefinedClass> superclasses = getSuperClasses(clazz);

        // get all fields in class reverse order
        List<ClassField> superclassFields = new ArrayList<>();
        Collections.reverse(superclasses);
        for (JDefinedClass classOutline : superclasses) {
            Map<String, JFieldVar> fields = classOutline.fields();
            for (JFieldVar jFieldVar : fields.values()) {
                if (!(isStatic(jFieldVar) && isFinal(jFieldVar))) {
                    superclassFields.add(new ClassField(classOutline, jFieldVar));
                }
            }
        }
        return superclassFields.toArray(new ClassField[0]);
    }

    private List<JDefinedClass> getSuperClasses(JClass clazz) {
        // first get all superclasses
        List<JDefinedClass> superclasses = new ArrayList<>();
        JClass superclass = clazz._extends();
        while (superclass != null) {
            if (superclass instanceof JDefinedClass) {
                superclasses.add((JDefinedClass) superclass);
            }
            superclass = superclass._extends();
        }
        return superclasses;
    }

    public boolean isStatic(JFieldVar var) {
        return (var.mods().getValue() & JMod.STATIC) != 0;
    }

    public boolean isFinal(JFieldVar var) {
        return (var.mods().getValue() & JMod.FINAL) != 0;
    }

    private JFieldVar[] getUnhandledSuperclassFields(ClassField[] superclassFieldsWithOwners) {
        JFieldVar[] superclassFields = Arrays.stream(superclassFieldsWithOwners).map(ClassField::getField).toArray(JFieldVar[]::new);
        if (!builderInheritance) {
            //we want to handle all inherited field
            return superclassFields;
        }

        // we only need fields whose classes don't have a builder themselves...
        // superclassFields are in class reverse order, i.e. root class first, direct superclass last, cf. #getSuperclassFields(ClassOutline)
        for (int i = superclassFields.length - 1; i >= 0; i--) {
            JDefinedClass type = superclassFieldsWithOwners[i].getClazz();
            if (type != null && getBuilderClass(type) != null) {
                // this class has its own builder, so we can stop here...
                if (i == superclassFields.length - 1) {
                    return new JFieldVar[0];
                }
                JFieldVar[] handledSuperclassFields = new JFieldVar[superclassFields.length - i - 1];
                System.arraycopy(superclassFields, i + 1, handledSuperclassFields, 0, handledSuperclassFields.length);
                return handledSuperclassFields;
            }
        }
        // no superclass with a builder, so we actually need them all...
        return superclassFields;
    }

    // init field is private :-( , we really need this
    private JExpression getInitJExpression(JFieldVar jFieldVar) {
        try {
            return (JExpression) FieldUtils.readField(jFieldVar, "init", true);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void log(final Level level, final String key, final Object... args) {
        final String message = "[" + MESSAGE_PREFIX + "] [" + level.getLocalizedName() + "] " + getMessage(key, args);

        int logLevel = Level.WARNING.intValue();
        if (this.options != null && !this.options.quiet) {
            if (this.options.verbose) {
                logLevel = Level.INFO.intValue();
            }
            if (this.options.debugMode) {
                logLevel = Level.ALL.intValue();
            }
        }

        if (level.intValue() >= logLevel) {
            if (level.intValue() <= Level.INFO.intValue()) {
                System.out.println(message);
            } else {
                System.err.println(message);
            }
        }
    }

    private static class ClassField {

        private final JDefinedClass clazz;
        private final JFieldVar field;

        public ClassField(JDefinedClass clazz, JFieldVar field) {
            this.clazz = clazz;
            this.field = field;
        }

        public JDefinedClass getClazz() {
            return clazz;
        }

        public JFieldVar getField() {
            return field;
        }
    }
}
