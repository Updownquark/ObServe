package org.observe.expresso;

import java.time.Instant;
import java.util.Objects;

import org.observe.Observable;
import org.observe.SimpleObservable;

/** A simple entity bean for the {@link ExpressoTests} */
public class ExpressoTestEntity {
	private int theInt;
	private double theDouble;
	private boolean theBoolean;
	private String theString;
	private Instant theInstant;

	private final SimpleObservable<Void> theChanges = new SimpleObservable<>();

	/** @return The value of the integer field */
	public int getInt() {
		return theInt;
	}

	/**
	 * @param i The value for the integer field
	 * @return This entity
	 */
	public ExpressoTestEntity setInt(int i) {
		theInt = i;
		theChanges.onNext(null);
		return this;
	}

	/** @return The value of the double field */
	public double getDouble() {
		return theDouble;
	}

	/**
	 * @param d The value for the double field
	 * @return This entity
	 */
	public ExpressoTestEntity setDouble(double d) {
		theDouble = d;
		theChanges.onNext(null);
		return this;
	}

	/** @return The value of the boolean field */
	public boolean getBoolean() {
		return theBoolean;
	}

	/**
	 * @param b The value for the boolean field
	 * @return This entity
	 */
	public ExpressoTestEntity setBoolean(boolean b) {
		theBoolean = b;
		theChanges.onNext(null);
		return this;
	}

	/** @return The value of the string field */
	public String getString() {
		return theString;
	}

	/**
	 * @param string The value for the string field
	 * @return This entity
	 */
	public ExpressoTestEntity setString(String string) {
		theString = string;
		theChanges.onNext(null);
		return this;
	}

	/** @return The value of the instant field */
	public Instant getInstant() {
		return theInstant;
	}

	/**
	 * @param instant The value for the instant field
	 * @return This entity
	 */
	public ExpressoTestEntity setInstant(Instant instant) {
		theInstant = instant;
		theChanges.onNext(null);
		return this;
	}

	/** @return An observable that fires when any of this entity's fields changes */
	public Observable<Void> changes() {
		return theChanges.readOnly();
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

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ExpressoTestEntity))
			return false;
		ExpressoTestEntity other = (ExpressoTestEntity) obj;
		return theInt == other.theInt//
			&& theDouble == other.theDouble//
			&& theBoolean == other.theBoolean//
			&& Objects.equals(theString, other.theString)//
			&& Objects.equals(theInstant, other.theInstant);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("{");
		str.append("int=").append(theInt).append(", dbl=").append(theDouble).append(", str=").append(theString).append(", inst=")
			.append(theInstant);
		return str.append("}").toString();
	}
}
