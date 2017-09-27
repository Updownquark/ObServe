package org.observe;

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
	}
}