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
import org.qommons.ArgumentParsing2;
import org.qommons.ArgumentParsing2.Arguments;
import org.qommons.ArgumentParsing2.ValuedArgumentBuilder;
import org.qommons.ArgumentParsing2.ValuedArgumentSetBuilder;
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
	/** May be used to describe a field/argument */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	public static @interface Description {
		/** @return The description for the field */
		String value();
	}
	/** My be specified on a field getter to require the argument in the command-line arguments */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Required {}

	/** May be specified on collection-type field getters to bound the number of values that may be specified */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Times {
		/** @return The minimum number of values that must be specified for the field */
		int min() default 0;
		/** @return The maximum number of values that may be specified for the field */
		int max() default Integer.MAX_VALUE;
	}

	/** Allows a default value to be specified for a field */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Default {
		/** @return The default value for the field, specified in the same format as a command-line argument */
		String value();
	}

	/** Specifies a range for a comparable-typed field */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Bound {
		/** @return The minimum value for the field, specified in the same format as a command-line argument */
		String min();
		/** @return The maximum value for the field, specified in the same format as a command-line argument */
		String max();
	}

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
	public static @interface TimeZone {
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
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface CheckValue {
		String value();
	}

	private final EntityReflector<E> theEntityType;
	private final ArgumentParsing2.ArgumentParser theParser;
	private final QuickMap<String, String> theArgNames;

	/** @param entityType The entity class to parse arguments for */
	public EntityArguments(Class<E> entityType) {
		this(EntityReflector.build(TypeTokens.get().of(entityType), true).build());
	}

	/** @param entityType The entity type to parse arguments for */
	public EntityArguments(EntityReflector<E> entityType) {
		theEntityType = entityType;
		theArgNames = theEntityType.getFields().keySet().createMap();
		ArgumentParsing2.ParserBuilder builder = ArgumentParsing2.build();
		Description parserDescrip = TypeTokens.getRawType(entityType.getType()).getAnnotation(Description.class);
		if (parserDescrip != null)
			builder.withDescription(parserDescrip.value());
		for (int f = 0; f < theEntityType.getFields().keySize(); f++) {
			ReflectedField<E, ?> field = theEntityType.getFields().get(f);
			Class<?> fieldType = TypeTokens.getRawType(field.getType());
			Class<?> argType;
			boolean multi;
			if (fieldType.isAssignableFrom(BetterList.class)) {
				multi = true;
				argType = TypeTokens.getRawType(field.getType().resolveType(Collection.class.getTypeParameters()[0]));
			} else {
				multi = false;
				argType = fieldType;
			}
			builder.forValuePattern(p -> addArgument(p, TypeTokens.get().unwrap(argType), field, multi));
		}
		theParser = builder.build();
	}

	@SuppressWarnings("rawtypes")
	private <T> void addArgument(ValuedArgumentSetBuilder builder, Class<T> type, ReflectedField<E, ?> field, boolean multi) {
		String argName = StringUtils.parseByCase(field.getName(), true).toKebabCase();
		theArgNames.put(field.getFieldIndex(), argName);
		Description descrip = field.getGetter().getMethod().getAnnotation(Description.class);
		Default def = field.getGetter().getMethod().getAnnotation(Default.class);
		boolean required;
		if (field.getGetter().getMethod().getAnnotation(Required.class) != null)
			required = true;
		else if (def != null)
			required = false;
		else
			required = field.getType().isPrimitive();
		Bound bound = field.getGetter().getMethod().getAnnotation(Bound.class);
		Times times = field.getGetter().getMethod().getAnnotation(Times.class);
		CheckValue check = field.getGetter().getMethod().getAnnotation(CheckValue.class);
		int index = field.getFieldIndex();
		if (times != null && !multi)
			throw new IllegalArgumentException(
				"Can't specify " + Times.class.getName() + " for a non-collection type field: " + field.getName());
		if (type == boolean.class)
			builder.addBooleanArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == int.class)
			builder.addIntArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == long.class)
			builder.addLongArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == double.class)
			builder.addDoubleArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == String.class)
			builder.addStringArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (Enum.class.isAssignableFrom(type))
			builder.addEnumArgument(argName, (Class<Enum>) type,
				ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == Matcher.class) {
			Pattern pattern = field.getGetter().getMethod().getAnnotation(Pattern.class);
			if (pattern == null)
				throw new IllegalArgumentException(
					"Matcher field " + field.getName() + " present with no " + Pattern.class.getName() + " annotation specified");
			builder.addPatternArgument(argName, pattern.value(),
				ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		} else if (type == Instant.class) {
			TimeZone timeZone = field.getGetter().getMethod().getAnnotation(TimeZone.class);
			builder.addInstantArgument(argName, ab -> {
				if (timeZone != null)
					ab.withTimeZone(timeZone.value());
				configureArg(ab, descrip, required, def, bound, times, check, multi, index);
			});
		} else if (type == Duration.class)
			builder.addDurationArgument(argName, ab -> configureArg(ab, descrip, required, def, bound, times, check, multi, index));
		else if (type == File.class) {
			FileField fileField = field.getGetter().getMethod().getAnnotation(FileField.class);
			builder.addFileArgument(argName, ab -> {
				if (fileField != null) {
					if (fileField.type() != FileType.Either)
						ab.directory(fileField.type() == FileType.Directory);
					ab.mustExist(fileField.mustExist());
					ab.create(fileField.create());
				}
				configureArg(ab, descrip, required, def, bound, times, check, multi, index);
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
				configureArg(ab, descrip, required, def, bound, times, check, multi, index);
			});
		} else
			throw new IllegalArgumentException("Unrecognized argument field type: " + type.getName() + " (" + field.getName() + ")");
	}

	private <T> void configureArg(ValuedArgumentBuilder<T, ?> ab, Description descrip, boolean required, Default def, Bound bound,
		Times times, CheckValue check, boolean multi, int index) {
		if (descrip != null)
			ab.withDescription(descrip.value());
		if (multi) {
			if (times != null)
				ab.times(times.min(), times.max());
			else if (required)
				ab.times(1, Integer.MAX_VALUE);
		} else {
			if (required)
				ab.required();
			else
				ab.optional();
		}
		if (def != null)
			ab.parseDefaultValue(def.value());
		if (bound != null)
			ab.constrain(tb -> tb.parseBetween(bound.min(), bound.max()));
		if (check != null) {
			Method checkMethod[] = new Method[1];
			for (Method m : TypeTokens.getRawType(theEntityType.getType()).getDeclaredMethods()) {
				if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(check.value()) && m.getReturnType() == boolean.class
					&& m.getParameterTypes().length == 1 && TypeTokens.get().wrap(m.getParameterTypes()[0])
					.isAssignableFrom(TypeTokens.get().wrap(ab.getArgument().getType()))) {
					checkMethod[0] = m;
					break;
				}
			}
			String filterName = theEntityType.getType() + "." + check.value() + "(" + ab.getArgument().getType().getName() + ")";
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

	public ArgumentParsing2.ArgumentParser getParser() {
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
		Arguments parsedArgs=theParser.parse(args);
		QuickMap<String, Object> fieldValues=theEntityType.getFields().keySet().createMap();
		for(int a=0;a<fieldValues.keySize();a++){
			Class<?> type=TypeTokens.getRawType(theEntityType.getFields().get(a).getType());
			if(type.isAssignableFrom(BetterList.class))
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
