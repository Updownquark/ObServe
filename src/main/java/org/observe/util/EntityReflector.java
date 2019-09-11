package org.observe.util;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

/**
 * A utility for reflectively compiling a public interface's fields with getters and setters. Especially, this class's
 * {@link #newInstance(IntFunction, BiConsumer) newInstance} method allows the caller to easily create new instances of the target interface
 * backed by a custom data set.
 *
 * @param <E> The type of interface entity this reflector is for
 */
public class EntityReflector<E> {
	private static final Constructor<Lookup> LOOKUP_CONSTRUCTOR;
	static {
		Constructor<Lookup> c;
		try {
			c = Lookup.class.getDeclaredConstructor(Class.class);
			c.setAccessible(true);
		} catch (NoSuchMethodException e) {
			System.err.println("Lookup class has changed: unable to call default or Object methods on entities");
			e.printStackTrace();
			c = null;
		} catch (SecurityException e) {
			System.err.println("Could not access Lookup: unable to call default or Object methods on entities");
			c = null;
		}
		LOOKUP_CONSTRUCTOR = c;
	}

	/**
	 * @param type The type to test
	 * @return Whether the type is an entity type candidate to be reflected by this class
	 */
	public static boolean isEntityType(Class<?> type) {
		return type.isInterface() && (type.getModifiers() & Modifier.PUBLIC) != 0;
	}

	/**
	 * @param type The interface type to reflect on
	 * @return A builder to create a reflector
	 */
	public static <E> Builder<E> build(TypeToken<E> type) {
		return new Builder<>(type);
	}

	/**
	 * Builds an {@link EntityReflector}
	 *
	 * @param <E> The interface type to reflect on
	 */
	public static class Builder<E> {
		private final TypeToken<E> theType;
		private final Class<E> theRawType;
		private Function<Method, String> theGetterFilter;
		private Function<Method, String> theSetterFilter;
		private Map<Method, BiFunction<? super E, Object[], ?>> theCustomMethods;
		private Set<String> theIdFields;
		private List<EntityReflectionMessage> theMessages;

		private Builder(TypeToken<E> type) {
			theRawType = TypeTokens.getRawType(type);
			if (theRawType == null || !theRawType.isInterface())
				throw new IllegalArgumentException("This class only works for interface types");
			else if ((theRawType.getModifiers() & Modifier.PUBLIC) == 0)
				throw new IllegalArgumentException("This class only works for public interface types");
			theType = type;
			theGetterFilter = new PrefixFilter("get", 0);
			theSetterFilter = new PrefixFilter("set", 1);
			theMessages = new ArrayList<>();
		}

		/**
		 * @param getterFilter The function to determine what field a getter is for
		 * @return This builder
		 */
		public Builder<E> withGetterFilter(Function<Method, String> getterFilter) {
			theGetterFilter = getterFilter;
			return this;
		}

		/**
		 * @param setterFilter The function to determine what field a setter is for
		 * @return This builder
		 */
		public Builder<E> withSetterFilter(Function<Method, String> setterFilter) {
			theSetterFilter = setterFilter;
			return this;
		}

		/**
		 * @param customMethods Custom method implementations for {@link EntityReflector#newInstance(IntFunction, BiConsumer) created
		 *        instances} of the type to use
		 * @return This builder
		 */
		public Builder<E> withCustomMethods(Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods) {
			if (theCustomMethods == null)
				theCustomMethods = new LinkedHashMap<>();
			theCustomMethods.putAll(customMethods);
			return this;
		}

		/**
		 * @param method The method to implement
		 * @param implementation The custom implementation of the method for {@link EntityReflector#newInstance(IntFunction, BiConsumer)
		 *        created instances} of the type to use
		 * @return This builder
		 */
		public Builder<E> withCustomMethod(Method method, BiFunction<? super E, Object[], ?> implementation) {
			if (theCustomMethods == null)
				theCustomMethods = new LinkedHashMap<>();
			theCustomMethods.put(method, implementation);
			return this;
		}

		public Builder<E> withId(String... ids) {
			if (theIdFields == null)
				theIdFields = new LinkedHashSet<>();
			for (String id : ids)
				theIdFields.add(id);
			return this;
		}

		/** @return The new reflector */
		public EntityReflector<E> build() {
			EntityReflector<E> reflector = buildNoPrint();
			for (EntityReflectionMessage message : theMessages)
				System.err.println(message);
			return reflector;
		}

		/**
		 * The default behavior of this builder is to print all the messages that occurred as a result of building the reflected type to
		 * System.err. This method does not do that. {@link #getMessages()} can be called to perform other operations on them.
		 *
		 * @return The new reflector
		 */
		public EntityReflector<E> buildNoPrint() {
			return new EntityReflector<>(theType, theRawType, theGetterFilter, theSetterFilter, theCustomMethods, theIdFields, theMessages);
		}

		/**
		 * @return The messages that occurred describing the results of the entity reflection after {@link #build()} or
		 *         {@link #buildNoPrint()}
		 */
		public List<EntityReflectionMessage> getMessages() {
			return theMessages;
		}
	}

	private enum EntityReflectionMessageLevel {
		FATAL, ERROR, WARNING, INFO;
	}

	public static class EntityReflectionMessage {
		private final EntityReflectionMessageLevel theLevel;
		private final Method theMethod;
		private final String theMessage;

		public EntityReflectionMessage(EntityReflectionMessageLevel level, Method method, String message) {
			theLevel = level;
			theMethod = method;
			theMessage = message;
		}

		public EntityReflectionMessageLevel getLevel() {
			return theLevel;
		}

		public Method getMethod() {
			return theMethod;
		}

		public String getMessage() {
			return theMessage;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theLevel).append(": ").append(theMethod).append(") ").append(theMessage).toString();
		}
	}

	/** A simple filter function to determine whether a method is a getter or setter for a field */
	public static class PrefixFilter implements Function<Method, String> {
		private final String thePrefix;
		private final int theParameterCount;

		/**
		 * @param prefix The prefix to look for
		 * @param paramCount The number of parameters the method should accept
		 */
		public PrefixFilter(String prefix, int paramCount) {
			thePrefix = prefix;
			theParameterCount = paramCount;
		}

		/** @return The prefix that this filter looks for */
		public String getPrefix() {
			return thePrefix;
		}

		/** @return The number of parameters a method must accept in order to pass this filter */
		public int getParameterCount() {
			return theParameterCount;
		}

		@Override
		public String apply(Method m) {
			if ((m.getModifiers() & Modifier.PUBLIC) == 0 || (m.getModifiers() & Modifier.STATIC) != 0)
				return null; // Must be public and not static
			else if (theParameterCount == 0 && (m.getReturnType() == void.class || m.getReturnType() == Void.class))
				return null; // Must have non-void return type
			else if (m.getParameterTypes().length != theParameterCount)
				return null;
			else if (!m.getName().startsWith(thePrefix) || m.getName().length() == thePrefix.length())
				return null;
			StringBuilder s = new StringBuilder(m.getName().substring(thePrefix.length()));
			s.setCharAt(0, Character.toLowerCase(s.charAt(0)));
			return s.toString();
		}
	}

	/**
	 * Represents a field in an interface entity type
	 *
	 * @param <E> The entity type
	 * @param <F> The field type
	 */
	public static class ReflectedField<E, F> {
		private final String theName;
		private int theFieldIndex;
		private boolean id;
		private final FieldGetter<? super E, F> theGetter;
		private FieldSetter<? super E, F> theSetter;

		ReflectedField(EntityReflector<E> reflector, String name, int fieldIndex, boolean id, Method getter) {
			theName = name;
			theFieldIndex = fieldIndex;
			this.id = id;
			theGetter = new FieldGetter<>(reflector, getter, this);
		}

		void setSetter(FieldSetter<? super E, F> setter) {
			theSetter = setter;
		}

		/** @return The name of the field */
		public String getName() {
			return theName;
		}

		/** @return The field index, i.e. the index of this field in {@link EntityReflector#getFields()} */
		public int getFieldIndex() {
			return theFieldIndex;
		}

		public boolean isId() {
			return id;
		}

		/** @return The entity type's getter method for this field */
		public FieldGetter<? super E, F> getGetter() {
			return theGetter;
		}

		/** @return The entity type's setter method for this field */
		public FieldSetter<? super E, F> getSetter() {
			return theSetter;
		}

		@Override
		public String toString() {
			return theName + " (" + theGetter.getReturnType() + ")";
		}
	}

	public enum SetterReturnType {
		OLD_VALUE, SELF, VOID;
	}

	static class MethodSignature implements Comparable<MethodSignature> {
		final String name;
		final Class<?>[] parameters;

		MethodSignature(Method method) {
			this.name = method.getName();
			this.parameters = method.getParameterTypes();
		}

		@Override
		public int compareTo(MethodSignature o) {
			if (this == o)
				return 0;
			int comp = QommonsUtils.compareNumberTolerant(name, o.name, true, true);
			if (comp != 0)
				return comp;
			comp = Integer.compare(parameters.length, o.parameters.length);
			if (comp != 0)
				return comp;
			for (int i = 0; i < parameters.length; i++) {
				comp = QommonsUtils.compareNumberTolerant(parameters[i].getName(), o.parameters[i].getName(), true, true);
				if (comp != 0)
					return comp;
			}
			return 0;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(name).append('(');
			for (int i = 0; i < parameters.length; i++) {
				if (i > 0)
					str.append(", ");
				str.append(parameters[i]);
			}
			return str.append(')').toString();
		}
	}

	public static abstract class MethodInterpreter<E, R> implements Comparable<MethodInterpreter<?, ?>> {
		private final EntityReflector<E> theReflector;
		private final Method theMethod;
		private final Class<?>[] theParameters;
		private final Invokable<E, R> theInvokable;

		public MethodInterpreter(EntityReflector<E> reflector, Method method) {
			theReflector = reflector;
			theMethod = method;
			theParameters = method.getParameterTypes();
			TypeToken<?> type;
			if (reflector != null)
				type = reflector.getType();
			else
				type = TypeTokens.get().OBJECT;
			theInvokable = (Invokable<E, R>) type.method(method);
		}

		public EntityReflector<E> getReflector() {
			return theReflector;
		}

		public Method getMethod() {
			return theMethod;
		}

		public String getName() {
			return theMethod.getName();
		}

		public Invokable<E, R> getInvokable() {
			return theInvokable;
		}

		public TypeToken<?> getReturnType() {
			return theInvokable.getReturnType();
		}

		public abstract R invoke(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable;

		@Override
		public int compareTo(MethodInterpreter<?, ?> o) {
			if (this == o)
				return 0;
			int comp = StringUtils.compareNumberTolerant(theMethod.getName(), o.theMethod.getName(), true, true);
			if (comp != 0)
				return comp;
			int i;
			for (i = 0; i < theParameters.length && i < o.theParameters.length; i++) {
				comp = StringUtils.compareNumberTolerant(theParameters[i].getName(), o.theParameters[i].getName(), true, true);
				if (comp != 0)
					return comp;
			}
			if (i < theParameters.length)
				return 1;
			else if (i < o.theParameters.length)
				return -1;
			return 0;
		}

		public int compare(Method method) {
			if (theMethod == method)
				return 0;
			int comp = StringUtils.compareNumberTolerant(theMethod.getName(), method.getName(), true, true);
			if (comp != 0)
				return comp;
			Class<?>[] parameters = method.getParameterTypes();
			int i;
			for (i = 0; i < theParameters.length && i < parameters.length; i++) {
				comp = StringUtils.compareNumberTolerant(theParameters[i].getName(), parameters[i].getName(), true, true);
				if (comp != 0)
					return comp;
			}
			if (i < theParameters.length)
				return 1;
			else if (i < parameters.length)
				return -1;
			return 0;
		}

		@Override
		public int hashCode() {
			int h = theMethod.getName().hashCode();
			h ^= Objects.hash((Object[]) theParameters);
			return h;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MethodInterpreter))
				return false;
			MethodInterpreter<?, ?> other = (MethodInterpreter<?, ?>) obj;
			// Although it's possible that the same method could be interpreted differently in different places, this seems safe enough
			return theMethod.equals(other.theMethod);
		}

		@Override
		public String toString() {
			return theMethod.toString(); // Probably do something prettier later
		}
	}

	public static abstract class FieldRelatedMethod<E, F, R> extends MethodInterpreter<E, R> {
		private final ReflectedField<E, F> theField;

		public FieldRelatedMethod(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field) {
			super(reflector, method);
			theField = field;
		}

		public ReflectedField<E, F> getField() {
			return theField;
		}
	}

	public static class FieldGetter<E, F> extends FieldRelatedMethod<E, F, F> {
		public FieldGetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field) {
			super(reflector, method, field);
		}

		@Override
		public F invoke(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			return (F) fieldGetter.apply(getField().getFieldIndex());
		}
	}

	public static class FieldSetter<E, F> extends FieldRelatedMethod<E, F, Object> {
		private final SetterReturnType theReturnType;

		public FieldSetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, SetterReturnType returnType) {
			super(reflector, method, field);
			theReturnType = returnType;
		}

		public SetterReturnType getSetterReturnType() {
			return theReturnType;
		}

		@Override
		public Object invoke(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			Object returnValue = null;
			switch (theReturnType) {
			case OLD_VALUE:
				returnValue = fieldGetter.apply(getField().getFieldIndex());
				break;
			case SELF:
				returnValue = proxy;
				break;
			case VOID:
				returnValue = null;
				break;
			}
			fieldSetter.accept(getField().getFieldIndex(), args[0]);
			return returnValue;
		}
	}

	public static class DefaultMethod<E, R> extends MethodInterpreter<E, R> {
		private final MethodHandle theHandle;

		public DefaultMethod(EntityReflector<E> reflector, Method method, MethodHandle handle) {
			super(reflector, method);
			theHandle = handle;
		}

		@Override
		public R invoke(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) throws Throwable {
			if (theHandle == null)
				throw new IllegalStateException("Unable to reflectively invoke default method " + getInvokable());
			synchronized (theHandle) {
				return (R) theHandle.bindTo(proxy).invokeWithArguments(args);
			}
		}
	}

	public static class CustomMethod<E, R> extends MethodInterpreter<E, R> {
		private BiFunction<? super E, Object[], R> theInterpreter;

		public CustomMethod(EntityReflector<E> reflector, Method method, BiFunction<? super E, Object[], R> interpreter) {
			super(reflector, method);
			theInterpreter = interpreter;
		}

		@Override
		public R invoke(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			return theInterpreter.apply(proxy, args);
		}
	}

	public static MethodInterpreter<Object, Boolean> DEFAULT_EQUALS;
	public static MethodInterpreter<Object, Integer> DEFAULT_HASH_CODE;
	public static MethodInterpreter<Object, String> DEFAULT_TO_STRING;
	public static MethodInterpreter<Object, Void> DEFAULT_NOTIFY;
	public static MethodInterpreter<Object, Void> DEFAULT_NOTIFY_ALL;
	public static MethodInterpreter<Object, Void> DEFAULT_WAIT;
	public static MethodInterpreter<Object, Void> DEFAULT_FINALIZE;
	public static MethodInterpreter<Object, Object> DEFAULT_CLONE;
	public static MethodInterpreter<Object, Class<?>> DEFAULT_GET_CLASS; // Does this get delegated to here?
	public static final NavigableSet<MethodInterpreter<Object, ?>> DEFAULT_OBJECT_METHODS;

	static {
		Method equals, hashCode, toString, notify, notifyAll, wait, finalize, clone, getClass;
		try {
			equals = Object.class.getMethod("equals", Object.class);
			hashCode = Object.class.getMethod("hashCode");
			toString = Object.class.getMethod("toString");
			notify = Object.class.getMethod("notify");
			notifyAll = Object.class.getMethod("notifyAll");
			wait = Object.class.getMethod("wait");
			finalize = Object.class.getDeclaredMethod("finalize");
			clone = Object.class.getDeclaredMethod("clone");
			getClass = Object.class.getMethod("getClass");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Could not find Object methods!", e);
		}
		DEFAULT_EQUALS = new MethodInterpreter<Object, Boolean>(null, equals) {
			@Override
			public Boolean invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				if (proxy == args[0])
					return true;
				else if (args[0] == null || !Proxy.isProxyClass(args[0].getClass()))
					return false;
				InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
				if (!(handler instanceof EntityReflector.ProxyMethodHandler))
					return false;
				EntityReflector<?>.ProxyMethodHandler p = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
				EntityReflector<?>.ProxyMethodHandler other = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(args[0]);
				if (p.getReflector() != other.getReflector())
					return false;
				else if (p.getReflector().getIdFields().isEmpty())
					return false;
				for (int id : p.getReflector().getIdFields()) {
					if (!Objects.equals(fieldGetter.apply(id), other.theFieldGetter.apply(id)))
						return false;
				}
				return true;
			}
		};
		DEFAULT_HASH_CODE = new MethodInterpreter<Object, Integer>(null, hashCode) {
			@Override
			public Integer invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				EntityReflector<?>.ProxyMethodHandler p = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
				if (p.getReflector().getIdFields().isEmpty())
					return System.identityHashCode(proxy);
				Object[] idValues = new Object[p.getReflector().getIdFields().size()];
				int i = 0;
				for (int id : p.getReflector().getIdFields()) {
					idValues[i++] = fieldGetter.apply(id);
				}
				return Objects.hash(idValues);
			}
		};
		DEFAULT_TO_STRING = new MethodInterpreter<Object, String>(null, toString) {
			@Override
			public String invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				EntityReflector<?>.ProxyMethodHandler p = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
				StringBuilder str = new StringBuilder(p.getReflector().theRawType.getSimpleName()).append(": ");
				for (int i = 0; i < p.getReflector().theFields.keySet().size(); i++) {
					if (i > 0)
						str.append(", ");
					str.append(p.getReflector().theFields.keySet().get(i)).append("=").append(fieldGetter.apply(i));
				}
				return str.toString();
			}
		};
		DEFAULT_NOTIFY = new MethodInterpreter<Object, Void>(null, notify) {
			@Override
			public Void invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				Proxy.getInvocationHandler(proxy).notify();
				return null;
			}
		};
		DEFAULT_NOTIFY_ALL = new MethodInterpreter<Object, Void>(null, notifyAll) {
			@Override
			public Void invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				Proxy.getInvocationHandler(proxy).notifyAll();
				return null;
			}
		};
		DEFAULT_WAIT = new MethodInterpreter<Object, Void>(null, wait) {
			@Override
			public Void invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
				throws InterruptedException {
				InvocationHandler handler = Proxy.getInvocationHandler(proxy);
				if (args.length == 0)
					handler.wait();
				else if (args.length == 1)
					handler.wait((Long) args[0]);
				else
					handler.wait((Long) args[0], (Integer) args[1]);
				return null;
			}
		};
		DEFAULT_FINALIZE = new MethodInterpreter<Object, Void>(null, finalize) {
			@Override
			public Void invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				return null;
			}
		};
		DEFAULT_CLONE = new MethodInterpreter<Object, Object>(null, clone) {
			@Override
			public Object invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
				throws CloneNotSupportedException {
				throw new CloneNotSupportedException("Cannot clone proxies");
			}
		};
		DEFAULT_GET_CLASS = new MethodInterpreter<Object, Class<?>>(null, getClass) {
			@Override
			public Class<?> invoke(Object proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
				EntityReflector<?>.ProxyMethodHandler p = (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
				return p.getReflector().theRawType;
			}
		};
		NavigableSet<MethodInterpreter<Object, ?>> defaultObjectMethods = new TreeSet<>(MethodInterpreter::compareTo);
		defaultObjectMethods.addAll(Arrays.asList(DEFAULT_EQUALS, DEFAULT_HASH_CODE, DEFAULT_TO_STRING, //
			DEFAULT_NOTIFY, DEFAULT_NOTIFY_ALL, DEFAULT_WAIT, DEFAULT_FINALIZE, DEFAULT_CLONE, DEFAULT_GET_CLASS));
		DEFAULT_OBJECT_METHODS = Collections.unmodifiableNavigableSet(new TreeSet<>(defaultObjectMethods));
	}

	private final TypeToken<E> theType;
	private final Class<?> theRawType;
	private final Function<Method, String> theGetterFilter;
	private final Function<Method, String> theSetterFilter;
	private final QuickMap<String, ReflectedField<? super E, ?>> theFields;
	private final BetterSortedSet<MethodInterpreter<? super E, ?>> theMethods;
	private final Set<Integer> theIdFields;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	private EntityReflector(TypeToken<E> type, Class<E> raw, Function<Method, String> getterFilter, Function<Method, String> setterFilter,
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, Set<String> idFieldNames,
			List<EntityReflectionMessage> messages) {
		theType = type;
		theRawType = TypeTokens.getRawType(theType);
		theGetterFilter = getterFilter == null ? new PrefixFilter("get", 0) : getterFilter;
		theSetterFilter = setterFilter == null ? new PrefixFilter("set", 1) : setterFilter;

		// First, find all the fields by their getters
		Map<String, Method> fieldGetters = new LinkedHashMap<>();
		findFields(type, getterFilter, fieldGetters, customMethods == null ? Collections.emptySet() : customMethods.keySet());
		QuickMap<String, ReflectedField<? super E, ?>> fields = QuickSet.of(fieldGetters.keySet()).createMap();
		Set<Integer> idFields = new LinkedHashSet<>();
		// Find ID fields
		if (idFieldNames != null) {
			for (String id : idFieldNames) {
				int i = fields.keyIndexTolerant(id);
				if (i < 0)
					throw new IllegalArgumentException("No such field for ID: " + id);
				idFields.add(i);
			}
		} else { // Not specified, see if we can find the IDs ourselves
			// First, see if any fields are tagged with @Id
			for (Map.Entry<String, Method> getter : fieldGetters.entrySet()) {
				for (Annotation annotation : getter.getValue().getAnnotations()) {
					if (annotation.annotationType().getSimpleName().equalsIgnoreCase("id")) // Any old ID annotation will do
						idFields.add(fields.keyIndex(getter.getKey()));
				}
			}
			if (idFields.isEmpty()) {
				// Otherwise, see if there is a field named ID
				for (String field : fieldGetters.keySet()) {
					if (field.equalsIgnoreCase("id")) {
						idFields.add(fields.keyIndex(field));
						break;
					}
				}
			}
		}
		BetterSortedSet<MethodInterpreter<? super E, ?>> methods = new BetterTreeSet<>(false, MethodInterpreter::compareTo);
		methods.addAll(DEFAULT_OBJECT_METHODS);
		for (int i = 0; i < fields.keySize(); i++) {
			String fieldName = fields.keySet().get(i);
			Method getter = fieldGetters.get(fieldName);
			fields.put(i, new ReflectedField<>(this, fields.keySet().get(i), i, idFields.contains(i), getter));
			methods.add(fields.get(i).getGetter());
		}
		theIdFields = idFields.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(idFields);

		populateMethods(this, theType, theGetterFilter, theSetterFilter, fields, methods, //
			customMethods == null ? Collections.emptyMap() : customMethods, messages);
		theFields = fields.unmodifiable();
		theMethods = BetterCollections.unmodifiableSortedSet(methods);

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { raw }, theProxyHandler);
	}

	private static <T> void findFields(TypeToken<T> type, Function<Method, String> getterFilter, Map<String, Method> fieldGetters,
		Set<Method> customMethods) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null || clazz == Object.class)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			if (customMethods.contains(m) || m.isDefault()) {
				continue;
			}
			String fieldName = getterFilter.apply(m);
			if (fieldName != null) {
				fieldGetters.put(fieldName, m);
			}
		}
		for (Class<?> intf : clazz.getInterfaces())
			findFields((TypeToken<T>) type.resolveType(intf), // Generics hack
				getterFilter, fieldGetters, customMethods);
	}

	private static <T> void populateMethods(EntityReflector<T> reflector, TypeToken<T> type, //
		Function<Method, String> getterFilter, Function<Method, String> setterFilter, //
		QuickMap<String, ReflectedField<? super T, ?>> fields, BetterSortedSet<MethodInterpreter<? super T, ?>> methods, //
		Map<Method, ? extends BiFunction<? super T, Object[], ?>> customMethods, List<EntityReflectionMessage> errors) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			BiFunction<? super T, Object[], ?> custom = customMethods.get(m);
			if (custom != null) {
				methods.add(new CustomMethod<>(reflector, m, custom));
				continue;
			} else if (m.isDefault()) {
				MethodHandle handle;
				if (LOOKUP_CONSTRUCTOR == null) {
					handle = null;
				} else {
					try {
						handle = LOOKUP_CONSTRUCTOR.newInstance(clazz).in(clazz).unreflectSpecial(m, clazz);
					} catch (IllegalArgumentException | InstantiationException | InvocationTargetException e) {
						throw new IllegalStateException("Bad method? " + m + ": " + e);
					} catch (SecurityException | IllegalAccessException e) {
						errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m, "No access to " + m + ": " + e));
						continue;
					}
				}
				methods.add(new DefaultMethod<>(reflector, m, handle));
				continue;
			}
			String fieldName = getterFilter.apply(m);
			if (fieldName != null) {
				continue; // Already added
			}
			fieldName = setterFilter.apply(m);
			if (fieldName != null) {
				ReflectedField<? super T, ?> field = fields.get(fieldName);
				if (field == null) {
					errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
						"No getter found for setter " + m + " of field " + fieldName));
					continue;
				}
				if (!m.getParameterTypes()[0].isAssignableFrom(TypeTokens.getRawType(field.getGetter().getReturnType()))) {
					errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
						"Setter " + m + " for field " + field + " must accept a " + field.getGetter().getReturnType()));
					continue;
				}
				SetterReturnType setterReturnType = null;
				Class<?> setterRT = m.getReturnType();
				if (setterRT == void.class || setterRT == Void.class)
					setterReturnType = SetterReturnType.VOID;
				else if (setterRT.isAssignableFrom(clazz))
					setterReturnType = SetterReturnType.SELF;
				else if (setterRT.isAssignableFrom(TypeTokens.getRawType(field.getGetter().getReturnType())))
					setterReturnType = SetterReturnType.OLD_VALUE;
				else {
					errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
						"Return type of setter " + m + " cannot be satisfied for this class"));
					continue;
				}
				methods.add(new FieldSetter<>(reflector, m, (ReflectedField<T, ?>) field, setterReturnType));
				continue;
			}
			if (clazz != Object.class) {
				errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
					"Method " + m + " is not default or a recognized getter or setter, and its implementation is not provided"));
			}
		}
		for (Class<?> intf : clazz.getInterfaces())
			populateMethods(reflector, (TypeToken<T>) type.resolveType(intf), // Generics hack
				getterFilter, setterFilter, //
				fields, methods, customMethods, errors);
	}

	/** @return The interface entity type */
	public TypeToken<E> getType() {
		return theType;
	}

	/** @return This entity's fields */
	public QuickMap<String, ReflectedField<? super E, ?>> getFields() {
		return theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theIdFields;
	}

	public MethodInterpreter<? super E, ?> getInterpreter(Method method) {
		return theMethods.searchValue(m -> -m.compare(method), SortedSearchFilter.OnlyMatch);
	}

	public <R> MethodInterpreter<? super E, R> getInterpreter(Function<? super E, R> method) throws IllegalArgumentException {
		Method invoked;
		synchronized (this) {
			theProxyHandler.reset();
			method.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
		}
		if (invoked == null)
			throw new IllegalArgumentException("No " + theType + " method invoked");
		MethodInterpreter<? super E, ?> found = getInterpreter(invoked);
		if (found == null)
			throw new IllegalStateException("Method " + invoked + " not found");
		return (MethodInterpreter<? super E, R>) found;
	}

	public <F> ReflectedField<? super E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException {
		MethodInterpreter<? super E, F> method = getInterpreter(fieldGetter);
		if (!(method instanceof FieldGetter))
			throw new IllegalArgumentException(method + " is not a field getter");
		return ((FieldGetter<? super E, F>) method).getField();
	}

	/**
	 * @param fieldGetter A function invoking a field getter on this type
	 * @return The index in {@link #getFields()} of the field invoked. If the function invokes multiple methods, this will be last method
	 *         invoked
	 * @throws IllegalArgumentException If the function does not invoke a field getter or the method the function invokes last is not an
	 *         included getter method
	 */
	public int getFieldIndex(Function<? super E, ?> fieldGetter) throws IllegalArgumentException {
		return getField(fieldGetter).getFieldIndex();
	}

	/**
	 * Creates a new instance of the type, backed by the given getter/setter implementations. The integer inputs to both functions are the
	 * {@link ReflectedField#getFieldIndex()}, which is also the index of the field in {@link #getFields()}.
	 *
	 * @param fieldGetter The field getter implementation for the new instance
	 * @param fieldSetter The field setter implementation for the new instance
	 * @return The new entity instance
	 */
	public E newInstance(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
		Class<E> rawType = TypeTokens.getRawType(theType);
		return (E) Proxy.newProxyInstance(rawType.getClassLoader(), new Class[] { rawType },
			new ProxyMethodHandler(fieldGetter, fieldSetter));
	}

	/**
	 * Allows the association of an entity with a custom piece of information (retrievable via {@link #getAssociated(Object, Object)})
	 *
	 * @param proxy The entity created with {@link #newInstance(IntFunction, BiConsumer)}
	 * @param key The key to associate the data with
	 * @param associated The data to associate with the entity for the key
	 * @return The entity
	 */
	public E associate(E proxy, Object key, Object associated) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		handler.associate(key, associated);
		return proxy;
	}

	/**
	 * @param proxy The entity created with {@link #newInstance(IntFunction, BiConsumer)}
	 * @param key The key the data is associated with
	 * @return The data {@link #associate(Object, Object, Object) associated} with the entity and key
	 */
	public Object getAssociated(E proxy, Object key) {
		return ((ProxyMethodHandler) Proxy.getInvocationHandler(proxy)).getAssociated(key);
	}

	public Object getField(E proxy, int fieldIndex) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		return handler.theFieldGetter.apply(fieldIndex);
	}

	public void setField(E proxy, int fieldIndex, Object value) {
		ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
		handler.theFieldSetter.accept(fieldIndex, value);
	}

	private class ProxyMethodHandler implements InvocationHandler {
		private final IntFunction<Object> theFieldGetter;
		private final BiConsumer<Integer, Object> theFieldSetter;
		private IdentityHashMap<Object, Object> theAssociated;

		ProxyMethodHandler(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			theFieldGetter = fieldGetter;
			theFieldSetter = fieldSetter;
		}

		EntityReflector<E> getReflector() {
			return EntityReflector.this;
		}

		Object associate(Object key, Object value) {
			if (theAssociated == null)
				theAssociated = new IdentityHashMap<>();
			return theAssociated.put(key, value);
		}

		Object getAssociated(Object key) {
			return theAssociated == null ? null : theAssociated.get(key);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			MethodInterpreter<? super E, ?> interpreter = getInterpreter(method);
			if (interpreter == null)
				throw new IllegalStateException("Method " + method + " not found!");
			return interpreter.invoke((E) proxy, args, theFieldGetter, theFieldSetter);
		}
	}
}
