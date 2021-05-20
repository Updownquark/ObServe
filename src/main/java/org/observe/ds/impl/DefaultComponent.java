package org.observe.ds.impl;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.ds.ComponentController;
import org.observe.ds.ComponentStage;
import org.observe.ds.DSComponent;
import org.observe.ds.Dependency;
import org.observe.ds.DependencyService;
import org.observe.ds.DependencyServiceStage;
import org.observe.ds.Service;
import org.qommons.Causable;
import org.qommons.Transaction;

class DefaultComponent<C> implements ComponentController<C> {
	private final DefaultDependencyService<C> theService;
	private final String theName;
	private final Function<? super ComponentController<C>, ? extends C> theSupplier;
	private final Consumer<? super C> theDisposer;
	private C theComponentValue;
	private final Map<Service<?>, Function<? super C, ?>> theProvided;
	private final Map<Service<?>, ? extends DefaultDependency<C, ?>> theDependencies;
	private final SettableValue<ComponentStage> theStage;
	private final SettableValue<Boolean> isAvailable;
	private int theUnsatisfied;
	private int theDynamicUnsatisfied;
	private boolean isRemoved;

	DefaultComponent(DefaultDependencyService<C> service, String name, Function<? super ComponentController<C>, ? extends C> supplier,
		Consumer<? super C> disposer, Map<Service<?>, Function<? super C, ?>> provided,
		Map<Service<?>, ? extends DefaultDependency<C, ?>> dependencies, boolean available) {
		theService = service;
		theName = name;
		theSupplier = supplier;
		theDisposer = disposer;
		theProvided = provided;
		theDependencies = dependencies;
		theStage = SettableValue.build(ComponentStage.class).withLock(service.getComponents()).withValue(ComponentStage.Defined).build();
		isAvailable = SettableValue.build(boolean.class).withLock(service.getComponents()).withValue(available).build();

		reset(available);
	}

	@Override
	public DefaultDependencyService<C> getDependencyService() {
		return theService;
	}

	@Override
	public DSComponent<C> getComponent() {
		return new DefaultComponentWrapper<>(this);
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public C getComponentValue() {
		return theComponentValue;
	}

	@Override
	public Set<? extends Service<?>> getProvided() {
		return theProvided.keySet();
	}

	@Override
	public <S> S provide(Service<S> service) {
		if (theComponentValue == null)
			return null;
		Function<? super C, ? extends S> provided = (Function<? super C, ? extends S>) theProvided.get(service);
		if (provided == null)
			throw new IllegalArgumentException("No such provision: " + theName + "-->" + service.getName());
		return provided.apply(theComponentValue);
	}

	@Override
	public Map<Service<?>, ? extends Dependency<C, ?>> getDependencies() {
		return theDependencies;
	}

	@Override
	public ObservableValue<ComponentStage> getStage() {
		return theStage.unsettable();
	}

	@Override
	public ObservableValue<Boolean> isAvailable() {
		return isAvailable.unsettable();
	}

	void setStage(ComponentStage stage, Object cause) {
		switch (stage) {
		case PreSatisfied:
		case Satisfied:
		case Complete:
			if (theComponentValue == null) {
				theComponentValue = theSupplier.apply(this);
				if (theComponentValue == null)
					throw new IllegalStateException("Component values may not be null");
			}
			break;
		default:
			if (theComponentValue != null && theDisposer != null) {
				theDisposer.accept(theComponentValue);
				theComponentValue = null;
			}
		}
		theStage.set(stage, cause);
	}

	@Override
	public DefaultComponent<C> setAvailable(boolean available) {
		if (isRemoved)
			throw new IllegalStateException("This component has been removed");
		if (isAvailable.get() == available)
			return this;
		Causable cause = Causable.simpleCause(this);
		try (Transaction t2 = cause.use(); Transaction t = theService.lock(cause)) {
			if (isRemoved)
				throw new IllegalStateException("This component has been removed");
			if (isAvailable.get() == available)
				return this;
			if (theService.isActivating())
				throw new IllegalStateException(
					"Component availability cannot be changed as a result of changes to the dependency service");
			if (available) {
				isAvailable.set(true, cause);
				reset(true);
			}
			if (available)
				theService.activate(this, cause);
			else
				theService.deactivate(this, cause);
			if (!available) {
				for (DefaultDependency<C, ?> dep : theDependencies.values())
					dep.clear(cause);
				reset(false);
				isAvailable.set(false, cause);
			}
		}
		return this;
	}

	private void reset(boolean available) {
		theUnsatisfied = theDynamicUnsatisfied = 0;
		for (DefaultDependency<C, ?> dep : theDependencies.values()) {
			if (dep.getMinimum() > 0) {
				theUnsatisfied++;
				if (dep.isDynamic())
					theDynamicUnsatisfied++;
			}
		}
		if (!available)
			setStage(ComponentStage.Unsatisfied, null);
		else if (theService.getStage().get() == DependencyServiceStage.Uninitialized)
			setStage(ComponentStage.Defined, null);
		else
			setStage(theUnsatisfied == 0 ? ComponentStage.Complete : ComponentStage.Unsatisfied, null);
	}

	boolean preInit(Object cause) {
		if (!isAvailable.get())
			return false;
		setStage(theUnsatisfied == 0 ? ComponentStage.Satisfied : ComponentStage.Unsatisfied, cause);
		return theUnsatisfied == 0;
	}

	@Override
	public DefaultComponent<C> remove() {
		if (isRemoved)
			throw new IllegalStateException("This component has been removed");
		Causable cause = Causable.simpleCause(this);
		try (Transaction t2 = cause.use(); Transaction t = theService.lock(cause)) {
			if (isRemoved)
				throw new IllegalStateException("This component has been removed");
			isRemoved = true;
			doRemove(cause);
		}
		return this;
	}

	void doRemove(Causable cause) {
		if (isAvailable.get()) {
			if (theService.isActivating())
				throw new IllegalStateException(
					"Component availability cannot be changed as a result of changes to the dependency service");
			theService.deactivate(this, cause);
			for (DefaultDependency<C, ?> dep : theDependencies.values())
				dep.clear(cause);
			reset(false);
			isAvailable.set(false, cause);
		}
		theService.remove(this);
		setStage(ComponentStage.Removed, cause);
	}

	@Override
	public String toString() {
		return theName + "(" + theStage.get() + ")";
	}

	int getUnsatisfied() {
		return theUnsatisfied;
	}

	public int getDynamicUnsatisfied() {
		return theDynamicUnsatisfied;
	}

	void modifyUnsatisfied(boolean satisfied, boolean dynamic, Object cause) {
		if (satisfied) {
			theUnsatisfied--;
			if (dynamic)
				theDynamicUnsatisfied--;
			if (theUnsatisfied == 0)
				nowSatisfied(cause);
		} else {
			theUnsatisfied++;
			if (dynamic)
				theDynamicUnsatisfied++;
			// This needs to be done by the service itself or else components using this component
			// will have a reference to an unsatisfied component, which is always illegal
			// if (theUnsatisfied == 1)
			// theStage.set(theService.isInitialized() ? ComponentStage.Defined : ComponentStage.Unsatisfied, null);
		}
	}

	void dependencyModified(Object cause) {
		if (theUnsatisfied == 0)
			nowSatisfied(cause);
	}

	void nowSatisfied(Object cause) {
		switch (theService.getStage().get()) {
		case Uninitialized:
			break;
		case Initializing:
		case PreInitialized:
			if (getStage().get() != ComponentStage.Defined)
				setStage(ComponentStage.Satisfied, cause);
			break;
		case Initialized:
			setStage(ComponentStage.Complete, cause);
			break;
		}
	}

	static class DefaultComponentWrapper<C> implements DSComponent<C> {
		private final DefaultComponent<C> theComponent;

		DefaultComponentWrapper(DefaultComponent<C> component) {
			theComponent = component;
		}

		@Override
		public DependencyService<C> getDependencyService() {
			return theComponent.getDependencyService();
		}

		@Override
		public String getName() {
			return theComponent.getName();
		}

		@Override
		public C getComponentValue() {
			return theComponent.getComponentValue();
		}

		@Override
		public Set<? extends Service<?>> getProvided() {
			return theComponent.getProvided();
		}

		@Override
		public <S> S provide(Service<S> service) throws IllegalArgumentException {
			return theComponent.provide(service);
		}

		@Override
		public Map<Service<?>, ? extends Dependency<C, ?>> getDependencies() {
			return theComponent.getDependencies();
		}

		@Override
		public ObservableValue<ComponentStage> getStage() {
			return theComponent.getStage();
		}

		@Override
		public ObservableValue<Boolean> isAvailable() {
			return theComponent.isAvailable();
		}

		@Override
		public int hashCode() {
			return theComponent.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof DefaultComponentWrapper && theComponent.equals(((DefaultComponentWrapper<C>) obj).theComponent);
		}

		@Override
		public String toString() {
			return theComponent.toString();
		}
	}
}
