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
import com.sun.tools.xjc.model.CTypeInfo;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.logging.Level;

/**
 * IMMUTABLE-XJC plugin implementation.
 * 
 * @author <a href="mailto:sabo.michal@gmail.com">Michal Sabo</a>
 *
 */
public final class PluginImpl extends Plugin {

	private static final String UNSET_PREFIX = "unset";
	private static final String SET_PREFIX = "set";
	private static final String MESSAGE_PREFIX = "IMMUTABLE-XJC";
	private static final String OPTION_NAME = "immutable";
	private static final JType[] NO_ARGS = new JType[0];
	//TODO: improve builder of collection classes, so not the whole collection, but single elements can be added incrementally. E.g. withLimit(List<Limit>) change to withLimit(Limit).withLimit(Limit)...
	private static final String BUILDER_OPTION_NAME = "-imm-builder";;

	private boolean success;
	private Options options;
	private boolean createBuilder;
	
	@Override
	public boolean run(final Outline model, final Options options, final ErrorHandler errorHandler) {
		this.success = true;
		this.options = options;

		this.log(Level.INFO, "title");

		for (ClassOutline clazz : model.getClasses()) {
			FieldOutline[] declaredFields = clazz.getDeclaredFields();
			JDefinedClass implClass = clazz.implClass;
			if (this.addStandardConstructor(implClass, declaredFields) == null) {
				this.log(Level.WARNING, "couldNotAddStdCtor", implClass.binaryName());
			}

			if (this.addPropertyContructor(implClass, declaredFields) == null) {
				this.log(Level.WARNING, "couldNotAddStdCtor", implClass.binaryName());
			}

			this.removeSetters(implClass);
			this.makeClassFinal(implClass);
			this.makePropertiesPrivate(implClass);
			this.makePropertiesFinal(implClass);
			
			if (createBuilder) {
				if (this.addBuilderClass(clazz) == null) {
					this.log(Level.WARNING, "couldNotAddClassBuilder", implClass.binaryName());
				}
			}
		}

		this.options = null;
		return this.success;
	}
	
	@Override
	public String getOptionName() {
		return OPTION_NAME;
	}
	
	@Override
	public String getUsage() {
		final String n = System.getProperty("line.separator", "\n");
		return new StringBuilder().append("  -").append(OPTION_NAME).append("  :  ").append(getMessage("usage")).append(n).
				append( "  " ).append( BUILDER_OPTION_NAME ).append( "       :  " ).
		        append( getMessage( "builderUsage" ) ).append( n ).toString();
	}
	
	@Override
	public int parseArgument(final Options opt, final String[] args, final int i) throws BadCommandLineException, IOException {
		if ( args[i].startsWith( BUILDER_OPTION_NAME ) ) {
            this.createBuilder = true;
            return 1;
        }
		return 0;
	}
	
	private static String getMessage(final String key, final Object... args) {
		return MessageFormat.format(ResourceBundle.getBundle("com/github/sabomichal/immutablexjc/PluginImpl").getString(key), args);
	}

	private JDefinedClass addBuilderClass(ClassOutline clazz) {
		JDefinedClass builderClass = generateBuilderClass(clazz.implClass);
		if (builderClass == null)  {
			return builderClass;
		}
		for (FieldOutline field : clazz.getDeclaredFields()) {
			addProperty(builderClass, field);
			addWithMethod(builderClass, field);
		}
		addNewBuilder(clazz.implClass, builderClass);
		addBuildMethod(clazz.implClass, builderClass, clazz.getDeclaredFields());
		return builderClass;
	}

	private void addProperty(JDefinedClass clazz, FieldOutline field) {
		clazz.field(JMod.PRIVATE, getJavaType(field), field.getPropertyInfo().getName(false));
	}

	private JMethod addBuildMethod(JDefinedClass clazz, JDefinedClass builderClass, FieldOutline[] declaredFields) {
		JMethod method = builderClass.method(JMod.PUBLIC, clazz, "build");
		JInvocation constructorInvocation = JExpr._new(clazz);
		for (FieldOutline field : declaredFields) {
			constructorInvocation.arg(JExpr.ref(field.getPropertyInfo().getName(false)));
		}
		method.body()._return(constructorInvocation);
		return method;
	}

	private void addNewBuilder(JDefinedClass clazz, JDefinedClass builderClass) {
		JMethod method = clazz.method(JMod.PUBLIC|JMod.STATIC, builderClass, "newBuilder");
		method.body()._return(JExpr._new(builderClass));
	}

	private Object addPropertyContructor(JDefinedClass clazz, FieldOutline[] declaredFields) {
		JMethod ctor = clazz.getConstructor(getFieldTypes(declaredFields));
		if (ctor == null) {
			ctor = this.generatePropertyConstructor(clazz, declaredFields);
		} else {
			this.log(Level.WARNING, "standardCtorExists");
		}
		return ctor;
	}

	private JMethod addStandardConstructor(final JDefinedClass clazz, FieldOutline[] declaredFields) {
		JMethod ctor = clazz.getConstructor(NO_ARGS);
		if (ctor == null) {
			ctor = this.generateStandardConstructor(clazz, declaredFields);
		} else {
			this.log(Level.WARNING, "standardCtorExists");
		}
		return ctor;
	}

	private JMethod addWithMethod(JDefinedClass builderClass, FieldOutline field) {
		String fieldName = field.getPropertyInfo().getName(false);
		JMethod method = builderClass.method(JMod.PUBLIC, builderClass, new StringBuilder("with").append(Character.toUpperCase(fieldName.charAt(0))).append(fieldName.substring(1)).toString());
		generatePropertyAssignment(method, field);
		method.body()._return(JExpr.direct("this"));
		return method;
	}

	private JDefinedClass generateBuilderClass(JDefinedClass clazz) {
		JDefinedClass builderClass = null;
		String builderClassName = new StringBuilder(clazz.name()).append("Builder").toString();
		try {
			builderClass = clazz._class(JMod.PUBLIC|JMod.STATIC, builderClassName);
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
		JFieldVar fieldVar = clazz.fields().get(field.getPropertyInfo().getName(false));
		JBlock block = newGetter.body();
		block._return(fieldVar);
		getter.javadoc().append("Returns unmodifiable collection.");
	}

	private void generatePropertyAssignment(final JMethod method, FieldOutline fieldOutline) {
		generatePropertyAssignment(method, fieldOutline, false);
	}

	private void generatePropertyAssignment(final JMethod method, FieldOutline fieldOutline, boolean wrapUnmodifiable) {
		JBlock block = method.body();
		JCodeModel codeModel = fieldOutline.parent().implClass.owner();
		String fieldName = fieldOutline.getPropertyInfo().getName(false);
		JType javaType = getJavaType(fieldOutline);
		JVar param = method.param(JMod.FINAL, javaType, fieldName);
		if (fieldOutline.getPropertyInfo().isCollection() && wrapUnmodifiable) {
			JConditional conditional = block._if(param.eq(JExpr._null()));
			conditional._then().assign(JExpr.refthis(fieldName), getEmptyCollectionExpression(codeModel, param));
			conditional._else().assign(JExpr.refthis(fieldName), getUnmodifiableWrappedExpression(codeModel, param));
			replaceCollectionGetter(fieldOutline, getGetterProperty(fieldOutline));
		} else {
			block.assign(JExpr.refthis(fieldName), JExpr.ref(fieldName));
		}
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
			return JExpr._new(codeModel.ref(SortedMap.class));
		} else if (param.type().erasure().equals(codeModel.ref(SortedSet.class))) {
			return JExpr._new(codeModel.ref(SortedSet.class));
		}
		return param;
	}

	private void generateOuterPropertyAssignment(final JMethod method, FieldOutline field, JClass outerClass) {
		JBlock block = method.body();
		String propertyName = field.getPropertyInfo().getName(false);
		JType javaType = getJavaType(field);
		method.param(javaType, propertyName);
		block.assign(outerClass.staticRef("this").ref(propertyName), JExpr.ref(propertyName));
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
			} else {
				return JExpr.lit(0);
			}
		}
		return JExpr._null();
	}

	private JMethod generatePropertyConstructor(JDefinedClass clazz, FieldOutline[] declaredFields) {
		final JMethod ctor = createConstructor(clazz, JMod.PUBLIC);
		ctor.body().invoke("super");
		for (FieldOutline fieldOutline : declaredFields) {
			generatePropertyAssignment(ctor, fieldOutline, true);
		}
		return ctor;
	}

	private JMethod generateStandardConstructor(final JDefinedClass clazz, FieldOutline[] declaredFields) {
		final JMethod ctor = createConstructor(clazz, JMod.PROTECTED);;
		ctor.javadoc().add("Used by JAX-B");
		for (FieldOutline fieldOutline : declaredFields) {
			generateDefaultPropertyAssignment(ctor, fieldOutline);
		}
		return ctor;
	}

	private JMethod createConstructor(final JDefinedClass clazz, final int visibility) {
		final JMethod ctor = clazz.constructor(visibility);
		ctor.body().directStatement("// " + getMessage("title"));
		return ctor;
	}

	private JType getJavaType(FieldOutline field) {
		JType javaType;
		if (field.getPropertyInfo().isCollection()) {
			javaType = field.getRawType();
		} else {
			CTypeInfo typeInfo = field.getPropertyInfo().ref().iterator().next();
			javaType = typeInfo.toType(field.parent().parent(), Aspect.IMPLEMENTATION);
		}
		return javaType;
	}
	
	private JType[] getFieldTypes(FieldOutline[] declaredFields) {
		JType[] fieldTypes = new JType[declaredFields.length];
		int i = 0;
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
		final StringBuilder b = new StringBuilder(512).append("[").append(MESSAGE_PREFIX).append("] [").append(level.getLocalizedName()).append("] ").append(getMessage(key, args));

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
				System.out.println(b.toString());
			} else {
				System.err.println(b.toString());
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
}