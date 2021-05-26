package org.observe.ds.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.ds.ComponentController;
import org.observe.ds.ComponentStage;
import org.observe.ds.DSComponent;
import org.observe.ds.DSComponent.Builder;
import org.observe.ds.Dependency;
import org.observe.ds.DependencyService;
import org.observe.ds.DependencyServiceStage;
import org.observe.ds.Service;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.RRWLockingStrategy;

/**
 * Default {@link DependencyService} implementation
 *
 * @param <C> The type of the components managed by the service
 */
public class DefaultDependencyService<C> implements DependencyService<C> {
	private final ObservableCollection<DefaultComponent<C>> theComponents;
	private final ObservableSet<Service<?>> theServices;

	private final Map<Service<?>, List<DefaultComponent<C>>> theServiceProviders;
	private final Map<Service<?>, List<DefaultDependency<C, ?>>> theDependents;

	private final ListenerList<Runnable> theScheduledTasks;

	private SettableValue<DependencyServiceStage> theStage;

	/** @param lock The lock to facilitate thread safety */
	public DefaultDependencyService(Transactable lock) {
		theComponents = ObservableCollection.build((Class<DefaultComponent<C>>) (Class<?>) DefaultComponent.class)
			.withLocker(new RRWLockingStrategy(lock)).build();
		theServices = ObservableSet.build(TypeTokens.get().keyFor(Service.class).<Service<?>> wildCard())
			.withLocker(new RRWLockingStrategy(lock)).build();

		theServiceProviders = new LinkedHashMap<>();
		theDependents = new LinkedHashMap<>();
		theScheduledTasks = ListenerList.build().build();
		theStage = SettableValue.build(DependencyServiceStage.class).withLock(new RRWLockingStrategy(lock))
			.withValue(DependencyServiceStage.Uninitialized).build();
	}

	@Override
	public Builder<C> inject(String componentName, Function<? super ComponentController<C>, ? extends C> component) {
		return new DefaultComponentBuilder(componentName, component);
	}

	/** @return The controllers for all components in this service */
	protected ObservableCollection<DefaultComponent<C>> getComponentControllers() {
		return theComponents.flow().unmodifiable().collect();
	}

	@Override
	public ObservableCollection<? extends DSComponent<C>> getComponents() {
		return theComponents.flow()
			.transform((Class<DSComponent<C>>) (Class<?>) DSComponent.class, tx -> tx.cache(false).map(c -> c.getComponent())).unmodifiable(false)
			.collect();
	}

	@Override
	public ObservableSet<Service<?>> getServices() {
		return theServices.flow().unmodifiable(false).collect();
	}

	@Override
	public ObservableValue<DependencyServiceStage> getStage() {
		return theStage.unsettable();
	}

	/** To be called after the initial round of components have been defined */
	public void init() {
		Causable cause = Causable.simpleCause(this);
		try (Transaction causeT = cause.use(); Transaction t = theComponents.lock(true, cause)) {
			switch (theStage.get()) {
			case Initialized:
				throw new IllegalStateException("This service has already finished initializing");
			case Initializing:
				throw new IllegalStateException("Recursive init call");
			default:
				break;
			}
			if (isActivating)
				throw new IllegalStateException("Illegal initialization call inside activate");
			theStage.set(DependencyServiceStage.Initializing, cause);
			isActivating = true;
			try {
				BetterSet<DefaultDependency<C, ?>> componentPath = BetterHashSet.build().unsafe().buildSet();
				boolean progress = true;
				while (progress) {
					progress = false;
					for (DefaultComponent<C> comp : theComponents) {
						if (comp.getStage().get() != ComponentStage.Defined)
							continue;
						boolean allSatisified = true;
						for (Service<?> dep : comp.getDependencies().keySet()) {
							for (DefaultComponent<C> provider : theServiceProviders.getOrDefault(dep, Collections.emptyList())) {
								if (provider.getStage().get() == ComponentStage.Defined) {
									allSatisified = false;
									break;
								}
							}
							if (!allSatisified)
								break;
						}
						if (allSatisified) {
							progress = true;
							if (comp.preInit(cause))
								satisfied(comp, componentPath, cause);
						}
					}
				}
				for (DefaultComponent<C> comp : theComponents) {
					if (comp.getStage().get() == ComponentStage.Defined) {
						if (comp.preInit(cause))
							satisfied(comp, componentPath, cause);
					}
				}
				theStage.set(DependencyServiceStage.PreInitialized, cause);
				for (DefaultComponent<C> comp : theComponents) {
					try {
						if (!comp.isAvailable().get() || comp.getUnsatisfied() > 0)
							comp.setStage(ComponentStage.Unsatisfied, cause);
						else
							comp.setStage(ComponentStage.Complete, cause);
					} catch (RuntimeException | Error e) {
						System.err.println("Error initializing component " + comp.getName());
						e.printStackTrace();
					}
				}
			} finally {
				isActivating = false;
			}
			runScheduledTasks();
			theStage.set(DependencyServiceStage.Initialized, cause);
		}
	}

	/** @return A message describing the status of all unsatisfied components in this service */
	public String printUnsatisfiedComponents() {
		StringBuilder str = new StringBuilder();
		for (DSComponent<C> component : getComponents()) {
			if (component.isAvailable().get() && !component.getStage().get().isActive()) {
				str.append("Unsatisfied component: ").append(component.getName()).append('\n');
				for (Entry<org.observe.ds.Service<?>, ? extends org.observe.ds.Dependency<C, ?>> dep : component.getDependencies()
					.entrySet()) {
					if (dep.getValue().getProviders().size() < dep.getValue().getMinimum()) {
						str.append('\t').append(dep.getKey()).append(": ")//
						.append(dep.getValue().getProviders().size()).append(" of ").append(dep.getValue().getMinimum()).append('\n');
					}
				}
			}
		}
		if (str.length() == 0)
			return null;
		else if (str.charAt(str.length() - 1) == '\n')
			str.deleteCharAt(str.length() - 1);
		return str.toString();
	}

	@Override
	public void schedule(Runnable task) {
		if (isActivating)
			theScheduledTasks.add(task, true);
		else
			task.run();
	}

	private void runScheduledTasks() {
		ListenerList.Element<Runnable> task = theScheduledTasks.poll(0);
		while (task != null) {
			try {
				task.get().run();
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			task = theScheduledTasks.poll(0);
		}
	}

	@Override
	public void close() {
		Causable cause = Causable.simpleCause(this);
		try (Transaction causeT = cause.use(); Transaction t = theComponents.lock(true, cause)) {
			while (!theComponents.isEmpty()) {
				theComponents.getLast().doRemove(cause);
			}
		}
	}

	void built(DefaultComponent<C> component) {
		Causable cause = Causable.simpleCause(component);
		try (Transaction causeT = cause.use(); Transaction t = theComponents.lock(true, cause)) {
			if (isActivating)
				throw new IllegalStateException("Cannot inject components during initialization or activation");
			theComponents.add(component);
			if (component.isAvailable().get())
				activate(component, cause);
			if (theStage.get() != DependencyServiceStage.Uninitialized)
				component.setStage((!component.isAvailable().get() || component.getUnsatisfied() > 0) ? ComponentStage.Unsatisfied
					: ComponentStage.Complete, cause);
		}
	}

	Transaction lock(Object cause) {
		return theComponents.lock(true, cause);
	}

	private boolean isActivating;

	boolean isActivating() {
		return isActivating;
	}

	void activate(DefaultComponent<C> component, Object cause) {
		if (isActivating)
			throw new IllegalStateException("Component availability cannot be changed as a result of changes to the dependency service");
		isActivating = true;
		try {
			// Register the new component's provided services and dependencies
			for (Service<?> provided : component.getProvided())
				theServiceProviders.computeIfAbsent(provided, __ -> new ArrayList<>(3)).add(component);
			for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values())
				theDependents.computeIfAbsent(dep.getTarget(), __ -> new ArrayList<>()).add(dep);

			boolean somethingSatisfied = getStage().get() != DependencyServiceStage.Uninitialized;
			BetterSet<DefaultDependency<C, ?>> componentPath = BetterHashSet.build().unsafe().buildSet();
			boolean wasSatisfied = false;
			while (somethingSatisfied) {
				somethingSatisfied = false;
				// Attempt to satisfy the component's dependencies with satisfied provider components
				for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
					List<DefaultComponent<C>> providers = theServiceProviders.get(dep.getTarget());
					if (providers == null)
						continue;
					for (DefaultComponent<C> provider : providers) {
						if (provider.getUnsatisfied() == 0 && dep.satisfy(provider, cause))
							somethingSatisfied = true;
					}
				}
				if (!wasSatisfied) {
					if (component.getUnsatisfied() == 0) {
						// Satisfy other components' dependencies with this provider
						wasSatisfied = true;
						somethingSatisfied |= satisfied(component, componentPath, cause);
					} else if (component.getUnsatisfied() == component.getDynamicUnsatisfied()
						&& isInSatisfiedDynamicCycle(component, componentPath)) {
						wasSatisfied = true;
						component.setStage(ComponentStage.PreSatisfied, cause);
						satisfied(component, componentPath, cause);
					}
				}
			}
		} finally {
			isActivating = false;
		}
		runScheduledTasks();
	}

	private boolean isInSatisfiedDynamicCycle(DefaultComponent<C> component, BetterSet<DefaultDependency<C, ?>> componentPath) {
		for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
			boolean[] added = new boolean[1];
			ElementId el = componentPath.getOrAdd(dep, null, null, false, () -> added[0] = true).getElementId();
			if (!added[0]) {
				// We're in a cycle, but is it dynamic?
				for (CollectionElement<DefaultDependency<C, ?>> dep2 = componentPath.getElement(el); dep2 != null; dep2 = componentPath
					.getAdjacentElement(dep2.getElementId(), true)) {
					if (dep.isDynamic())
						return true;
				}
				return false; // Static dependency
			}
			try {
				int needed = dep.getMinimum() - dep.getProviders().size();
				if (needed <= 0)
					continue;
				List<DefaultComponent<C>> providers = theServiceProviders.get(dep.getTarget());
				if (providers == null)
					return false;
				for (DefaultComponent<C> provider : providers) {
					if (dep.getProviders().contains(provider))
						continue;
					if (!isInSatisfiedDynamicCycle(provider, componentPath))
						continue;
					needed--;
					if (needed == 0)
						break;
				}
				if (needed > 0)
					return false;
			} finally {
				componentPath.remove(dep);
			}
		}
		return true;
	}

	private boolean satisfied(DefaultComponent<C> component, BetterSet<DefaultDependency<C, ?>> componentPath, Object cause) {
		boolean somethingSatisfied = false;
		for (Service<?> provided : component.getProvided()) {
			theServices.add(provided);
			List<DefaultDependency<C, ?>> dependents = theDependents.get(provided);
			if (dependents == null)
				continue;
			for (DefaultDependency<C, ?> dep : dependents) {
				boolean wasActive = dep.getRealOwner().getStage().get().isActive();
				if (dep.satisfy(component, cause)) {
					somethingSatisfied = true;
				}
				if (!wasActive && getStage().get() != DependencyServiceStage.Uninitialized) {
					if (dep.getRealOwner().getUnsatisfied() == 0)
						satisfied(dep.getRealOwner(), componentPath, cause);
					else if (dep.getRealOwner().getUnsatisfied() == dep.getRealOwner().getDynamicUnsatisfied()
						&& isInSatisfiedDynamicCycle(dep.getRealOwner(), componentPath)) {
						wasActive = true;
						dep.getRealOwner().setStage(ComponentStage.PreSatisfied, cause);
						satisfied(dep.getRealOwner(), componentPath, cause);
					}
				}
			}
		}
		return somethingSatisfied;
	}

	void deactivate(DefaultComponent<C> component, Object cause) {
		if (isActivating)
			throw new IllegalStateException("Component availability cannot be changed as a result of changes to the dependency service");
		isActivating = true;
		try {
			// Unregister the component
			for (Service<?> provided : component.getProvided())
				theServiceProviders.get(provided).remove(component);
			for (Dependency<C, ?> dep : component.getDependencies().values())
				theDependents.get(dep.getTarget()).remove(dep);

			if (component.getUnsatisfied() == 0)
				unsatisfied(component, cause);
		} finally {
			isActivating = false;
		}
	}

	private void unsatisfied(DefaultComponent<C> component, Object cause) {
		for (Service<?> provided : component.getProvided()) {
			if (!theServiceProviders.get(provided).stream().anyMatch(c -> c.getStage().get().isActive()))
				theServices.remove(provided);
			List<DefaultDependency<C, ?>> dependents = theDependents.get(provided);
			if (dependents == null)
				continue;
			for (DefaultDependency<C, ?> dep : dependents) {
				boolean wasSatisfied = dep.getRealOwner().getUnsatisfied() == 0;
				if (dep.remove(component, cause) && wasSatisfied && dep.getRealOwner().getUnsatisfied() > 0) {
					unsatisfied(dep.getRealOwner(), cause);
					dep.getRealOwner().setStage(
						theStage.get() == DependencyServiceStage.Uninitialized ? ComponentStage.Defined : ComponentStage.Unsatisfied,
							cause);
				}
			}
		}
	}

	void remove(DefaultComponent<C> component) {
		theComponents.remove(component);
	}

	/**
	 * @param builder The builder to create the component from
	 * @return The component to create in this service
	 */
	protected DefaultComponent<C> createComponent(DSComponent.Builder<C> builder) {
		return new DefaultComponent<>(this, builder.getName(), builder.getSupplier(), builder.isDisposedWhenInactive(), //
			builder.getProvided(), (Map<Service<?>, ? extends DefaultDependency<C, ?>>) builder.getDependencies(),
			builder.isInitiallyAvailable());
	}

	/**
	 * @param <S> The type of the service dependency
	 * @param componentName The name of the owner component
	 * @param owner Supplies the owner (after it has been created)
	 * @param builder The builder to create the dependency from
	 * @return The dependency to create in this service
	 */
	protected <S> DefaultDependency<C, S> createDependency(String componentName, Supplier<DefaultComponent<C>> owner,
		Dependency.Builder<C, S> builder) {
		return new DefaultDependency<>(componentName, owner, builder.getTarget(), builder.getMinimum(), builder.isDynamic(), theComponents);
	}

	class DefaultComponentBuilder implements DSComponent.Builder<C> {
		private final String theComponentName;
		private final Function<? super ComponentController<C>, ? extends C> theSupplier;
		private Consumer<? super C> theDisposer;
		private final Map<Service<?>, Function<? super C, ?>> theProvidedServices;
		private final Map<Service<?>, DefaultDependency<C, ?>> theDependencies;
		private boolean isAvailable;
		private DefaultComponent<C> theBuiltComponent;

		DefaultComponentBuilder(String componentName, Function<? super ComponentController<C>, ? extends C> supplier) {
			theComponentName = componentName;
			theSupplier = supplier;
			theProvidedServices = new LinkedHashMap<>();
			theDependencies = new LinkedHashMap<>();
			isAvailable = true;
		}

		@Override
		public String getName() {
			return theComponentName;
		}

		void assertNotBuilt() {
			if (theBuiltComponent != null)
				throw new IllegalStateException("This component (" + theComponentName + ") has already been built and cannot be changed");
		}

		@Override
		public synchronized <S> Builder<C> provides(Service<S> service, Function<? super C, ? extends S> provided) {
			if (provided == null)
				throw new NullPointerException("provided service cannot be null");
			theProvidedServices.put(service, provided);
			return this;
		}

		@Override
		public synchronized <S> Builder<C> depends(Service<S> service, Consumer<Dependency.Builder<C, S>> dependency) {
			assertNotBuilt();
			if (theDependencies.containsKey(service))
				throw new IllegalArgumentException("Component " + theComponentName + " already depends on service " + service.getName()
				+ " and the dependency cannot be modified");
			DefaultDependencyBuilder<S> depBuilder = new DefaultDependencyBuilder<>(this, service);
			if (dependency != null)
				dependency.accept(depBuilder);
			DefaultDependency<C, S> dep = depBuilder.build();
			if (theDependencies.putIfAbsent(service, dep) != null)
				throw new IllegalArgumentException("Component " + theComponentName + " already depends on service " + service.getName()
				+ " and the dependency cannot be modified");
			return this;
		}

		@Override
		public Builder<C> initiallyAvailable(boolean available) {
			assertNotBuilt();
			isAvailable = available;
			return this;
		}

		@Override
		public Builder<C> disposeWhenInactive(Consumer<? super C> dispose) {
			theDisposer = dispose;
			return this;
		}

		@Override
		public Function<? super ComponentController<C>, ? extends C> getSupplier() {
			return theSupplier;
		}

		@Override
		public Map<Service<?>, Function<? super C, ?>> getProvided() {
			return Collections.unmodifiableMap(theProvidedServices);
		}

		@Override
		public Map<Service<?>, ? extends Dependency<C, ?>> getDependencies() {
			return Collections.unmodifiableMap(theDependencies);
		}

		@Override
		public boolean isInitiallyAvailable() {
			return isAvailable;
		}

		@Override
		public Consumer<? super C> isDisposedWhenInactive() {
			return theDisposer;
		}

		@Override
		public ComponentController<C> build() {
			assertNotBuilt();
			DefaultComponent<C> component = createComponent(this);
			theBuiltComponent = component;
			built(component);
			return component;
		}

		DefaultComponent<C> get() {
			return theBuiltComponent;
		}
	}

	class DefaultDependencyBuilder<S> implements Dependency.Builder<C, S> {
		private DefaultComponentBuilder theComponentBuilder;
		private final Service<S> theTarget;
		private int theMinimum;
		private boolean isDynamic;
		private boolean isBuilt;

		DefaultDependencyBuilder(DefaultComponentBuilder componentBuilder, Service<S> target) {
			theComponentBuilder = componentBuilder;
			theTarget = target;
			theMinimum = 1;
		}

		void assertNotBuilt() {
			if (isBuilt)
				throw new IllegalStateException("This dependency (" + theComponentBuilder.getName() + "<--" + theTarget.getName()
				+ ") has already been built and cannot be changed");
		}

		@Override
		public Service<S> getTarget() {
			return theTarget;
		}

		@Override
		public Dependency.Builder<C, S> minimum(int min) {
			assertNotBuilt();
			theMinimum = min;
			return this;
		}

		@Override
		public Dependency.Builder<C, S> dynamic(boolean dynamic) {
			assertNotBuilt();
			isDynamic = dynamic;
			return this;
		}

		@Override
		public int getMinimum() {
			return theMinimum;
		}

		@Override
		public boolean isDynamic() {
			return isDynamic;
		}

		DefaultDependency<C, S> build() {
			assertNotBuilt();
			isBuilt = true;
			return createDependency(theComponentBuilder.getName(), theComponentBuilder::get, this);
		}
	}
}
