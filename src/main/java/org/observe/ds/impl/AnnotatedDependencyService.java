package org.observe.ds.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.ds.ComponentController;
import org.observe.ds.DSComponent;
import org.observe.util.TypeTokens;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.osgi.ComponentBasedExecutor;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

public class AnnotatedDependencyService extends DefaultTypedDependencyService<Object> implements ComponentBasedExecutor {
	static class ComponentMethod<T> {
		private final Invokable<T, ?> theMethod;
		private final boolean isConstructor;
		private final int theControllerParamIndex;

		ComponentMethod(Invokable<T, ?> m, boolean constructor) {
			theMethod = m;
			isConstructor = constructor;
			if (!m.isAccessible())
				m.setAccessible(true);
			int controllerIdx = -1;
			for (int i = 0; i < m.getParameters().size(); i++) {
				if (TypeTokens.get().of(ComponentController.class).isAssignableFrom(m.getParameters().get(i).getType())) {
					if (controllerIdx >= 0)
						throw new IllegalStateException(
							m.getDeclaringClass().getName() + "." + m.getName() + " declares multiple controller parameters");
					controllerIdx = i;
				} else if (i > (controllerIdx >= 0 ? 1 : 0))
					throw new IllegalStateException(m.getDeclaringClass().getName() + "." + m.getName() + " declares multiple parameters");
			}
			theControllerParamIndex = controllerIdx;
		}

		Class<?> getType() {
			if (theMethod.getParameters().size() == (theControllerParamIndex >= 0 ? 1 : 0))
				return null;
			return TypeTokens.getRawType(theMethod.getParameters().get(theControllerParamIndex == 0 ? 1 : 0).getType());
		}

		Object invoke(ComponentController<Object> controller, Object param) {
			if (!isConstructor && controller.getComponentValue() == null)
				return null;
			try {
				switch (theMethod.getParameters().size()) {
				case 0:
					return theMethod.invoke(isConstructor ? null : (T) controller.getComponentValue());
				case 1:
					if (theControllerParamIndex < 0)
						return theMethod.invoke(isConstructor ? null : (T) controller.getComponentValue(), param);
					else
						return theMethod.invoke(isConstructor ? null : (T) controller.getComponentValue(), controller);
				default:
					if (theControllerParamIndex == 0)
						return theMethod.invoke(isConstructor ? null : (T) controller.getComponentValue(), controller, param);
					else
						return theMethod.invoke(isConstructor ? null : (T) controller.getComponentValue(), param, controller);
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
			return theMethod.getDeclaringClass().getName() + "." + theMethod.getName();
		}
	}

	enum StageChangeType {
		Activate, Deactivate;
	}

	static class Component<T> {
		final ComponentMethod<T> creator;
		final Map<Class<?>, CDependency<T, ?>> dependencies;
		final Map<StageChangeType, List<ComponentMethod<T>>> stageChanges;
		final List<ComponentMethod<T>> disposes;
		final SimpleObservable<Void> until;

		private boolean isNewValue;

		Component(ComponentMethod<T> creator) {
			this.creator = creator;
			dependencies = new HashMap<>();
			stageChanges = new HashMap<>();
			disposes = new ArrayList<>();
			until = new SimpleObservable<>();
		}

		Object create(ComponentController<Object> controller) {
			isNewValue = true;
			controller.getStage().changes().takeUntil(until).act(evt -> {
				List<ComponentMethod<T>> listeners;
				if (evt.getOldValue() != null && evt.getOldValue().isActive() && !evt.getNewValue().isActive())
					listeners = stageChanges.get(StageChangeType.Deactivate);
				else if (!(evt.getOldValue() != null && evt.getOldValue().isActive()) && evt.getNewValue().isActive()) {
					if (isNewValue) {
						isNewValue = false;
						for (CDependency<T, ?> dep : dependencies.values()) {
							dep.monitorService(controller, until);
						}
					}
					listeners = stageChanges.get(StageChangeType.Activate);
				} else
					listeners = null;
				if (listeners != null)
					for (ComponentMethod<T> listener : listeners)
						listener.invoke(controller, null);
			});
			Object value = creator.invoke(controller, null);
			return value;
		}

		void dispose() {
			until.onNext(null);
			for (ComponentMethod<T> m : disposes)
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
				if (evt.getIndex() > max)
					return;
				switch (evt.getType()) {
				case add:
					if (count[0] == max) {
						release(controller, services.get(max - 1));
						count[0]--;
					}
					accepter.invoke(controller, evt.getNewValue());
					count[0]++;
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

		private void release(ComponentController<Object> controller, S service) {
			for (ComponentMethod<T> release : releases)
				release.invoke(controller, service);
		}
	}

	public AnnotatedDependencyService() {
		this(new RRWLockingStrategy(new ReentrantReadWriteLock(), "DS", ThreadConstraint.ANY));
	}

	public AnnotatedDependencyService(Transactable lock) {
		super(lock);
	}

	public AnnotatedDependencyService loadComponents(Class<?>... componentTypes) {
		for (Class<?> componentType : componentTypes)
			loadComponent(componentType);
		return this;
	}

	@Override
	public <T> ComponentController<Object> loadComponent(Class<T> componentType) {
		ComponentMethod<T> constructor = null;
		TypeToken<T> ct = TypeTokens.get().of(componentType);
		for (Constructor<?> c : componentType.getDeclaredConstructors()) {
			if (c.getParameterTypes().length == 1 && ComponentController.class.isAssignableFrom(c.getParameterTypes()[0])) {
				constructor = new ComponentMethod<>(ct.constructor(c), true);
				break;
			} else if (c.getParameterTypes().length == 0)
				constructor = new ComponentMethod<>(ct.constructor(c), true);
		}
		if (constructor == null)
			throw new IllegalArgumentException("No acceptable constructor for component " + componentType.getName());
		Component<T> comp = new Component<>(constructor);
		DSComponent.Builder<Object> builder = this.inject(componentType.getName(), comp::create);
		org.observe.ds.impl.Component compAnn = componentType.getAnnotation(org.observe.ds.impl.Component.class);
		if (compAnn != null)
			builder.initiallyAvailable(compAnn.loadImmediately());
		else
			builder.initiallyAvailable(true);
		if (compAnn != null && compAnn.provides().length > 0) {
			for (Class<?> service : compAnn.provides()) {
				if (!service.isAssignableFrom(componentType))
					throw new IllegalArgumentException(componentType.getName()+" does not implement provided service "+service.getName());
				provide(builder, service);
			}
		} else
			provide(builder, componentType);
		for (Method m : componentType.getDeclaredMethods()) {
			if (m.isSynthetic())
				continue;
			Dispose dispose = m.getAnnotation(Dispose.class);
			if (dispose != null) {
				if (m.getParameterTypes().length != 0)
					throw new IllegalStateException(componentType + ": Dispose methods cannot accept parameters");
				if (!m.isAccessible())
					m.setAccessible(true);
				comp.disposes.add(new ComponentMethod<>(ct.method(m), false));
				continue;
			}
			Dependency dep = m.getAnnotation(Dependency.class);
			if (dep != null) {
				ComponentMethod<T> depMethod = new ComponentMethod<>(ct.method(m), false);
				if (depMethod.getType() == null)
					throw new IllegalArgumentException(
						"Dependency method accepts no service: " + componentType.getName() + "." + m.getName());
				CDependency<T, ?> cdep = new CDependency<>(depMethod, dep.max());
				if (null != comp.dependencies.put(depMethod.getType(), cdep))
					throw new IllegalArgumentException(componentType + ": Duplicate dependency methods for service " + depMethod.getType());
				builder.depends(cdep.service, dependency -> {
					dependency.minimum(dep.min()).dynamic(dep.dynamic());
				});
				continue;
			}
			Activate activate = m.getAnnotation(Activate.class);
			if (activate != null) {
				ComponentMethod<T> cm = new ComponentMethod<>(ct.method(m), false);
				if (cm.getType() != null)
					throw new IllegalArgumentException(componentType + ": Activate method " + cm + " has too many parameters");
				comp.stageChanges.computeIfAbsent(StageChangeType.Activate, __ -> new ArrayList<>(2)).add(cm);
			}
			Deactivate deactivate = m.getAnnotation(Deactivate.class);
			if (deactivate != null) {
				ComponentMethod<T> cm = new ComponentMethod<>(ct.method(m), false);
				if (cm.getType() != null)
					throw new IllegalArgumentException(componentType + ": Deactivate method " + cm + " has too many parameters");
				comp.stageChanges.computeIfAbsent(StageChangeType.Deactivate, __ -> new ArrayList<>(2)).add(cm);
			}
		}
		for (Method m : componentType.getDeclaredMethods()) {
			Release release = m.getAnnotation(Release.class);
			if (release != null) {
				ComponentMethod<T> cm = new ComponentMethod<>(ct.method(m), false);
				if (cm.getType() == null)
					throw new IllegalStateException(
						componentType.getName() + "." + m.getName() + " does not accept the service to release");
				else if (!comp.dependencies.containsKey(cm.getType()))
					System.err.println(componentType.getName() + "." + m.getName() + "'s accepted service (" + cm.getType().getName()
						+ " is not a dependency");
				comp.dependencies.get(cm.getType()).releases.add(cm);
			}
		}
		builder.disposeWhenInactive(__ -> comp.dispose());
		ComponentController<Object> built = builder.build();
		return built;
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
}
