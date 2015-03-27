package org.observe;

/**
 * A tuple with two typed values
 *
 * @param <V1> The type of the first value
 * @param <V2> The type of the second value
 * @param <V3> The type of the third value
 */
public class TriTuple<V1, V2, V3> {
	private final V1 theValue1;
	private final V2 theValue2;
	private final V3 theValue3;

	/**
	 * @param v1 The first value
	 * @param v2 The second value
	 * @param v3 The third value
	 */
	public TriTuple(V1 v1, V2 v2, V3 v3) {
		theValue1 = v1;
		theValue2 = v2;
		theValue3 = v3;
	}

	/** @return The first Value */
	public V1 getValue1() {
		return theValue1;
	}

	/** @return The second value */
	public V2 getValue2() {
		return theValue2;
	}

	/** @return The third value */
	public V3 getValue3() {
		return theValue3;
	}

	/** @return Whether this tuple has at least one non-null value */
	public boolean hasValue() {
		return theValue1 != null || theValue2 != null || theValue3 != null;
	}

	/** @return Whether this tuple has both non-null values */
	public boolean has3Values() {
		return theValue1 != null && theValue2 != null && theValue3 != null;
	}

	@Override
	public boolean equals(Object o) {
		if(o == this)
			return true;
		if(!(o instanceof TriTuple))
			return false;
		TriTuple<?, ?, ?> tuple = (TriTuple<?, ?, ?>) o;
		return java.util.Objects.equals(theValue1, tuple.theValue1) && java.util.Objects.equals(theValue2, tuple.theValue2)
			&& java.util.Objects.equals(theValue3, tuple.theValue3);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(theValue1, theValue2, theValue3);
	}
}
