package com.github.sabomichal.immutablexjc;

import java.beans.Introspector;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
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

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.ErrorHandler;

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
import com.sun.tools.xjc.outline.Outline;

/**
 * IMMUTABLE-XJC plugin implementation.
 *
 * @author <a href="mailto:sabo.michal@gmail.com">Michal Sabo</a>
 */
@SuppressWarnings("static-method")
public final class PluginImpl extends Plugin {

	private static final String BUILDER_OPTION_NAME = "-imm-builder";
	private static final String CCONSTRUCTOR_OPTION_NAME = "-imm-cc";
	private static final String WITHIFNOTNULL_OPTION_NAME = "-imm-ifnotnull";
	private static final String NOPUBLICCONSTRUCTOR_OPTION_NAME = "-imm-nopubconstructor";
	private static final String PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME = "-imm-pubconstructormaxargs";
	private static final String SKIPCOLLECTIONS_OPTION_NAME = "-imm-skipcollections";

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
	private int publicConstructorMaxArgs = Integer.MAX_VALUE;
	private boolean leaveCollectionsMutable;
	private Options options;

	@Override
	public boolean run(final Outline model, final Options options, final ErrorHandler errorHandler) {
		boolean success = true;
		this.options = options;

		this.log(Level.INFO, "title");

		for (ClassOutline clazz : model.getClasses()) {
			JDefinedClass implClass = clazz.implClass;

			JFieldVar[] declaredFields = getDeclaredFields(implClass);
			JFieldVar[] superclassFields = getSuperclassFields(implClass);

			int declaredFieldsLength = declaredFields != null ? declaredFields.length : 0;
			int superclassFieldsLength = superclassFields.length;
			if (declaredFieldsLength + superclassFieldsLength > 0) {
				if (addStandardConstructor(implClass, declaredFields, superclassFields) == null) {
					log(Level.WARNING, "couldNotAddStdCtor", implClass.binaryName());
				}
			}
			if (declaredFieldsLength + superclassFieldsLength > 0) {
				if (createBuilderWithoutPublicConstructor
						|| (createBuilder && declaredFieldsLength + superclassFieldsLength > publicConstructorMaxArgs)) {
					if (addPropertyContructor(implClass, declaredFields, superclassFields, JMod.NONE) == null) {
						log(Level.WARNING, "couldNotAddPropertyCtor", implClass.binaryName());
					}
				} else {
					if (addPropertyContructor(implClass, declaredFields, superclassFields, JMod.PUBLIC) == null) {
						log(Level.WARNING, "couldNotAddPropertyCtor", implClass.binaryName());
					}
				}
			}
			// implClass.direct("// " + getMessage("title"));
			makeClassFinal(implClass);
			removeSetters(implClass);
			makePropertiesPrivate(implClass);
			makePropertiesFinal(implClass, declaredFields);

			if (createBuilder) {
				if (!clazz.implClass.isAbstract()) {
					JDefinedClass builderClass;
					if ((builderClass = addBuilderClass(clazz, declaredFields, superclassFields)) == null) {
						log(Level.WARNING, "couldNotAddClassBuilder", implClass.binaryName());
					}

					if (createCConstructor && builderClass != null) {
						if (addCopyConstructor(clazz.implClass, builderClass, declaredFields,
								superclassFields) == null) {
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
		StringBuilder retval = new StringBuilder("  -");
		retval.append(OPTION_NAME);
		retval.append("  :  ");
		retval.append(getMessage("usage"));
		retval.append(n);
		retval.append("  ");
		retval.append(BUILDER_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("builderUsage"));
		retval.append(n);
		retval.append("  ");
		retval.append(CCONSTRUCTOR_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("cConstructorUsage"));
		retval.append(n);
		retval.append("  ");
		retval.append(WITHIFNOTNULL_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("withIfNotNullUsage"));
		retval.append(n);
		retval.append("  ");
		retval.append(NOPUBLICCONSTRUCTOR_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("builderWithoutPublicConstructor"));
		retval.append(n);
		retval.append("  ");
		retval.append(SKIPCOLLECTIONS_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("leaveCollectionsMutable"));
		retval.append(n);
		retval.append("  ");
		retval.append(PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME);
		retval.append("       :  ");
		retval.append(getMessage("publicConstructorMaxArgs"));
		retval.append(n);
		return retval.toString();
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
		if (args[i].startsWith(SKIPCOLLECTIONS_OPTION_NAME)) {
			this.leaveCollectionsMutable = true;
			return 1;
		}
		if (args[i].startsWith(PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME)) {
			this.publicConstructorMaxArgs  = Integer.parseInt(args[i].substring(PUBLICCONSTRUCTOR_MAXARGS_OPTION_NAME.length()+1));
			return 1;
		}
		return 0;
	}

	private String getMessage(final String key, final Object... args) {
		return MessageFormat.format(resourceBundle.getString(key), args);
	}

	private JDefinedClass addBuilderClass(ClassOutline clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
		JDefinedClass builderClass = generateBuilderClass(clazz.implClass);
		if (builderClass == null) {
			return null;
		}
		for (JFieldVar field : declaredFields) {
			addProperty(builderClass, field);
			JMethod unconditionalWithMethod = addWithMethod(builderClass, field);
			if (createWithIfNotNullMethod) {
				addWithIfNotNullMethod(builderClass, field, unconditionalWithMethod);
			}
			if (isCollection(field)) {
				addAddMethod(builderClass, field);
			}
		}
		for (JFieldVar field : superclassFields) {
			addProperty(builderClass, field);
			JMethod unconditionalWithMethod = addWithMethod(builderClass, field);
			if (createWithIfNotNullMethod) {
				addWithIfNotNullMethod(builderClass, field, unconditionalWithMethod);
			}
			if (isCollection(field)) {
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

	private JVar addProperty(JDefinedClass clazz, JFieldVar field) {
		JType jType = getJavaType(field);
		if (isCollection(field)) {
			return clazz.field(JMod.PRIVATE, jType, field.name(),
					getNewCollectionExpression(field.type().owner(), jType));
		} 
		
		Map<String, JFieldVar> fields = clazz.fields();
		JFieldVar jFieldVar = fields.get(field.name());
		if (jFieldVar == null){
			return clazz.field(JMod.PRIVATE, jType, field.name());
		}
		return jFieldVar;
	}

	private JMethod addBuildMethod(JDefinedClass clazz, JDefinedClass builderClass, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
		JMethod method = builderClass.method(JMod.PUBLIC, clazz, "build");
		JInvocation constructorInvocation = JExpr._new(clazz);
		for (JFieldVar field : superclassFields) {
			constructorInvocation.arg(JExpr.ref(field.name()));
		}
		for (JFieldVar field : declaredFields) {
			constructorInvocation.arg(JExpr.ref(field.name()));
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
			JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass,
					Introspector.decapitalize(clazz.implClass.name()) + "Builder");
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
			JMethod method = clazz.implClass.method(JMod.PUBLIC | JMod.STATIC, builderClass,
					Introspector.decapitalize(clazz.implClass.name()) + "Builder");
			JVar param = method.param(JMod.FINAL, clazz.implClass, "o1");
			method.body()._return(JExpr._new(builderClass).arg(param));
		}
	}

	private Object addPropertyContructor(JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields, int constAccess) {
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
		if (ctor != null) {
			createConstructor(builderClass, JMod.PUBLIC);
		}
		return ctor;
	}

	private JMethod addWithMethod(JDefinedClass builderClass, JFieldVar field) {
		String fieldName = StringUtils.capitalize(field.name());
		JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName);
		generatePropertyAssignment(method, field);
		method.body()._return(JExpr.direct("this"));
		return method;
	}

	private JMethod addWithIfNotNullMethod(JDefinedClass builderClass, JFieldVar field, JMethod unconditionalWithMethod) {
		if (field.type().isPrimitive())
			return null;
		String fieldName = StringUtils.capitalize(field.name());
		JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "with" + fieldName + "IfNotNull");
		JVar param = generateMethodParameter(method, field);
		JBlock block = method.body();
		JConditional conditional = block._if(param.eq(JExpr._null()));
		conditional._then()._return(JExpr.direct("this"));
		conditional._else()._return(JExpr.invoke(unconditionalWithMethod).arg(param));
		return method;
	}

	private JMethod addAddMethod(JDefinedClass builderClass, JFieldVar field) {
		List<JClass> typeParams = ((JClass) getJavaType(field)).getTypeParameters();
		if (!typeParams.iterator().hasNext()) {
			return null;
		}
		JMethod method = builderClass.method(JMod.PUBLIC, builderClass, "add" + StringUtils.capitalize(field.name()));
		JBlock block = method.body();
		String fieldName = field.name();
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

	private void replaceCollectionGetter(JFieldVar field, final JMethod getter) {
		JDefinedClass clazz = (JDefinedClass) field.type();
		// remove the old getter
		clazz.methods().remove(getter);
		// and create a new one
		JMethod newGetter = clazz.method(getter.mods().getValue(), getter.type(), getter.name());
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

	private void generatePropertyAssignment(final JMethod method, JFieldVar fieldOutline) {
		generatePropertyAssignment(method, fieldOutline, false);
	}

	private void generatePropertyAssignment(final JMethod method, JFieldVar fieldOutline, boolean wrapUnmodifiable) {
		JBlock block = method.body();
		JCodeModel codeModel = fieldOutline.type().owner();
		String fieldName = fieldOutline.name();
		JVar param = generateMethodParameter(method, fieldOutline);
		if (isCollection(fieldOutline) && !leaveCollectionsMutable) {
			if (wrapUnmodifiable) {
				JConditional conditional = block._if(param.eq(JExpr._null()));
				conditional._then().assign(JExpr.refthis(fieldName), JExpr._null());
				conditional._else().assign(JExpr.refthis(fieldName),
						getDefensiveCopyExpression(codeModel, getJavaType(fieldOutline), param));
			} else {
				block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
			}
			replaceCollectionGetter(fieldOutline, getGetterProperty((JDefinedClass) fieldOutline.type(),fieldOutline.name()));
		} else {
			block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
		}
	}

	private JVar generateMethodParameter(final JMethod method, JFieldVar fieldOutline) {
		String fieldName = fieldOutline.name();
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

	private void generateDefaultPropertyAssignment(JMethod method, JFieldVar fieldOutline) {
		JBlock block = method.body();
		String propertyName = fieldOutline.name();
		block.assign(JExpr.refthis(propertyName), defaultValue(fieldOutline));
	}

	private JExpression defaultValue(JFieldVar fieldOutline) {
		JType javaType = fieldOutline.type();
		if (javaType.isPrimitive()) {
			if (fieldOutline.type().owner().BOOLEAN.equals(javaType)) {
				return JExpr.lit(false);
			} else if (javaType.owner().SHORT.equals(javaType)) {
				return JExpr.cast(javaType.owner().SHORT, JExpr.lit(0));
			} else {
				return JExpr.lit(0);
			}
		}
		return JExpr._null();
	}

	private JMethod generatePropertyConstructor(JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields, int constAccess) {
		final JMethod ctor = createConstructor(clazz, constAccess);
		if (superclassFields.length > 0) {
			JInvocation superInvocation = ctor.body().invoke("super");
			for (JFieldVar fieldOutline : superclassFields) {
				superInvocation.arg(JExpr.ref(fieldOutline.name()));
				generateMethodParameter(ctor, fieldOutline);
			}
		}

		for (JFieldVar fieldOutline : declaredFields) {
			generatePropertyAssignment(ctor, fieldOutline, true);
		}
		return ctor;
	}

	private JMethod generateStandardConstructor(final JDefinedClass clazz, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
		final JMethod ctor = createConstructor(clazz, JMod.PROTECTED);
		ctor.javadoc().add("Used by JAX-B");
		if (superclassFields.length > 0) {
			JInvocation superInvocation = ctor.body().invoke("super");
			for (JFieldVar fieldOutline : superclassFields) {
				superInvocation.arg(defaultValue(fieldOutline));
			}
		}
		for (JFieldVar fieldOutline : declaredFields) {
			generateDefaultPropertyAssignment(ctor, fieldOutline);
		}
		return ctor;
	}

	private JMethod generateCopyConstructor(final JDefinedClass clazz, final JDefinedClass builderClass, JFieldVar[] declaredFields, JFieldVar[] superclassFields) {
		final JMethod ctor = createConstructor(builderClass, JMod.PUBLIC);
		final JVar o = ctor.param(JMod.FINAL, clazz, "o");
		ctor.body()._if(o.eq(JExpr._null()))._then()
				._throw(JExpr._new(builderClass.owner().ref(NullPointerException.class))
						.arg("Cannot create a copy of '" + builderClass.name() + "' from 'null'."));

		JCodeModel codeModel = clazz.owner();

		for (JFieldVar field : superclassFields) {
			String propertyName = field.name();
			JType type = field.type();
			if (type instanceof JDefinedClass) {
				JMethod getter = getGetterProperty(clazz ,field.name());
				if (isCollection(field)) {
					JVar tmpVar = ctor.body().decl(0, getJavaType(field), "_" + propertyName, JExpr.invoke(o, getter));
					JConditional conditional = ctor.body()._if(tmpVar.eq(JExpr._null()));
					conditional._then().assign(JExpr.refthis(propertyName),	getNewCollectionExpression(codeModel, getJavaType(field)));
					conditional._else().assign(JExpr.refthis(propertyName),	getDefensiveCopyExpression(codeModel, getJavaType(field), tmpVar));
				} else {
					ctor.body().assign(JExpr.refthis(propertyName), JExpr.invoke(o, getter));
				}
			}
		}
		for (JFieldVar field : declaredFields) {
			String propertyName = field.name();

			if (isCollection(field)) {
				JVar tmpVar = ctor.body().decl(0, getJavaType(field), "_" + propertyName, JExpr.ref(o, propertyName));
				JConditional conditional = ctor.body()._if(tmpVar.eq(JExpr._null()));
				conditional._then().assign(JExpr.refthis(propertyName),	getNewCollectionExpression(codeModel, getJavaType(field)));
				conditional._else().assign(JExpr.refthis(propertyName),	getDefensiveCopyExpression(codeModel, getJavaType(field), tmpVar));
			} else {
				ctor.body().assign(JExpr.refthis(propertyName), JExpr.ref(o, propertyName));
			}
		}
		return ctor;
	}

	private JMethod getGetterProperty(final JDefinedClass clazz, final String name) {
		JMethod getter = clazz.getMethod("get" + StringUtils.capitalize(name), NO_ARGS);
		if (getter == null) {
			getter = clazz.getMethod("is" + StringUtils.capitalize(name), NO_ARGS);
		}
		
		if (getter == null) {
			List<JDefinedClass> superClasses = getSuperClasses(clazz);
			for (JDefinedClass definedClass : superClasses) {
				getter = getGetterProperty(definedClass, name);
				
				if (getter != null) {
					break;
				}
			}
		}
		return getter;
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
		for (JFieldVar fieldOutline : superclassFields) {
			fieldTypes[i++] = fieldOutline.type();
		}
		for (JFieldVar fieldOutline : declaredFields) {
			fieldTypes[i++] = fieldOutline.type();
		}
		return fieldTypes;
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

	private void makePropertiesFinal(JDefinedClass clazz, JFieldVar[] declaredFields) {
		for (JFieldVar fieldOutline : declaredFields) {
			String fieldName = fieldOutline.name();
			clazz.fields().get(fieldName).mods().setFinal(!(leaveCollectionsMutable && isCollection(fieldOutline)));
		}
	}

	private boolean isCollection(JFieldVar fieldOutline) {
		if (fieldOutline.type() instanceof JClass) {
			return isCollection((JClass)fieldOutline.type());
		}
		return false;
	}

	private boolean isCollection(JClass fieldOutline) {
		return fieldOutline.owner().ref(Collection.class).isAssignableFrom(fieldOutline) ||
				fieldOutline.owner().ref(Map.class).isAssignableFrom(fieldOutline);
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
	
	private JFieldVar[] getSuperclassFields(JDefinedClass clazz) {
		List<JDefinedClass> superclasses = getSuperClasses(clazz);

		// get all fields in class reverse order
		List<JFieldVar> superclassFields = new ArrayList<>();
		Collections.reverse(superclasses);
		for (JDefinedClass classOutline : superclasses) {
			Map<String, JFieldVar> fields = classOutline.fields();
			for (JFieldVar jFieldVar : fields.values()) {
				if (!(isStatic(jFieldVar) && isFinal(jFieldVar))) {
					superclassFields.add(jFieldVar);
				}
			}
		}
		return superclassFields.stream().toArray(JFieldVar[]::new);
	}

	private List<JDefinedClass> getSuperClasses(JDefinedClass clazz) {
		// first get all superclasses
		List<JDefinedClass> superclasses = new ArrayList<>();
		JClass superclass = clazz._extends();
		while (superclass != null) {
			if (superclass instanceof JDefinedClass) {
				superclasses.add((JDefinedClass)superclass);
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
}
