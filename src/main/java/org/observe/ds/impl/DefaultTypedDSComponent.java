package org.observe.ds.impl;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.ds.ComponentController;
import org.observe.ds.ComponentStage;
import org.observe.ds.DSComponent;
import org.observe.ds.Dependency;
import org.observe.ds.DependencyService;
import org.observe.ds.Service;

class DefaultTypedDSComponent<C> extends DefaultComponent<C> implements TypedDSComponent<C>, TypedComponentController<C> {
	DefaultTypedDSComponent(DefaultDependencyService<C> service, String name,
		Function<? super ComponentController<C>, ? extends C> supplier, Consumer<? super C> disposer,
		Map<Service<?>, Function<? super C, ?>> provided, Map<Service<?>, ? extends DefaultDependency<C, ?>> dependencies,
			boolean available) {
		super(service, name, supplier, disposer, provided, dependencies, available);
	}

	@Override
	public TypedDSComponent<C> getComponent() {
		return new WrappedTypedDSComponent<>(this);
	}

	static class Builder<C> implements TypedDSComponent.Builder<C> {
		private final DSComponent.Builder<C> theWrapped;

		Builder(org.observe.ds.DSComponent.Builder<C> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public Builder<C> initiallyAvailable(boolean available) {
			theWrapped.initiallyAvailable(available);
			return this;
		}

		@Override
		public Builder<C> disposeWhenInactive(Consumer<? super C> dispose) {
			theWrapped.disposeWhenInactive(dispose);
			return this;
		}

		@Override
		public Map<Service<?>, Function<? super C, ?>> getProvided() {
			return theWrapped.getProvided();
		}

		@Override
		public Map<Service<?>, ? extends Dependency<C, ?>> getDependencies() {
			return theWrapped.getDependencies();
		}

		@Override
		public boolean isInitiallyAvailable() {
			return theWrapped.isInitiallyAvailable();
		}

		@Override
		public String getName() {
			return theWrapped.getName();
		}

		@Override
		public <S> Builder<C> provides(Service<S> service, Function<? super C, ? extends S> provided) {
			theWrapped.provides(service, provided);
			return this;
		}

		@Override
		public <S> Builder<C> depends(Service<S> service, Consumer<Dependency.Builder<C, S>> dependency) {
			theWrapped.depends(service, dependency);
			return this;
		}

		@Override
		public Function<? super ComponentController<C>, ? extends C> getSupplier() {
			return theWrapped.getSupplier();
		}

		@Override
		public Consumer<? super C> isDisposedWhenInactive() {
			return theWrapped.isDisposedWhenInactive();
		}

		@Override
		public TypedComponentController<C> build() {
			return (TypedComponentController<C>) theWrapped.build();
		}
	}

	static class WrappedTypedDSComponent<C> implements TypedDSComponent<C> {
		private final DefaultTypedDSComponent<C> theWrapped;

		public WrappedTypedDSComponent(DefaultTypedDSComponent<C> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public DependencyService<C> getDependencyService() {
			return theWrapped.getDependencyService();
		}

		@Override
		public C getComponentValue() {
			return theWrapped.getComponentValue();
		}

		@Override
		public Set<? extends Service<?>> getProvided() {
			return theWrapped.getProvided();
		}

		@Override
		public <S> S provide(Service<S> service) throws IllegalArgumentException {
			return theWrapped.provide(service);
		}

		@Override
		public Map<Service<?>, ? extends Dependency<C, ?>> getDependencies() {
			return theWrapped.getDependencies();
		}

		@Override
		public ObservableValue<ComponentStage> getStage() {
			return theWrapped.getStage();
		}

		@Override
		public ObservableValue<Boolean> isAvailable() {
			return theWrapped.isAvailable();
		}

		@Override
		public String getName() {
			return theWrapped.getName();
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
