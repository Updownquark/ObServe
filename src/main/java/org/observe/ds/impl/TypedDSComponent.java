package org.observe.ds.impl;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.ds.ComponentController;
import org.observe.ds.DSComponent;
import org.observe.ds.Dependency;
import org.observe.ds.Service;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/**
 * A {@link DSComponent} in a {@link DefaultTypedDependencyService}
 *
 * @param <C> The component value type
 */
public interface TypedDSComponent<C> extends DSComponent<C> {
	/**
	 * @param <S> The service type
	 * @param type The service type
	 * @return All provided services of the given type
	 */
	default <S> ObservableCollection<S> getDependencies(TypeToken<S> type) {
		return getDependencies(new TypeDefinedService<>(type));
	}
	/**
	 * @param <S> The service type
	 * @param type The service type
	 * @return All provided services of the given type
	 */
	default <S> ObservableCollection<S> getDependencies(Class<S> type) {
		return getDependencies(new TypeDefinedService<>(TypeTokens.get().of(type)));
	}

	/**
	 * @param <S> The service type
	 * @param type The service type
	 * @return The first provided service of the given type
	 */
	default <S> S getDependency(TypeToken<S> type) {
		return getDependency(new TypeDefinedService<>(type));
	}
	/**
	 * @param <S> The service type
	 * @param type The service type
	 * @return The first provided service of the given type
	 */
	default <S> S getDependency(Class<S> type) {
		return getDependency(new TypeDefinedService<>(TypeTokens.get().of(type)));
	}

	/**
	 * Allows configuration of a {@link TypedDSComponent} before it is built
	 *
	 * @param <C> The component value type
	 */
	public interface Builder<C> extends DSComponent.Builder<C> {
		@Override
		<S> Builder<C> provides(Service<S> service, Function<? super C, ? extends S> provided);

		@Override
		<S> Builder<C> depends(Service<S> service, Consumer<Dependency.Builder<C, S>> dependency);

		@Override
		Builder<C> initiallyAvailable(boolean available);

		@Override
		Builder<C> disposeWhenInactive(Consumer<? super C> dispose);

		/**
		 * @param <S> The service type to provide
		 * @param type The type of the service
		 * @param value Retrieves the service from the component value
		 * @return This builder
		 */
		default <S> Builder<C> provides(TypeToken<S> type, Function<? super C, ? extends S> value) {
			provides(new TypeDefinedService<>(type), value);
			return this;
		}
		/**
		 * @param <S> The service type to provide
		 * @param type The type of the service
		 * @param value Retrieves the service from the component value
		 * @return This builder
		 */
		default <S> Builder<C> provides(Class<S> type, Function<? super C, ? extends S> value) {
			provides(new TypeDefinedService<>(TypeTokens.get().of(type)), value);
			return this;
		}

		/**
		 * @param <S> The service type to depend on
		 * @param type The type of the service
		 * @param dependency Configures the dependency
		 * @return This builder
		 */
		default <S> Builder<C> depends(TypeToken<S> type, Consumer<Dependency.Builder<C, S>> dependency) {
			return depends(new TypeDefinedService<>(type), dependency);
		}
		/**
		 * @param <S> The service type to depend on
		 * @param type The type of the service
		 * @param dependency Configures the dependency
		 * @return This builder
		 */
		default <S> Builder<C> depends(Class<S> type, Consumer<Dependency.Builder<C, S>> dependency) {
			return depends(new TypeDefinedService<>(TypeTokens.get().of(type)), dependency);
		}

		@Override
		ComponentController<C> build();
	}
}
