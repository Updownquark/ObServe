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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.config.Cached;
import org.observe.entity.impl.ObservableEntityUtils;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
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
		private Map<TypeToken<?>, EntityReflector<?>> theSupers;
		private Function<Method, String> theGetterFilter;
		private Function<Method, String> theSetterFilter;
		private Map<Method, BiFunction<? super E, Object[], ?>> theCustomMethods;
		private Set<String> theIdFields;
		private List<EntityReflectionMessage> theMessages;

		private MethodRetrievingHandler theProxyHandler;
		private E theProxy;

		private Builder(TypeToken<E> type) {
			theRawType = TypeTokens.getRawType(type);
			if (theRawType == null || !theRawType.isInterface())
				throw new IllegalArgumentException("This class only works for interface types");
			else if ((theRawType.getModifiers() & Modifier.PUBLIC) == 0)
				throw new IllegalArgumentException("This class only works for public interface types");
			theType = type;
			theSupers = new LinkedHashMap<>();
			theGetterFilter = new OrFilter(//
				new PrefixFilter("get", 0), new PrefixFilter("is", 0));
			theSetterFilter = new PrefixFilter("set", 1);
			theMessages = new ArrayList<>();
		}

		public Builder<E> withSupers(Map<TypeToken<?>, EntityReflector<?>> supers) {
			theSupers = supers;
			return this;
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

		public <R> Builder<E> withCustomMethod(Function<? super E, R> method, BiFunction<? super E, Object[], R> implementation) {
			if (theProxyHandler == null) {
				theProxyHandler = new MethodRetrievingHandler();
				theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { theRawType },
					theProxyHandler);
			}
			Method m;
			synchronized (this) {
				theProxyHandler.reset(null, true);
				method.apply(theProxy);
				m = theProxyHandler.getInvoked();
			}
			if (m == null)
				throw new IllegalArgumentException("No " + theType + " method invoked");
			return withCustomMethod(m, implementation);
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
			return new EntityReflector<E>(theSupers, theType, theRawType, theGetterFilter, theSetterFilter, //
				theCustomMethods == null ? Collections.emptyMap() : theCustomMethods, theIdFields, theMessages);
		}

		/**
		 * @return The messages that occurred describing the results of the entity reflection after {@link #build()} or
		 *         {@link #buildNoPrint()}
		 */
		public List<EntityReflectionMessage> getMessages() {
			return theMessages;
		}
	}

	public enum EntityReflectionMessageLevel {
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

	public static class OrFilter implements Function<Method, String> {
		private final Function<Method, String>[] theComponents;

		public OrFilter(Function<Method, String>... components) {
			theComponents = components;
		}

		@Override
		public String apply(Method m) {
			for (Function<Method, String> component : theComponents) {
				String fieldName = component.apply(m);
				if (fieldName != null)
					return fieldName;
			}
			return null;
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

	static final Object[] NO_ARGS = new Object[0];

	/**
	 * Represents a field in an interface entity type
	 *
	 * @param <E> The entity type
	 * @param <F> The field type
	 */
	public static class ReflectedField<E, F> {
		private final EntityReflector<E> theReflector;
		private final String theName;
		private int theFieldIndex;
		private boolean id;
		private final FieldGetter<E, F> theGetter;
		private FieldSetter<E, F> theSetter;

		ReflectedField(EntityReflector<E> reflector, String name, int fieldIndex, boolean id, Method getter) {
			theReflector = reflector;
			theName = name;
			theFieldIndex = fieldIndex;
			this.id = id;
			theGetter = new FieldGetter<>(reflector, getter, this);
		}

		void setSetter(FieldSetter<E, F> setter) {
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

		public TypeToken<F> getType() {
			return theGetter.getReturnType();
		}

		public boolean isId() {
			return id;
		}

		/** @return The entity type's getter method for this field */
		public FieldGetter<E, F> getGetter() {
			return theGetter;
		}

		/** @return The entity type's setter method for this field */
		public FieldSetter<E, F> getSetter() {
			return theSetter;
		}

		public F get(E entity) {
			EntityReflector<E>.ProxyMethodHandler p = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(entity);
			try {
				return p.invokeOnType(entity, theGetter, NO_ARGS);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new IllegalStateException(e);
			}
		}

		public void set(E entity, F value) {
			if (theSetter == null)
				throw new UnsupportedOperationException("No setter found for field " + theReflector.getType() + "." + theName);
			EntityReflector<E>.ProxyMethodHandler p = (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(entity);
			try {
				p.invokeOnType(entity, theSetter, new Object[] { value });
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public String toString() {
			return theReflector.getType() + "." + theName + " (" + theGetter.getReturnType() + ")";
		}
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

	static class SuperPath {
		final int index;
		final SuperPath parent;

		SuperPath(int index, SuperPath parent) {
			this.index = index;
			this.parent = parent;
		}
	}

	public static abstract class MethodInterpreter<E, R> implements Comparable<MethodInterpreter<?, ?>> {
		private final EntityReflector<E> theReflector;
		private ElementId theMethodElement;
		private final Method theMethod;
		protected MethodInterpreter<? super E, ? super R>[] theSuperMethods;
		private final Class<?>[] theParameters;
		private final Invokable<E, R> theInvokable;

		MethodInterpreter(EntityReflector<E> reflector, Method method) {
			theReflector = reflector;
			theMethod = method;
			theSuperMethods = new MethodInterpreter[(reflector == null || reflector.theSupers.isEmpty()) ? 0 : reflector.theSupers.size()];
			theParameters = method.getParameterTypes();
			TypeToken<?> type;
			if (reflector != null)
				type = reflector.getType();
			else
				type = TypeTokens.get().OBJECT;
			theInvokable = (Invokable<E, R>) type.method(method);
		}

		void setElement(ElementId methodElement) {
			theMethodElement = methodElement;
		}

		void setSuper(int superIndex, MethodInterpreter<? super E, ? super R> superMethod) {
			theSuperMethods[superIndex] = superMethod;
		}

		public EntityReflector<E> getReflector() {
			return theReflector;
		}

		public ElementId getMethodElement() {
			return theMethodElement;
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

		public TypeToken<R> getReturnType() {
			return (TypeToken<R>) theInvokable.getReturnType();
		}

		protected R invoke(E proxy, SuperPath path, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable {
			if (path == null)
				return invokeLocal(proxy, args, fieldGetter, fieldSetter);
			return (R) theSuperMethods[path.index].invoke(proxy, path.parent, args, fieldIdx -> {
				if (this instanceof FieldGetter && ((FieldGetter<E, R>) this).getField().getFieldIndex() == fieldIdx)
					return fieldGetter.apply(fieldIdx);
				try {
					return getReflector().theSuperFieldGetters.get(path.index).get(fieldIdx).invoke(proxy, null, NO_ARGS,
						fieldGetter, fieldSetter);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			}, (fieldIdx, value) -> {
				if (this instanceof FieldSetter && ((FieldSetter<E, ?>) this).getField().getFieldIndex() == fieldIdx) {
					fieldSetter.accept(fieldIdx, args[0]);
					return;
				}
				try {
					getReflector().theSuperFieldSetters.get(path.index).get(fieldIdx).invoke(proxy, null, new Object[] { value },
						fieldGetter, fieldSetter);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			});
		}

		protected abstract R invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable;

		@Override
		public int compareTo(MethodInterpreter<?, ?> o) {
			if (this == o)
				return 0;
			return compare(o.theMethod.getName(), o.theParameters);
		}

		public int compare(Method method) {
			if (theMethod == method)
				return 0;
			return compare(method.getName(), method.getParameterTypes());
		}

		public int compare(MethodSignature sig) {
			return compare(sig.name, sig.parameters);
		}

		private int compare(String methodName, Class<?>[] parameters) {
			int comp = StringUtils.compareNumberTolerant(theMethod.getName(), methodName, true, true);
			if (comp != 0)
				return comp;
			comp = Integer.compare(theParameters.length, parameters.length);
			if (comp != 0)
				return comp;
			int i;
			for (i = 0; i < theParameters.length && i < parameters.length; i++) {
				if (theParameters[i].equals(parameters[i]))
					continue;
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
			StringBuilder str = new StringBuilder().append(theReflector.getType()).append('.').append(theMethod.getName()).append('(');
			StringUtils.conversational(", ", null).print(str, theParameters, (s, p) -> s.append(p.getName()));
			str.append(')');
			return str.toString();
		}
	}

	public static abstract class FieldRelatedMethod<E, F, R> extends MethodInterpreter<E, R> {
		private final ReflectedField<E, F> theField;

		FieldRelatedMethod(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field) {
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
		protected F invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			return (F) fieldGetter.apply(getField().getFieldIndex());
		}
	}

	public static class CachedFieldGetter<E, F> extends FieldGetter<E, F> {
		private final MethodHandle theHandle;

		public CachedFieldGetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, MethodHandle handle) {
			super(reflector, method, field);
			theHandle = handle;
		}

		@Override
		protected F invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			if (theHandle == null)
				throw new IllegalStateException("Unable to reflectively invoke default method " + getInvokable());
			Object value = fieldGetter.apply(getField().getFieldIndex());
			if (value == null) {
				value = super.invokeLocal(proxy, args, fieldGetter, fieldSetter);
				if (value != null)
					fieldSetter.accept(getField().getFieldIndex(), value);
			}
			return (F) value;
		}
	}

	public static class FieldSetter<E, F> extends FieldRelatedMethod<E, F, Object> {
		private final SetterReturnType theReturnType;

		FieldSetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, SetterReturnType returnType) {
			super(reflector, method, field);
			theReturnType = returnType;
			field.setSetter(this);
		}

		public SetterReturnType getSetterReturnType() {
			return theReturnType;
		}

		@Override
		protected Object invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
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

	public enum SetterReturnType {
		OLD_VALUE, SELF, VOID;
	}

	public static class DefaultMethod<E, R> extends MethodInterpreter<E, R> {
		private final MethodHandle theHandle;

		DefaultMethod(EntityReflector<E> reflector, Method method, MethodHandle handle) {
			super(reflector, method);
			theHandle = handle;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable {
			if (theHandle == null)
				throw new IllegalStateException("Unable to reflectively invoke default method " + getInvokable());
			synchronized (theHandle) {
				return (R) theHandle.bindTo(proxy).invokeWithArguments(args);
			}
		}
	}

	public static class CustomMethod<E, R> extends MethodInterpreter<E, R> {
		private BiFunction<? super E, Object[], R> theInterpreter;

		CustomMethod(EntityReflector<E> reflector, Method method, BiFunction<? super E, Object[], R> interpreter) {
			super(reflector, method);
			theInterpreter = interpreter;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
			return theInterpreter.apply(proxy, args);
		}
	}

	public static class ObjectMethodWrapper<E, R> extends MethodInterpreter<E, R> {
		private final MethodInterpreter<Object, R> theObjectMethod;

		ObjectMethodWrapper(EntityReflector<E> reflector, MethodInterpreter<Object, R> objectMethod) {
			super(reflector, objectMethod.getMethod());
			theObjectMethod = objectMethod;
		}

		public MethodInterpreter<Object, R> getObjectMethod() {
			return theObjectMethod;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable {
			return theObjectMethod.invokeLocal(proxy, args, fieldGetter, fieldSetter);
		}
	}

	public static class SuperDelegateMethod<E, R> extends MethodInterpreter<E, R> {
		public SuperDelegateMethod(EntityReflector<E> reflector, Method method) {
			super(reflector, method);
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter)
			throws Throwable {
			throw new IllegalStateException("No super method set");
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
	public static final Map<Method, MethodInterpreter<Object, ?>> DEFAULT_OBJECT_METHODS;

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
		Map<Method, MethodInterpreter<Object, ?>> defaultObjectMethods = new LinkedHashMap<>();
		DEFAULT_EQUALS = new MethodInterpreter<Object, Boolean>(null, equals) {
			@Override
			protected Boolean invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				if (proxy == args[0])
					return true;
				else if (args[0] == null || !Proxy.isProxyClass(args[0].getClass()))
					return false;
				InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
				if (!(handler instanceof EntityReflector.ProxyMethodHandler))
					return false;
				EntityReflector<?>.ProxyMethodHandler p = getHandler(proxy);
				EntityReflector<?>.ProxyMethodHandler other = (EntityReflector<?>.ProxyMethodHandler) handler;
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
		defaultObjectMethods.put(equals, DEFAULT_EQUALS);
		DEFAULT_HASH_CODE = new MethodInterpreter<Object, Integer>(null, hashCode) {
			@Override
			protected Integer invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				EntityReflector<?>.ProxyMethodHandler p = getHandler(proxy);
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
		defaultObjectMethods.put(hashCode, DEFAULT_HASH_CODE);
		DEFAULT_TO_STRING = new MethodInterpreter<Object, String>(null, toString) {
			@Override
			protected String invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				EntityReflector<?>.ProxyMethodHandler p = getHandler(proxy);
				StringBuilder str = new StringBuilder(p.getReflector().theRawType.getSimpleName()).append(": ");
				for (int i = 0; i < p.getReflector().theFields.keySet().size(); i++) {
					if (i > 0)
						str.append(", ");
					str.append(p.getReflector().theFields.keySet().get(i)).append("=")
						.append(((EntityReflector<Object>) p.getReflector()).getFields().get(i).get(proxy));
				}
				return str.toString();
			}
		};
		defaultObjectMethods.put(toString, DEFAULT_TO_STRING);
		DEFAULT_NOTIFY = new MethodInterpreter<Object, Void>(null, notify) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				Proxy.getInvocationHandler(proxy).notify();
				return null;
			}
		};
		defaultObjectMethods.put(notify, DEFAULT_NOTIFY);
		DEFAULT_NOTIFY_ALL = new MethodInterpreter<Object, Void>(null, notifyAll) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				Proxy.getInvocationHandler(proxy).notifyAll();
				return null;
			}
		};
		defaultObjectMethods.put(notifyAll, DEFAULT_NOTIFY_ALL);
		DEFAULT_WAIT = new MethodInterpreter<Object, Void>(null, wait) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) throws InterruptedException {
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
		defaultObjectMethods.put(wait, DEFAULT_WAIT);
		DEFAULT_FINALIZE = new MethodInterpreter<Object, Void>(null, finalize) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				return null;
			}
		};
		defaultObjectMethods.put(finalize, DEFAULT_FINALIZE);
		DEFAULT_CLONE = new MethodInterpreter<Object, Object>(null, clone) {
			@Override
			protected Object invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) throws CloneNotSupportedException {
				throw new CloneNotSupportedException("Cannot clone proxies");
			}
		};
		defaultObjectMethods.put(clone, DEFAULT_CLONE);
		DEFAULT_GET_CLASS = new MethodInterpreter<Object, Class<?>>(null, getClass) {
			@Override
			protected Class<?> invokeLocal(Object proxy, Object[] args, IntFunction<Object> fieldGetter,
				BiConsumer<Integer, Object> fieldSetter) {
				return getHandler(proxy).getReflector().theRawType;
			}
		};
		defaultObjectMethods.put(getClass, DEFAULT_GET_CLASS);
		DEFAULT_OBJECT_METHODS = Collections.unmodifiableMap(defaultObjectMethods);
	}

	private final List<EntityReflector<? super E>> theSupers;
	private final List<Map<ElementId, MethodInterpreter<E, ?>>> theSuperMethodMappings;
	final List<List<MethodInterpreter<E, ?>>> theSuperFieldGetters;
	final List<List<MethodInterpreter<E, ?>>> theSuperFieldSetters;
	private final Map<Class<?>, SuperPath> theSuperPaths;
	private final TypeToken<E> theType;
	private final Class<?> theRawType;
	private final Function<Method, String> theGetterFilter;
	private final Function<Method, String> theSetterFilter;
	private final QuickMap<String, ReflectedField<E, ?>> theFields;
	private final BetterSortedSet<MethodInterpreter<E, ?>> theMethods;
	private final Set<Integer> theIdFields;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;

	private EntityReflector(Map<TypeToken<?>, EntityReflector<?>> supers, TypeToken<E> type, Class<E> raw,
		Function<Method, String> getterFilter, Function<Method, String> setterFilter,
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, Set<String> idFieldNames,
			List<EntityReflectionMessage> messages) {
		theSupers = (List<EntityReflector<? super E>>) (List<?>) QommonsUtils.map(Arrays.asList(raw.getGenericInterfaces()), intf -> {
			TypeToken<? super E> intfT = (TypeToken<? super E>) type.resolveType(intf);
			EntityReflector<?> superR = supers.get(intfT);
			if (superR == null) {
				superR = new EntityReflector<>(supers, (TypeToken<E>) intfT, (Class<E>) intf, // Generics hack
					getterFilter, setterFilter, customMethods, idFieldNames, messages);
				if (supers.putIfAbsent(intfT, superR) != null)
					superR = supers.get(intfT);
			}
			return superR;
		}, true);
		theSuperMethodMappings = QommonsUtils.map(theSupers, r -> new LinkedHashMap<>(), true);
		theSuperFieldGetters = QommonsUtils.map(theSupers, r -> new ArrayList<>(r.getFields().keySize()), true);
		theSuperFieldSetters = QommonsUtils.map(theSupers, r -> new ArrayList<>(r.getFields().keySize()), true);
		theType = type;
		theRawType = TypeTokens.getRawType(theType);
		theGetterFilter = getterFilter == null ? new OrFilter(new PrefixFilter("get", 0), new PrefixFilter("is", 0)) : getterFilter;
		theSetterFilter = setterFilter == null ? new PrefixFilter("set", 1) : setterFilter;

		// First, find all the fields by their getters
		Map<String, Method> fieldGetters = new LinkedHashMap<>();
		findFields(type, getterFilter, fieldGetters, customMethods == null ? Collections.emptySet() : customMethods.keySet(),
			new HashSet<>());
		QuickMap<String, ReflectedField<E, ?>> fields = QuickSet.of(fieldGetters.keySet()).createMap();
		Set<Integer> idFields = new LinkedHashSet<>();
		// Find ID fields
		if (!theSupers.isEmpty()) {
			// If any of the super types are identified, all of its fields must either be present in all other identified super types
			// (the types are not checked since this should be done by the compiler)
			// or the field needs to be defaulted or customized in the sub type as either a constant or a function of other ID fields
			// At the moment I don't check that last constraint--only that the field getter is defaulted or customized
			for (int i = 0; i < theSupers.size(); i++) {
				if (theSupers.get(i).getIdFields().isEmpty())
					continue;
				for (int id : theSupers.get(i).getIdFields()) {
					ReflectedField<? super E, ?> fieldI = theSupers.get(i).getFields().get(id);
					int fieldIdx = fields.keyIndexTolerant(fieldI.getName());
					for (int j = 0; j < theSupers.size(); j++) {
						if (i == j)
							continue;
						if (theSupers.get(j).getIdFields().isEmpty())
							continue;
						ReflectedField<? super E, ?> fieldJ = theSupers.get(j).getFields().getIfPresent(fieldI.getName());
						if (fieldJ == null) {
							if (fieldIdx >= 0)
								throw new IllegalArgumentException("ID signatures of super types" + theSupers.get(i) + " and "
									+ theSupers.get(j) + " for sub-type " + theType + " do not match: field " + fieldI
									+ " is not present in " + theSupers.get(j) + " and must be defaulted or customized in " + this);
						}
					}
					if (fieldIdx >= 0)
						idFields.add(fieldIdx);
				}
			}
		}
		if (!idFields.isEmpty()) {
			// Inherited from super type(s)
		} else if (idFieldNames != null) {
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
		BetterSortedSet<MethodInterpreter<E, ?>> methods = new BetterTreeSet<>(false, MethodInterpreter::compareTo);
		for (int i = 0; i < fields.keySize(); i++) {
			String fieldName = fields.keySet().get(i);
			Method getter = fieldGetters.get(fieldName);
			fields.put(i, new ReflectedField<>(this, fields.keySet().get(i), i, idFields.contains(i), getter));
			fields.get(i).getGetter().setElement(methods.addElement(fields.get(i).getGetter(), false).getElementId());
		}
		theIdFields = idFields.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(idFields);

		Map<Class<?>, SuperPath> superPaths = new HashMap<>();
		populateMethods(theType, superPaths, fields, methods, //
			customMethods == null ? Collections.emptyMap() : customMethods, messages);
		theSuperPaths = Collections.unmodifiableMap(superPaths);
		theFields = fields.unmodifiable();
		theMethods = BetterCollections.unmodifiableSortedSet(methods);

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(ObservableEntityUtils.class.getClassLoader(), new Class[] { raw }, theProxyHandler);
	}

	private static <T> void findFields(TypeToken<T> type, Function<Method, String> getterFilter, Map<String, Method> fieldGetters,
		Set<Method> customMethods, Set<String> fieldOverrides) {
		Class<T> clazz = TypeTokens.getRawType(type);
		if (clazz == null || clazz == Object.class)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			if (m.isSynthetic())
				continue;
			String fieldName = getterFilter.apply(m);
			if (fieldName != null) {
				if (fieldOverrides.contains(fieldName))
					continue;
				else if (customMethods.contains(m)) {
					fieldOverrides.add(fieldName);
					continue;
				} else if (m.isDefault() && m.getAnnotation(Cached.class) == null) {
					fieldOverrides.add(fieldName);
					continue;
				}
				fieldGetters.put(fieldName, m);
			}
		}
		for (Class<?> intf : clazz.getInterfaces())
			findFields((TypeToken<T>) type.resolveType(intf), // Generics hack
				getterFilter, fieldGetters, customMethods, fieldOverrides);
	}

	private void populateMethods(TypeToken<E> type, Map<Class<?>, SuperPath> superPaths, //
		QuickMap<String, ReflectedField<E, ?>> fields, BetterSortedSet<MethodInterpreter<E, ?>> methods, //
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, List<EntityReflectionMessage> errors) {
		Class<E> clazz = TypeTokens.getRawType(type);
		if (clazz == null)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			if (m.isSynthetic())
				continue;
			if (methods.search(method -> -method.compare(m), BetterSortedList.SortedSearchFilter.OnlyMatch) != null)
				continue; // Overridden by a subclass and handled
			BiFunction<? super E, Object[], ?> custom = customMethods.get(m);
			if (custom != null) {
				CustomMethod<E, ?> method = new CustomMethod<>(this, m, custom);
				method.setElement(methods.addElement(method, false).getElementId());
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
				if (m.getAnnotation(Cached.class) != null) {
					String fieldName = theGetterFilter.apply(m);
					ReflectedField<? super E, ?> field = fieldName == null ? null : fields.get(fieldName);
					if (field != null) {
						CachedFieldGetter<E, ?> method = new CachedFieldGetter<>(this, m, (ReflectedField<E, ?>) field, handle);
						method.setElement(methods.addElement(method, false).getElementId());
						continue;
					} else
						errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, m,
							"Default method is marked as Cached, but is not a field"));
				}
				DefaultMethod<E, ?> method = new DefaultMethod<>(this, m, handle);
				method.setElement(methods.addElement(method, false).getElementId());
				continue;
			}
			MethodInterpreter<Object, ?> objectMethod = DEFAULT_OBJECT_METHODS.get(m);
			if (objectMethod != null) {
				MethodInterpreter<E, ?> method = new ObjectMethodWrapper<>(this, objectMethod);
				method.setElement(methods.addElement(method, false).getElementId());
				continue;
			}
			String fieldName = theGetterFilter.apply(m);
			if (fieldName != null)
				continue; // Already added
			fieldName = theSetterFilter.apply(m);
			if (fieldName != null) {
				ReflectedField<? super E, ?> field = fields.getIfPresent(fieldName);
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
				FieldSetter<E, ?> method = new FieldSetter<>(this, m, (ReflectedField<E, ?>) field, setterReturnType);
				method.setElement(methods.addElement(method, false).getElementId());
				continue;
			}
			if (clazz != Object.class) {
				errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
					"Method " + m + " is not default or a recognized getter or setter, and its implementation is not provided"));
			}
		}
		if (clazz == Object.class) {} else if (theSupers.isEmpty()) {
			populateMethods((TypeToken<E>) TypeTokens.get().OBJECT, superPaths, // Generics hack
				fields, methods, customMethods, errors);
		} else {
			IntList superIndexes = new IntList();
			for (int i = 0; i < theSupers.size(); i++) {
				populateSuperMethods(fields, methods, theSupers.get(i), i, errors);
				superIndexes.add(i);
				populateSuperPaths(superPaths, theSupers.get(i), superIndexes);
				superIndexes.clear();
			}
		}
	}

	private <S> void populateSuperMethods(QuickMap<String, ReflectedField<E, ?>> fields, BetterSortedSet<MethodInterpreter<E, ?>> methods,
		EntityReflector<S> superR, int superIndex, List<EntityReflectionMessage> errors) {
		for (CollectionElement<MethodInterpreter<S, ?>> superMethod : superR.getMethods().elements()) {
			MethodInterpreter<E, ?> subMethod = methods.searchValue(superMethod.get(), BetterSortedList.SortedSearchFilter.OnlyMatch);
			if (subMethod == null) {
				if (superMethod.get() instanceof CachedFieldGetter)
					subMethod = new CachedFieldGetter<>(this, superMethod.get().getMethod(),
						fields.get(((FieldGetter<?, ?>) superMethod.get()).getField().getName()),
						((CachedFieldGetter<E, ?>) superMethod.get()).theHandle);
				else if (superMethod.get() instanceof FieldGetter)
					subMethod = new FieldGetter<>(this, superMethod.get().getMethod(),
						fields.get(((FieldGetter<?, ?>) superMethod.get()).getField().getName()));
				else if (superMethod.get() instanceof FieldSetter)
					subMethod = new FieldSetter<>(this, superMethod.get().getMethod(),
						fields.get(((FieldSetter<?, ?>) superMethod.get()).getField().getName()),
						((FieldSetter<?, ?>) superMethod.get()).getSetterReturnType());
				else
					subMethod = new SuperDelegateMethod<>(this, superMethod.get().getMethod());
				subMethod.setElement(methods.addElement(subMethod, false).getElementId());
			}
			((MethodInterpreter<E, Object>) subMethod).setSuper(superIndex, (MethodInterpreter<? super E, Object>) superMethod.get());
			theSuperMethodMappings.get(superIndex).put(superMethod.getElementId(), subMethod);
		}
		for (ReflectedField<S, ?> field : superR.getFields().allValues()) {
			theSuperFieldGetters.get(superIndex).add(theSuperMethodMappings.get(superIndex).get(field.getGetter().getMethodElement()));
			if (field.getSetter() != null)
				theSuperFieldSetters.get(superIndex).add(theSuperMethodMappings.get(superIndex).get(field.getSetter().getMethodElement()));
		}
	}

	private static void populateSuperPaths(Map<Class<?>, SuperPath> superPaths, EntityReflector<?> superR, IntList path) {
		SuperPath superPath = new SuperPath(path.getLast(), null);
		for (int i = path.size() - 2; i >= 0; i--)
			superPath = new SuperPath(path.get(i), superPath);
		superPaths.putIfAbsent(superR.theRawType, superPath);
		if (!superPaths.containsKey(Object.class) && superR.theSupers.isEmpty())
			superPaths.put(Object.class, superPath);
		for (int i = 0; i < superR.theSupers.size(); i++) {
			path.add(i);
			populateSuperPaths(superPaths, superR.theSupers.get(i), path);
			path.removeLast();
		}
	}

	/** @return The reflectors for each of this reflector's type's super types */
	public List<EntityReflector<? super E>> getSuper() {
		return theSupers;
	}

	/** @return The interface entity type */
	public TypeToken<E> getType() {
		return theType;
	}

	/** @return This entity's fields */
	public QuickMap<String, ReflectedField<E, ?>> getFields() {
		return theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theIdFields;
	}

	/** @return All of this reflector's method interpreters */
	public BetterSortedSet<MethodInterpreter<E, ?>> getMethods() {
		return theMethods;
	}

	/**
	 * @param method A method on this type
	 * @return The interpreter for the given method in this reflector
	 */
	public MethodInterpreter<E, ?> getInterpreter(Method method) {
		if (!method.getDeclaringClass().isAssignableFrom(theRawType))
			throw new IllegalArgumentException("Method " + method + " cannot be applied to " + theType);
		MethodSignature sig = new MethodSignature(method);
		return theMethods.searchValue(m -> -m.compare(sig), BetterSortedList.SortedSearchFilter.OnlyMatch);
	}

	/**
	 * @param method A function invoking a method on this type
	 * @return The interpreter for the method invoked. If the function invokes multiple methods, this will be first method invoked
	 * @throws IllegalArgumentException If the function does not invoke a method on this type
	 */
	public <R> MethodInterpreter<E, R> getInterpreter(Function<? super E, R> method) throws IllegalArgumentException {
		Method invoked;
		synchronized (this) {
			theProxyHandler.reset(null, true);
			method.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
		}
		if (invoked == null)
			throw new IllegalArgumentException("No " + theType + " method invoked");
		MethodInterpreter<? super E, ?> found = getInterpreter(invoked);
		if (found == null)
			throw new IllegalStateException("Method " + invoked + " not found");
		return (MethodInterpreter<E, R>) found;
	}

	/**
	 * @param fieldGetter A function invoking a field getter on this type
	 * @return The field of the getter invoked. If the function invokes multiple field getters, the first will be used
	 * @throws IllegalArgumentException If the function does not invoke a field getter
	 */
	public <F> ReflectedField<E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException {
		Method invoked, first;
		synchronized (this) {
			theProxyHandler.reset(m -> {
				String fieldName = theGetterFilter.apply(m);
				return fieldName != null && theFields.keySet().contains(fieldName);
			}, true);
			fieldGetter.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
			first = theProxyHandler.getFirstNoFilter();
		}
		if (invoked == null) {
			if (first != null)
				throw new IllegalArgumentException(first + " is not a field getter");
			throw new IllegalArgumentException("No " + theType + " method invoked");
		}
		MethodInterpreter<? super E, F> method = (MethodInterpreter<? super E, F>) getInterpreter(invoked);
		if (!(method instanceof FieldGetter))
			throw new IllegalArgumentException(method + " is not a field getter");
		return ((FieldGetter<E, F>) method).getField();
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

	@Override
	public String toString() {
		return theType.toString();
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

	static EntityReflector<?>.ProxyMethodHandler getHandler(Object proxy) {
		return (EntityReflector<?>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
	}

	<R> MethodInterpreter<E, R> findMethodFromSuper(MethodInterpreter<? super E, R> superMethod) {
		for (int i = 0; i < theSupers.size(); i++) {
			if (theSupers.get(i) == superMethod.getReflector())
				return (MethodInterpreter<E, R>) theSuperMethodMappings.get(i).get(superMethod.getMethodElement());
			MethodInterpreter<? super E, R> superMethod_i = ((EntityReflector<E>) theSupers.get(i))// Generics hack
				.findMethodFromSuper(superMethod);
			if (superMethod_i != null)
				return (MethodInterpreter<E, R>) theSuperMethodMappings.get(i).get(superMethod_i.getMethodElement());
		}
		throw new IllegalArgumentException("Method " + superMethod + " cannot be applied to " + theType);
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
			MethodInterpreter<E, ?> interpreter = getInterpreter(method);
			if (interpreter == null)
				throw new IllegalStateException("Method " + method + " not found!");
			SuperPath path;
			if (getSuper().isEmpty() || method.getDeclaringClass() == theRawType)
				path = null;
			else
				path = theSuperPaths.get(method.getDeclaringClass());
			return interpreter.invoke((E) proxy, path, args, theFieldGetter, theFieldSetter);
		}

		protected <T> T invokeOnType(E proxy, MethodInterpreter<? super E, T> method, Object[] args) throws Throwable {
			if (method.getReflector() != getReflector())
				method = findMethodFromSuper(method);
			return ((MethodInterpreter<E, T>) method).invoke(proxy, null, args, theFieldGetter, theFieldSetter);
		}
	}
}
