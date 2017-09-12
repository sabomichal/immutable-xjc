package com.github.sabomichal.immutablexjc;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;

import java.beans.Introspector;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * IMMUTABLE-XJC plugin implementation.
 *
 * @author <a href="mailto:sabo.michal@gmail.com">Michal Sabo</a>
 */
public final class PluginImpl extends Plugin {

    private static final String BUILDER_OPTION_NAME = "-imm-builder";
    private static final String CCONSTRUCTOR_OPTION_NAME = "-imm-cc";
    private static final String WITHIFNOTNULL_OPTION_NAME = "-imm-ifnotnull";
    private static final String NOPUBLICCONSTRUCTOR_OPTION_NAME = "-imm-nopubconstructor";

    private static final String UNSET_PREFIX = "unset";
    private static final String SET_PREFIX = "set";
    private static final String MESSAGE_PREFIX = "IMMUTABLE-XJC";
    private static final String OPTION_NAME = "immutable";
    private static final JType[] NO_ARGS = new JType[0];

    private ResourceBundle resourceBundle = ResourceBundle.getBundle(PluginImpl.class.getCanonicalName());
    private boolean createBuilder;
    private boolean createCConstructor;
    private boolean createWithIfNotNullMethod;
    private boolean createBuilderWithoutPublicConstructor;
    private Options options;

    @Override
    public boolean run(final Outline model, final Options options, final ErrorHandler errorHandler) {
        boolean success = true;
        this.options = options;

        this.log(Level.INFO, "title");

        for (ClassOutline clazz : model.getClasses()) {
            JDefinedClass implClass = clazz.implClass;

            FieldOutline[] declaredFields = clazz.getDeclaredFields();
            FieldOutline[] superclassFields = getSuperclassFields(clazz);

            int declaredFieldsLength = declaredFields != null ? declaredFields.length : 0;
            int superclassFieldsLength = superclassFields.length;
            if (declaredFieldsLength + superclassFieldsLength > 0) {
                if (addStandardConstructor(implClass, declaredFields, superclassFields) == null) {
                    log(Level.WARNING, "couldNotAddStdCtor", implClass.binaryName());
                }
            }
            if (declaredFieldsLength + superclassFieldsLength > 0) {
                if(createBuilderWithoutPublicConstructor){
                    if (addPropertyContructor(implClass, declaredFields, superclassFields,JMod.PROTECTED) == null) {
                        log(Level.WARNING, "couldNotAddPropertyCtor", implClass.binaryName());
                    }
                }else {
                    if (addPropertyContructor(implClass, declaredFields, superclassFields, JMod.PUBLIC) == null) {
                        log(Level.WARNING, "couldNotAddPropertyCtor", implClass.binaryName());
                    }
                }
            }
            //implClass.direct("// " + getMessage("title"));
            makeClassFinal(implClass);
            removeSetters(implClass);
            makePropertiesPrivate(implClass);
            makePropertiesFinal(implClass);

            if (createBuilder) {
                if (!clazz.implClass.isAbstract()) {
                    JDefinedClass builderClass;
                    if ((builderClass = addBuilderClass(clazz, declaredFields, superclassFields)) == null) {
                        log(Level.WARNING, "couldNotAddClassBuilder", implClass.binaryName());
                    }

                    if (createCConstructor && builderClass != null) {
                        if (addCopyConstructor(clazz.implClass, builderClass, declaredFields, superclassFields) == null) {
                            log(Level.WARNING, "couldNotAddCopyCtor", implClass.binaryName());
                        }
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
        return "  -" + OPTION_NAME + "  :  " + getMessage("usage") + n + "  " + BUILDER_OPTION_NAME + "       :  " + getMessage("builderUsage") + n + "  " + CCONSTRUCTOR_OPTION_NAME + "       :  " + getMessage("cConstructorUsage") + n + "  " + WITHIFNOTNULL_OPTION_NAME + "       :  " + getMessage("withIfNotNullUsage") + n + "  " + NOPUBLICCONSTRUCTOR_OPTION_NAME + "       :  " + getMessage("builderWithoutPublicConstructor") + n;
    }

    @Override
    public int parseArgument(final Options opt, final String[] args, final int i) throws BadCommandLineException, IOException {
        if (args[i].startsWith(BUILDER_OPTION_NAME)) {
            this.createBuilder = true;
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
        return 0;
    }

    private String getMessage(final String key, final Object... args) {
        return MessageFormat.format(resourceBundle.getString(key), args);
    }

    private JDefinedClass addBuilderClass(ClassOutline clazz, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        JDefinedClass builderClass = generateBuilderClass(clazz.implClass);
        if (builderClass == null) {
            return null;
        }
        for (FieldOutline field : declaredFields) {
            addProperty(builderClass, field);
            JMethod unconditionalWithMethod = addWithMethod(builderClass, field);
            if (createWithIfNotNullMethod) {
                addWithIfNotNullMethod(builderClass, field, unconditionalWithMethod);
            }
            if (field.getPropertyInfo().isCollection()) {
                addAddMethod(builderClass, field);
            }
        }
        for (FieldOutline field : superclassFields) {
            addProperty(builderClass, field);
            JMethod unconditionalWithMethod = addWithMethod(builderClass, field);
            if (createWithIfNotNullMethod) {
                addWithIfNotNullMethod(builderClass, field, unconditionalWithMethod);
            }
            if (field.getPropertyInfo().isCollection()) {
                addAddMethod(builderClass, field);
            }
        }
        addNewBuilder(clazz, builderClass);
        if (createCConstructor) {
            addNewBuilderCc(clazz, builderClass);
        }
        addBuildMethod(clazz.implClass, builderClass, declaredFields, superclassFields);
        return builderClass;
    }

    private JVar addProperty(JDefinedClass clazz, FieldOutline field) {
        JType jType = getJavaType(field);
        if (field.getPropertyInfo().isCollection()) {
            return clazz.field(JMod.PRIVATE, jType, field.getPropertyInfo().getName(false), getNewCollectionExpression(field.parent().implClass.owner(), jType));
        } else {
            return clazz.field(JMod.PRIVATE, jType, field.getPropertyInfo().getName(false));
        }
    }

    private JMethod addBuildMethod(JDefinedClass clazz, JDefinedClass builderClass, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        JMethod method = builderClass.method(JMod.PUBLIC, clazz, "build");
        JInvocation constructorInvocation = JExpr._new(clazz);
        for (FieldOutline field : superclassFields) {
            constructorInvocation.arg(JExpr.ref(field.getPropertyInfo().getName(false)));
        }
        for (FieldOutline field : declaredFields) {
            constructorInvocation.arg(JExpr.ref(field.getPropertyInfo().getName(false)));
        }
        method.body()._return(constructorInvocation);
        return method;
    }

    private void addNewBuilder(ClassOutline clazz, JDefinedClass builderClass) {
        boolean superClassWithSameName = false;
        ClassOutline superclass = clazz.getSuperClass();
        while (superclass != null) {
            if (superclass.implClass.name().equals(clazz.implClass.name())) {
                superClassWithSameName = true;
            }
            superclass = superclass.getSuperClass();
        }
        if (!superClassWithSameName) {
            JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, Introspector.decapitalize(clazz.implClass.name()) + "Builder");
            method.body()._return(JExpr._new(builderClass));
        }
    }

    private void addNewBuilderCc(ClassOutline clazz, JDefinedClass builderClass) {
        boolean superClassWithSameName = false;
        ClassOutline superclass = clazz.getSuperClass();
        while (superclass != null) {
            if (superclass.implClass.name().equals(clazz.implClass.name())) {
                superClassWithSameName = true;
            }
            superclass = superclass.getSuperClass();
        }
        if (!superClassWithSameName) {
            JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, Introspector.decapitalize(clazz.implClass.name()) + "Builder");
            JVar param = method.param(JMod.FINAL, clazz.implClass, "o");
            method.body()._return(JExpr._new(builderClass).arg(param));
        }
    }

    private Object addPropertyContructor(JDefinedClass clazz, FieldOutline[] declaredFields, FieldOutline[] superclassFields, int constAccess) {
        JMethod ctor = clazz.getConstructor(getFieldTypes(declaredFields, superclassFields));
        if (ctor == null) {
            ctor = this.generatePropertyConstructor(clazz, declaredFields, superclassFields, constAccess);
        } else {
            this.log(Level.WARNING, "standardCtorExists");
        }
        return ctor;
    }

    private JMethod addStandardConstructor(final JDefinedClass clazz, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        JMethod ctor = clazz.getConstructor(NO_ARGS);
        if (ctor == null) {
            ctor = this.generateStandardConstructor(clazz, declaredFields, superclassFields);
        } else {
            this.log(Level.WARNING, "standardCtorExists");
        }
        return ctor;
    }

    private JMethod addCopyConstructor(final JDefinedClass clazz, final JDefinedClass builderClass, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        JMethod ctor = generateCopyConstructor(clazz, builderClass, declaredFields, superclassFields);
        if (ctor != null) {
            createConstructor(builderClass, JMod.PUBLIC);
        }
        return ctor;
    }

    private JMethod addWithMethod(JDefinedClass builderClass, FieldOutline field) {
        String fieldName = field.getPropertyInfo().getName(true);
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName);
        generatePropertyAssignment(method, field);
        method.body()._return(JExpr.direct("this"));
        return method;
    }

    private JMethod addWithIfNotNullMethod(JDefinedClass builderClass, FieldOutline field, JMethod unconditionalWithMethod) {
        if (field.getRawType().isPrimitive())
          return null;
        String fieldName = field.getPropertyInfo().getName(true);
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName + "IfNotNull");
        JVar param = generateMethodParameter(method, field);
        JBlock block = method.body();
        JConditional conditional = block._if(param.eq(JExpr._null()));
        conditional._then()._return(JExpr.direct("this"));
        conditional._else()._return(JExpr.invoke(unconditionalWithMethod).arg(param));
        return method;
    }

    private JMethod addAddMethod(JDefinedClass builderClass, FieldOutline field) {
        List<JClass> typeParams = ((JClass) getJavaType(field)).getTypeParameters();
        if (!typeParams.iterator().hasNext()) {
            return null;
        }
        JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "add" + field.getPropertyInfo().getName(true));
        JBlock block = method.body();
        String fieldName = field.getPropertyInfo().getName(false);
        JVar param = method.param(JMod.FINAL, typeParams.iterator().next(), fieldName);
        JInvocation invocation = JExpr.refthis(fieldName).invoke("add").arg(param);
        block.add(invocation);
        block._return(JExpr.direct("this"));
        return method;
    }

    private JDefinedClass generateBuilderClass(JDefinedClass clazz) {
        JDefinedClass builderClass = null;
        String builderClassName = clazz.name() + "Builder";
        try {
            builderClass = clazz._class(JMod.PUBLIC | JMod.STATIC, builderClassName);
        } catch (JClassAlreadyExistsException e) {
            this.log(Level.WARNING, "builderClassExists", builderClassName);
        }
        return builderClass;
    }

    private void replaceCollectionGetter(FieldOutline field, final JMethod getter) {
        JDefinedClass clazz = field.parent().implClass;
        // remove the old getter
        clazz.methods().remove(getter);
        // and create a new one
        JMethod newGetter = field.parent().implClass.method(getter.mods().getValue(), getter.type(), getter.name());
        JBlock block = newGetter.body();

        JVar ret = block.decl(getJavaType(field), "ret");
        JCodeModel codeModel = field.parent().implClass.owner();
        JVar param = generateMethodParameter(getter, field);
        JConditional conditional = block._if(param.eq(JExpr._null()));
        conditional._then().assign(ret, getEmptyCollectionExpression(codeModel, param));
        conditional._else().assign(ret, getUnmodifiableWrappedExpression(codeModel, param));
        block._return(ret);

        getter.javadoc().append("Returns unmodifiable collection.");
    }

    private void generatePropertyAssignment(final JMethod method, FieldOutline fieldOutline) {
        generatePropertyAssignment(method, fieldOutline, false);
    }

    private void generatePropertyAssignment(final JMethod method, FieldOutline fieldOutline, boolean wrapUnmodifiable) {
        JBlock block = method.body();
        JCodeModel codeModel = fieldOutline.parent().implClass.owner();
        String fieldName = fieldOutline.getPropertyInfo().getName(false);
        JVar param = generateMethodParameter(method, fieldOutline);
        if (fieldOutline.getPropertyInfo().isCollection()) {
            if (wrapUnmodifiable) {
                JConditional conditional = block._if(param.eq(JExpr._null()));
                conditional._then().assign(JExpr.refthis(fieldName), JExpr._null());
                conditional._else().assign(JExpr.refthis(fieldName), getDefensiveCopyExpression(codeModel, getJavaType(fieldOutline), param));
            } else {
                block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
            }

            replaceCollectionGetter(fieldOutline, getGetterProperty(fieldOutline));
        } else {
            block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
        }
    }

    private JVar generateMethodParameter(final JMethod method, FieldOutline fieldOutline) {
        String fieldName = fieldOutline.getPropertyInfo().getName(false);
        JType javaType = getJavaType(fieldOutline);
        return method.param(JMod.FINAL, javaType, fieldName);
    }

    private JExpression getDefensiveCopyExpression(JCodeModel codeModel, JType jType, JVar param) {
        List<JClass> typeParams = ((JClass) jType).getTypeParameters();
        JClass typeParameter = null;
        if (typeParams.iterator().hasNext()) {
            typeParameter = typeParams.iterator().next();
        }

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
        if (newClass != null && typeParameter != null) {
            newClass = newClass.narrow(typeParameter);
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
        JClass typeParameter = null;
        if (typeParams.iterator().hasNext()) {
            typeParameter = typeParams.iterator().next();
        }

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
        if (newClass != null && typeParameter != null) {
            newClass = newClass.narrow(typeParameter);
        }

        return newClass == null ? JExpr._null() : JExpr._new(newClass);
    }

    private void generateDefaultPropertyAssignment(JMethod method, FieldOutline fieldOutline) {
        JBlock block = method.body();
        String propertyName = fieldOutline.getPropertyInfo().getName(false);
        block.assign(JExpr.refthis(propertyName), defaultValue(getJavaType(fieldOutline), fieldOutline));
    }

    private JExpression defaultValue(JType javaType, FieldOutline fieldOutline) {
        if (javaType.isPrimitive()) {
            if (fieldOutline.parent().parent().getCodeModel().BOOLEAN.equals(javaType)) {
                return JExpr.lit(false);
            } else if (fieldOutline.parent().parent().getCodeModel().SHORT.equals(javaType)) {
                return JExpr.cast(fieldOutline.parent().parent().getCodeModel().SHORT, JExpr.lit(0));
            } else {
                return JExpr.lit(0);
            }
        }
        return JExpr._null();
    }

    private JMethod generatePropertyConstructor(JDefinedClass clazz, FieldOutline[] declaredFields, FieldOutline[] superclassFields, int constAccess) {
        final JMethod ctor = createConstructor(clazz, constAccess);
        if (superclassFields.length > 0) {
            JInvocation superInvocation = ctor.body().invoke("super");
            for (FieldOutline fieldOutline : superclassFields) {
                superInvocation.arg(JExpr.ref(fieldOutline.getPropertyInfo().getName(false)));
                generateMethodParameter(ctor, fieldOutline);
            }
        }

        for (FieldOutline fieldOutline : declaredFields) {
            generatePropertyAssignment(ctor, fieldOutline, true);
        }
        return ctor;
    }

    private JMethod generateStandardConstructor(final JDefinedClass clazz, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        final JMethod ctor = createConstructor(clazz, JMod.PROTECTED);
        ctor.javadoc().add("Used by JAX-B");
        if (superclassFields.length > 0) {
            JInvocation superInvocation = ctor.body().invoke("super");
            for (FieldOutline fieldOutline : superclassFields) {
                superInvocation.arg(defaultValue(getJavaType(fieldOutline), fieldOutline));
            }
        }
        for (FieldOutline fieldOutline : declaredFields) {
            generateDefaultPropertyAssignment(ctor, fieldOutline);
        }
        return ctor;
    }

    private JMethod generateCopyConstructor(final JDefinedClass clazz, final JDefinedClass builderClass, FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        final JMethod ctor = createConstructor(builderClass, JMod.PUBLIC);
        final JVar o = ctor.param(JMod.FINAL, clazz, "o");
        ctor.body()._if(o.eq(JExpr._null()))._then()._throw(
                JExpr._new(builderClass.owner().ref(NullPointerException.class)).
                        arg("Cannot create a copy of '" + builderClass.name() + "' from 'null'."));

        JCodeModel codeModel = clazz.owner();

        for (FieldOutline field : superclassFields) {
            String propertyName = field.getPropertyInfo().getName(false);
            JMethod getter = getPropertyGetter(field);

            if (field.getPropertyInfo().isCollection()) {
                JVar tmpVar = ctor.body().decl(0, getJavaType(field), "_" + propertyName, JExpr.invoke(o, getter));
                JConditional conditional = ctor.body()._if(tmpVar.eq(JExpr._null()));
                conditional._then().assign(JExpr.refthis(propertyName), getNewCollectionExpression(codeModel, getJavaType(field)));
                conditional._else().assign(JExpr.refthis(propertyName),  getDefensiveCopyExpression(codeModel, getJavaType(field), tmpVar));
            } else {
                ctor.body().assign(JExpr.refthis(propertyName), JExpr.invoke(o, getter));
            }
        }
        for (FieldOutline field : declaredFields) {
            String propertyName = field.getPropertyInfo().getName(false);

            if (field.getPropertyInfo().isCollection()) {
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

    private JMethod getPropertyGetter(final FieldOutline f) {
        final JDefinedClass clazz = f.parent().implClass;
        final String name = f.getPropertyInfo().getName(true);
        JMethod getter = clazz.getMethod("get" + name, NO_ARGS);

        if (getter == null) {
            getter = clazz.getMethod("is" + name, NO_ARGS);
        }

        return getter;
    }

    private JMethod createConstructor(final JDefinedClass clazz, final int visibility) {
        return clazz.constructor(visibility);
    }

    private JType getJavaType(FieldOutline field) {
        return field.getRawType();
    }

    private JType[] getFieldTypes(FieldOutline[] declaredFields, FieldOutline[] superclassFields) {
        JType[] fieldTypes = new JType[declaredFields.length + superclassFields.length];
        int i = 0;
        for (FieldOutline fieldOutline : superclassFields) {
            fieldTypes[i++] = fieldOutline.getPropertyInfo().baseType;
        }
        for (FieldOutline fieldOutline : declaredFields) {
            fieldTypes[i++] = fieldOutline.getPropertyInfo().baseType;
        }
        return fieldTypes;
    }

    private JMethod getGetterProperty(final FieldOutline fieldOutline) {
        final JDefinedClass clazz = fieldOutline.parent().implClass;
        final String name = fieldOutline.getPropertyInfo().getName(true);
        JMethod getter = clazz.getMethod("get" + name, NO_ARGS);

        if (getter == null) {
            getter = clazz.getMethod("is" + name, NO_ARGS);
        }

        return getter;
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

    private void makeClassFinal(JDefinedClass clazz) {
        clazz.mods().setFinal(true);
    }

    private void makePropertiesPrivate(JDefinedClass clazz) {
        for (JFieldVar field : clazz.fields().values()) {
            field.mods().setPrivate();
        }
    }

    private void makePropertiesFinal(JDefinedClass clazz) {
        for (JFieldVar field : clazz.fields().values()) {
            field.mods().setFinal(true);
        }
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

    private FieldOutline[] getSuperclassFields(ClassOutline clazz) {
        // first get all superclasses
        List<ClassOutline> superclasses = new ArrayList<ClassOutline>();
        ClassOutline superclass = clazz.getSuperClass();
        while (superclass != null) {
            superclasses.add(superclass);
            superclass = superclass.getSuperClass();
        }

        // get all fields in class reverse order
        List<FieldOutline> superclassFields = new ArrayList<FieldOutline>();
        Collections.reverse(superclasses);
        for (ClassOutline classOutline : superclasses) {
            superclassFields.addAll(Arrays.asList(classOutline.getDeclaredFields()));
        }
        return superclassFields.toArray(new FieldOutline[superclassFields.size()]);
    }
}