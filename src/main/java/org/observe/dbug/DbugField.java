package org.observe.dbug;

import org.observe.SettableValue;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class DbugField<A, F> {
	private final DbugAnchor<A> theAnchor;
	private final DbugFieldType<A, F> theType;
	private final SettableValue<F> theValue;
	private final SettableValue<DbugAnchor<? super F>> theTarget;
	private volatile boolean isActive;

	DbugField(DbugAnchor<A> anchor, DbugFieldType<A, F> type, F initialValue) {
		theAnchor = anchor;
		theType = type;
		TypeToken<DbugAnchor<? super F>> targetType = type.getTarget() == null ? null
			: TypeTokens.get().keyFor(DbugAnchor.class).<DbugAnchor<? super F>> parameterized(type.getTarget().getType());
		if (type.isFinal()) {
			if (type.getTarget() != null && type.getTarget().isActive()) {
				theValue = null;
				theTarget = SettableValue.of(targetType, initialValue == null ? null : type.getTarget().instance(initialValue),
					"This field is final");
			} else {
				theTarget = null;
				theValue = SettableValue.of(type.getType(), initialValue, "This field is final");
			}
		} else {
			if (type.getTarget() != null && type.getTarget().isActive()) {
				theValue = null;
				theTarget = SettableValue.build(targetType).withLocking(anchor.getType().getDbug().getLock())//
					.withValue(initialValue == null ? null : type.getTarget().instance(initialValue))//
					.build();
			} else {
				theTarget = null;
				theValue = SettableValue.build(type.getType())//
					.withLocking(anchor.getType().getDbug().getLock())//
					.withValue(initialValue)//
					.build();
			}
		}
	}

	public DbugAnchor<A> getAnchor() {
		return theAnchor;
	}

	public DbugFieldType<A, F> getType() {
		return theType;
	}

	public boolean isActive() {
		return isActive;
	}

	F get() {
		if (theValue != null)
			return theValue.get();
		DbugAnchor<? super F> target = theTarget.get();
		return target == null ? null : (F) target.getInstance();
	}

	void set(F value, Object cause) {
		if (theValue != null)
			theValue.set(value, cause);
		// TODO
	}
}
