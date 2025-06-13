package org.observe.ds.impl;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.ds.ComponentController;
import org.observe.ds.DSComponent;
import org.observe.ds.DependencyService;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.MultiMap;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.collect.SortedMultiMap;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.qommons.osgi.ComponentBasedExecutor;
import org.qommons.tree.BetterTreeMultiMap;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

/**
 * A {@link DependencyService} implementation that determines component dependencies and behavior based on annotations on the component
 * class's methods.
 *
 * @see Component
 * @see Dependency
 * @see Activate
 * @see Release
 * @see Deactivate
 * @see Dispose
 */
public class AnnotatedDependencyService extends DefaultTypedDependencyService<Object> implements ComponentBasedExecutor {
	static class ComponentMethod<T> {
		private final Invokable<T, ?> theMethod;
		private final Field theField;
		private final boolean isConstructor;
		private final int theControllerParamIndex;

		ComponentMethod(Invokable<T, ?> m, boolean constructor) {
			theMethod = m;
			theField = null;
			isConstructor = constructor;
			if (!m.isAccessible())
				m.setAccessible(true);
			int controllerIdx = -1;
			for (int i = 0; i < m.getParameters().size(); i++) {
				if (TypeTokens.get().isAssignable(TypeTokens.get().of(ComponentController.class), m.getParameters().get(i).getType())) {
					if (controllerIdx >= 0)
						throw new IllegalStateException(
							m.getDeclaringClass().getName() + "." + m.getName() + " declares multiple controller parameters");
					controllerIdx = i;
				} else if (i > (controllerIdx >= 0 ? 1 : 0))
					throw new IllegalStateException(m.getDeclaringClass().getName() + "." + m.getName() + " declares multiple parameters");
			}
			theControllerParamIndex = controllerIdx;
		}

		ComponentMethod(Field f) {
			theField = f;
			theMethod = null;
			isConstructor = false;
			if (!f.isAccessible())
				f.setAccessible(true);
			theControllerParamIndex = -1;
		}

		Class<?> getType() {
			if (theField != null)
				return theField.getType();
			else if (theMethod.getParameters().size() == (theControllerParamIndex >= 0 ? 1 : 0))
				return null;
			else
				return TypeTokens.getRawType(theMethod.getParameters().get(theControllerParamIndex == 0 ? 1 : 0).getType());
		}

		Object invoke(ComponentController<Object> controller, Object param) {
			if (!isConstructor && controller.getComponentValue() == null)
				return null;
			return invoke(controller, controller.getComponentValue(), param);
		}

		Object invoke(ComponentController<Object> controller, Object instance, Object param) {
			try {
				if (theField != null) {
					theField.set(instance, param);
					return null;
				} else {
					switch (theMethod.getParameters().size()) {
					case 0:
						return theMethod.invoke(isConstructor ? null : (T) instance);
					case 1:
						if (theControllerParamIndex < 0)
							return theMethod.invoke(isConstructor ? null : (T) instance, param);
						else
							return theMethod.invoke(isConstructor ? null : (T) instance, controller);
					default:
						if (theControllerParamIndex == 0)
							return theMethod.invoke(isConstructor ? null : (T) instance, controller, param);
						else
							return theMethod.invoke(isConstructor ? null : (T) instance, param, controller);
					}
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Could not access method " + theMethod.getDeclaringClass() + "." + theMethod.getName(), e);
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException("Error invoking method " + theMethod.getDeclaringClass() + "." + theMethod.getName(), e);
			} catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof RuntimeException) {
					throw (RuntimeException) e.getTargetException();
				} else if (e.getTargetException() instanceof Error) {
					throw (Error) e.getTargetException();
				} else {
					throw new CheckedExceptionWrapper(e.getTargetException());
				}
			}
		}

		@Override
		public String toString() {
			if (theField != null)
				return theField.getDeclaringClass().getName() + "." + theField.getName();
			else
				return theMethod.getDeclaringClass().getName() + "." + theMethod.getName();
		}
	}

	class ComponentActivate<T> extends ComponentMethod<T> {
		private final Function<T, ObservableValue<String>> theLoadStatus;

		ComponentActivate(Invokable<T, ?> m, boolean constructor, Function<T, ObservableValue<String>> loadStatus) {
			super(m, constructor);
			theLoadStatus = loadStatus;
		}

		@Override
		Object invoke(ComponentController<Object> controller, Object param) {
			String preStatus = theCurrentLoadStatus;
			SimpleObservable<Void> until = new SimpleObservable<>();
			ObservableValue<String> status = theLoadStatus.apply((T) controller.getComponentValue());
			status.changes().takeUntil(until).act(evt -> theCurrentLoadStatus = evt.getNewValue());
			try {
				return super.invoke(controller, param);
			} finally {
				until.act(null);
				theCurrentLoadStatus = preStatus;
			}
		}
	}

	enum StageChangeType {
		Activate, Deactivate;
	}

	private static final Object NULL_CONFIG = new Object() {
		@Override
		public String toString() {
			return "no value";
		}
	};

	static class Component<T> {
		private final Class<T> theComponentType;
		private final ClassLoader theComponentClassLoader;
		private final ComponentMethod<T> theCreator;
		private final Map<ComponentMethod<?>, Object> theConfiguration;
		private final Map<Class<?>, CDependency<T, ?>> theDependencies;
		private final Map<StageChangeType, List<ComponentMethod<T>>> theStageChanges;
		private final List<ComponentMethod<T>> theDisposes;
		private final SimpleObservable<Void> theUntil;

		private boolean isNewValue;

		Component(Class<T> componentType, ClassLoader classLoader, ComponentMethod<T> creator) {
			theComponentType = componentType;
			theComponentClassLoader = classLoader;
			this.theCreator = creator;
			theConfiguration = new LinkedHashMap<>();
			theDependencies = new HashMap<>();
			theStageChanges = new EnumMap<>(StageChangeType.class);
			theDisposes = new ArrayList<>();
			theUntil = new SimpleObservable<>();
		}

		void addConfiguration(ComponentMethod<?> configMethod, Object configValue) {
			theConfiguration.put(configMethod, configValue);
		}

		void addDependency(Class<?> service, CDependency<T, ?> dependency) {
			if (null != theDependencies.put(service, dependency))
				throw new IllegalArgumentException(
					theComponentType.getName() + ": Duplicate dependency methods for service " + service.getName());
		}

		void addDependencyRelease(Class<?> service, ComponentMethod<T> release) {
			CDependency<T, ?> dependency = theDependencies.get(release.getType());
			if (dependency == null)
				System.err.println(release + "'s accepted service (" + service.getName() + " is not a dependency");
			dependency.releases.add(release);
		}

		void onStageChange(StageChangeType type, ComponentMethod<T> action) {
			theStageChanges.computeIfAbsent(type, __ -> new ArrayList<>()).add(action);
		}

		void onDispose(ComponentMethod<T> dispose) {
			theDisposes.add(dispose);
		}

		Object create(ComponentController<Object> controller) {
			isNewValue = true;
			controller.getStage().changes().takeUntil(theUntil).act(evt -> {
				Thread thread = Thread.currentThread();
				ClassLoader preCCL = thread.getContextClassLoader();
				try {
					thread.setContextClassLoader(theComponentClassLoader);
					List<ComponentMethod<T>> listeners;
					if (evt.getOldValue() != null && evt.getOldValue().isActive() && !evt.getNewValue().isActive())
						listeners = theStageChanges.get(StageChangeType.Deactivate);
					else if (!(evt.getOldValue() != null && evt.getOldValue().isActive()) && evt.getNewValue().isActive()) {
						if (isNewValue) {
							isNewValue = false;
							for (CDependency<T, ?> dep : theDependencies.values()) {
								dep.monitorService(controller, theUntil);
							}
						}
						listeners = theStageChanges.get(StageChangeType.Activate);
					} else
						listeners = null;
					if (listeners != null)
						for (ComponentMethod<T> listener : listeners)
							listener.invoke(controller, null);
				} finally {
					thread.setContextClassLoader(preCCL);
				}
			});
			Thread thread = Thread.currentThread();
			ClassLoader preCCL = thread.getContextClassLoader();
			try {
				thread.setContextClassLoader(theComponentClassLoader);
				Object instance = theCreator.invoke(controller, null);
				for (Map.Entry<ComponentMethod<?>, Object> config : theConfiguration.entrySet()) {
					try {
						if (config.getValue() == NULL_CONFIG)
							config.getKey().invoke(controller, instance, null);
						else
							config.getKey().invoke(controller, instance, config.getValue());
					} catch (RuntimeException e) {
						System.err.println(theComponentType.getName() + " configuration " + config.getKey().toString() + "("
							+ config.getValue() + ") failed");
						e.printStackTrace();
					}
				}
				return instance;
			} finally {
				thread.setContextClassLoader(preCCL);
			}
		}

		void dispose() {
			theUntil.onNext(null);
			for (ComponentMethod<T> m : theDisposes)
				m.invoke(null, null);
		}
	}

	static class CDependency<T, S> {
		final TypeDefinedService<S> service;
		final ComponentMethod<T> accepter;
		final List<ComponentMethod<T>> releases;
		final int max;

		CDependency(ComponentMethod<T> accepter, int max) {
			if (accepter.getType() == null)
				throw new IllegalArgumentException("Dependency method accepts no service: " + accepter.toString());
			service = new TypeDefinedService<>(TypeTokens.get().of((Class<S>) accepter.getType()));
			this.accepter = accepter;
			this.max = max;
			releases = new ArrayList<>(2);
		}

		void monitorService(ComponentController<Object> controller, Observable<?> until) {
			int[] count = new int[1];
			ObservableCollection<S> services = controller.getDependencies(service);
			Subscription sub = services.subscribe(evt -> {
				if (evt.getIndex() >= max)
					return;
				switch (evt.getType()) {
				case add:
					if (count[0] < max) {
						accepter.invoke(controller, evt.getNewValue());
						count[0]++;
					}
					break;
				case remove:
					release(controller, evt.getOldValue());
					count[0]--;
					break;
				case set:
					release(controller, evt.getOldValue());
					accepter.invoke(controller, evt.getNewValue());
					break;
				}
			}, true);
			until.take(1).act(__ -> sub.unsubscribe());
		}

		private void release(ComponentController<Object> controller, S serviceInstance) {
			for (ComponentMethod<T> release : releases)
				release.invoke(controller, serviceInstance);
		}
	}

	String theCurrentLoadStatus;

	/** Creates a dependency service with standard {@link ReentrantReadWriteLock} locking */
	public AnnotatedDependencyService() {
		this(new RRWLockingStrategy(new ReentrantReadWriteLock(), "DS", ThreadConstraint.ANY));
	}

	/**
	 * Creates a dependency service with custom locking
	 *
	 * @param lock The lock to use to thread-safe this dependency service
	 */
	public AnnotatedDependencyService(Transactable lock) {
		super(lock);
	}

	private static final Format<Double> DOUBLE_FORMAT = Format.doubleFormat(new DecimalFormat("0.0"));
	private static final Format<Instant> INSTANT_FORMAT = SpinnerFormat.flexDate(Instant::now, "YYYY MMM dd", null);

	@Override
	public <T> ComponentController<Object> loadComponent(Class<T> componentType, ClassLoader loader, Map<String, String> configuration) {
		TypeToken<T> ct = TypeTokens.get().of(componentType);
		ComponentMethod<T> constructor = null;
		org.observe.ds.impl.Component compAnn = componentType.getAnnotation(org.observe.ds.impl.Component.class);
		if (compAnn != null && !compAnn.creator().isEmpty()) {
			for (Method m : componentType.getDeclaredMethods()) {
				if (!Modifier.isStatic(m.getModifiers()) || !m.getName().equals(compAnn.creator()))
					continue;
				boolean ok = m.getParameterTypes().length == 0 //
					|| (m.getParameterTypes().length == 1 && ComponentController.class.isAssignableFrom(m.getParameterTypes()[0]));
				if (ok) {
					if (!componentType.isAssignableFrom(m.getReturnType()))
						throw new IllegalArgumentException("creator method " + m.getName() + " has return type "
							+ m.getReturnType().getName() + ", not " + componentType.getName());
					constructor = new ComponentMethod<>(ct.method(m), true);
					break;
				}
			}
			if (constructor == null)
				throw new IllegalArgumentException(
					"No acceptable creator method " + compAnn.creator() + "() for component " + componentType.getName());
		} else {
			for (Constructor<?> c : componentType.getDeclaredConstructors()) {
				if (c.getParameterTypes().length == 1 && ComponentController.class.isAssignableFrom(c.getParameterTypes()[0])) {
					constructor = new ComponentMethod<>(ct.constructor(c), true);
					break;
				} else if (c.getParameterTypes().length == 0) {
					constructor = new ComponentMethod<>(ct.constructor(c), true);
					break;
				}
			}
			if (constructor == null)
				throw new IllegalArgumentException("No acceptable constructor for component " + componentType.getName());
		}
		Component<T> comp = new Component<>(componentType, loader, constructor);
		DSComponent.Builder<Object> builder = this.inject(componentType.getName(), comp::create);
		if (compAnn != null)
			builder.initiallyAvailable(compAnn.loadImmediately());
		else
			builder.initiallyAvailable(true);
		if (compAnn != null && compAnn.provides().length > 0) {
			for (Class<?> service : compAnn.provides()) {
				if (!service.isAssignableFrom(componentType))
					throw new IllegalArgumentException(
						componentType.getName() + " does not implement provided service " + service.getName());
				provide(builder, service);
			}
		} else
			provide(builder, componentType);

		Map<String, Member> configMethods = new HashMap<>();
		for (Field f : componentType.getDeclaredFields()) {
			ComponentMethod<T> depMethod = new ComponentMethod<>(f);
			Dependency dep = f.getAnnotation(Dependency.class);
			if (dep != null) {
				ensureAccessible(f);
				CDependency<T, ?> cdep = new CDependency<>(depMethod, dep.max());
				comp.addDependency(depMethod.getType(), cdep);
				builder.depends(cdep.service, dependency -> dependency.minimum(dep.min()).dynamic(dep.dynamic()));
				continue;
			}
			Configure config = f.getAnnotation(Configure.class);
			if (config != null) {
				ensureAccessible(f);
				if (configuration.containsKey(config.value())) {
					Member prev = configMethods.put(config.value(), f);
					if (prev != null)
						throw new IllegalArgumentException("Multiple members specified for component configuration "
							+ componentType.getName() + "." + config.value() + " (" + prev.getName() + " and " + f.getName() + ")");
				} else if (!config.optional())
					throw new IllegalArgumentException("Non-optional component configuration " + componentType.getName() + "."
						+ config.value() + " (Field " + f.getName() + ") was not provided");
			}
		}
		for (Method m : componentType.getDeclaredMethods()) {
			if (m.isSynthetic())
				continue;
			Dispose dispose = m.getAnnotation(Dispose.class);
			if (dispose != null) {
				if (m.getParameterTypes().length != 0)
					throw new IllegalStateException(componentType + ": Dispose methods cannot accept parameters");
				ensureAccessible(m);
				comp.onDispose(new ComponentMethod<>(ct.method(m), false));
				continue;
			}
			Dependency dep = m.getAnnotation(Dependency.class);
			if (dep != null) {
				ComponentMethod<T> depMethod = new ComponentMethod<>(ct.method(m), false);
				if (depMethod.getType() == null)
					throw new IllegalArgumentException(
						"Dependency method accepts no service: " + componentType.getName() + "." + m.getName());
				CDependency<T, ?> cdep = new CDependency<>(depMethod, dep.max());
				comp.addDependency(depMethod.getType(), cdep);
				builder.depends(cdep.service, dependency -> dependency.minimum(dep.min()).dynamic(dep.dynamic()));
				continue;
			}
			// Check for unmet required configuration here, but don't actually configure it in case the specified order matters
			Configure config = m.getAnnotation(Configure.class);
			if (config != null) {
				ensureAccessible(m);
				if (configuration.containsKey(config.value())) {
					Member prev = configMethods.put(config.value(), m);
					if (prev != null)
						throw new IllegalArgumentException("Multiple methods specified for component configuration "
							+ componentType.getName() + "." + config.value() + " (" + prev.getName() + " and " + m.getName() + ")");
				} else if (!config.optional())
					throw new IllegalArgumentException("Non-optional component configuration " + componentType.getName() + "."
						+ config.value() + " (Method " + m.getName() + ") was not provided");
			}
			Activate activate = m.getAnnotation(Activate.class);
			if (activate != null) {
				String status = compAnn == null ? "" : compAnn.loadStatus();
				Function<T, ObservableValue<String>> loadStatus;
				if (status.isEmpty())
					loadStatus = LambdaUtils.constantFn(ObservableValue.of("Initializing " + componentType.getSimpleName()), "simpleStatus",
						null);
				else if (status.endsWith("()")) {
					Method statusMethod;
					try {
						statusMethod = componentType.getDeclaredMethod(status.substring(0, status.length() - 2));
						if (!statusMethod.isAccessible())
							statusMethod.setAccessible(true);
					} catch (NoSuchMethodException | SecurityException e) {
						statusMethod = null;
						System.err.println("Load status method " + componentType.getName() + "." + status + " could not be retrieved: ");
						e.printStackTrace();
					}

					Method fStatusMethod = statusMethod;
					Type statusType = statusMethod == null ? null : statusMethod.getGenericReturnType();
					if (statusType == null) {
						loadStatus = LambdaUtils.constantFn(ObservableValue.of("Initializing " + componentType.getSimpleName()),
							"simpleStatus", null);
					} else if (statusType == String.class) {
						loadStatus = inst -> {
							try {
								return ObservableValue.of((String) fStatusMethod.invoke(inst));
							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
								System.err
								.println("Load status method " + componentType.getName() + "." + status + " could not be invoked: ");
								e.printStackTrace();
								return ObservableValue.of("Initializing " + componentType.getSimpleName());
							}
						};
					} else if (statusType instanceof ParameterizedType) {
						ParameterizedType pst = (ParameterizedType) statusType;
						if (pst.getActualTypeArguments().length == 1 && pst.getRawType() instanceof Class//
							&& ObservableValue.class.isAssignableFrom((Class<?>) pst.getRawType())//
							&& pst.getActualTypeArguments()[0] == String.class) {
							loadStatus = inst -> {
								try {
									return (ObservableValue<String>) fStatusMethod.invoke(inst);
								} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
									System.err.println(
										"Load status method " + componentType.getName() + "." + status + " could not be invoked: ");
									e.printStackTrace();
									return ObservableValue.of("Initializing " + componentType.getSimpleName());
								}
							};
						} else {
							System.err.println("Load status method " + componentType.getName() + "." + status + " returns " + statusType
								+ ", which cannot be interpreted.  Return a String or an ObservableValue<String>.");
							loadStatus = LambdaUtils.constantFn(ObservableValue.of("Initializing " + componentType.getSimpleName()),
								"simpleStatus", null);
						}
					} else {
						System.err.println("Load status method " + componentType.getName() + "." + status + " returns " + statusType
							+ ", which cannot be interpreted.  Return a String or an ObservableValue<String>.");
						loadStatus = LambdaUtils.constantFn(ObservableValue.of("Initializing " + componentType.getSimpleName()),
							"simpleStatus", null);
					}
				} else
					loadStatus = LambdaUtils.constantFn(ObservableValue.of(status), status, null);
				ComponentActivate<T> cm = new ComponentActivate<>(ct.method(m), false, loadStatus);
				if (cm.getType() != null)
					throw new IllegalArgumentException(componentType + ": Activate method " + cm
						+ " has too many parameters or has unrecognized parameters: " + cm.getType());
				comp.onStageChange(StageChangeType.Activate, cm);
			}
			Deactivate deactivate = m.getAnnotation(Deactivate.class);
			if (deactivate != null) {
				ComponentMethod<T> cm = new ComponentMethod<>(ct.method(m), false);
				if (cm.getType() != null)
					throw new IllegalArgumentException(componentType + ": Deactivate method " + cm
						+ " has too many parameters or has unrecognized parameters: " + cm.getType());
				comp.onStageChange(StageChangeType.Deactivate, cm);
			}
		}
		// Do configuration separately in case the order of the configuration matters
		for (Map.Entry<String, String> config : configuration.entrySet()) {
			Member configMember = configMethods.get(config.getKey());
			Type configType;
			ComponentMethod<?> cm;
			if (configMember == null) {
				System.err.println("WARNING: Configuration '" + config.getKey() + "' is not accepted by any @"
					+ Configure.class.getSimpleName() + "-tagged method in component " + componentType.getName());
				continue;
			} else if (configMember instanceof Field) {
				Field f = (Field) configMember;
				configType = f.getType();
				cm = new ComponentMethod<>(f);
			} else if (configMember instanceof Method) {
				Method m2 = (Method) configMember;
				cm = new ComponentMethod<>(ct.method(m2), false);
				if (m2.getParameterCount() == 0) {
					if (config.getValue() != null && !config.getValue().isEmpty())
						System.err
						.println("WARNING: Configuration " + componentType.getName() + "." + config.getKey() + " specifies value '"
							+ config.getValue() + "', but no value is accepted by configuration method " + m2.getName());
					configType = null;
				} else if (m2.getParameterCount() != 1)
					throw new IllegalArgumentException("Configuration methods may only accept a single parameter: "
						+ componentType.getName() + "." + m2.getName() + "("
						+ StringUtils.print(", ", Arrays.asList(m2.getGenericParameterTypes()), t -> TypeTokens.get().of(t).toString()));
				else
					configType = m2.getGenericParameterTypes()[0];
			} else {
				System.out.println("What configuration is this? " + configMember.getClass().getName());
				continue;
			}
			if (configType == null)
				comp.addConfiguration(cm, NULL_CONFIG);
			else {
				try {
					comp.addConfiguration(cm, parseConfigurationValue(config.getValue(), configType, loader));
				} catch (ParseException | RuntimeException e) {
					throw new IllegalArgumentException("Could not parse configuration " + componentType + "." + config.getKey() + " as a "
						+ TypeTokens.get().of(configType) + " for method " + cm.toString(), e);
				}
			}
		}
		for (Method m : componentType.getDeclaredMethods()) {
			Release release = m.getAnnotation(Release.class);
			if (release != null) {
				ComponentMethod<T> cm = new ComponentMethod<>(ct.method(m), false);
				if (cm.getType() == null)
					throw new IllegalStateException(
						componentType.getName() + "." + m.getName() + " does not accept the service to release");
				else
					comp.addDependencyRelease(cm.getType(), cm);
			}
		}
		builder.disposeWhenInactive(__ -> comp.dispose());
		return builder.build();
	}

	private static void ensureAccessible(AccessibleObject m) {
		// Though this is deprecated as of Java 9, the suggested alternate to this, AccessibleObject.canAccess(Object),
		// requires a target object, which we don't have here.
		boolean accessible = m.isAccessible();
		if (!accessible)
			m.setAccessible(true);
	}

	private <T> void provide(DSComponent.Builder<Object> builder, Class<T> componentType) {
		builder.provides(new TypeDefinedService<>(TypeTokens.get().of(componentType)), c -> (T) c);
		Class<? super T> superC = componentType.getSuperclass();
		if (superC != null && superC != Object.class)
			provide(builder, superC);
		for (Class<?> intf : componentType.getInterfaces())
			provide(builder, (Class<? super T>) intf);
	}

	@Override
	public String getLoadStatus() {
		switch (getStage().get()) {
		case Uninitialized:
			return "Loading...";
		case Initializing:
			String loading = theCurrentLoadStatus;
			if (loading == null)
				return "Initializing...";
			return loading;
		case Initialized:
			return "Loaded";
		}
		return "Loaded";
	}

	@Override
	public Object loadingComplete(Set<String> startComponents) {
		if (!startComponents.isEmpty()) {
			for (DefaultComponent<Object> component : getComponentControllers()) {
				if (startComponents.contains(component.getName()))
					component.setAvailable(true);
			}
		}
		init();
		String msg = printUnsatisfiedComponents();
		if (msg != null)
			System.out.println(msg);
		return null;
	}

	private static Object parseConfigurationValue(String value, Type type, ClassLoader loader) throws ParseException {
		if (type instanceof Class) {
			Class<?> configType = (Class<?>) type;
			if (configType.isAssignableFrom(String.class))
				return value;
			else if (configType.isAssignableFrom(Double.class) || configType == double.class) {
				return DOUBLE_FORMAT.parse(value);
			} else if (configType.isAssignableFrom(Long.class) || configType == long.class) {
				return Format.LONG.parse(value);
			} else if (configType.isAssignableFrom(Integer.class) || configType == int.class) {
				return Format.INT.parse(value);
			} else if (configType.isAssignableFrom(Boolean.class) || configType == boolean.class) {
				return Format.BOOLEAN.parse(value);
			} else if (configType.isAssignableFrom(Instant.class)) {
				return INSTANT_FORMAT.parse(value);
			} else if (configType.isAssignableFrom(Duration.class)) {
				return Format.DURATION.parse(value);
			} else if (configType.isEnum()) {
				try {
					return Format.parseEnum((Class<? extends Enum<?>>) configType, value.trim());
				} catch (IllegalArgumentException e) {
					throw new ParseException("No such " + configType.getSimpleName() + " value '" + value.trim() + "'", 0);
				}
			} else if (configType.isAssignableFrom(URL.class)) {
				URL found = loader.getResource(value);
				if (found == null)
					throw new ParseException("Could not locate resource '" + value + "' in " + loader, 0);
				return found;
			} else
				throw new IllegalArgumentException("Unrecognized configuration value type: " + configType.getName());
		} else if (type instanceof ParameterizedType) {
			ParameterizedType parameterized = (ParameterizedType) type;
			Class<?> raw = TypeTokens.getRawType(parameterized.getRawType());
			if (Collection.class.isAssignableFrom(raw)) {
				TypeToken<?> param = TypeTokens.get().of(parameterized).resolveType(Collection.class.getTypeParameters()[0]);
				Collection<Object> collection;
				if (raw.isAssignableFrom(List.class))
					collection = new ArrayList<>();
				else if (raw.isAssignableFrom(Set.class))
					collection = new LinkedHashSet<>();
				else if (raw.isAssignableFrom(NavigableSet.class)) {
					if (!(TypeTokens.get().isComparable(param)))
						throw new IllegalArgumentException("A sorted set must have a comparable parameter, not " + param);
					collection = new TreeSet<>();
				} else
					throw new IllegalArgumentException("Unrecognized collection type: " + raw.getName());

				int start = 0;
				int delimiter = value.indexOf('+'); // OSGi manifest attribute values can't use ','
				do {
					String v = value.substring(start, delimiter < 0 ? value.length() : delimiter);
					try {
						if (!collection.add(parseConfigurationValue(v, param.getType(), loader)))
							throw new IllegalArgumentException("Duplicate values in collection: " + v);
					} catch (ParseException e) {
						ParseException e2 = new ParseException(e.getMessage(), start + e.getErrorOffset());
						e2.setStackTrace(e.getStackTrace());
						throw e2;
					}
					start = delimiter + 1;
					delimiter = value.indexOf('+', start);
				} while (start > 0);
				return collection;
			} else if (Map.class.isAssignableFrom(raw)) {
				Map<Object, Object> map;
				TypeToken<?> keyType = TypeTokens.get().of(parameterized).resolveType(Map.class.getTypeParameters()[0]);
				TypeToken<?> valueType = TypeTokens.get().of(parameterized).resolveType(Map.class.getTypeParameters()[0]);
				if (raw.isAssignableFrom(Map.class))
					map = new LinkedHashMap<>();
				else if (raw.isAssignableFrom(NavigableMap.class)) {
					if (!(TypeTokens.get().isComparable(keyType)))
						throw new IllegalArgumentException("A sorted map must have a comparable key parameter, not " + keyType);
					map = new TreeMap<>();
				} else
					throw new IllegalArgumentException("Unrecognized map type: " + raw.getName());

				int start = 0;
				int delimiter = value.indexOf('+'); // OSGi manifest attribute values can't use ','
				do {
					String entry = value.substring(start, delimiter < 0 ? value.length() : delimiter);
					int sep = entry.indexOf('#'); // OSGi manifest attribute values can't use '=' or ':'
					if (sep < 0)
						throw new ParseException("No '#' found in entry to separate key from value", start);
					Object key, v;
					try {
						key = parseConfigurationValue(entry.substring(0, sep), keyType.getType(), loader);
					} catch (ParseException e) {
						ParseException e2 = new ParseException(e.getMessage(), start + e.getErrorOffset());
						e2.setStackTrace(e.getStackTrace());
						throw e2;
					}
					try {
						v = parseConfigurationValue(entry.substring(sep + 1), valueType.getType(), loader);
					} catch (ParseException e) {
						ParseException e2 = new ParseException(e.getMessage(), sep + 1 + e.getErrorOffset());
						e2.setStackTrace(e.getStackTrace());
						throw e2;
					}
					if (null != map.putIfAbsent(key, v))
						throw new IllegalArgumentException("Duplicate keys in map: " + key);
					start = delimiter + 1;
					delimiter = value.indexOf('+', start);
				} while (start > 0);
				return map;
			} else if (MultiMap.class.isAssignableFrom(raw)) {
				MultiMap<Object, Object> map;
				TypeToken<?> keyType = TypeTokens.get().of(parameterized).resolveType(MultiMap.class.getTypeParameters()[0]);
				TypeToken<?> valueType = TypeTokens.get().of(parameterized).resolveType(MultiMap.class.getTypeParameters()[0]);
				if (raw.isAssignableFrom(MultiMap.class))
					map = BetterHashMultiMap.build().buildMultiMap();
				else if (raw.isAssignableFrom(SortedMultiMap.class)) {
					if (!(TypeTokens.get().isComparable(keyType)))
						throw new IllegalArgumentException("A sorted multi-map must have a comparable key parameter, not " + keyType);
					map = BetterTreeMultiMap.buildSorted((o1, o2) -> ((Comparable<Object>) o1).compareTo(o2)).buildMultiMap();
				} else
					throw new IllegalArgumentException("Unrecognized multi-map type: " + raw.getName());

				int start = 0;
				int valuesEnd = value.indexOf(']');
				do {
					if (valuesEnd < 0)
						throw new ParseException("Multi-map entries must end with ']'", start);
					String entry = value.substring(start, valuesEnd < 0 ? value.length() : valuesEnd);
					int sep = entry.indexOf('#'); // OSGi manifest attribute values can't use '=' or ':'
					if (sep < 0)
						throw new ParseException("No ':' found in entry to separate key from values", start);
					if (sep == entry.length() - 1 || entry.charAt(sep + 1) != '[')
						throw new ParseException("Values after separator ':' must start with '['", start + sep + 1);
					Object key;
					try {
						key = parseConfigurationValue(entry.substring(0, sep), keyType.getType(), loader);
					} catch (ParseException e) {
						ParseException e2 = new ParseException(e.getMessage(), start + e.getErrorOffset());
						e2.setStackTrace(e.getStackTrace());
						throw e2;
					}
					int valueStart = sep + 2;
					int delimiter = entry.indexOf('+', valueStart); // OSGi manifest attribute values can't use ','
					do {
						try {
							Object v = parseConfigurationValue(entry.substring(sep + 1), valueType.getType(), loader);
							map.add(key, v);
						} catch (ParseException e) {
							ParseException e2 = new ParseException(e.getMessage(), start + delimiter + e.getErrorOffset());
							e2.setStackTrace(e.getStackTrace());
							throw e2;
						}
						valueStart = delimiter + 1;
						delimiter = entry.indexOf('+', valueStart);
					} while (valueStart > 0);
					start = valuesEnd + 1;
					valuesEnd = value.indexOf(']', start);
				} while (valuesEnd < value.length() - 1);
				return map;
			} else
				throw new IllegalArgumentException("Unrecognized configuration value type: " + raw.getName());
		} else
			throw new IllegalArgumentException("Unrecognized configuration value type: " + TypeTokens.get().of(type));
	}
}
