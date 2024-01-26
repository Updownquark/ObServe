package org.observe.expresso;

/** A little java class used by some of the Expresso tests */
public class ExpressoReflectTester {
	/** Static integer containing the number of entities of this type created */
	public static int CREATED = 0;

	/** @return {@link #CREATED} */
	public static int getCreated() {
		return CREATED;
	}

	/** The length field */
	public int length;
	/** The number of times {@link #getLength()} has been called on this entity */
	public int lengthCalled;

	/** @param value The string whose length to go in the {@link #length} field */
	public ExpressoReflectTester(String value) {
		CREATED++;
		length = value == null ? -1 : value.length();
	}

	/** @return {@link #length} */
	public int getLength() {
		lengthCalled++;
		return length;
	}

	/**
	 * @param value The value to add to the length
	 * @return {@link #length}<code>+value</code>
	 */
	public int getLengthPlus(int value) {
		return length + value;
	}

	/**
	 * @param one The first value to add
	 * @param args All other values to add
	 * @return The sum of all the arguments
	 */
	public int varArgsCall(int one, int... args) {
		int sum = one;
		for (int i : args)
			sum += i;
		return sum;
	}
}
