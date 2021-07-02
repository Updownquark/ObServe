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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.config.ParentReference;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.IntList;
import org.qommons.MethodRetrievingHandler;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A utility for reflectively compiling a public interface's fields with getters and setters. Especially, this class's
 * {@link #newInstance(EntityInstanceBacking) newInstance} method allows the caller to easily create new instances of the target interface
 * backed by a custom data set.
 * </p>
 *
 * <p>
 * This class can be used to create proxy-backed instances of entity interfaces for which all non-static methods (and methods of
 * super-interfaces) are one of:
 * <ul>
 * <li>A getter of the form <code>type getXXX()</code></li>
 * <li>A setter of the form <code>R setXXX(type value)</code> for a field that also has a getter. R may be:
 * <ul>
 * <li>void</li>
 * <li>type (the type of the field), in which case the return value will be the previous value of the field</li>
 * <li>the entity type, in which case the return value will be the entity</li>
 * </ul>
 * </li>
 * <li>A default method</li>
 * <li>A method with the same signature (exclusive of name) as an {@link Object} method, and tagged with @{@link ObjectMethodOverride}</li>
 * <li>{@link Identifiable#getIdentity()}--an identity will be provided unless the method is defaulted</li>
 * </ul>
 * </p>
 * <p>
 * This class supports @{@link Cached} getters, observability of fields (if the {@link EntityInstanceBacking} is an instance of
 * {@link ObservableEntityInstanceBacking}), and entity-associated data (via {@link #associate(Object, Object, Object)} and
 * {@link #getAssociated(Object, Object)})
 * </p>
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
	 * @param useDirectly Whether the caller intends to use the built type directly or just as a super-type
	 * @return A builder to create a reflector
	 */
	public static <E> Builder<E> build(TypeToken<E> type, boolean useDirectly) {
		return new Builder<>(type, useDirectly);
	}

	/**
	 * Builds an {@link EntityReflector}
	 *
	 * @param <E> The interface type to reflect on
	 */
	public static class Builder<E> {
		private final TypeToken<E> theType;
		private final boolean isUsingDirectly;
		private final Class<E> theRawType;
		private Map<TypeToken<?>, EntityReflector<?>> theSupers;
		private Function<Method, String> theGetterFilter;
		private Function<Method, String> theSetterFilter;
		private Function<Method, String> theObservableFilter;
		private Map<Method, BiFunction<? super E, Object[], ?>> theCustomMethods;
		private Set<String> theIdFields;
		private List<EntityReflectionMessage> theMessages;

		private MethodRetrievingHandler theProxyHandler;
		private E theProxy;

		private Builder(TypeToken<E> type, boolean useDirectly) {
			theRawType = TypeTokens.getRawType(type);
			isUsingDirectly = useDirectly;
			if (theRawType == null || !theRawType.isInterface())
				throw new IllegalArgumentException("This class only works for interface types: " + type);
			else if ((theRawType.getModifiers() & Modifier.PUBLIC) == 0)
				throw new IllegalArgumentException("This class only works for public interface types: " + type);
			theType = type;
			theSupers = new LinkedHashMap<>();
			theGetterFilter = new OrFilter(//
				new PrefixFilter("get", 0), new PrefixFilter("is", 0));
			theSetterFilter = new PrefixFilter("set", 1);
			theMessages = new ArrayList<>();
		}

		/**
		 * @param supers A set of reflectors for potential super-interfaces of a reflector built with this builder
		 * @return This builder
		 */
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
		 * @param observableFilter The function to determine what field an observable field getter is for
		 * @return This builder
		 */
		public Builder<E> withObservableFilter(Function<Method, String> observableFilter) {
			theObservableFilter = observableFilter;
			return this;
		}

		/**
		 * @param customMethods Custom method implementations for {@link EntityReflector#newInstance(EntityInstanceBacking) created
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
		 * @param implementation The custom implementation of the method for {@link EntityReflector#newInstance(EntityInstanceBacking)
		 *        created instances} of the type to use
		 * @return This builder
		 */
		public Builder<E> withCustomMethod(Method method, BiFunction<? super E, Object[], ?> implementation) {
			if (theCustomMethods == null)
				theCustomMethods = new LinkedHashMap<>();
			theCustomMethods.put(method, implementation);
			return this;
		}

		/**
		 * @param method A function that calls the method to implement
		 * @param implementation The custom implementation of the method
		 * @return This builder
		 */
		public <R> Builder<E> withCustomMethod(Function<? super E, R> method, BiFunction<? super E, Object[], R> implementation) {
			if (theProxyHandler == null) {
				theProxyHandler = new MethodRetrievingHandler();
				theProxy = (E) Proxy.newProxyInstance(TypeTokens.getRawType(theType).getClassLoader(), new Class[] { theRawType },
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

		/**
		 * @param ids The names of the fields that should be marked as ID fields in the entity
		 * @return This builder
		 */
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
			for (EntityReflectionMessage message : theMessages) {
				switch (message.getLevel()) {
				case WARNING:
				case ERROR:
				case FATAL:
					System.err.println(message);
					break;
				case INFO:
					System.out.println(message);
					break;
				}
			}

			return reflector;
		}

		/**
		 * The default behavior of this builder is to print all the messages that occurred as a result of building the reflected type to
		 * System.err. This method does not do that. {@link #getMessages()} can be called to perform other operations on them.
		 *
		 * @return The new reflector
		 */
		public EntityReflector<E> buildNoPrint() {
			EntityReflector<E> reflector = new EntityReflector<>(theSupers, theType, theRawType, //
				theGetterFilter, theSetterFilter, theObservableFilter, //
				theCustomMethods == null ? Collections.emptyMap() : theCustomMethods, theIdFields, theMessages);
			if (isUsingDirectly)
				theMessages.addAll(reflector.getDirectUseErrors());
			return reflector;
		}

		/**
		 * @return The messages that occurred describing the results of the entity reflection after {@link #build()} or
		 *         {@link #buildNoPrint()}
		 */
		public List<EntityReflectionMessage> getMessages() {
			return theMessages;
		}
	}

	/** The severity level of an {@link EntityReflectionMessage} */
	public enum EntityReflectionMessageLevel {
		/** A fatal message implies that the entity reflector could not be created or cannot be used to create instances at all */
		FATAL,
		/**
		 * An error message implies that entities created with the reflector may throw unexpected exceptions in some situations, typically
		 * when the message's {@link EntityReflector.EntityReflectionMessage#getMethod() method} is called
		 */
		ERROR,
		/**
		 * A warning message implies that the entity type violates best practices, which may cause unexpected behavior in some situations
		 */
		WARNING,
		/** An info message is a message that does not imply any problem or misbehavior */
		INFO;
	}

	/** A message generated from generating an {@link EntityReflector} */
	public static class EntityReflectionMessage {
		private final EntityReflectionMessageLevel theLevel;
		private final Method theMethod;
		private final String theMessage;

		EntityReflectionMessage(EntityReflectionMessageLevel level, Method method, String message) {
			theLevel = level;
			theMethod = method;
			theMessage = message;
		}

		/** @return The severity level of this message */
		public EntityReflectionMessageLevel getLevel() {
			return theLevel;
		}

		/** @return The method that this message is in relation to */
		public Method getMethod() {
			return theMethod;
		}

		/** @return The content of the message */
		public String getMessage() {
			return theMessage;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theLevel).append(": ").append(theMethod).append(") ").append(theMessage).toString();
		}
	}

	/** A method categorizer that combines more than one method */
	public static class OrFilter implements Function<Method, String> {
		private final Function<Method, String>[] theComponents;

		/** @param components The method categorizers to combine */
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

		/** @return The type of this field */
		public TypeToken<F> getType() {
			return theGetter.getReturnType();
		}

		/** @return Whether this field is an identifier for its type */
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

		/**
		 * @param entity The entity to get the field of
		 * @return This field's value in the given entity
		 */
		public F get(E entity) {
			InvocationHandler handler = Proxy.isProxyClass(entity.getClass()) ? Proxy.getInvocationHandler(entity) : null;
			if (handler instanceof EntityReflector.ProxyMethodHandler) {
				EntityReflector<E>.ProxyMethodHandler p = (EntityReflector<E>.ProxyMethodHandler) handler;
				try {
					return p.invokeOnType(entity, theGetter, NO_ARGS);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			} else {
				try {
					return (F) theGetter.getMethod().invoke(entity, NO_ARGS);
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new IllegalStateException(e);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else if (e.getTargetException() instanceof Error)
						throw (Error) e.getTargetException();
					else
						throw new IllegalStateException(e.getTargetException());
				}
			}
		}

		/**
		 * @param entity The entity to set the field of
		 * @param value The new value for this field in the given entity
		 */
		public void set(E entity, F value) {
			if (theSetter == null)
				throw new UnsupportedOperationException("No setter found for field " + theReflector.getType() + "." + theName);
			InvocationHandler handler = Proxy.isProxyClass(entity.getClass()) ? Proxy.getInvocationHandler(entity) : null;
			if (handler instanceof EntityReflector.ProxyMethodHandler) {
				EntityReflector<E>.ProxyMethodHandler p = (EntityReflector<E>.ProxyMethodHandler) handler;
				try {
					p.invokeOnType(entity, theSetter, new Object[] { value });
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			} else {
				try {
					theSetter.getMethod().invoke(entity, new Object[] { value });
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new IllegalStateException(e);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else if (e.getTargetException() instanceof Error)
						throw (Error) e.getTargetException();
					else
						throw new IllegalStateException(e.getTargetException());
				}
			}
		}

		@Override
		public String toString() {
			return theReflector.getType() + "." + theName + " (" + theGetter.getReturnType() + ")";
		}
	}

	/**
	 * Represents a non-{@link ObservableValueEvent#isInitial() initial} change to the value of an observable field in an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static abstract class EntityFieldChangeEvent<E, F> extends ObservableValueEvent<F> {
		private final int theFieldIndex;

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex()} of the field whose value changed
		 * @param type The type of the field whose value changed
		 * @param oldValue The previous value of the field
		 * @param newValue The new (current) value of the field
		 * @param cause The cause of the change
		 */
		public EntityFieldChangeEvent(int fieldIndex, TypeToken<F> type, F oldValue, F newValue, Object cause) {
			super(type, false, oldValue, newValue, cause);
			theFieldIndex = fieldIndex;
		}

		/** @return The entity whose field value changed */
		public abstract E getEntity();

		/** @return The {@link EntityReflector.ReflectedField#getFieldIndex()} of the field whose value changed */
		public int getFieldIndex() {
			return theFieldIndex;
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

	/** Provided to {@link EntityReflector#newInstance(EntityInstanceBacking)} to provide and modify field values in the entity instance */
	public interface EntityInstanceBacking {
		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field to get
		 * @return The current value of the field in this backing's entity
		 */
		Object get(int fieldIndex);

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field to set
		 * @param newValue The new value for the field in this backing's entity
		 */
		void set(int fieldIndex, Object newValue);
	}

	/**
	 * An extension of {@link EntityInstanceBacking} that provides support for observable fields
	 *
	 * @param <E> The entity type to support
	 */
	public interface ObservableEntityInstanceBacking<E> extends EntityInstanceBacking {
		/**
		 * Registers a listener to a field for a particular entity
		 *
		 * This method will not be called if {@link #watchField(Object, int, Consumer)} is overridden.
		 *
		 * @param entity The entity instance to listen to
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field
		 * @param listener The listener to install
		 * @return A subscription to {@link Subscription#unsubscribe() unsubscribe} to stop listening
		 */
		Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener);

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field
		 * @return The locking mechanism for the field
		 */
		Transactable getLock(int fieldIndex);

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field
		 * @return The {@link Stamped#getStamp() stamp} for the field's value
		 */
		long getStamp(int fieldIndex);

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field
		 * @param value The value for the field
		 * @return null if the given value is acceptable for the field, or a reason why it is not
		 */
		String isAcceptable(int fieldIndex, Object value);

		/**
		 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field
		 * @return A value containing null when the field may be modified, or a reason why it cannot be
		 */
		ObservableValue<String> isEnabled(int fieldIndex);

		/**
		 * @param entity The entity whose field to watch (backed by this backing instance)
		 * @param fieldIndex The index of the field to watch
		 * @param listener The listener to receive an event when the field changes
		 * @return The subscription to use to cease listening
		 */
		default Subscription watchField(E entity, int fieldIndex, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
			return getHandler(entity).addFieldListener(entity, fieldIndex, listener);
		}

		/**
		 * @param entity The entity whose field to watch (backed by this backing instance)
		 * @param listener The listener to receive an event any field in the entity changes
		 * @return The subscription to use to cease listening
		 */
		default Subscription watchAllFields(E entity, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
			EntityReflector<E> reflector = getHandler(entity).getReflector();
			Subscription[] subs = new Subscription[reflector.getFields().keySize()];
			for (int f = 0; f < reflector.getFields().keySize(); f++)
				subs[f] = watchField(entity, f, listener);
			return Subscription.forAll(subs);
		}

		/**
		 * @param <F> The type of the field to observe
		 * @param entity The entity to observe the field on
		 * @param field The field to observe
		 * @return A settable value reflecting the value of the given field, allowing monitoring of changes to it, and (where allowed)
		 *         providing setter functionality
		 */
		default <F> ObservableField<E, F> observeField(E entity, ReflectedField<E, F> field) {
			return new ObservableFieldImpl<>(entity, field);
		}

		/**
		 * Creates a change event when an observable field's value changes
		 *
		 * @param entity The entity to create the event for
		 * @param field The field that changed
		 * @param oldValue The previous value of the field
		 * @param newValue The new (current) value of the field
		 * @param cause The cause of the change
		 * @return The event to fire
		 */
		default <F> EntityFieldChangeEvent<E, F> createFieldChangeEvent(E entity, ReflectedField<E, F> field, F oldValue, F newValue,
			Object cause) {
			return new EntityFieldChangeEventImpl<>(entity, field, oldValue, newValue, cause);
		}
	}

	private static class EntityFieldChangeEventImpl<E, F> extends EntityFieldChangeEvent<E, F> {
		private final E theEntity;

		EntityFieldChangeEventImpl(E entity, ReflectedField<E, F> field, F oldValue, F newValue, Object cause) {
			super(field.getFieldIndex(), field.getType(), oldValue, newValue, cause);
			theEntity = entity;
		}

		@Override
		public E getEntity() {
			return theEntity;
		}
	}

	/**
	 * Represents a change to a entity's field (see
	 * {@link EntityReflector.ObservableEntityInstanceBacking#addListener(Object, int, Consumer)})
	 *
	 * @param <F> The type of the field that changed
	 */
	public static class FieldChange<F> {
		/** The previous value of the field */
		public final F oldValue;
		/** The new (current) value of the field */
		public final F newValue;
		/** The cause of the change */
		public final Object cause;

		/**
		 * @param oldValue The previous value of the field
		 * @param newValue The new (current) value of the field
		 * @param cause The cause of the change
		 */
		public FieldChange(F oldValue, F newValue, Object cause) {
			this.oldValue = oldValue;
			this.newValue = newValue;
			this.cause = cause;
		}
	}

	/**
	 * <p>
	 * A {@link SettableValue} representing the value of a field in an observably-supported instance of an entity.
	 * </p>
	 * <p>
	 * An instance is observably-supported if the {@link EntityReflector.EntityInstanceBacking} given to the
	 * {@link EntityReflector#newInstance(EntityInstanceBacking) newInstance} call that created it was an instance of
	 * {@link EntityReflector.ObservableEntityInstanceBacking}.
	 * </p>
	 * <p>
	 * An instance of this type may be obtained by calling {@link EntityReflector#observeField(Object, int)} or from an observable field
	 * getter (by default, of the form <code>SettableValue<F> observeFieldName()</code>, see
	 * {@link EntityReflector.Builder#withObservableFilter(Function)}) on the entity that returns {@link SettableValue} or
	 * {@link EntityReflector.ObservableField}.
	 * </p>
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public interface ObservableField<E, F> extends SettableValue<F> {
		/** @return The entity whose field this value represents */
		public E getEntity();

		/** @return The {@link EntityReflector.ReflectedField#getFieldIndex()} of the field that this value represents */
		public int getFieldIndex();
	}

	static class ObservableFieldImpl<E, F> extends AbstractIdentifiable implements ObservableField<E, F> {
		private final E theEntity;
		private final ReflectedField<E, F> theField;
		private final Transactable theLock;

		ObservableFieldImpl(E entity, ReflectedField<E, F> field) {
			theEntity = entity;
			theField = field;
			theLock = getBacking().getLock(field.getFieldIndex());
		}

		/** @return The backing of the entity */
		protected ObservableEntityInstanceBacking<E> getBacking() {
			return (ObservableEntityInstanceBacking<E>) getHandler(theEntity).theBacking;
		}

		@Override
		public E getEntity() {
			return theEntity;
		}

		@Override
		public int getFieldIndex() {
			return theField.getFieldIndex();
		}

		@Override
		public F get() {
			return theField.get(theEntity);
		}

		@Override
		public Observable<ObservableValueEvent<F>> noInitChanges() {
			return new FieldObserver();
		}

		@Override
		public boolean isLockSupported() {
			return theLock.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theLock.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theLock.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theLock.getCoreId();
		}

		@Override
		public long getStamp() {
			return getBacking().getStamp(theField.getFieldIndex());
		}

		@Override
		public TypeToken<F> getType() {
			return theField.getType();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theEntity, theField.getName());
		}

		@Override
		public <V extends F> F set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = lock(true, cause)) {
				F oldValue = get();
				theField.set(theEntity, value);
				return oldValue;
			}
		}

		@Override
		public <V extends F> String isAcceptable(V value) {
			return getBacking().isAcceptable(theField.getFieldIndex(), value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getBacking().isEnabled(theField.getFieldIndex());
		}

		@Override
		public String toString() {
			return new StringBuilder()//
				.append(theEntity).append('.').append(theField.getName()).append('=').append(get())//
				.toString();
		}

		class FieldObserver extends AbstractIdentifiable implements Observable<ObservableValueEvent<F>> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableFieldImpl.this.getIdentity(), "noInitChanges");
			}

			@Override
			public boolean isSafe() {
				return isLockSupported();
			}

			@Override
			public Transaction lock() {
				return ObservableFieldImpl.this.lock(false, null);
			}

			@Override
			public Transaction tryLock() {
				return ObservableFieldImpl.this.tryLock(false, null);
			}

			@Override
			public CoreId getCoreId() {
				return ObservableFieldImpl.this.getCoreId();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<F>> observer) {
				return theField.theReflector.watchField(theEntity, theField.getFieldIndex(),
					event -> observer.onNext((EntityFieldChangeEvent<E, F>) event));
			}
		};
	}

	/**
	 * Returned from the default {@link Identifiable#getIdentity()} method for entities that have {@link EntityReflector#getIdFields() ID
	 * fields}.
	 *
	 * @param <E> The type of the entity
	 */
	public static class EntityFieldIdentity<E> {
		private final Class<E> theType;
		private final Object[] theIdentityValues;

		EntityFieldIdentity(EntityReflector<E> reflector, E entity) {
			theType = reflector.theRawType;
			theIdentityValues = new Object[reflector.getIdFields().size()];
			int idx = 0;
			for (int idField : reflector.getIdFields())
				theIdentityValues[idx++] = reflector.getFields().get(idField).get(entity);
		}

		@Override
		public int hashCode() {
			return theType.hashCode() * 7 + Arrays.hashCode(theIdentityValues);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof EntityFieldIdentity))
				return false;
			EntityFieldIdentity<?> other = (EntityFieldIdentity<?>) obj;
			return theType == other.theType && Arrays.equals(theIdentityValues, other.theIdentityValues);
		}

		@Override
		public String toString() {
			return StringUtils.print(", ", Arrays.asList(theIdentityValues), String::valueOf).toString();
		}
	}

	/**
	 * Returned from the default {@link Identifiable#getIdentity()} method for entities that have no {@link EntityReflector#getIdFields() ID
	 * fields}.
	 *
	 * @param <E> The type of the entity
	 */
	public static class EntityInstanceIdentity<E> {
		private final E theEntity;

		EntityInstanceIdentity(E entity) {
			theEntity = entity;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(theEntity);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else
				return obj instanceof EntityInstanceIdentity && theEntity == ((EntityInstanceIdentity<?>) obj).theEntity;
		}

		@Override
		public String toString() {
			return new StringBuilder()//
				.append(getHandler(theEntity).getReflector().theRawType.getName())//
				.append('@').append(Integer.toHexString(hashCode()))//
				.toString();
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

	/**
	 * Called when any method in an entity created by an {@link EntityReflector} is called
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static abstract class MethodInterpreter<E, R> implements Comparable<MethodInterpreter<?, ?>> {
		private final EntityReflector<E> theReflector;
		private ElementId theMethodElement;
		private final Method theMethod;
		/** Methods in super interfaces of this method's entity that this method overrides */
		protected MethodInterpreter<? super E, ? super R>[] theSuperMethods;
		private SuperPath theDefaultSuper;
		private final Class<?>[] theParameters;
		private final Invokable<E, R> theInvokable;

		MethodInterpreter(EntityReflector<E> reflector, Method method) {
			this(reflector, reflector.getType(), method);
		}

		MethodInterpreter(EntityReflector<E> reflector, TypeToken<E> type, Method method) {
			theReflector = reflector;
			theMethod = method;
			theSuperMethods = new MethodInterpreter[(reflector == null || reflector.theSupers.isEmpty()) ? 0 : reflector.theSupers.size()];
			theParameters = method.getParameterTypes();
			theInvokable = (Invokable<E, R>) type.method(method);
		}

		void setElement(ElementId methodElement) {
			theMethodElement = methodElement;
		}

		void setSuper(int superIndex, MethodInterpreter<? super E, ? super R> superMethod) {
			theSuperMethods[superIndex] = superMethod;
			if (theDefaultSuper == null || overrides(theSuperMethods[theDefaultSuper.index], superMethod))
				theDefaultSuper = new SuperPath(superIndex,
					superMethod instanceof SuperDelegateMethod ? superMethod.theDefaultSuper : null);
		}

		private static boolean overrides(MethodInterpreter<?, ?> target, MethodInterpreter<?, ?> override) {
			while (target instanceof SuperDelegateMethod)
				target = target.theSuperMethods[target.theDefaultSuper.index];
			if (!(target instanceof ObjectMethodWrapper))
				return false;
			while (override instanceof SuperDelegateMethod)
				override = override.theSuperMethods[override.theDefaultSuper.index];
			return !(override instanceof ObjectMethodWrapper);
		}

		/** @return The reflector that this method belongs to */
		public EntityReflector<E> getReflector() {
			return theReflector;
		}

		/** @return The address of this method in its reflector's {@link EntityReflector#getMethods() methods} */
		public ElementId getMethodElement() {
			return theMethodElement;
		}

		/** @return The {@link Method} that this interpreter implements */
		public Method getMethod() {
			return theMethod;
		}

		/** @return The name of this method */
		public String getName() {
			return theMethod.getName();
		}

		/** @return The Invokable representing this method */
		public Invokable<E, R> getInvokable() {
			return theInvokable;
		}

		/** @return The return type of the method */
		public TypeToken<R> getReturnType() {
			return (TypeToken<R>) theInvokable.getReturnType();
		}

		/**
		 * Called when the method is invoked
		 *
		 * @param proxy The entity instance that this method is being invoked on
		 * @param declaringClass The declaring class of the method that was called
		 * @param path The path to the target super method to invoke
		 * @param args The arguments with which the method was called
		 * @param backing The backing of the entity
		 * @return The return value of the method
		 * @throws Throwable If the method throws an exception
		 */
		protected R invoke(E proxy, Class<?> declaringClass, SuperPath path, Object[] args, EntityInstanceBacking backing)
			throws Throwable {
			if (path == null)
				return invokeLocal(proxy, args, backing);
			if (declaringClass == Object.class)
				path = theDefaultSuper;
			return (R) theSuperMethods[path.index].invoke(proxy, declaringClass, path.parent, args,
				superBacking(backing, proxy, path, args));
		}

		EntityInstanceBacking superBacking(EntityInstanceBacking backing, E proxy, SuperPath path, Object[] args) {
			return new SuperBacking(backing, proxy, path, args);
		}

		class SuperBacking implements EntityInstanceBacking {
			final EntityInstanceBacking theBacking;
			final E theProxy;
			final SuperPath thePath;
			final Object[] theArgs;

			SuperBacking(EntityInstanceBacking backing, E proxy, SuperPath path, Object[] args) {
				theBacking = backing;
				theProxy = proxy;
				thePath = path;
				theArgs = args;
			}

			@Override
			public Object get(int fieldIndex) {
				if (MethodInterpreter.this instanceof FieldGetter
					&& ((FieldGetter<E, R>) MethodInterpreter.this).getField().getFieldIndex() == fieldIndex)
					return theBacking.get(fieldIndex);
				try {
					MethodInterpreter<? super E, ?> superGetter = getReflector().theSuperFieldGetters.get(thePath.index).get(fieldIndex);
					return superGetter.invoke(theProxy, superGetter.getReflector().theRawType, null, NO_ARGS, theBacking);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			}

			@Override
			public void set(int fieldIndex, Object newValue) {
				if (MethodInterpreter.this instanceof FieldSetter
					&& ((FieldSetter<E, ?>) MethodInterpreter.this).getField().getFieldIndex() == fieldIndex) {
					theBacking.set(fieldIndex, theArgs[0]);
					return;
				}
				try {
					MethodInterpreter<? super E, ?> superSetter = getReflector().theSuperFieldSetters.get(thePath.index).get(fieldIndex);
					superSetter.invoke(theProxy, superSetter.getReflector().theRawType, null, new Object[] { newValue }, theBacking);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			}
		}

		/**
		 * This method's local implementation (as distinct from a super call)
		 *
		 * @param proxy The entity instance that this method is being invoked on
		 * @param args The arguments with which the method was called
		 * @param backing The backing of the entity
		 * @return The return value of the method
		 * @throws Throwable If the method throws an exception
		 */
		protected abstract R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable;

		@Override
		public int compareTo(MethodInterpreter<?, ?> o) {
			if (this == o)
				return 0;
			return compare(o.theMethod.getName(), o.theParameters);
		}

		/**
		 * @param method The method to compare with
		 * @return Whether this method should appear before (-) or after (+) the given method, or 0 if its signature is identical to this
		 *         method
		 */
		public int compare(Method method) {
			if (theMethod == method)
				return 0;
			return compare(method.getName(), method.getParameterTypes());
		}

		/**
		 * @param sig The method signature to compare with
		 * @return Whether this method should appear before (-) or after (+) the given method, or 0 if its signature is identical to this
		 *         method
		 */
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
			StringUtils.print(", ", Arrays.asList(theParameters), p -> p.getName());
			str.append(')');
			return str.toString();
		}
	}

	/**
	 * A getter or setter for a field
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 * @param <R> The return type of the method
	 */
	public static abstract class FieldRelatedMethod<E, F, R> extends MethodInterpreter<E, R> {
		private final ReflectedField<E, F> theField;

		FieldRelatedMethod(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field) {
			super(reflector, method);
			theField = field;
		}

		/** @return The field this method is a getter or setter for */
		public ReflectedField<E, F> getField() {
			return theField;
		}
	}

	/**
	 * A getter for a field
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class FieldGetter<E, F> extends FieldRelatedMethod<E, F, F> {
		private final boolean isParentReference;
		FieldGetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field) {
			super(reflector, method, field);
			isParentReference = method.getAnnotation(ParentReference.class) != null;
		}

		@Override
		protected F invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) {
			return (F) backing.get(getField().getFieldIndex());
		}

		/** @return Whether this getter is tagged with {@link ParentReference @ParentReference} */
		public boolean isParentReference() {
			return isParentReference;
		}
	}

	/**
	 * A getter for a field that has a default implementation which is called once and the value reused thereafter
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class CachedFieldGetter<E, F> extends FieldGetter<E, F> {
		private final MethodHandle theHandle;

		CachedFieldGetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, MethodHandle handle) {
			super(reflector, method, field);
			theHandle = handle;
		}

		@Override
		protected F invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) {
			if (theHandle == null)
				throw new IllegalStateException("Unable to reflectively invoke default method " + getInvokable());
			Object value = backing.get(getField().getFieldIndex());
			if (value == null) {
				value = super.invokeLocal(proxy, args, backing);
				if (value != null)
					backing.set(getField().getFieldIndex(), value);
			}
			return (F) value;
		}
	}

	/**
	 * A setter for a field
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class FieldSetter<E, F> extends FieldRelatedMethod<E, F, Object> {
		private final SetterReturnType theReturnType;

		FieldSetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, SetterReturnType returnType) {
			super(reflector, method, field);
			theReturnType = returnType;
			field.setSetter(this);
		}

		/** @return The return type of the setter */
		public SetterReturnType getSetterReturnType() {
			return theReturnType;
		}

		@Override
		protected Object invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) {
			Object returnValue = null;
			switch (theReturnType) {
			case OLD_VALUE:
				returnValue = backing.get(getField().getFieldIndex());
				break;
			case SELF:
				returnValue = proxy;
				break;
			case VOID:
				returnValue = null;
				break;
			}
			backing.set(getField().getFieldIndex(), args[0]);
			return returnValue;
		}
	}

	/** The return type of a field setter */
	public enum SetterReturnType {
		/** If the setter's return type is the type of the field, the previous value of the field will be returned */
		OLD_VALUE,
		/** If the setter's return type is the type of the entity, the entity will be returned (e.g. for chained calls) */
		SELF,
		/** A void-typed setter */
		VOID;
	}

	/**
	 * An observable field getter--returns an {@link ObservableValue} (or {@link SettableValue} or {@link EntityReflector.ObservableField})
	 * that represents the field value of the entity.
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class ObservableFieldGetter<E, F> extends FieldRelatedMethod<E, F, ObservableValue<F>> {
		private final ObservableGetterType theType;

		ObservableFieldGetter(EntityReflector<E> reflector, Method method, ReflectedField<E, F> field, ObservableGetterType type) {
			super(reflector, method, field);
			theType = type;
		}

		/** @return The type of the observable this getter returns */
		public ObservableGetterType getGetterType() {
			return theType;
		}

		@Override
		protected ObservableValue<F> invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			ObservableField<E, F> observable = (ObservableField<E, F>) getReflector().observeField(proxy, getField().getFieldIndex());
			ObservableValue<F> returnValue = null;
			switch (theType) {
			case OBSERVABLE:
				returnValue = observable.unsettable();
				break;
			case SETTABLE:
			case FIELD_SETTABLE:
				returnValue = observable;
				break;
			}
			if (returnValue == null)
				throw new IllegalStateException("Unrecognized observable getter type: " + theType);
			return returnValue;
		}
	}

	/** The type of observable returned by an {@link EntityReflector.ObservableFieldGetter} */
	public enum ObservableGetterType {
		/** A simple, un-settable {@link ObservableValue} */
		OBSERVABLE,
		/** A {@link SettableValue} (the actual instance will be an {@link EntityReflector.ObservableField}) */
		SETTABLE,
		/** Explicitly {@link EntityReflector.ObservableField} */
		FIELD_SETTABLE;
	}

	/**
	 * A method implemented in the entity interface via Java 8's default implementation feature
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static class DefaultMethod<E, R> extends MethodInterpreter<E, R> {
		private final MethodHandle theHandle;

		DefaultMethod(EntityReflector<E> reflector, Method method, MethodHandle handle) {
			super(reflector, method);
			theHandle = handle;
		}

		MethodHandle getHandle() {
			return theHandle;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			if (theHandle == null)
				throw new IllegalStateException("Unable to reflectively invoke default method " + getInvokable());
			synchronized (theHandle) {
				return (R) theHandle.bindTo(proxy).invokeWithArguments(args);
			}
		}
	}

	/**
	 * A default method whose implementation is called once and the value cached and returned thereafter
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static class CachedMethod<E, R> extends DefaultMethod<E, R> {
		private final DefaultMethod<E, R> theDefaultMethod;

		CachedMethod(DefaultMethod<E, R> defaultMethod) {
			super(defaultMethod.getReflector(), defaultMethod.getMethod(), defaultMethod.theHandle);
			theDefaultMethod = defaultMethod;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			EntityReflector<E>.ProxyMethodHandler handler = getHandler(proxy);
			ValueHolder<R> holder = (ValueHolder<R>) handler.getAssociated(this);
			if (holder == null) {
				R value = theDefaultMethod.invokeLocal(proxy, args, backing);
				holder = new ValueHolder<>(value);
				handler.associate(this, value);
			}
			return holder.get();
		}

		/** @return The default method that this cached method fronts */
		public MethodInterpreter<E, R> getDefaultMethod() {
			return theDefaultMethod;
		}
	}

	/**
	 * A method whose default implementation is overridden by another
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static abstract class SyntheticMethodOverride<E, R> extends MethodInterpreter<E, R> {
		static class MethodInvocation {
			final SyntheticMethodOverride<?, ?> method;
			final Object proxy;
			final Object[] args;

			MethodInvocation(SyntheticMethodOverride<?, ?> method, Object proxy, Object[] args) {
				this.method = method;
				this.proxy = proxy;
				this.args = args == null ? NO_ARGS : args;
			}

			@Override
			public int hashCode() {
				int hash = method.hashCode();
				hash = hash * 19 + System.identityHashCode(proxy);
				for (Object arg : args)
					hash = hash * 7 + System.identityHashCode(arg);
				return hash;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof MethodInvocation))
					return false;
				MethodInvocation other = (MethodInvocation) o;
				if (method != other.method || proxy != other.proxy || args.length != other.args.length)
					return false;
				for (int i = 0; i < args.length; i++)
					if (args[i] != other.args[i])
						return false;
				return true;
			}
		}
		static final ThreadLocal<BetterSet<MethodInvocation>> INVOCATIONS = ThreadLocal
			.withInitial(() -> BetterHashSet.build().unsafe().buildSet());

		SyntheticMethodOverride(EntityReflector<E> reflector, Method method) {
			super(reflector, method);
		}

		@Override
		protected R invoke(E proxy, Class<?> declaringClass, SuperPath path, Object[] args, EntityInstanceBacking backing)
			throws Throwable {
			BetterSet<MethodInvocation> invocations = null;
			ElementId invocation = null;
			if (path != null) {
				invocations = INVOCATIONS.get();
				invocation = CollectionElement.getElementId(invocations.addElement(new MethodInvocation(this, proxy, args), false));
			}
			if (path == null || invocation != null) {
				try {
					return invokeLocal(proxy, args, backing);
				} finally {
					if (invocation != null)
						invocations.mutableElement(invocation).remove();
				}
			} else
				return super.invoke(proxy, declaringClass, path, args, backing);
		}
	}

	/**
	 * A method for which a custom implementation has been provided through code (either not specified in the entity type or overridden over
	 * it). See {@link EntityReflector.Builder#withCustomMethod(Method, BiFunction)}.
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static class CustomMethod<E, R> extends SyntheticMethodOverride<E, R> {
		private BiFunction<? super E, Object[], R> theInterpreter;

		CustomMethod(EntityReflector<E> reflector, Method method, BiFunction<? super E, Object[], R> interpreter) {
			super(reflector, method);
			theInterpreter = interpreter;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) {
			return theInterpreter.apply(proxy, args);
		}
	}

	/**
	 * A method with a default implementation (used for entities when an implementation is not otherwise provided)
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static class ObjectMethodWrapper<E, R> extends MethodInterpreter<E, R> {
		private final MethodInterpreter<Object, R> theObjectMethod;

		ObjectMethodWrapper(EntityReflector<E> reflector, MethodInterpreter<Object, R> objectMethod) {
			super(reflector, objectMethod.getMethod());
			theObjectMethod = objectMethod;
		}

		/** @return The default method interpreter (see {@link EntityReflector#OBJECT_METHODS}) */
		public MethodInterpreter<Object, R> getObjectMethod() {
			return theObjectMethod;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			return theObjectMethod.invokeLocal(proxy, args, backing);
		}
	}

	/**
	 * A method tagged with @{@link ObjectMethodOverride} to override an Object method
	 *
	 * @param <E> The type of the entity
	 * @param <R> The type of the field
	 */
	public static class OverrideMethod<E, R> extends SyntheticMethodOverride<E, R> {
		private final MethodInterpreter<E, R> theOverride;

		OverrideMethod(EntityReflector<E> reflector, Method method, MethodInterpreter<E, R> override) {
			super(reflector, method);
			theOverride = override;
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			return theOverride.invokeLocal(proxy, args, backing);
		}
	}

	/**
	 * A method that is implemented in a super type and not overridden
	 *
	 * @param <E> The type of the entity
	 * @param <R> The return type of the method
	 */
	public static class SuperDelegateMethod<E, R> extends MethodInterpreter<E, R> {
		SuperDelegateMethod(EntityReflector<E> reflector, Method method) {
			super(reflector, method);
		}

		@Override
		protected R invokeLocal(E proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
			throw new IllegalStateException("No super method set");
		}
	}

	/** Default implementation of {@link Object#equals(Object)} for entities when not overridden using @{@link ObjectMethodOverride} */
	public static MethodInterpreter<Object, Boolean> OBJECT_EQUALS;
	/** Default implementation of {@link Object#hashCode()} for entities when not overridden using @{@link ObjectMethodOverride} */
	public static MethodInterpreter<Object, Integer> OBJECT_HASH_CODE;
	/** Default implementation of {@link Object#toString()} for entities when not overridden using @{@link ObjectMethodOverride} */
	public static MethodInterpreter<Object, String> OBJECT_TO_STRING;
	/** Default implementation of {@link Object#notify()} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_NOTIFY;
	/** Default implementation of {@link Object#notifyAll()} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_NOTIFY_ALL;
	/** Default implementation of {@link Object#wait()} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_WAIT0;
	/** Default implementation of {@link Object#wait(long)} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_WAIT1;
	/** Default implementation of {@link Object#wait(long, int)} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_WAIT2;
	/** Default implementation of {@link Object#finalize()} for entities */
	public static MethodInterpreter<Object, Void> OBJECT_FINALIZE;
	/** Default implementation of {@link Object#clone()} for entities */
	public static MethodInterpreter<Object, Object> OBJECT_CLONE;
	/** Default implementation of {@link Object#getClass()} for entities */
	public static MethodInterpreter<Object, Class<?>> OBJECT_GET_CLASS; // Does this get delegated to here?
	/** Default implementation of {@link Identifiable#getIdentity()} for entities when not implemented in the entity type */
	public static MethodInterpreter<Identifiable, Object> OBJECT_GET_IDENTITY;
	/** All default methods implementations for entities */
	public static final Map<Method, MethodInterpreter<Object, ?>> OBJECT_METHODS;

	static {
		Method equals, hashCode, toString, notify, notifyAll, wait0, wait1, wait2, finalize, clone, getClass, getIdentity;
		try {
			equals = Object.class.getMethod("equals", Object.class);
			hashCode = Object.class.getMethod("hashCode");
			toString = Object.class.getMethod("toString");
			notify = Object.class.getMethod("notify");
			notifyAll = Object.class.getMethod("notifyAll");
			wait0 = Object.class.getMethod("wait");
			wait1 = Object.class.getMethod("wait", long.class);
			wait2 = Object.class.getMethod("wait", long.class, int.class);
			finalize = Object.class.getDeclaredMethod("finalize");
			clone = Object.class.getDeclaredMethod("clone");
			getClass = Object.class.getMethod("getClass");
			getIdentity = Identifiable.class.getMethod("getIdentity");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Could not find Object methods!", e);
		}
		Map<Method, MethodInterpreter<Object, ?>> defaultObjectMethods = new LinkedHashMap<>();
		OBJECT_EQUALS = new MethodInterpreter<Object, Boolean>(null, TypeTokens.get().OBJECT, equals) {
			@Override
			protected Boolean invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
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
					Object f1 = ((EntityReflector<Object>) p.getReflector()).getFields().get(id).get(proxy);
					Object f2 = ((EntityReflector<Object>) p.getReflector()).getFields().get(id).get(args[0]);
					if (!Objects.equals(f1, f2))
						return false;
				}
				return true;
			}
		};
		defaultObjectMethods.put(equals, OBJECT_EQUALS);
		OBJECT_HASH_CODE = new MethodInterpreter<Object, Integer>(null, TypeTokens.get().OBJECT, hashCode) {
			@Override
			protected Integer invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				EntityReflector<?>.ProxyMethodHandler p = getHandler(proxy);
				if (p.getReflector().getIdFields().isEmpty())
					return System.identityHashCode(proxy);
				Object[] idValues = new Object[p.getReflector().getIdFields().size()];
				int i = 0;
				for (int id : p.getReflector().getIdFields())
					idValues[i++] = ((EntityReflector<Object>) p.getReflector()).getFields().get(id).get(proxy);
				return Objects.hash(idValues);
			}
		};
		defaultObjectMethods.put(hashCode, OBJECT_HASH_CODE);
		OBJECT_TO_STRING = new MethodInterpreter<Object, String>(null, TypeTokens.get().OBJECT, toString) {
			@Override
			protected String invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				EntityReflector<?>.ProxyMethodHandler p = getHandler(proxy);
				StringBuilder str = new StringBuilder(p.getReflector().theRawType.getSimpleName());
				boolean hasPrintedField = false;
				for (int i = 0; i < p.getReflector().theFields.keySet().size(); i++) {
					if (p.getReflector().getFields().get(i).getGetter().isParentReference())
						continue; // Avoid stack overflows
					if (hasPrintedField)
						str.append(", ");
					else
						str.append('{');
					hasPrintedField = true;
					str.append(p.getReflector().theFields.keySet().get(i)).append("=")
					.append(((EntityReflector<Object>) p.getReflector()).getFields().get(i).get(proxy));
				}
				if (hasPrintedField)
					str.append('}');
				return str.toString();
			}
		};
		defaultObjectMethods.put(toString, OBJECT_TO_STRING);
		OBJECT_NOTIFY = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, notify) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				Proxy.getInvocationHandler(proxy).notify();
				return null;
			}
		};
		defaultObjectMethods.put(notify, OBJECT_NOTIFY);
		OBJECT_NOTIFY_ALL = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, notifyAll) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				Proxy.getInvocationHandler(proxy).notifyAll();
				return null;
			}
		};
		defaultObjectMethods.put(notifyAll, OBJECT_NOTIFY_ALL);
		OBJECT_WAIT0 = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, wait0) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) throws InterruptedException {
				InvocationHandler handler = Proxy.getInvocationHandler(proxy);
				handler.wait();
				return null;
			}
		};
		defaultObjectMethods.put(wait0, OBJECT_WAIT0);
		OBJECT_WAIT1 = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, wait1) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) throws InterruptedException {
				InvocationHandler handler = Proxy.getInvocationHandler(proxy);
				handler.wait((Long) args[0]);
				return null;
			}
		};
		defaultObjectMethods.put(wait1, OBJECT_WAIT1);
		OBJECT_WAIT2 = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, wait2) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) throws InterruptedException {
				InvocationHandler handler = Proxy.getInvocationHandler(proxy);
				handler.wait((Long) args[0], (Integer) args[1]);
				return null;
			}
		};
		defaultObjectMethods.put(wait2, OBJECT_WAIT2);
		OBJECT_FINALIZE = new MethodInterpreter<Object, Void>(null, TypeTokens.get().OBJECT, finalize) {
			@Override
			protected Void invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				return null;
			}
		};
		defaultObjectMethods.put(finalize, OBJECT_FINALIZE);
		OBJECT_CLONE = new MethodInterpreter<Object, Object>(null, TypeTokens.get().OBJECT, clone) {
			@Override
			protected Object invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) throws CloneNotSupportedException {
				throw new CloneNotSupportedException("Cannot clone proxies");
			}
		};
		defaultObjectMethods.put(clone, OBJECT_CLONE);
		OBJECT_GET_CLASS = new MethodInterpreter<Object, Class<?>>(null, TypeTokens.get().OBJECT, getClass) {
			@Override
			protected Class<?> invokeLocal(Object proxy, Object[] args, EntityInstanceBacking backing) {
				return getHandler(proxy).getReflector().theRawType;
			}
		};
		defaultObjectMethods.put(getClass, OBJECT_GET_CLASS);
		OBJECT_GET_IDENTITY = new MethodInterpreter<Identifiable, Object>(null, TypeTokens.get().of(Identifiable.class), getIdentity) {
			@Override
			protected Object invokeLocal(Identifiable proxy, Object[] args, EntityInstanceBacking backing) throws Throwable {
				return ((EntityReflector<Identifiable>.IdentifiableProxyMethodHandler) getHandler(proxy)).getIdentity(proxy);
			}
		};
		OBJECT_METHODS = Collections.unmodifiableMap(defaultObjectMethods);
	}

	private final List<EntityReflector<? super E>> theSupers;
	private final List<Map<ElementId, MethodInterpreter<E, ?>>> theSuperMethodMappings;
	final List<List<MethodInterpreter<E, ?>>> theSuperFieldGetters;
	final List<List<MethodInterpreter<E, ?>>> theSuperFieldSetters;
	private final Map<Class<?>, SuperPath> theSuperPaths;
	private final TypeToken<E> theType;
	private final Class<E> theRawType;
	private final Function<Method, String> theGetterFilter;
	private final Function<Method, String> theSetterFilter;
	private final Function<Method, String> theObservableFilter;
	private final QuickMap<String, ReflectedField<E, ?>> theFields;
	private final BetterSortedSet<MethodInterpreter<E, ?>> theMethods;
	private final Set<Integer> theIdFields;
	private final boolean hasObservableFields;
	private final boolean isIdentifiable;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;
	private final List<EntityReflectionMessage> theDirectUseErrors;

	private EntityReflector(Map<TypeToken<?>, EntityReflector<?>> supers, TypeToken<E> type, Class<E> raw,
		Function<Method, String> getterFilter, Function<Method, String> setterFilter, Function<Method, String> observableFilter,
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, Set<String> idFieldNames,
			List<EntityReflectionMessage> messages) {
		theDirectUseErrors = new LinkedList<>();
		theSupers = (List<EntityReflector<? super E>>) (List<?>) QommonsUtils.map(Arrays.asList(raw.getGenericInterfaces()), intf -> {
			TypeToken<? super E> intfT = (TypeToken<? super E>) type.resolveType(intf);
			EntityReflector<?> superR = supers.get(intfT);
			if (superR == null) {
				superR = new EntityReflector<>(supers, (TypeToken<E>) intfT, (Class<E>) TypeTokens.getRawType(intfT), // Generics hack
					getterFilter, setterFilter, observableFilter, customMethods, idFieldNames, messages);
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
		theObservableFilter = observableFilter == null ? new PrefixFilter("observe", 0) : observableFilter;

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
		theSuperPaths = Collections.unmodifiableMap(superPaths);
		MiscAttributes attrs = new MiscAttributes();
		populateMethods(theType, superPaths, fields, methods, //
			customMethods == null ? Collections.emptyMap() : customMethods, attrs, messages);
		theFields = fields.unmodifiable();
		theMethods = BetterCollections.unmodifiableSortedSet(methods);
		for (EntityReflector<? super E> superRef : theSupers)
			if (superRef.hasObservableFields)
				attrs.hasObservables = true;
		hasObservableFields = attrs.hasObservables;

		theProxyHandler = new MethodRetrievingHandler();
		theProxy = (E) Proxy.newProxyInstance(raw.getClassLoader(), new Class[] { raw }, theProxyHandler);

		MethodInterpreter<E, ?> equals;
		try {
			equals = getInterpreter(Object.class.getDeclaredMethod("equals", Object.class));
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Could not find Object.equals(Object)", e);
		}
		MethodInterpreter<E, ?> hashCode = getInterpreter(Object::hashCode);
		boolean equalOverride = equals != OBJECT_EQUALS;
		boolean hashCodeOverride = hashCode != OBJECT_HASH_CODE;
		if (Identifiable.class.isAssignableFrom(theRawType)) {
			isIdentifiable = true;
			if (equalOverride)
				messages.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, equals.getMethod(),
					"Object.equals(Object) should not be overridden for an extension of " + Identifiable.class.getName()));
			if (hashCodeOverride)
				messages.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, hashCode.getMethod(),
					"Object.hashCode() should not be overridden for an extension of " + Identifiable.class.getName()));
		} else
			isIdentifiable = !equalOverride && !hashCodeOverride;
	}

	static class MiscAttributes {
		boolean hasObservables;
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
		Map<Method, ? extends BiFunction<? super E, Object[], ?>> customMethods, MiscAttributes attrs,
			List<EntityReflectionMessage> errors) {
		Class<E> clazz = TypeTokens.getRawType(type);
		if (clazz == null)
			return;
		for (Method m : clazz.getDeclaredMethods()) {
			if (m.isSynthetic() || Modifier.isStatic(m.getModifiers()))
				continue;

			MethodInterpreter<E, ?> method = null; // Shouldn't have to initialize this, but the continues seem to be confusing the compiler
			method = methods.searchValue(m2 -> -m2.compare(m), BetterSortedList.SortedSearchFilter.OnlyMatch);
			if (method != null) { // Overridden by a subclass and handled
			} else {
				BiFunction<? super E, Object[], ?> custom = customMethods.get(m);
				if (custom != null) {
					method = new CustomMethod<>(this, m, custom);
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
					String fieldName = theGetterFilter.apply(m);
					ReflectedField<? super E, ?> field = fieldName == null ? null : fields.getIfPresent(fieldName);
					if (field != null)
						method = new CachedFieldGetter<>(this, m, (ReflectedField<E, ?>) field, handle);
					else {
						DefaultMethod<E, ?> defaultMethod = new DefaultMethod<>(this, m, handle);
						method = defaultMethod;
						if (m.getAnnotation(Cached.class) != null) {
							if (m.getParameterCount() > 0) {
								errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, m,
									"Cached Default methods cannot have parameters"));
							} else if (m.getReturnType() == void.class) {
								errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, m,
									"Cached Default methods cannot return void"));
							} else
								method = new CachedMethod<>(defaultMethod);
						}
					}
				} else {
					MethodInterpreter<Object, ?> objectMethod = OBJECT_METHODS.get(m);
					if (objectMethod != null) {
						method = new ObjectMethodWrapper<>(this, objectMethod);
					} else if (m.getDeclaringClass() == Object.class) {
						continue; // e.g. private void Object.registerNatives()
					} else {
						String fieldName = theGetterFilter.apply(m);
						if (fieldName != null) {
							method = getInterpreter(m); // Already added
							if (method == null)
								throw new IllegalStateException(m + " should have been a field");
						} else {
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
								if (field.id)
									errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, m, //
										"ID fields (" + field.getName() + ") should not be settable"));
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
								method = new FieldSetter<>(this, m, (ReflectedField<E, ?>) field, setterReturnType);
							} else if (clazz != Object.class) {
								fieldName = theObservableFilter.apply(m);
								if (fieldName != null && ObservableValue.class.isAssignableFrom(m.getReturnType())) {
									ReflectedField<? super E, ?> field = fields.getIfPresent(fieldName);
									if (field == null) {
										errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
											"No getter found for observer " + m + " of field " + fieldName));
										continue;
									}
									TypeToken<?> observableType = theType.resolveType(m.getGenericReturnType())
										.resolveType(ObservableValue.class.getTypeParameters()[0]);
									if (!TypeTokens.get().isAssignable(field.getType(), observableType)) {
										errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
											"Observer " + m + " for field " + field + " must provide a " + field.getType()));
										continue;
									}
									if (m.getReturnType() != ObservableValue.class && m.getReturnType() != SettableValue.class
										&& m.getReturnType() != ObservableField.class) {
										errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m, "Observer " + m
											+ " for field " + field + " is of unrecognized observable type " + m.getReturnType()));
										continue;
									}
									boolean setter = SettableValue.class.isAssignableFrom(m.getReturnType());
									if (setter && field.id)
										errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.WARNING, m, //
											"ID fields (" + field.getName() + ") should not be settable"));
									ObservableGetterType getterType;
									if (ObservableField.class.isAssignableFrom(m.getReturnType()))
										getterType = ObservableGetterType.FIELD_SETTABLE;
									else if (setter)
										getterType = ObservableGetterType.SETTABLE;
									else
										getterType = ObservableGetterType.OBSERVABLE;
									attrs.hasObservables = true;
									method = new ObservableFieldGetter<>(this, m, (ReflectedField<E, ?>) field, getterType);
								} else {
									theDirectUseErrors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m, "Method " + m
										+ " is not default or a recognized getter or setter, and its implementation is not provided"));
									continue;
								}
							}
						}
					}
				}
				method.setElement(methods.addElement(method, false).getElementId());
			}
			ObjectMethodOverride override = m.getAnnotation(ObjectMethodOverride.class);
			if (override != null) {
				OverrideMethod<E, ?> overrideMethod = null;
				try {
					switch (override.value()) {
					case equals:
						if (boolean.class != m.getReturnType() || m.getParameterCount() != 1 || m.getParameterTypes()[0] != Object.class) {
							errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
								"Method is tagged override for boolean Object.equals(Object), but the signature does not match"));
							continue;
						}
						overrideMethod = new OverrideMethod<>(this, Object.class.getDeclaredMethod("equals", Object.class), method);
						break;
					case hashCode:
						if (int.class != m.getReturnType() || m.getParameterCount() != 0) {
							errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
								"Method is tagged override for int Object.hashCode(), but the signature does not match"));
							continue;
						}
						overrideMethod = new OverrideMethod<>(this, Object.class.getDeclaredMethod("hashCode"), method);
						break;
					case toString:
						if (String.class != m.getReturnType() || m.getParameterCount() != 0) {
							errors.add(new EntityReflectionMessage(EntityReflectionMessageLevel.ERROR, m,
								"Method is tagged override for String Object.toString(), but the signature does not match"));
							continue;
						}
						overrideMethod = new OverrideMethod<>(this, Object.class.getDeclaredMethod("toString"), method);
						break;
					}
					if (overrideMethod == null)
						throw new IllegalStateException("Unrecognized or mishandled Object method override: " + override.value());
				} catch (NoSuchMethodException e) {
					throw new IllegalStateException(e);
				}
				overrideMethod.setElement(methods.addElement(overrideMethod, false).getElementId());
			}
		}
		if (clazz == Object.class) {} else if (theSupers.isEmpty()) {
			populateMethods((TypeToken<E>) TypeTokens.get().OBJECT, superPaths, // Generics hack
				fields, methods, customMethods, attrs, errors);
		} else {
			IntList superIndexes = new IntList();
			for (int i = 0; i < theSupers.size(); i++) {
				superIndexes.add(i);
				populateSuperPaths(superPaths, theSupers.get(i), superIndexes);
				superIndexes.clear();
				populateSuperMethods(fields, methods, theSupers.get(i), i, errors);
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
				else if (superMethod.get() instanceof CachedMethod) {
					DefaultMethod<E, ?> subDefault = new DefaultMethod<>(this, superMethod.get().getMethod(),
						((CachedMethod<E, ?>) superMethod.get()).getHandle());
					subMethod = new CachedMethod<>(subDefault);
				} else
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

	/** @return Errors that only matter if this type is used directly, not as a super type for another entity */
	public List<EntityReflectionMessage> getDirectUseErrors() {
		return Collections.unmodifiableList(theDirectUseErrors);
	}

	/** @return This entity's fields */
	public QuickMap<String, ReflectedField<E, ?>> getFields() {
		return theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theIdFields;
	}

	/**
	 * @return Whether this entity type exposes observable field getters. If this is true, then entities created with
	 *         {@link #newInstance(EntityInstanceBacking)} must be backed by an instance of {@link ObservableEntityInstanceBacking}.
	 */
	public boolean hasObservableFields() {
		return hasObservableFields;
	}

	/**
	 * @return Whether instances created with this reflector will be instances of {@link Identifiable}. This will be true either for entity
	 *         types that themselves extends {@link Identifiable} or for types that do not override {@link Object#equals(Object)} or
	 *         {@link Object#hashCode()} (via @{@link ObjectMethodOverride}).
	 */
	public boolean isIdentifiable() {
		return isIdentifiable;
	}

	/**
	 * @param value The value to test
	 * @return Whether the given value is an instance of this class
	 */
	public boolean isInstance(E value) {
		return value != null && Proxy.isProxyClass(value.getClass()) && getReflector(value) == this;
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
	 * Retrieves the value from a default-ed method of the entity. This will only work if the default method's value does not depend on any
	 * field values of the entity.
	 *
	 * @param <F> The type of the field
	 * @param fieldGetter Function to invoke the method
	 * @return The default value of the method
	 * @throws IllegalArgumentException If the method is not default-ed,
	 */
	public <F> F getDefaultValue(Function<? super E, F> fieldGetter) throws IllegalArgumentException {
		Method invoked;
		synchronized (this) {
			theProxyHandler.reset(m -> {
				String fieldName = theGetterFilter.apply(m);
				return fieldName != null && theFields.keySet().contains(fieldName);
			}, true);
			fieldGetter.apply(theProxy);
			invoked = theProxyHandler.getInvoked();
		}
		if (invoked == null) {
			throw new IllegalArgumentException("No " + theType + " method invoked");
		}
		MethodInterpreter<? super E, F> method = (MethodInterpreter<? super E, F>) getInterpreter(invoked);
		if (method instanceof CachedMethod)
			method = ((CachedMethod<? super E, F>) method).getDefaultMethod();
		if (!(method instanceof DefaultMethod))
			throw new IllegalArgumentException(method + " is not default-ed");
		else if (method.getMethod().getParameterCount() > 0)
			throw new IllegalArgumentException("Cannot get stateless default value for methods with parameters: " + method);
		try {
			return method.invokeLocal(theProxy, NO_ARGS, new EntityInstanceBacking() {
				@Override
				public void set(int fieldIndex, Object newValue) {
					throw new IllegalArgumentException("No default value available without actual entity");
				}

				@Override
				public Object get(int fieldIndex) {
					throw new IllegalArgumentException("No default value available without actual entity");
				}
			});
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalArgumentException("Could not invoke default method " + method.getName(), e);
		}
	}

	/**
	 * Creates a new instance of the type, backed by the given implementation. The backing must implement
	 * {@link ObservableEntityInstanceBacking}, if observable fields (e.g. {@link #observeField(Object, int)} or via observable entity
	 * getters) are to be supported.
	 *
	 * @param backing The implementation for getting, setting, and possibly {@link ObservableEntityInstanceBacking observing}, the entity's
	 *        field values
	 * @return The new entity instance
	 */
	public E newInstance(EntityInstanceBacking backing) {
		if (hasObservableFields && !(backing instanceof ObservableEntityInstanceBacking))
			throw new IllegalArgumentException(
				"This entity type requires observable support--use an instance of " + ObservableEntityInstanceBacking.class.getName());
		if (isIdentifiable)
			return (E) Proxy.newProxyInstance(theRawType.getClassLoader(), new Class[] { theRawType, Identifiable.class },
				new IdentifiableProxyMethodHandler(backing));
		else
			return (E) Proxy.newProxyInstance(theRawType.getClassLoader(), new Class[] { theRawType }, new ProxyMethodHandler(backing));
	}

	/**
	 * @param entity The entity whose field to watch
	 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field to watch
	 * @param listener The listener to receive change events when the value of the given field in the entity changes
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to stop receiving change events
	 */
	public Subscription watchField(E entity, int fieldIndex, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
		EntityInstanceBacking backing = getHandler(entity).theBacking;
		if (!(backing instanceof ObservableEntityInstanceBacking))
			throw new UnsupportedOperationException("Observation is not supported by this entity's backing");
		return ((ObservableEntityInstanceBacking<E>) backing).watchField(entity, fieldIndex, listener);
	}

	/**
	 * @param entity The entity whose fields to watch
	 * @param listener The listener to receive change events when the value of any field in the entity changes
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to stop receiving change events
	 */
	public Subscription watchAllFields(E entity, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
		EntityInstanceBacking backing = getHandler(entity).theBacking;
		if (!(backing instanceof ObservableEntityInstanceBacking))
			throw new UnsupportedOperationException("Observation is not supported by this entity's backing");
		return ((ObservableEntityInstanceBacking<E>) backing).watchAllFields(entity, listener);
	}

	/**
	 * @param entity The entity whose field to watch
	 * @param fieldIndex The {@link EntityReflector.ReflectedField#getFieldIndex() index} of the field to watch
	 * @return A {@link SettableValue} representing the value of the given field in the entity
	 */
	public ObservableField<? super E, ?> observeField(E entity, int fieldIndex) {
		EntityInstanceBacking backing = getHandler(entity).theBacking;
		if (!(backing instanceof ObservableEntityInstanceBacking))
			throw new UnsupportedOperationException("Observation is not supported by this entity's backing");
		return ((ObservableEntityInstanceBacking<E>) backing).observeField(entity, theFields.get(fieldIndex));
	}

	/**
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 * @param entity The entity whose field value to observe
	 * @param getter The getter for the field to observe
	 * @return The observable value for the field of the given entity
	 * @throws IllegalArgumentException If the given entity is not governed by an instance of this class
	 * @throws UnsupportedOperationException If the entity's backing does not support observation
	 */
	public static <E, F> ObservableField<? extends E, F> observeField(E entity, Function<? super E, ? extends F> getter)
		throws IllegalArgumentException, UnsupportedOperationException {
		if (!Proxy.isProxyClass(entity.getClass()))
			throw new IllegalArgumentException(entity.getClass() + " instance is not an entity");
		InvocationHandler proxyHandler = Proxy.getInvocationHandler(entity);
		if (!(proxyHandler instanceof EntityReflector.ProxyMethodHandler))
			throw new IllegalArgumentException(entity.getClass() + " instance is not an entity");
		EntityReflector<E>.ProxyMethodHandler handler = (EntityReflector<E>.ProxyMethodHandler) proxyHandler;
		EntityInstanceBacking backing = getHandler(entity).theBacking;
		if (!(backing instanceof ObservableEntityInstanceBacking))
			throw new UnsupportedOperationException("Observation is not supported by this entity's backing");
		int fieldIndex = handler.getReflector().getField(getter).getFieldIndex();
		return (ObservableField<? extends E, F>) ((ObservableEntityInstanceBacking<E>) backing).observeField(entity,
			handler.getReflector().getFields().get(fieldIndex));
	}

	@Override
	public String toString() {
		return theType.toString();
	}

	/**
	 * Allows the association of an entity with a custom piece of information (retrievable via {@link #getAssociated(Object, Object)})
	 *
	 * @param proxy The entity created with {@link #newInstance(EntityReflector.EntityInstanceBacking)}
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
	 * @param proxy The entity created with {@link #newInstance(EntityReflector.EntityInstanceBacking)}
	 * @param key The key the data is associated with
	 * @return The data {@link #associate(Object, Object, Object) associated} with the entity and key
	 */
	public Object getAssociated(E proxy, Object key) {
		return ((ProxyMethodHandler) Proxy.getInvocationHandler(proxy)).getAssociated(key);
	}

	/**
	 * @param <E> The type of the entity
	 * @param proxy The entity instance
	 * @return The reflector managing the entity
	 */
	public static <E> EntityReflector<E> getReflector(E proxy) {
		EntityReflector<E>.ProxyMethodHandler handler = getHandler(proxy);
		return handler.getReflector();
	}

	static <E> EntityReflector<E>.ProxyMethodHandler getHandler(E proxy) {
		return (EntityReflector<E>.ProxyMethodHandler) Proxy.getInvocationHandler(proxy);
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
		private final EntityInstanceBacking theBacking;
		private IdentityHashMap<Object, Object> theAssociated;
		private ListenerList<Consumer<? super EntityFieldChangeEvent<E, ?>>>[] theListeners;

		ProxyMethodHandler(EntityInstanceBacking backing) {
			theBacking = backing;
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

		Subscription addFieldListener(E entity, int fieldIndex, Consumer<? super EntityFieldChangeEvent<E, ?>> listener) {
			if (!(theBacking instanceof ObservableEntityInstanceBacking))
				throw new UnsupportedOperationException("Backing does not support observation");
			if (theListeners == null)
				theListeners = new ListenerList[theFields.keySize()];
			if (theListeners[fieldIndex] == null) {
				theListeners[fieldIndex] = ListenerList.build().withInUse(new ListenerList.InUseListener() {
					private Subscription fieldSub;

					@Override
					public void inUseChanged(boolean inUse) {
						if (inUse) {
							fieldSub = ((ObservableEntityInstanceBacking<E>) theBacking).addListener(entity, fieldIndex,
								change -> fieldChanged(entity, fieldIndex, change));
						} else {
							fieldSub.unsubscribe();
							fieldSub = null;
						}
					}
				}).build();
			}
			return theListeners[fieldIndex].add(listener, true)::run;
		}

		<F> void fieldChanged(E entity, int fieldIndex, FieldChange<F> change) {
			if (theListeners == null)
				return;
			ReflectedField<E, F> field = (ReflectedField<E, F>) theFields.get(fieldIndex);
			ListenerList<Consumer<? super EntityFieldChangeEvent<E, ?>>> listeners = theListeners[field.getFieldIndex()];
			if (listeners == null || listeners.isEmpty())
				return;
			EntityFieldChangeEvent<E, F> event = ((ObservableEntityInstanceBacking<E>) theBacking).createFieldChangeEvent(entity, field,
				change.oldValue, change.newValue, change.cause);
			try (Transaction t = event.use()) {
				listeners.forEach(//
					l -> l.accept(event));
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			MethodInterpreter<E, ?> interpreter = getInterpreter(method);
			if (interpreter == null)
				throw new IllegalStateException("Method " + method + " not found!");
			SuperPath path;
			Class<?> dc = method.getDeclaringClass();
			if (getSuper().isEmpty() || dc == theRawType)
				path = null;
			else
				path = theSuperPaths.get(dc);
			return interpreter.invoke((E) proxy, dc, path, args, theBacking);
		}

		protected <T> T invokeOnType(E proxy, MethodInterpreter<? super E, T> method, Object[] args) throws Throwable {
			if (method.getReflector() != getReflector())
				method = findMethodFromSuper(method);
			return ((MethodInterpreter<E, T>) method).invoke(proxy, theRawType, null, args, theBacking);
		}
	}

	private class IdentifiableProxyMethodHandler extends ProxyMethodHandler {
		private Object theIdentity;

		IdentifiableProxyMethodHandler(EntityInstanceBacking backing) {
			super(backing);
		}

		Object getIdentity(E entity) {
			if (theIdentity == null) {
				if (theIdFields.isEmpty())
					theIdentity = new EntityInstanceIdentity<>(entity);
				else
					theIdentity = new EntityFieldIdentity<>(getReflector(), entity);
			}
			return theIdentity;
		}
	}
}
