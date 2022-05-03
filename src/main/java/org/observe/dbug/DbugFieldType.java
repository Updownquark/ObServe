package org.observe.dbug;

import org.observe.util.TypeTokens;
import org.qommons.Named;

import com.google.common.reflect.TypeToken;

public class DbugFieldType<A, F> implements Named {
	private final DbugAnchorType<A> theAnchor;
	private final String theName;
	private final TypeToken<F> theType;
	private final DbugAnchorType<? super F> theTarget;
	private final boolean isFinal;
	private final boolean isNullable;

	public DbugFieldType(DbugAnchorType<A> anchor, String name, TypeToken<F> type, DbugAnchorType<? super F> target, boolean isFinal,
		boolean nullable) {
		theAnchor = anchor;
		theName = name;
		theType = type;
		theTarget = target;
		this.isFinal = isFinal;
		isNullable = nullable;
	}

	public DbugAnchorType<A> getAnchor() {
		return theAnchor;
	}

	@Override
	public String getName() {
		return theName;
	}

	public TypeToken<F> getType() {
		return theType;
	}

	public DbugAnchorType<? super F> getTarget() {
		return theTarget;
	}

	public boolean isFinal() {
		return isFinal;
	}

	public boolean isNullable() {
		return isNullable;
	}

	public boolean checkValue(Object value) {
		if (value == null && !isNullable)
			return false;
		return TypeTokens.get().isInstance(theType, value);
	}

	public F castValue(Object value) {
		if (!checkValue(value))
			throw new IllegalArgumentException("Value " + value + (value == null ? "" : ", type " + value.getClass().getName() + ", ")
				+ " cannot be used for field " + this + ", type " + getType());
		return (F) value;
	}

	@Override
	public String toString() {
		return theAnchor.getType().getSimpleName() + "." + theName;
	}
}
