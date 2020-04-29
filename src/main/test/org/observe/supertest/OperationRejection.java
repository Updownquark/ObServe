package org.observe.supertest;

/**
 * Provides the ability for the target observable link being modified or one upstream of it to reject an attempted modification due to the
 * expectation that the observable structure represented by the link will reject it
 */
public interface OperationRejection {
	/** @return Whether the operation was rejected */
	boolean isRejected();

	/** @param message The message with which to reject the operation */
	void reject(String message);

	/** @return Whether this operation can be rejected */
	boolean isRejectable();

	/**
	 * Marks this operation as unrejectable. This may be done when a single operation cascades into batch operations for collections
	 * upstream. Before using this, the downstream collection should ensure that the operation is not rejected by calling
	 * {@link ObservableCollectionLink#expect(ExpectedCollectionOperation, OperationRejection, boolean) expect} with execute=false.
	 *
	 * @return This operation
	 */
	OperationRejection unrejectable();

	/**
	 * @return The message of the actual operation's rejection by the observable structure, or null if the operation was not rejected
	 */
	String getActualRejection();

	/**
	 * Resets this rejection
	 *
	 * @return this rejection
	 */
	OperationRejection reset();

	/** Simple implementation of OperationRejection */
	public static class Simple implements OperationRejection {
		private boolean isRejectable;
		private String theActualRejection;
		private String theMessage;

		/** Creates the rejection capability */
		public Simple() {
			isRejectable = true;
		}

		/**
		 * @param actualRejection The actual rejection message given by the attempted operation
		 * @return This rejection
		 */
		public Simple withActualRejection(String actualRejection) {
			this.theActualRejection = actualRejection;
			return this;
		}

		@Override
		public void reject(String message) {
			if (!isRejectable)
				throw new IllegalStateException("Not rejectable");
			theMessage = message;
		}

		@Override
		public boolean isRejected() {
			return theMessage != null;
		}

		/** @return The message that the operation was rejected with */
		public String getMessage() {
			return theMessage;
		}

		@Override
		public String getActualRejection() {
			return theActualRejection;
		}

		@Override
		public boolean isRejectable() {
			return isRejectable;
		}

		@Override
		public OperationRejection unrejectable() {
			isRejectable = false;
			return this;
		}

		@Override
		public OperationRejection reset() {
			isRejectable = true;
			theMessage = null;
			return this;
		}
	}
}