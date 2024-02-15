package org.observe;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.Ternian;
import org.qommons.Transaction;

/** Super-interface used for options by several map-type operations */
public interface XformOptions {
	/**
	 * @param nullToNull Whether null inputs should be mapped to null outputs without calling the mapping function. False by default.
	 * @return This option set
	 */
	XformOptions nullToNull(boolean nullToNull);

	/**
	 * @param cache Whether to store both the source and result values for performance. Default is true.
	 * @return This option set
	 */
	XformOptions cache(boolean cache);

	/**
	 * @param reEval Whether the result should be re-evaluated on an update from the source. Default is true.
	 * @return This option set
	 */
	XformOptions reEvalOnUpdate(boolean reEval);

	/**
	 * @param fire Whether the result should fire an update as a result of a source event that does not affect the result value. Default is
	 *        true.
	 * @return This option set
	 */
	XformOptions fireIfUnchanged(boolean fire);

	/**
	 * @param manyToOne Whether the mapping may produce the same output from different source values
	 * @return This builder
	 */
	XformOptions manyToOne(boolean manyToOne);

	/**
	 * @param oneToMany Whether the reverse mapping may produce the same source value from different potential collection values
	 * @return This builder
	 */
	XformOptions oneToMany(boolean oneToMany);

	/** @return Whether null inputs should be mapped to null outputs without calling the mapping function */
	boolean isNullToNull();

	/** @return Whether to store both the source and result values for performance */
	boolean isCached();

	/** @return Whether the result should be re-evaluated on an update from the source */
	boolean isReEvalOnUpdate();

	/** @return Whether the result should fire an update as a result of a source event that does not affect the result value */
	boolean isFireIfUnchanged();

	/** @return Whether the mapping may produce the same output from different source values */
	boolean isManyToOne();

	/** @return Whether the reverse mapping may produce the same source value from different mapped values */
	boolean isOneToMany();

	/** A simple abstract implementation of XformOptions */
	public class SimpleXformOptions implements XformOptions {
		private boolean isNullToNull;
		private boolean isCached;
		private boolean reEvalOnUpdate;
		private boolean fireIfUnchanged;
		private boolean isManyToOne;
		private boolean isOneToMany;

		/** Creates the options */
		public SimpleXformOptions() {
			this((XformOptions) null);
		}

		/**
		 * Copies a set of options
		 *
		 * @param options The options to copy
		 */
		public SimpleXformOptions(XformOptions options) {
			if (options != null) {
				isCached = options.isCached();
				reEvalOnUpdate = options.isReEvalOnUpdate();
				fireIfUnchanged = options.isFireIfUnchanged();
				isManyToOne = options.isManyToOne();
				isOneToMany = options.isOneToMany();
			} else {
				isCached = true;
				reEvalOnUpdate = true;
				fireIfUnchanged = true;
			}
		}

		/**
		 * Copies a set of options
		 *
		 * @param options The options to copy
		 */
		public SimpleXformOptions(XformDef options) {
			if (options != null) {
				isNullToNull = options.isNullToNull();
				isCached = options.isCached();
				reEvalOnUpdate = options.isReEvalOnUpdate();
				fireIfUnchanged = options.isFireIfUnchanged();
				isManyToOne = options.isManyToOne();
				isOneToMany = options.isOneToMany();
			} else {
				isCached = true;
				reEvalOnUpdate = true;
				fireIfUnchanged = true;
			}
		}

		@Override
		public XformOptions nullToNull(boolean nullToNull) {
			isNullToNull = nullToNull;
			return this;
		}

		@Override
		public boolean isNullToNull() {
			return isNullToNull;
		}

		@Override
		public XformOptions cache(boolean cache) {
			isCached = cache;
			return this;
		}

		@Override
		public XformOptions reEvalOnUpdate(boolean reEval) {
			reEvalOnUpdate = reEval;
			return this;
		}

		@Override
		public XformOptions fireIfUnchanged(boolean fire) {
			fireIfUnchanged = fire;
			return this;
		}

		@Override
		public XformOptions manyToOne(boolean manyToOne) {
			isManyToOne = manyToOne;
			return this;
		}

		@Override
		public XformOptions oneToMany(boolean oneToMany) {
			isOneToMany = oneToMany;
			return this;
		}

		@Override
		public boolean isCached() {
			return isCached;
		}

		@Override
		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		@Override
		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		@Override
		public boolean isManyToOne() {
			return isManyToOne;
		}

		@Override
		public boolean isOneToMany() {
			return isOneToMany;
		}
	}

	/** An immutable version of {@link XformOptions} */
	public class XformDef {
		private final boolean isNullToNull;
		private final boolean isCached;
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isManyToOne;
		private final boolean isOneToMany;

		/** @param options The configured options */
		public XformDef(XformOptions options) {
			isNullToNull = options.isNullToNull();
			isCached = options.isCached();
			reEvalOnUpdate = options.isReEvalOnUpdate();
			fireIfUnchanged = options.isFireIfUnchanged();
			isManyToOne = options.isManyToOne();
			isOneToMany = options.isOneToMany();
		}

		/** @return Whether null inputs should be mapped to null outputs without calling the mapping function */
		public boolean isNullToNull() {
			return isNullToNull;
		}

		/** @return Whether to store both the source and result values for performance */
		public boolean isCached() {
			return isCached;
		}

		/** @return Whether the result should be re-evaluated on an update from the source */
		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		/** @return Whether the result should fire an update as a result of a source event that does not affect the result value */
		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		/**
		 * @return Whether the mapping may produce the same output from different source values.<br>
		 *         This subtle attribute is seldom needed and may be ignored most of the time.<br>
		 *         It influences, for example, how a result behaves when searching for result values, as optimizations can be made on
		 *         one-to-one mappings.
		 */
		public boolean isManyToOne() {
			return isManyToOne;
		}

		/**
		 * @return Whether the reverse mapping may produce the same source value from different mapped values.<br>
		 *         This subtle attribute is seldom needed and may be ignored most of the time.<br>
		 *         Thought should generally be given to this only for the strictest testing.
		 */
		public boolean isOneToMany() {
			return isOneToMany;
		}

		/** @return An options set with the same configuration as this definition */
		public XformOptions toOptions() {
			return new SimpleXformOptions(this);
		}

		/**
		 * @param intf The interface dictating behavior for the cache
		 * @return A cache handler that obeys the settings of this option set
		 */
		public <E, T> XformCacheHandler<E, T> createCacheHandler(XformCacheHandlingInterface<E, T> intf) {
			return new XformCacheHandlerImpl<>(this, intf);
		}

		@Override
		public int hashCode() {
			return Objects.hash(isCached, reEvalOnUpdate, fireIfUnchanged, isManyToOne, isOneToMany);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof XformDef))
				return false;
			XformDef other = (XformDef) obj;
			return isNullToNull == other.isNullToNull//
				&& isCached == other.isCached//
				&& reEvalOnUpdate == other.reEvalOnUpdate//
				&& fireIfUnchanged == other.fireIfUnchanged//
				&& isManyToOne == other.isManyToOne//
				&& isOneToMany == other.isOneToMany;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(isCached ? "" : "un").append("cached, ");
			str.append("re-eval=").append(reEvalOnUpdate).append(", fire-on-update=").append(fireIfUnchanged);
			if (isNullToNull)
				str.append(", null-to-null");
			if (isManyToOne)
				str.append(", many-to-one");
			if (isOneToMany)
				str.append(", one-to-many");
			return str.toString();
		}
	}

	/**
	 * Interfaces between a {@link XformCacheHandlerImpl} and the data it manages
	 *
	 * @param <E> The type of the source values
	 * @param <T> The type of the mapped values
	 */
	public interface XformCacheHandlingInterface<E, T> {
		/** @return A mapping function to produce mapped values from source values */
		BiFunction<? super E, ? super T, ? extends T> map();

		/**
		 * Ensures no modification occurs while the lock is held
		 *
		 * @return A transaction to use to release the lock
		 */
		Transaction lock();

		/** @return The value of the destination cache (may be shared) */
		T getDestCache();

		/** @param value The value for the destination cache */
		void setDestCache(T value);
	}

	/**
	 * Class used for implementing {@link XformOptions}
	 *
	 * @param <E> The type of the source values
	 * @param <T> The type of the mapped values
	 */
	public interface XformCacheHandler<E, T> {
		/**
		 * @param value Supplies the initial source value for this cache
		 * @return This handler
		 */
		XformCacheHandler<E, T> initialize(Supplier<E> value);

		/** @return The cached source value, if caching is enabled */
		E getSourceCache();

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @return Whether the change is to be treated as an update, or {@link Ternian#NONE NONE} if the change is irrelevant to the data
		 *         set
		 */
		Ternian isSourceUpdate(E oldSource, E newSource);

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		BiTuple<T, T> handleSourceChange(E oldSource, E newSource);

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @param update Whether the change is to be treated as an update
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		BiTuple<T, T> handleSourceChange(E oldSource, E newSource, boolean update);

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param oldMap The mapping function to use to produce the old mapped value (may be null to use the current map)
		 * @param newSource The new source value
		 * @param update Whether the change is to be treated as an update
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		BiTuple<T, T> handleSourceChange(E oldSource, Function<? super E, ? extends T> oldMap, E newSource, boolean update);

		// /**
		// * @param newResult The new result value
		// * @return Whether the change is to be treated as an update, or {@link Ternian#NONE NONE} if the change is irrelevant to the data
		// * set
		// */
		// Ternian isResultUpdate(T newResult);
		// /**
		// * @param newResult The new result value
		// * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		// */
		// BiTuple<T, T> handleResultChange(E newResult);
		// /**
		// * @param oldSource The previous source value (according to an event)
		// * @param newSource The new source value
		// * @param update Whether the change is to be treated as an update
		// * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		// */
		// BiTuple<T, T> handleSourceChange(E oldSource, E newSource, boolean update);
		// /**
		// * @param oldSource The previous source value (according to an event)
		// * @param oldMap The mapping function to use to produce the old mapped value (may be null to use the current map)
		// * @param newSource The new source value
		// * @param update Whether the change is to be treated as an update
		// * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		// */
		// BiTuple<T, T> handleSourceChange(E oldSource, Function<? super E, ? extends T> oldMap, E newSource, boolean update);
	}

	/**
	 * Default {@link XformCacheHandler} implementation
	 *
	 * @param <E> The type of the source values
	 * @param <T> The type of the mapped values
	 */
	class XformCacheHandlerImpl<E, T> implements XformCacheHandler<E, T> {
		private final XformDef theDef;
		private E theSrcCache;
		private final XformCacheHandlingInterface<E, T> theIntf;

		XformCacheHandlerImpl(XformDef def, XformCacheHandlingInterface<E, T> intf) {
			theDef = def;
			theIntf = intf;
		}

		@Override
		public XformCacheHandler<E, T> initialize(Supplier<E> value) {
			if (theDef.isCached() || !theDef.isFireIfUnchanged()) {
				theSrcCache = value == null ? null : value.get();
				if (theDef.isCached())
					theIntf.setDestCache(map(theSrcCache, null));
			}
			return this;
		}

		@Override
		public E getSourceCache() {
			return theSrcCache;
		}

		@Override
		public Ternian isSourceUpdate(E oldSource, E newSource) {
			E oldStored = theSrcCache; // May or may not have a valid value depending on caching
			if (theDef.isCached())
				theSrcCache = newSource;
			boolean isUpdate;
			if (!theDef.isReEvalOnUpdate() || !theDef.isFireIfUnchanged()) {
				if (theDef.isCached())
					isUpdate = oldStored == newSource;
				else
					isUpdate = oldSource == newSource;
			} else
				isUpdate = false; // Otherwise we don't care if it's an update
			if (theDef.isFireIfUnchanged() || !isUpdate)
				return Ternian.of(isUpdate);
			else
				return Ternian.NONE;
		}

		@Override
		public BiTuple<T, T> handleSourceChange(E oldSource, E newSource) {
			Ternian update = isSourceUpdate(oldSource, newSource);
			if (update.value == null)
				return null; // No change, no event
			try (Transaction t = theIntf.lock()) {
				return handleSourceChange(oldSource, newSource, update.value);
			}
		}

		@Override
		public BiTuple<T, T> handleSourceChange(E oldSource, E newSource, boolean update) {
			return handleSourceChange(oldSource, null, newSource, update);
		}

		@Override
		public BiTuple<T, T> handleSourceChange(E oldSource, Function<? super E, ? extends T> oldMap, E newSource, boolean update) {
			// Now figure out if we need to fire an event
			T oldValue, newValue;
			BiFunction<? super E, ? super T, ? extends T> map = theIntf.map();
			if (theDef.isCached()) {
				oldValue = theIntf.getDestCache();
				if (!update || theDef.isReEvalOnUpdate()) {
					newValue = map(map, newSource, oldValue);
					theIntf.setDestCache(newValue);
				} else
					newValue = oldValue;
			} else {
				if (update)
					oldValue = newValue = map(map, newSource, null);
				else {
					if (oldMap != null)
						oldValue = map(oldMap, oldSource);
					else
						oldValue = map(map, oldSource, null);
					newValue = map(map, newSource, oldValue);
				}
			}
			if (oldValue != newValue || theDef.isFireIfUnchanged())
				return new BiTuple<>(oldValue, newValue);
			return null;
		}

		public T map(E source, T previous) {
			return map(theIntf.map(), source, previous);
		}

		private T map(BiFunction<? super E, ? super T, ? extends T> map, E source, T previous) {
			if (source == null && theDef.isNullToNull())
				return null;
			else
				return map.apply(source, previous);
		}

		private T map(Function<? super E, ? extends T> map, E source) {
			if (source == null && theDef.isNullToNull())
				return null;
			else
				return map.apply(source);
		}
	}
}