package org.observe.expresso;

public class ExpressoReflectTester {
	public static int CREATED = 0;

	public static int getCreated() {
		return CREATED;
	}

	public boolean condition;
	public int length;
	public int lengthCalled;

	public ExpressoReflectTester(String value) {
		CREATED++;
		length = value == null ? -1 : value.length();
	}

	public int getLength() {
		lengthCalled++;
		return length;
	}

	public int getLengthPlus(int value) {
		return length + value;
	}

	public int varArgsCall(int one, int... args) {
		int sum = one;
		for (int i : args)
			sum += i;
		return sum;
	}
}
