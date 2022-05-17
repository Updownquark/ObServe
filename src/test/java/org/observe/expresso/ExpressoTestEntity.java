package org.observe.expresso;

import java.time.Instant;
import java.util.Objects;

public class ExpressoTestEntity {
	private int theInt;
	private double theDouble;
	private boolean theBoolean;
	private String theString;
	private Instant theInstant;

	public int getInt() {
		return theInt;
	}

	public void setInt(int i) {
		theInt = i;
	}

	public double getDouble() {
		return theDouble;
	}

	public void setDouble(double d) {
		theDouble = d;
	}

	public boolean getBoolean() {
		return theBoolean;
	}

	public void setBoolean(boolean b) {
		theBoolean = b;
	}

	public String getString() {
		return theString;
	}

	public void setString(String string) {
		theString = string;
	}

	public Instant getInstant() {
		return theInstant;
	}

	public void setInstant(Instant instant) {
		theInstant = instant;
	}

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
