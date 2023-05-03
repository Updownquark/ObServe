package org.observe.util;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import org.observe.util.EntityReflector.ReflectedField;
import org.qommons.ArgumentParsing;
import org.qommons.ArgumentParsing.ArgumentPattern;
import org.qommons.ArgumentParsing.ArgumentType;
import org.qommons.ArgumentParsing.ValuedArgumentSetBuilder;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.BetterFile;

/**
 * An easy way of parsing command-line arguments as an entity structure
 *
 * @param <E> The type of the entity to parse
 */
public class EntityArguments<E> {
	/** May be specified on the entity interface to configure parser-wide settings */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE })
	public @interface Arguments {
		/** The description for the argument parser */
		String description() default "";

		/**
		 * The name of a field in the entity type containing the {@link org.qommons.ArgumentParsing.ArgumentPattern argument pattern} to
		 * use for {@link Flag flag} arguments by default
		 */
		String flagPattern() default "";

		/**
		 * The name of a field in the entity type containing the {@link org.qommons.ArgumentParsing.ArgumentPattern argument pattern} to
		 * use for single-valued arguments by default
		 */
		String singleValuePattern() default "";

		/**
		 * The name of a field in the entity type containing the {@link org.qommons.ArgumentParsing.ArgumentPattern argument pattern} to
		 * use for multi-valued arguments by default
		 */
		String multiValuePattern() default "";
	}

	/** My be specified on a field getter to configure some general argument settings */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.METHOD })
	public @interface Argument {
		/** @return The description for the field */
		String description() default "";

		/** My be specified on a field getter to require the argument in the command-line arguments */
		boolean required() default false;

		/** @return The minimum number of values that must be specified for the field */
		int minTimes() default 0;

		/** @return The maximum number of values that may be specified for the field */
		int maxTimes() default Integer.MAX_VALUE;

		/** @return The default value for the field, specified in the same format as a command-line argument */
		String defaultValue() default "";

		/** @return The minimum value for the field, specified in the same format as a command-line argument */
		String minValue() default "";

		/** @return The maximum value for the field, specified in the same format as a command-line argument */
		String maxValue() default "";

		/**
		 * The name of a field in the entity type containing the {@link org.qommons.ArgumentParsing.ArgumentPattern argument pattern} to
		 * use for this argument
		 */
		String argPattern() default "";

		/**
		 * <p>
		 * May be specified on any field getter with the name of a static method in the entity interface. The signature of the targeted
		 * method must be <code>T methodName(String text)<code> or <code>T methodName(String text, {@link Arguments} args)<code>. The
		 * parameter names don't matter and any exception can be thrown.</p>
		 * <p>
		 * This works for collection fields too. A field of type List&lt;Date> may be parsed, for example, by a method
		 * <code>boolean parseDate(String arg)</code>.
		 * </p>
		 *
		 * @return The name of the static method in the entity class to use to parse the values of this field
		 */
		String parseWith() default "";

		/**
		 * <p>
		 * Maybe specified on any field getter with the name of a static method in the entity interface. The signature of the targeted
		 * method must be <code>boolean methodName(Type arg)</code> where "Type" is the type (or a super type) of the argument type. The
		 * parameter name doesn't matter and any exception can be thrown.
		 * </p>
		 *
		 * <p>
		 * This works for collection fields too. A field of type List&lt;String> may be validated, for example, using a method
		 * <code>boolean checkString(String arg)</code>.
		 * </p>
		 *
		 * @return The name of the static method in the entity class to use to validate the values of this field
		 */
		String validate() default "";
	}

	/** Marks a boolean field as a flag field, as opposed to a boolean-valued field */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.METHOD })
	public @interface Flag {}

	/** Specifies the pattern to be used for a {@link Matcher}-type field */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Pattern {
		/** @return The regex pattern to use to match the field value */
		String value();
	}

	/** Specifies the time zone to use for {@link Instant}-typed fields */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface TimeField {
		/** The ID of the time zone to use for the field. If not recognized, GMT will be used. */
		String value();
	}

	/** Whether a file specified as an argument must be a directory, file, or either */
	public enum FileType {
		/** Directory */
		Directory,
		/** File */
		File,
		/** Either a file or a directory */
		Either;
	}

	/** May be specified on a {@link File}- or {@link BetterFile}-typed field to configure additional options */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface FileField {
		/** @return The type that will be enforced for the field value */
		FileType type() default FileType.Either;

		/** @return Whether the file or directory must exist */
		boolean mustExist() default false;

		/** @return Whether to create the file or directory if it does not exist */
		boolean create() default false;

		/** @return The path relative to which this field will be evaluated */
		String relativeTo() default "";

		/**
		 * @return The name of another file-typed field in this entity whose value will be used as the base folder out of which to parse
		 *         this field
		 */
		String relativeToField() default "";
	}

	private final EntityReflector<E> theEntityType;
	private ArgumentParsing.ArgumentParser theParser;
	private final QuickMap<String, String> theArgNames;

	/** @param entityType The entity class to parse arguments for */
	public EntityArguments(Class<E> entityType) {
		this(EntityReflector.build(TypeTokens.get().of(entityType), true).build());
	}

	/** @param entityType The entity type to parse arguments for */
	public EntityArguments(EntityReflector<E> entityType) {
		theEntityType = entityType;
		theArgNames = theEntityType.getFields().keySet().createMap();
	}

	/**
	 * If the parser has not already been configured, initializes a parser from the entity's fields
	 *
	 * @return This argument parser
	 */
	public EntityArguments<E> initParser() {
		if (theParser != null)
			return this;
		ArgumentParsing.ParserBuilder builder = ArgumentParsing.build();
		return defineArguments(builder).setParser(builder.build());
	}

	/**
	 * Defines arguments on the given argument parser builder from this entity's fields. If a field argument is already defined and is
	 * compatible with this entity, no action is taken. If an argument is defined and incompatible, an {@link IllegalArgumentException} is
	 * thrown.
	 *
	 * @param builder The builder to define arguments on
	 * @return This parser
	 */
	public EntityArguments<E> defineArguments(ArgumentParsing.ParserBuilder builder){
		Arguments argsAnn = TypeTokens.getRawType(theEntityType.getType()).getAnnotation(Arguments.class);
		ArgumentParsing.ArgumentPattern flagPattern = ArgumentParsing.DEFAULT_FLAG_PATTERN;
		ArgumentParsing.ArgumentPattern svPattern = ArgumentParsing.DEFAULT_VALUE_PATTERN;
		ArgumentParsing.ArgumentPattern mvPattern = ArgumentParsing.DEFAULT_MULTI_VALUE_PATTERN;
		if (argsAnn != null) {
			if (argsAnn.description().length() > 0 && builder.getDescription() == null)
				builder.withDescription(argsAnn.description());
			if (argsAnn.flagPattern().length() > 0)
				flagPattern = getArgPattern(argsAnn.flagPattern(), 'f');
			if (argsAnn.singleValuePattern().length() > 0)
				svPattern = getArgPattern(argsAnn.singleValuePattern(), 's');
			if (argsAnn.multiValuePattern().length() > 0)
				mvPattern = getArgPattern(argsAnn.multiValuePattern(), 'm');
		}
		for (int f = 0; f < theEntityType.getFields().keySize(); f++) {
			ReflectedField<E, ?> field = theEntityType.getFields().get(f);
			Class<?> fieldType = TypeTokens.getRawType(field.getType());
			Class<?> argType;
			boolean multi, flag;
			if (fieldType.isAssignableFrom(BetterList.class)) {
				flag = false;
				multi = true;
				argType = TypeTokens.getRawType(field.getType().resolveType(Collection.class.getTypeParameters()[0]));
			} else {
				multi = false;
				flag = TypeTokens.get().isBoolean(field.getType()) && field.getGetter().getMethod().getAnnotation(Flag.class) != null;
				argType = fieldType;
			}
			String argName = StringUtils.parseByCase(field.getName(), true).toKebabCase();
			ArgumentType<?> oldArg = builder.getArgument(argName);
			if (oldArg != null) {
				checkArgument(oldArg, argType, flag, multi);
				continue;
			}
			ArgumentParsing.ArgumentPattern pattern;
			if (flag)
				pattern = flagPattern;
			else if (multi)
				pattern = mvPattern;
			else
				pattern = svPattern;
			Argument argAnn = field.getGetter().getMethod().getAnnotation(Argument.class);
			if (argAnn != null && argAnn.argPattern().length() > 0)
				pattern = getArgPattern(argAnn.argPattern(), flag ? 'f' : (multi ? 'm' : 's'));
			if (flag) {
				theArgNames.put(field.getFieldIndex(), argName);
				builder.forFlagPattern(pattern, p -> p.add(argName, arg -> {
					if (argAnn != null && argAnn.description().length() > 0)
						arg.withDescription(argAnn.description());
				}));
			} else
				builder.forValuePattern(pattern, p -> addArgument(p, TypeTokens.get().unwrap(argType), field, multi, argAnn));
		}
		return this;
	}

	private void checkArgument(ArgumentType<?> oldArg, Class<?> argType, boolean flag, boolean multi) {
		if (oldArg.getPattern().getMaxValues() == 0) {
			if (!flag)
				throw new IllegalArgumentException("Argument " + oldArg.getName() + " is already defined as a flag argument");
		} else if (flag)
			throw new IllegalArgumentException("Argument " + oldArg.getName() + " is already defined as a valued argument");
		else if (!TypeTokens.get().wrap(argType).isAssignableFrom(TypeTokens.get().wrap(oldArg.getType())))
			throw new IllegalArgumentException(
				"Argument " + oldArg.getName() + " is already defined with incompatible type " + oldArg.getType());

	}

	/**
	 * @param parser The parser to use to parse arguments. This should have been {@link org.qommons.ArgumentParsing.ParserBuilder#build()
	 *        built} from a builder on which this instance's {@link #defineArguments(org.qommons.ArgumentParsing.ParserBuilder)
	 *        defineArguments} method was called to ensure arguments for each field in this entity have been defined.
	 * @return This argument parser
	 */
	public EntityArguments<E> setParser(ArgumentParsing.ArgumentParser parser) {
		theParser = parser;
		for (int f = 0; f < theEntityType.getFields().keySize(); f++) {
			ReflectedField<E, ?> field = theEntityType.getFields().get(f);
			Class<?> fieldType = TypeTokens.getRawType(field.getType());
			Class<?> argType;
			boolean multi, flag;
			if (fieldType.isAssignableFrom(BetterList.class)) {
				flag = false;
				multi = true;
				argType = TypeTokens.getRawType(field.getType().resolveType(Collection.class.getTypeParameters()[0]));
			} else {
				multi = false;
				flag = TypeTokens.get().isBoolean(field.getType()) && field.getGetter().getMethod().getAnnotation(Flag.class) != null;
				argType = fieldType;
			}
			String argName = StringUtils.parseByCase(field.getName(), true).toKebabCase();
			ArgumentType<?> arg = parser.getArgumentIfExists(argName);
			if (arg != null)
				checkArgument(arg, argType, flag, multi);
		}
		return this;
	}

	private ArgumentParsing.ArgumentPattern getArgPattern(String argPattern, char type) {
		// Lookup static field from annotation value
		ArgumentParsing.ArgumentPattern pattern;
		try {
			pattern = (ArgumentPattern) TypeTokens.getRawType(theEntityType.getType()).getDeclaredField(argPattern).get(null);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Could not access field " + theEntityType.getType().toString() + "." + argPattern, e);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("No such field " + theEntityType.getType().toString() + "." + argPattern);
		} catch (SecurityException e) {
			throw new IllegalArgumentException("Could not access field " + theEntityType.getType().toString() + "." + argPattern, e);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException(
				"Field " + theEntityType.getType().toString() + "." + argPattern + " is not an ArgumentPattern");
		}
		if (pattern == null)
			throw new IllegalArgumentException("Field " + theEntityType.getType().toString() + "." + argPattern + " is null");
		switch (type) {
		case 'f':
			if (pattern.getMaxValues() > 0)
				throw new IllegalArgumentException(argPattern + " is not a flag pattern");
			break;
		case 's':
			if (pattern.getMaxValues() == 0)
				throw new IllegalArgumentException(argPattern + " is not a value pattern");
			else if (pattern.getMaxValues() > 1)
				throw new IllegalArgumentException(argPattern + " is not a single-valued pattern");
			break;
		case 'm':
			if (pattern.getMaxValues() == 0)
				throw new IllegalArgumentException(argPattern + " is not a value pattern");
			else if (pattern.getMaxValues() == 1)
				throw new IllegalArgumentException(argPattern + " is not a multi-valued pattern");
			break;
		default:
			throw new IllegalStateException("Unrecognized pattern type: " + type);
		}
		return pattern;
	}

	@SuppressWarnings("rawtypes")
	private <T> void addArgument(ValuedArgumentSetBuilder builder, Class<T> type, ReflectedField<E, ?> field, boolean multi,
		Argument argAnn) {
		String argName = StringUtils.parseByCase(field.getName(), true).toKebabCase();
		theArgNames.put(field.getFieldIndex(), argName);
		boolean required;
		if (argAnn != null && argAnn.required())
			required = true;
		else if (argAnn != null && argAnn.defaultValue().length() > 0)
			required = false;
		else
			required = field.getType().isPrimitive();
		int index = field.getFieldIndex();
		if (argAnn != null && argAnn.parseWith().length() > 0) {
			Method parseMethod[] = new Method[1];
			boolean[] withArgs = new boolean[1];
			for (Method m : TypeTokens.getRawType(theEntityType.getType()).getDeclaredMethods()) {
				if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(argAnn.parseWith())
					&& TypeTokens.get().wrap(type).isAssignableFrom(TypeTokens.get().wrap(m.getReturnType()))
					&& m.getParameterTypes().length >= 1 && m.getParameterTypes().length <= 2 && m.getParameterTypes()[0] == String.class) {
					if (m.getParameterTypes().length == 1) {
						parseMethod[0] = m;
						break;
					} else if (m.getParameterTypes()[1] == ArgumentParsing.Arguments.class) {
						withArgs[0] = true;
						parseMethod[0] = m;
						break;
					}
				}
			}
			String parserName = theEntityType.getType() + "." + argAnn.parseWith() + "(String";
			if (withArgs[0])
				parserName += ", Arguments";
			parserName += ")";
			if (parseMethod[0] == null)
				throw new IllegalArgumentException("No such parse method matching " + type.getName() + " " + parserName + " for field "
					+ theEntityType.getFields().keySet().get(index));
			builder.addArgument(argName, type, (text, args) -> {
				try {
					if (withArgs[0])
						return (T) parseMethod[0].invoke(null, text, args);
					else
						return (T) parseMethod[0].invoke(null, text);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else
						throw new IllegalStateException(e.getTargetException());
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new IllegalStateException(e);
				}
			}, ab -> configureArg(ab, argAnn, required, multi, index));
		} else if (type == boolean.class)
			builder.addBooleanArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == int.class)
			builder.addIntArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == long.class)
			builder.addLongArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == double.class)
			builder.addDoubleArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == String.class)
			builder.addStringArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (Enum.class.isAssignableFrom(type))
			builder.addEnumArgument(argName, (Class<Enum>) type, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == Matcher.class) {
			Pattern patternAnn = field.getGetter().getMethod().getAnnotation(Pattern.class);
			if (patternAnn == null)
				throw new IllegalArgumentException(
					"Matcher field " + field.getName() + " present with no " + Pattern.class.getName() + " annotation specified");
			builder.addPatternArgument(argName, patternAnn.value(), ab -> configureArg(ab, argAnn, required, multi, index));
		} else if (type == Instant.class) {
			TimeField timeZone = field.getGetter().getMethod().getAnnotation(TimeField.class);
			builder.addInstantArgument(argName, ab -> {
				if (timeZone != null)
					ab.withTimeZone(timeZone.value());
				configureArg(ab, argAnn, required, multi, index);
			});
		} else if (type == Duration.class)
			builder.addDurationArgument(argName, ab -> configureArg(ab, argAnn, required, multi, index));
		else if (type == File.class) {
			FileField fileField = field.getGetter().getMethod().getAnnotation(FileField.class);
			builder.addFileArgument(argName, ab -> {
				if (fileField != null) {
					if (fileField.type() != FileType.Either)
						ab.directory(fileField.type() == FileType.Directory);
					ab.mustExist(fileField.mustExist());
					ab.create(fileField.create());
					if (fileField.relativeTo().length() > 0)
						ab.relativeTo(fileField.relativeTo());
					if (fileField.relativeToField().length() > 0) {
						int relIndex = theEntityType.getFields().keySet().indexOfTolerant(fileField.relativeToField());
						if (relIndex < 0)
							throw new IllegalArgumentException(
								"Unrecognized field: " + fileField.relativeToField() + " as relativeToField for field " + field.getName());
						String relArgName = StringUtils.parseByCase(fileField.relativeToField(), true).toKebabCase();
						ab.relativeToFileArg(relArgName);
					}
				}
				configureArg(ab, argAnn, required, multi, index);
			});
		} else if (type == BetterFile.class) {
			FileField fileField = field.getGetter().getMethod().getAnnotation(FileField.class);
			builder.addBetterFileArgument(argName, ab -> {
				if (fileField != null) {
					if (fileField.type() != FileType.Either)
						ab.directory(fileField.type() == FileType.Directory);
					ab.mustExist(fileField.mustExist());
					ab.create(fileField.create());
				}
				configureArg(ab, argAnn, required, multi, index);
			});
		} else
			throw new IllegalArgumentException("Unrecognized argument field type: " + type.getName() + " (" + field.getName() + ")");
	}

	private <T> void configureArg(ArgumentParsing.ArgumentBuilder<T, ?> ab, Argument argAnn, boolean required, boolean multi, int index) {
		if (multi && argAnn != null) {
			if (argAnn.minTimes() > 0 || argAnn.maxTimes() < Integer.MAX_VALUE)
				ab.times(argAnn.minTimes(), argAnn.maxTimes());
			else if (required)
				ab.times(1, Integer.MAX_VALUE);
		} else if (!multi) {
			if (required)
				ab.required();
			else
				ab.optional();
		}
		if (argAnn != null && argAnn.defaultValue().length() > 0)
			ab.parseDefaultValue(argAnn.defaultValue());
		if (argAnn != null) {
			if (argAnn.minValue().length() > 0) {
				if (argAnn.maxValue().length() > 0) {
					ab.constrain(tb -> tb.parseBetween(argAnn.minValue(), argAnn.maxValue()));
				} else {
					ab.constrain(tb -> tb.parseGte(argAnn.minValue()));
				}
			} else if (argAnn.maxValue().length() > 0) {
				ab.constrain(tb -> tb.parseLte(argAnn.maxValue()));
			}
		}
		if (argAnn != null && argAnn.validate().length() > 0) {
			Method checkMethod[] = new Method[1];
			for (Method m : TypeTokens.getRawType(theEntityType.getType()).getDeclaredMethods()) {
				if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(argAnn.validate()) && m.getReturnType() == boolean.class
					&& m.getParameterTypes().length == 1 && TypeTokens.get().wrap(m.getParameterTypes()[0])
					.isAssignableFrom(TypeTokens.get().wrap(ab.getArgument().getType()))) {
					checkMethod[0] = m;
					break;
				}
			}
			String filterName = theEntityType.getType() + "." + argAnn.validate() + "(" + ab.getArgument().getType().getName() + ")";
			if (checkMethod[0] == null)
				throw new IllegalArgumentException(
					"No such check method matching boolean " + filterName + " for field " + theEntityType.getFields().keySet().get(index));
			Predicate<T> filter = LambdaUtils.printablePred(v -> {
				try {
					return ((Boolean) checkMethod[0].invoke(null, v)).booleanValue();
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else
						throw new IllegalStateException(e.getTargetException());
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new IllegalStateException(e);
				}
			}, filterName, null);
			ab.constrain(tb -> tb.check(filter));
		}
	}

	/**
	 * @return The underlying command-line arg parser. This will be null if the parser has not been either {@link #initParser() initialized}
	 *         or {@link #setParser(org.qommons.ArgumentParsing.ArgumentParser) set}.
	 */
	public ArgumentParsing.ArgumentParser getParser() {
		return theParser;
	}

	/**
	 * @param args The command-line arguments to parse
	 * @return The entity representation of the parsed arguments
	 */
	public E parse(String... args) {
		return parse(Arrays.asList(args));
	}

	/**
	 * @param args The command-line arguments to parse
	 * @return The entity representation of the parsed arguments
	 */
	public E parse(Collection<String> args) {
		if (theParser == null)
			initParser();
		ArgumentParsing.Arguments parsedArgs = theParser.parse(args);
		QuickMap<String, Object> fieldValues = theEntityType.getFields().keySet().createMap();
		for (int a = 0; a < fieldValues.keySize(); a++) {
			Class<?> type = TypeTokens.getRawType(theEntityType.getFields().get(a).getType());
			if (theParser.getArgument(theArgNames.get(a)).getPattern().getMaxValues() == 0)
				fieldValues.put(a, parsedArgs.has(theArgNames.get(a)));
			else if (type.isAssignableFrom(BetterList.class))
				fieldValues.put(a, parsedArgs.getAll(theArgNames.get(a)));
			else
				fieldValues.put(a, parsedArgs.get(theArgNames.get(a)));
		}
		return theEntityType.newInstance(new EntityReflector.EntityInstanceBacking() {
			@Override
			public Object get(int fieldIndex) {
				return fieldValues.get(fieldIndex);
			}

			@Override
			public void set(int fieldIndex, Object newValue) {
				throw new UnsupportedOperationException("Can't set argument values");
			}
		});
	}
}
