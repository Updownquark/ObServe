package org.observe.supertest;

/**
 * Provides the ability for the target observable link being modified or one upstream of it to reject an attempted modification due to
 * the expectation that the observable structure represented by the link will reject it
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
}