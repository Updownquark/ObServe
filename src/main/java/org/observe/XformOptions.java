package org.observe;

import java.util.function.Function;

import org.qommons.BiTuple;
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

		public <E, T> CacheHandler<E, T> createCacheHandler(CacheHandlingInterface<E, T> intf) {
			return new CacheHandler<>(this, intf);
		}
	}

	interface CacheHandlingInterface<E, T> {
		Function<? super E, ? extends T> map();

		Transaction lock();

		T getDestCache();

		void setDestCache(T value);
	}

	class CacheHandler<E, T> {
		private final XformDef theDef;
		private E theSrcCache;
		private final CacheHandlingInterface<E, T> theIntf;

		CacheHandler(XformDef def, CacheHandlingInterface<E, T> intf) {
			theDef = def;
			theIntf = intf;
		}

		public void initialize(E value) {
			if (theDef.isCached())
				theSrcCache = value;
		}

		public BiTuple<T, T> handleChange(E oldSource, E newSource) {
			E oldStored = theSrcCache; // May or may not have a valid value depending on caching
			if (theDef.isCached())
				theSrcCache = newSource;
			boolean isUpdate;
			if (!theDef.isReEvalOnUpdate() && !theDef.isFireIfUnchanged()) {
				if (theDef.isCached())
					isUpdate = oldStored == newSource;
				else
					isUpdate = oldSource == newSource;
			} else
				isUpdate = false; // Otherwise we don't care if it's an update
			if (!theDef.isFireIfUnchanged() && isUpdate)
				return null; // No change, no event
			try (Transaction t = theIntf.lock()) {
				// Now figure out if we need to fire an event
				T oldValue, newValue;
				Function<? super E, ? extends T> map = theIntf.map();
				if (theDef.isReEvalOnUpdate() || !isUpdate) {
					if (theDef.isCached()) {
						oldValue = theIntf.getDestCache();
						theIntf.setDestCache(newValue = map.apply(newSource));
					} else {
						oldValue = map.apply(oldSource);
						newValue = map.apply(newSource);
					}
				} else {
					oldValue = newValue = map.apply(newSource);
				}
				if (theDef.isCached())
					theIntf.setDestCache(newValue);
				if (theDef.isFireIfUnchanged() || oldValue != newValue)
					new BiTuple<>(oldValue, newValue);
				return null;
			}
		}
	}
}