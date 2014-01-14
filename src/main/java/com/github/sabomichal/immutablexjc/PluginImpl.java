package com.github.sabomichal.immutablexjc;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.logging.Level;

import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CTypeInfo;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

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

	//TODO: improve builder of collection classes, so not the whole collection, but single elements can be added incrementaly. E.g. withLimit(List<Limit>) change to withLimit(Limit).withLimit(Limit)...
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

			if (this.addStandardConstructor(clazz) == null) {
				this.log(Level.WARNING, "couldNotAddStdCtor", clazz.implClass.binaryName());
			}

			JType[] propertyTypes = getProperties(clazz);
			if (this.addPropertyContructor(clazz, propertyTypes) == null) {
				this.log(Level.WARNING, "couldNotAddStdCtor", clazz.implClass.binaryName());
			}

			this.removeSetters(clazz);
			this.makeFinal(clazz);
			this.makePropertiesPrivate(clazz);
			this.makeCollectionsUnmodifiable(clazz);
			
			if (createBuilder) {
				if (this.addBuilderClass(clazz) == null) {
					this.log(Level.WARNING, "couldNotAddClassBuilder", clazz.implClass.binaryName());
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
		return new StringBuilder(1024).append("  -").append(OPTION_NAME).append("  :  ").append(getMessage("usage")).append(n).
				append( "  " ).append( BUILDER_OPTION_NAME ).append( "       :  " ).
		        append( getMessage( "builderUsage" ) ).append( n ).toString();
	}
	
	@Override
	public int parseArgument(final Options opt, final String[] args, final int i) throws BadCommandLineException, IOException {
		if ( args[i].startsWith( BUILDER_OPTION_NAME ) )
        {
            this.createBuilder = true;
            return 1;
        }
		
		return 0;
	}
	
	private static String getMessage(final String key, final Object... args) {
		return MessageFormat.format(ResourceBundle.getBundle("com/github/sabomichal/immutablexjc/PluginImpl").getString(key), args);

	}

	private JDefinedClass addBuilderClass(ClassOutline clazz) {
		JDefinedClass builderClass = generateBuilderClass(clazz);
		if (builderClass == null)  {
			return builderClass;
		}
		for (FieldOutline field : clazz.getDeclaredFields()) {
			addWithMethod(builderClass, field);
		}
		addNewBuilder(clazz, builderClass);
		addBuildMethod(clazz, builderClass);
		return builderClass;
	}

	private JMethod addBuildMethod(ClassOutline clazz, JDefinedClass builderClass) {
		JMethod method = builderClass.method(JMod.PUBLIC, clazz.ref, "build");
		method.body()._return(clazz.implClass.staticRef("this"));
		return method;
	}

	private void addNewBuilder(ClassOutline clazz, JDefinedClass builderClass) {
		JMethod method = clazz.implClass.method(JMod.PUBLIC|JMod.STATIC, builderClass, "newBuilder");
		method.body().directStatement(new StringBuilder("return (new ").append(clazz.implClass.fullName()).append("()).new ").append(builderClass.name()).append("();").toString());
	}

	private Object addPropertyContructor(ClassOutline clazz, JType[] properties) {
		JMethod ctor = clazz.implClass.getConstructor(properties);
		if (ctor == null) {
			ctor = this.generatePropertyConstructor(clazz);
		} else {
			this.log(Level.WARNING, "standardCtorExists", clazz.implClass.binaryName());
		}

		return ctor;
	}

	private JMethod addStandardConstructor(final ClassOutline clazz) {
		JMethod ctor = clazz.implClass.getConstructor(NO_ARGS);
		if (ctor == null) {
			ctor = this.generateStandardConstructor(clazz);
		} else {
			this.log(Level.WARNING, "standardCtorExists", clazz.implClass.binaryName());
		}

		return ctor;
	}

	private JMethod addWithMethod(JDefinedClass builderClass, FieldOutline field) {
		String fieldName = field.getPropertyInfo().getName(false);
		JMethod method = builderClass.method(JMod.PUBLIC, builderClass, new StringBuilder("with").append(Character.toUpperCase(fieldName.charAt(0))).append(fieldName.substring(1)).toString());
		generatePropertyAssignment(method, field, builderClass.outer());
		method.body()._return(JExpr.direct("this"));
		return method;
	}

	private JDefinedClass generateBuilderClass(ClassOutline clazz) {
		JDefinedClass builderClass = null;
		try {
			builderClass = clazz.implClass._class(JMod.PUBLIC, new StringBuilder(clazz.implClass.name()).append("Builder").toString());
			return builderClass;
		} catch (JClassAlreadyExistsException e) {
			//suppress
		}
		return builderClass;
	}

	private void generateCollectionGetter(ClassOutline clazz, FieldOutline field, final JMethod getter) {
		JMethod newMethod = clazz.implClass.method(getter.mods().getValue(), getter.type(), getter.name());
		JBlock block = newMethod.body();
		for (Object o  : getter.body().getContents()) {
			if (o instanceof JStatement) {
				// can not use instanceof since JReturn is package visible only
				if ("com.sun.codemodel.JReturn".equals(o.getClass().getName())) {
					block.directStatement("// " + getMessage("title"));
					block._return(JExpr.cast(getter.type(), clazz.implClass.owner().ref(Collections.class).staticInvoke("unmodifiableList").arg(JExpr.ref(field.getPropertyInfo().getName(false)))));
				} else {
					//JConditional fieldNotNull = block._if(JExpr.ref(field.getPropertyInfo().getName(false)).eq(JExpr._null()));
					//fieldNotNull._then().assign(JExpr.ref(field.getPropertyInfo().getName(false)), JExpr._new(field.getRawType()));
					block.add((JStatement) o);
				}
			}
		}
	}

	private void generatePropertyAssignment(final JMethod method, FieldOutline field) {
		JBlock block = method.body();
		String propertyName = field.getPropertyInfo().getName(false);
		JType javaType = getJavaType(field);
		method.param(javaType, propertyName);
		block.assign(JExpr.refthis(propertyName), JExpr.ref(propertyName));
	}
	
	private void generatePropertyAssignment(final JMethod method, FieldOutline field, JClass outerClass) {
		JBlock block = method.body();
		String propertyName = field.getPropertyInfo().getName(false);
		JType javaType = getJavaType(field);
		method.param(javaType, propertyName);
		block.assign(outerClass.staticRef("this").ref(propertyName), JExpr.ref(propertyName));
	}

	private JMethod generatePropertyConstructor(ClassOutline clazz) {
		final JMethod ctor = generateStandardConstructor(clazz);
		for (FieldOutline fieldOutline : clazz.getDeclaredFields()) {
			generatePropertyAssignment(ctor, fieldOutline);
		}
		return ctor;
	}

	private JMethod generateStandardConstructor(final ClassOutline clazz) {
		final JMethod ctor = clazz.implClass.constructor(JMod.PUBLIC);
		ctor.body().directStatement("// " + getMessage("title"));
		ctor.body().invoke("super");
		ctor.javadoc().add("Creates a new {@code " + clazz.implClass.name() + "} instance.");
		return ctor;
	}

	private JType getJavaType(FieldOutline field) {
		JType javaType = null;
		if (field.getPropertyInfo().isCollection()) {
			javaType = field.getRawType();
			if (javaType.isArray()) {
			} else {
			}
		} else {
			CTypeInfo typeInfo = field.getPropertyInfo().ref().iterator().next();
			javaType = typeInfo.toType(field.parent().parent(), Aspect.IMPLEMENTATION);
		}
		return javaType;
	}
	
	private JType[] getProperties(ClassOutline clazz) {
		JType[] propertyTypes = new JType[clazz.getDeclaredFields().length];
		int i = 0;
		for (FieldOutline fieldOutline : clazz.getDeclaredFields()) {
			propertyTypes[i++] = fieldOutline.getPropertyInfo().baseType;
		}
		return propertyTypes;
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

	private void makeCollectionsUnmodifiable(ClassOutline clazz) {
		for (FieldOutline field : clazz.getDeclaredFields()) {
			final JMethod getter = this.getPropertyGetter(field);
			if (field.getPropertyInfo().isCollection()) {
				clazz.implClass.methods().remove(getter);
				this.generateCollectionGetter(clazz, field, getter);
			}
		}
	}

	private void makeFinal(ClassOutline clazz) {
		clazz.implClass.mods().setFinal(true);
	}

	private void makePropertiesPrivate(ClassOutline clazz) {
		for (JFieldVar field : clazz.implClass.fields().values()) {
			field.mods().setPrivate();
		}
	}

	private void removeSetters(ClassOutline clazz) {
		Collection<JMethod> methods = clazz.implClass.methods();
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
