package org.observe.config;

import java.util.Set;

import org.observe.Observable;
import org.observe.config.ObservableConfigFormat.EntityConfigCreator;
import org.qommons.collect.ElementId;

public abstract class SimpleValueCreator<E, E2 extends E> implements SyncValueCreator<E, E2> {
	private final ObservableConfigFormat.EntityConfigCreator<E2> theCreator;
	private ElementId theAfter;
	private ElementId theBefore;
	private boolean isTowardBeginning;

	public SimpleValueCreator(EntityConfigCreator<E2> creator) {
		theCreator = creator;
	}

	@Override
	public ConfiguredValueType<E2> getType() {
		return theCreator.getEntityType();
	}

	protected ElementId getAfter() {
		return theAfter;
	}

	protected ElementId getBefore() {
		return theBefore;
	}

	protected boolean isTowardBeginning() {
		return isTowardBeginning;
	}

	@Override
	public SimpleValueCreator<E, E2> after(ElementId after) {
		theAfter = after;
		return this;
	}

	@Override
	public SimpleValueCreator<E, E2> before(ElementId before) {
		theBefore = before;
		return this;
	}

	@Override
	public SimpleValueCreator<E, E2> towardBeginning(boolean towardBeginning) {
		this.isTowardBeginning = towardBeginning;
		return this;
	}

	@Override
	public Set<Integer> getRequiredFields() {
		return theCreator.getRequiredFields();
	}

	@Override
	public <F> SimpleValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException {
		theCreator.with(field, value);
		return this;
	}

	protected E2 createValue(ObservableConfig config, Observable<?> until) {
		return theCreator.create(config, until);
	}
}
