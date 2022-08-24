package org.observe.expresso;

import java.time.Instant;
import java.util.Objects;

/** A simple entity bean for the {@link ExpressoTests} */
public class ExpressoTestEntity {
	private int theInt;
	private double theDouble;
	private boolean theBoolean;
	private String theString;
	private Instant theInstant;

	/** @return The value of the integer field */
	public int getInt() {
		return theInt;
	}

	/** @param i The value for the integer field */
	public void setInt(int i) {
		theInt = i;
	}

	/** @return The value of the double field */
	public double getDouble() {
		return theDouble;
	}

	/** @param d The value for the double field */
	public void setDouble(double d) {
		theDouble = d;
	}

	/** @return The value of the boolean field */
	public boolean getBoolean() {
		return theBoolean;
	}

	/** @param b The value for the boolean field */
	public void setBoolean(boolean b) {
		theBoolean = b;
	}

	/** @return The value of the string field */
	public String getString() {
		return theString;
	}

	/** @param string The value for the string field */
	public void setString(String string) {
		theString = string;
	}

	/** @return The value of the instant field */
	public Instant getInstant() {
		return theInstant;
	}

	/** @param instant The value for the instant field */
	public void setInstant(Instant instant) {
		theInstant = instant;
	}

	/**
	 * @param expected The entity to test this entity against
	 * @return A string with the way in which this entity is not equal to the given entity, or null if it is equal
	 */
	public String assertEquals(ExpressoTestEntity expected) {
		if (theInt != expected.theInt)
			return "ints are diffferent: " + theInt + " vs " + expected.theInt;
		if (theDouble != expected.theDouble)
			return "doubles are diffferent: " + theDouble + " vs " + expected.theDouble;
		if (theBoolean != expected.theBoolean)
			return "booleans are diffferent: " + theBoolean + " vs " + expected.theBoolean;
		if (!Objects.equals(theString, expected.theString))
			return "strings are diffferent: \"" + theString + "\" vs \"" + expected.theString + "\"";
		if (!Objects.equals(theInstant, expected.theInstant))
			return "instants are diffferent: " + theInstant + " vs " + expected.theInstant;
		return null;
	}
}
