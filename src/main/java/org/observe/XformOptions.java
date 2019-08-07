package org.observe;

import java.util.function.BiFunction;

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

	/** @return Whether the result should be re-evaluated on an update from the source */
	boolean isReEvalOnUpdate();
	/** @return Whether the result should fire an update as a result of a source event that does not affect the result value */
	boolean isFireIfUnchanged();
	/** @return Whether to store both the source and result values for performance */
	boolean isCached();

	/** A simple abstract implementation of XformOptions */
	class SimpleXformOptions implements XformOptions {
		private boolean reEvalOnUpdate;
		private boolean fireIfUnchanged;
		private boolean isCached;

		public SimpleXformOptions() {
			reEvalOnUpdate = true;
			fireIfUnchanged = true;
			isCached = true;
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
	}

	/** An immutable version of {@link XformOptions} */
	class XformDef {
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		public XformDef(XformOptions options) {
			reEvalOnUpdate = options.isReEvalOnUpdate();
			fireIfUnchanged = options.isFireIfUnchanged();
			isCached = options.isCached();
		}

		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		public boolean isCached() {
			return isCached;
		}

		public <E, T> XformCacheHandler<E, T> createCacheHandler(XformCacheHandlingInterface<E, T> intf) {
			return new XformCacheHandler<>(this, intf);
		}
	}

	/**
	 * Interfaces between a {@link XformCacheHandler} and the data it manages
	 *
	 * @param <E> The type of the source values
	 * @param <T> The type of the mapped values
	 */
	interface XformCacheHandlingInterface<E, T> {
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
	class XformCacheHandler<E, T> {
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