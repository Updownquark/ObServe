package org.observe;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.BiTuple;
import org.qommons.Ternian;
import org.qommons.Transaction;

/** Super-interface used for options by several map-type operations */
public interface XformOptions {
	/**
	 * @param reEval Whether the result should be re-evaluated on an update from the source. Default is true.
	 * @return This option set
	 */
	XformOptions reEvalOnUpdate(boolean reEval);
	/**
	 * @param fire Whether the result should fire an update as a result of a source event that does not affect the result value. Default is
	 *        true
	 * @return This option set
	 */
	XformOptions fireIfUnchanged(boolean fire);
	/**
	 * @param cache Whether to store both the source and result values for performance. Default is true.
	 * @return This option set
	 */
	XformOptions cache(boolean cache);

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

	/** @return Whether the result should be re-evaluated on an update from the source */
	boolean isReEvalOnUpdate();
	/** @return Whether the result should fire an update as a result of a source event that does not affect the result value */
	boolean isFireIfUnchanged();
	/** @return Whether to store both the source and result values for performance */
	boolean isCached();

	/** @return Whether the mapping may produce the same output from different source values */
	boolean isManyToOne();
	/** @return Whether the reverse mapping may produce the same source value from different mapped values */
	boolean isOneToMany();

	/** A simple abstract implementation of XformOptions */
	public class SimpleXformOptions implements XformOptions {
		private boolean reEvalOnUpdate;
		private boolean fireIfUnchanged;
		private boolean isCached;
		private boolean isManyToOne;
		private boolean isOneToMany;

		/** Creates the options */
		public SimpleXformOptions() {
			this(null);
		}

		/**
		 * Copies a set of options
		 *
		 * @param options The options to copy
		 */
		public SimpleXformOptions(XformOptions options) {
			if (options != null) {
				reEvalOnUpdate = options.isReEvalOnUpdate();
				fireIfUnchanged = options.isFireIfUnchanged();
				isCached = options.isCached();
				isManyToOne = options.isManyToOne();
				isOneToMany = options.isOneToMany();
			} else {
				reEvalOnUpdate = true;
				fireIfUnchanged = true;
				isCached = true;
			}
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
		public XformOptions cache(boolean cache) {
			isCached = cache;
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
		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		@Override
		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		@Override
		public boolean isCached() {
			return isCached;
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
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;
		private final boolean isManyToOne;
		private final boolean isOneToMany;

		/** @param options The configured options */
		public XformDef(XformOptions options) {
			reEvalOnUpdate = options.isReEvalOnUpdate();
			fireIfUnchanged = options.isFireIfUnchanged();
			isCached = options.isCached();
			isManyToOne = options.isManyToOne();
			isOneToMany = options.isOneToMany();
		}

		/** @return Whether the result should be re-evaluated on an update from the source */
		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		/** @return Whether the result should fire an update as a result of a source event that does not affect the result value */
		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		/** @return Whether to store both the source and result values for performance */
		public boolean isCached() {
			return isCached;
		}

		/** @return Whether the mapping may produce the same output from different source values */
		public boolean isManyToOne() {
			return isManyToOne;
		}

		/** @return Whether the reverse mapping may produce the same source value from different mapped values */
		public boolean isOneToMany() {
			return isOneToMany;
		}

		/**
		 * @param intf The interface dictating behavior for the cache
		 * @return A cache handler that obeys the settings of this option set
		 */
		public <E, T> XformCacheHandler<E, T> createCacheHandler(XformCacheHandlingInterface<E, T> intf) {
			return new XformCacheHandler<>(this, intf);
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
			return isCached == other.isCached//
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
			if (isManyToOne)
				str.append(", many-to-one");
			if (isOneToMany)
				str.append(", one-to-many");
			return str.toString();
		}
	}

	/**
	 * Interfaces between a {@link XformCacheHandler} and the data it manages
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
	public class XformCacheHandler<E, T> {
		private final XformDef theDef;
		private E theSrcCache;
		private final XformCacheHandlingInterface<E, T> theIntf;

		XformCacheHandler(XformDef def, XformCacheHandlingInterface<E, T> intf) {
			theDef = def;
			theIntf = intf;
		}

		/** @return The cached source value, if caching is enabled */
		public E getSourceCache() {
			return theSrcCache;
		}

		/** @param value The initial source value for this cache */
		public void initialize(E value) {
			if (theDef.isCached())
				theSrcCache = value;
		}

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @return Whether the change is to be treated as an update, or {@link Ternian#NONE NONE} if the change is irrelevant to the data
		 *         set
		 */
		public Ternian isUpdate(E oldSource, E newSource) {
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

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @param update Whether the change is to be treated as an update
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		public BiTuple<T, T> handleChange(E oldSource, E newSource, boolean update) {
			return handleChange(oldSource, null, newSource, update);
		}

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param oldMap The mapping function to use to produce the old mapped value (may be null to use the current map)
		 * @param newSource The new source value
		 * @param update Whether the change is to be treated as an update
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		public BiTuple<T, T> handleChange(E oldSource, Function<? super E, ? extends T> oldMap, E newSource, boolean update) {
			// Now figure out if we need to fire an event
			T oldValue, newValue;
			BiFunction<? super E, ? super T, ? extends T> map = theIntf.map();
			if (theDef.isCached()) {
				oldValue = theIntf.getDestCache();
				if (!update || theDef.isReEvalOnUpdate()) {
					newValue = map.apply(newSource, oldValue);
					theIntf.setDestCache(newValue);
				} else
					newValue = oldValue;
			} else {
				if (update)
					oldValue = newValue = map.apply(newSource, null);
				else {
					if (oldMap != null)
						oldValue = oldMap.apply(oldSource);
					else
						oldValue = map.apply(oldSource, null);
					newValue = map.apply(newSource, oldValue);
				}
			}
			if (oldValue != newValue || theDef.isFireIfUnchanged())
				return new BiTuple<>(oldValue, newValue);
			return null;
		}

		/**
		 * @param oldSource The previous source value (according to an event)
		 * @param newSource The new source value
		 * @return A tuple of old and new mapped values to fire an event on, or null if the change is irrelevant to this data set
		 */
		public BiTuple<T, T> handleChange(E oldSource, E newSource) {
			Ternian update = isUpdate(oldSource, newSource);
			if (update.value == null)
				return null; // No change, no event
			try (Transaction t = theIntf.lock()) {
				return handleChange(oldSource, newSource, update.value);
			}
		}
	}
}