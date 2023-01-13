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

	/** A list of all satisfied, active components providing each service available from this dependency service */
	private final Map<Service<?>, List<DefaultComponent<C>>> theServiceProviders;
	/** A list of all active components depending on each service depended on in this dependency service */
	private final Map<Service<?>, List<DefaultDependency<C, ?>>> theDependents;

	private final ListenerList<Runnable> theScheduledTasks;

	private SettableValue<DependencyServiceStage> theStage;

	/** @param lock The lock to facilitate thread safety */
	public DefaultDependencyService(Transactable lock) {
		theComponents = ObservableCollection.build((Class<DefaultComponent<C>>) (Class<?>) DefaultComponent.class)
			.withLocking(new RRWLockingStrategy(lock)).build();
		theServices = ObservableSet.build(TypeTokens.get().keyFor(Service.class).<Service<?>> wildCard())
			.withLocking(new RRWLockingStrategy(lock)).build();

		theServiceProviders = new LinkedHashMap<>();
		theDependents = new LinkedHashMap<>();
		theScheduledTasks = ListenerList.build().build();
		theStage = SettableValue.build(DependencyServiceStage.class).withLocking(new RRWLockingStrategy(lock))
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
				BetterSet<DefaultComponent<C>> componentPath = BetterHashSet.build().build();
				tryActivateAll(cause, componentPath);
				// for (DefaultComponent<C> comp : theComponents) {
				// if (comp.getStage().get() == ComponentStage.Defined)
				// comp.preInit(cause);
				// }
				// theStage.set(DependencyServiceStage.PreInitialized, cause);
				// for (DefaultComponent<C> comp : theComponents) {
				// try {
				// if (!comp.isAvailable().get() || comp.getUnsatisfied() > 0)
				// comp.setStage(ComponentStage.Unsatisfied, cause);
				// else
				// comp.setStage(ComponentStage.Complete, cause);
				// } catch (AssertionError e) {
				// throw e;
				// } catch (RuntimeException | Error e) {
				// System.err.println("Error initializing component " + comp.getName());
				// e.printStackTrace();
				// }
				// }
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
			for (Service<?> svc : component.getProvided())
				theServiceProviders.computeIfAbsent(svc, __ -> new ArrayList<>(3)).add(component);
			for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values())
				theDependents.computeIfAbsent(dep.getTarget(), __ -> new ArrayList<>()).add(dep);
			if (getStage().get() != DependencyServiceStage.Uninitialized)
				activate(component, cause);
		}
	}

	Transaction lock(Object cause) {
		return theComponents.lock(true, cause);
	}

	private boolean isActivating;

	boolean isActivating() {
		return isActivating;
	}

	void activate(DefaultComponent<C> component, Causable cause) {
		if (isActivating)
			throw new IllegalStateException("Component availability cannot be changed as a result of changes to the dependency service");
		isActivating = true;
		try {
			BetterSet<DefaultComponent<C>> componentPath = BetterHashSet.build().build();
			if (tryActivate(component, componentPath, cause)) {
				// Satisfy other components with the newly available one
				tryActivateAll(cause, componentPath);
			}
		} finally {
			isActivating = false;
		}
		runScheduledTasks();
	}

	enum Satisfaction {
		Unsatisfied, Satisfied, Cycle
	}

	private void tryActivateAll(Causable cause, BetterSet<DefaultComponent<C>> componentPath) {
		for (DefaultComponent<C> comp : theComponents)
			tryActivate(comp, componentPath, cause);
	}

	/** @return If the component is active afer this call */
	boolean tryActivate(DefaultComponent<C> component, BetterSet<DefaultComponent<C>> componentPath, Causable cause) {
		return trySatisfy(component, componentPath, cause, false) == Satisfaction.Satisfied;
	}

	private Satisfaction trySatisfy(DefaultComponent<C> component, BetterSet<DefaultComponent<C>> componentPath, Causable cause,
		boolean dynamic) {
		boolean[] added = new boolean[1];
		ElementId el = componentPath.getOrAdd(component, null, null, false, null, () -> added[0] = true).getElementId();

		int satisfiedDeps = 0, dynamicLocalSatisfiedDeps = 0, dynamicAboveSatisfiedDeps = 0;
		boolean somethingSatisfied = false, hasCycle = false;
		// These are dynamic dependencies that we can satisfy here
		Map<DefaultDependency<C, ?>, List<DefaultComponent<C>>> dynamicDependencies = null;
		try {
			for (Dependency<C, ?> dep : component.getDependencies().values()) {
				int depSatisfied = dep.getProviders().size(), depDynamicLocalSatisfied = 0, depDynamicAboveSatisfied = 0;
				for (DefaultComponent<C> provider : theServiceProviders.getOrDefault(dep.getTarget(), Collections.emptyList())) {
					if (provider.equals(component))
						continue; // A component cannot satisfy its own dependencies
					else if (!provider.isAvailable().get())
						continue;
					else if (dep.getProviders().contains(provider))
						continue;
					Satisfaction depSatisfaction;
					if (provider.getStage().get().isActive())
						depSatisfaction = Satisfaction.Satisfied;
					else if (added[0]) // No cycle
						depSatisfaction = trySatisfy(provider, componentPath, cause, dynamic || dep.isDynamic());
					else
						depSatisfaction = Satisfaction.Cycle;
					switch (depSatisfaction) {
					case Unsatisfied:
						break;
					case Satisfied:
						((DefaultDependency<C, Object>) dep).satisfy(provider, cause);
						somethingSatisfied = true;
						depSatisfied++;
						break;
					case Cycle:
						hasCycle = true;
						if (dep.isDynamic()) {
							if (dynamicDependencies == null)
								dynamicDependencies = new LinkedHashMap<>();
							dynamicDependencies.computeIfAbsent((DefaultDependency<C, ?>) dep, __ -> new ArrayList<>(2)).add(provider);
							depDynamicLocalSatisfied++;
						} else if (dynamic)
							depDynamicAboveSatisfied++;
						break;
					}
				}
				if (depSatisfied >= dep.getMinimum())
					satisfiedDeps++;
				else if (depSatisfied + depDynamicLocalSatisfied >= dep.getMinimum())
					dynamicLocalSatisfiedDeps++;
				else if (depSatisfied + depDynamicLocalSatisfied + depDynamicAboveSatisfied >= dep.getMinimum())
					dynamicAboveSatisfiedDeps++;
			}
		} finally {
			if (added[0])
				componentPath.mutableElement(el).remove();
		}

		// Check the satisfiability
		if (satisfiedDeps + dynamicLocalSatisfiedDeps + dynamicAboveSatisfiedDeps < component.getDependencies().size()) {
			// Unsatisfiable with current component set
			component.setStage(ComponentStage.Unsatisfied, cause, somethingSatisfied);
			return Satisfaction.Unsatisfied;
		} else if (satisfiedDeps >= component.getDependencies().size() && (!component.isAvailable().get() || !hasCycle)) {
			// Statically satisfied
			try {
				component.setStage(component.isAvailable().get() ? ComponentStage.Satisfied : ComponentStage.Unavailable, cause,
					somethingSatisfied);
			} catch (RuntimeException | Error e) {
				// At the moment, it's not possible for these exceptions to happen, as they are swallowed by the observable listeners
				// However, it seems like it should be possible for a component to "error out", in which case the service should treat it
				// as unavailable. Not sure how this functionality will actually play out if I ever implement it.
				System.err.println("Component " + component.getName() + " activation error");
				e.printStackTrace();
				component.setAvailable(false);
				return Satisfaction.Unsatisfied;
			}
			return Satisfaction.Satisfied;
		} else if (!component.isAvailable().get()) {
			// Would be dynamically satisfiable, but since we're part of the cycle, we're the missing link
			component.setStage(ComponentStage.Unsatisfied, cause, somethingSatisfied);
			return Satisfaction.Unsatisfied;
		} // else Dynamically satisfiable

		if (satisfiedDeps + dynamicLocalSatisfiedDeps >= component.getDependencies().size()) {
			if (dynamicDependencies != null) {
				try {
					component.setStage(ComponentStage.PreSatisfied, cause, false);
				} catch (RuntimeException | Error e) {
					System.err.println("Component " + component.getName() + " activation error");
					e.printStackTrace();
					component.setAvailable(false);
					return Satisfaction.Unsatisfied;
				}
				for (Map.Entry<DefaultDependency<C, ?>, List<DefaultComponent<C>>> dep : dynamicDependencies.entrySet()) {
					for (DefaultComponent<C> provider : dep.getValue()) {
						Satisfaction depSatisfaction = trySatisfy(provider, componentPath, cause, false);
						// Although the satisfaction should work in theory, exceptions could prevent it from actually happening
						if (depSatisfaction == Satisfaction.Satisfied) {
							dep.getKey().satisfy(provider, cause);
							somethingSatisfied = true;
						}
					}
					if (dep.getKey().getProviders().size() < dep.getKey().getMinimum()) {
						// Dynamic cycle satisfaction failed due to exceptions
						_deactivate(component, cause, ComponentStage.Unsatisfied, //
							BetterHashSet.build().build()); // Use a different component path
						return Satisfaction.Unsatisfied;
					}
				}
			}
			try {
				component.setStage(ComponentStage.Satisfied, cause, false);
			} catch (RuntimeException | Error e) {
				System.err.println("Component " + component.getName() + " activation error");
				e.printStackTrace();
				component.setAvailable(false); // This call will cause the deactivation
				return Satisfaction.Unsatisfied;
			}
			return Satisfaction.Satisfied;
		} else
			return Satisfaction.Cycle; // Dynamically satisfiable, but the caller has to do it
	}

	/*boolean trySatisfyInitial(DefaultComponent<C> component, BetterSet<DefaultComponent<C>> componentPath, Object cause) {
		if (!component.isAvailable().get())
			return false;
		boolean wasSatisfied = component.getStage().get() == ComponentStage.Satisfied;
		// Attempt to satisfy the component's dependencies with satisfied provider components
		for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
			for (DefaultComponent<C> provider : theServiceProviders.getOrDefault(dep.getTarget(), Collections.emptyList())) {
				if (provider != component && provider.getStage().get().isActive())
					dep.satisfy(provider, cause);
			}
		}
		if (component.isAvailable().get() && component.getUnsatisfied() > 0
			&& component.getUnsatisfied() == component.getDynamicUnsatisfied()) {
			componentPath.add(component);
			boolean allCycles = true;
			for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
				if (dep.isDynamic() && dep.getProviders().size() < dep.getMinimum() //
					&& !hasDependencyCycles(dep, //
						componentPath, dep.getMinimum() - dep.getProviders().size())) {
					allCycles = false;
					break;
				}
			}
			if (allCycles) {
				component.setStage(ComponentStage.PreSatisfied, cause);
				for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
					if (dep.isDynamic())
						satisfyInitialDynamicCycles(dep, componentPath, cause);
				}
			}
			componentPath.clear();
		}
		if (component.getUnsatisfied() == 0 && component.isAvailable().get() && !wasSatisfied) {
			component.preInit(cause);
			return true;
		} else
			return false;
	}*/

	/*private boolean hasDependencyCycles(DefaultDependency<C, ?> dep, BetterSet<DefaultComponent<C>> componentPath, int needed) {
		boolean[] added = new boolean[1];
		for (DefaultComponent<C> provider : theServiceProviders.getOrDefault(dep.getTarget(), Collections.emptyList())) {
			if (dep.getProviders().contains(provider))
				continue;
			added[0] = false;
			ElementId el = componentPath.getOrAdd(provider, null, null, false, () -> added[0] = true).getElementId();
			if (!added[0]) { // Cycle detected
				if (componentPath.getAdjacentElement(el, false) == null && componentPath.getElement(el).get() != provider)
					needed--;
			} else {
				try {
					boolean allCycles = true;
					for (DefaultDependency<C, ?> d2 : (Collection<DefaultDependency<C, ?>>) provider.getDependencies().values()) {
						if (d2.getProviders().size() < d2.getMinimum() //
							&& !hasDependencyCycles(d2, //
								componentPath, d2.getMinimum() - d2.getProviders().size())) {
							allCycles = false;
							break;
						}
					}
					if (allCycles)
						needed--;
				} finally {
					componentPath.mutableElement(el).remove();
				}
			}
			if (needed == 0)
				break;
		}
		return needed == 0;
	}*/

	/*private void satisfyInitialDynamicCycles(DefaultDependency<C, ?> dep, BetterSet<DefaultComponent<C>> componentPath, Object cause) {
		boolean[] added = new boolean[1];
		for (DefaultComponent<C> provider : theServiceProviders.getOrDefault(dep.getTarget(), Collections.emptyList())) {
			if (dep.getProviders().contains(provider))
				continue;
			added[0] = false;
			ElementId el = componentPath.getOrAdd(provider, null, null, false, () -> added[0] = true).getElementId();
			if (!added[0]) { // Cycle detected
				if (componentPath.getAdjacentElement(el, false) == null
					&& componentPath.getFirst().getComponentValue() != dep.getOwner().getComponentValue())
					dep.satisfy(provider, cause);
			} else {
				try {
					for (DefaultDependency<C, ?> d2 : (Collection<DefaultDependency<C, ?>>) provider.getDependencies().values())
						satisfyInitialDynamicCycles(d2, componentPath, cause);
					if (provider.getUnsatisfied() == 0) {
						if (!provider.getStage().get().isActive())
							provider.preInit(cause);
						dep.satisfy(provider, cause);
					}
				} finally {
					componentPath.mutableElement(el).remove();
				}
			}
		}
	}*/

	/*private boolean isInSatisfiedDynamicCycle(DefaultComponent<C> component, BetterSet<DefaultDependency<C, ?>> componentPath) {
		for (DefaultDependency<C, ?> dep : (Collection<DefaultDependency<C, ?>>) component.getDependencies().values()) {
			boolean[] added = new boolean[1];
			ElementId el = componentPath.getOrAdd(dep, null, null, false, () -> added[0] = true).getElementId();
			if (!added[0]) {
				// We're in a cycle, but is it dynamic?
				if (component.getComponentValue() == componentPath.getElement(el).get().getOwner().getComponentValue())
					return false;
				for (CollectionElement<DefaultDependency<C, ?>> dep2 = componentPath.getElement(el); //
					dep2 != null;//
					dep2 = componentPath.getAdjacentElement(dep2.getElementId(), true)) {
					if (dep2.get().isDynamic())
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
				componentPath.mutableElement(el).remove();
			}
		}
		return true;
	}*/

	/*private boolean satisfied(DefaultComponent<C> component, BetterSet<DefaultDependency<C, ?>> componentPath, Object cause) {
		switch (theStage.get()) {
		case Uninitialized:
			throw new IllegalStateException("Nothing should be happening");
		case Initializing:
		case PreInitialized:
			component.setStage(ComponentStage.Satisfied, cause);
			break;
		case Initialized:
			component.setStage(ComponentStage.Complete, cause);
			break;
		}
		boolean somethingSatisfied = false;
		for (Service<?> provided : component.getProvided()) {
			theServices.add(provided);
			List<DefaultDependency<C, ?>> dependents = theDependents.get(provided);
			if (dependents == null)
				continue;
			for (DefaultDependency<C, ?> dep : dependents) {
				boolean wasActive = dep.getRealOwner().getStage().get().isActive();
				if (dep.getOwner().getComponentValue() != component.getComponentValue() && dep.satisfy(component, cause)) {
					somethingSatisfied = true;
				}
				if (!wasActive && getStage().get() != DependencyServiceStage.Uninitialized) {
					if (dep.getRealOwner().getUnsatisfied() == 0)
						satisfied(dep.getRealOwner(), componentPath, cause);
					else if (dep.getRealOwner().getUnsatisfied() == dep.getRealOwner().getDynamicUnsatisfied() && //
						isInSatisfiedDynamicCycle(dep.getRealOwner(), componentPath)) {
						wasActive = true;
						dep.getRealOwner().setStage(ComponentStage.PreSatisfied, cause);
						;
						satisfied(dep.getRealOwner(), componentPath, cause);
					}
				}
			}
		}
		return somethingSatisfied;
	}*/

	void deactivate(DefaultComponent<C> component, Object cause, ComponentStage stage) {
		if (isActivating)
			throw new IllegalStateException("Component availability cannot be changed as a result of changes to the dependency service");
		isActivating = true;
		try {
			BetterSet<DefaultComponent<C>> componentPath = BetterHashSet.build().build();
			_deactivate(component, cause, stage, componentPath);
		} finally {
			isActivating = false;
		}
	}

	private void _deactivate(DefaultComponent<C> component, Object cause, ComponentStage stage,
		BetterSet<DefaultComponent<C>> componentPath) {
		ElementId el = CollectionElement.getElementId(componentPath.addElement(component, false));
		if (el == null) // Recursion
			return;
		for (Service<?> provided : component.getProvided()) {
			for (DefaultDependency<C, ?> dep : theDependents.getOrDefault(provided, Collections.emptyList())) {
				if (dep.getProviders().contains(component)) {
					boolean newlyUnsatisfied = dep.getProviders().size() == dep.getMinimum();
					if (newlyUnsatisfied && dep.getOwner().getStage().get().isSatisfied())
						_deactivate(dep.getRealOwner(), cause, ComponentStage.Unsatisfied, componentPath); // Newly unsatisfied
					dep.remove(component, cause);
					if (!newlyUnsatisfied)
						dep.getRealOwner().setStage(dep.getOwner().getStage().get(), cause, true); // Update
				}
			}
		}
		if (stage == ComponentStage.Removed) {
			for (Service<?> provided : component.getProvided()) {
				theServiceProviders.compute(provided, (s, old) -> {
					if (old == null || !old.remove(component))
						return old;
					return old.isEmpty() ? null : old;
				});
			}
			for (Dependency<C, ?> dep : component.getDependencies().values()) {
				theDependents.compute(dep.getTarget(), (d, old) -> {
					if (old == null || !old.remove(dep))
						return old;
					return old.isEmpty() ? null : old;
				});
			}
			theComponents.remove(component);
			component.setStage(ComponentStage.Removed, cause, false);
		} else if (stage == ComponentStage.Unsatisfied || component.getUnsatisfied() == 0)
			component.setStage(stage, cause, false);
		else
			component.setStage(ComponentStage.Unsatisfied, cause, false);
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
			if (isDynamic && theMinimum == 0) {
				System.err.println("Dynamic dependency with min=0, which is pointless");
				isDynamic = false;
			}
			return createDependency(theComponentBuilder.getName(), theComponentBuilder::get, this);
		}
	}
}
