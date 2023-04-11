package org.observe.expresso.ops;

import org.qommons.Named;

/** A simple name which may be surrounded by white space */
public interface BufferedName extends Named {
	/** @return The number of white space characters occurring before the name */
	int getBefore();

	/** @return The number of white space characters occurring after the name */
	int getAfter();

	/** @return The length of this name with white space */
	default int length() {
		return getBefore() + getName().length() + getAfter();
	}

	/**
	 * @param name The name
	 * @return The buffered name
	 */
	public static BufferedName trivial(String name) {
		return new TrivialName(name);
	}

	/**
	 * @param before The number of whitespace characters before the name
	 * @param name The name's text
	 * @param after The number of whitespace characters after the name
	 * @return The buffered name
	 */
	public static BufferedName buffer(int before, String name, int after) {
		if (before == 0 && after == 0)
			return new TrivialName(name);
		return new OffsetName(name, before, after);
	}

	/** A {@link BufferedName} with no whitespace */
	static class TrivialName implements BufferedName {
		private final String theName;

		/** @param name The name */
		public TrivialName(String name) {
			if (name.isEmpty())
				throw new IllegalArgumentException("Empty name");
			theName = name;
		}

		@Override
		public int getBefore() {
			return 0;
		}

		@Override
		public int getAfter() {
			return 0;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public int length() {
			return theName.length();
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/** A simple {@link BufferedName} implementation */
	static class OffsetName implements BufferedName {
		private final String theName;
		private final int theBefore;
		private final int theAfter;

		/**
		 * @param before The number of whitespace characters before the name
		 * @param name The name's text
		 * @param after The number of whitespace characters after the name
		 */
		public OffsetName(String name, int before, int after) {
			if (name.isEmpty())
				throw new IllegalArgumentException("Empty name");
			theName = name;
			theBefore = before;
			theAfter = after;
		}

		@Override
		public int getBefore() {
			return theBefore;
		}

		@Override
		public int getAfter() {
			return theAfter;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public int length() {
			return theBefore + theName.length() + theAfter;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theBefore; i++)
				str.append(' ');
			str.append(theName);
			for (int i = 0; i < theAfter; i++)
				str.append(' ');
			return str.toString();
		}
	}
}
