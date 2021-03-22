package org.observe.config;

import java.util.Set;

import org.observe.Observable;
import org.observe.config.ObservableConfigFormat.EntityConfigCreator;
import org.qommons.collect.ElementId;

/**
 * An abstract {@link SimpleValueCreator} implementation based on an {@link EntityConfigCreator}
 *
 * @param <E> The type of values in the set
 * @param <E2> The type of value to create
 */
public abstract class SimpleValueCreator<E, E2 extends E> implements SyncValueCreator<E, E2> {
	private final ObservableConfigFormat.EntityConfigCreator<E2> theCreator;
	private ElementId theAfter;
	private ElementId theBefore;
	private boolean isTowardBeginning;

	/** @param creator The config creator to create entities */
	public SimpleValueCreator(EntityConfigCreator<E2> creator) {
		theCreator = creator;
	}

	@Override
	public ConfiguredValueType<E2> getType() {
		return theCreator.getEntityType();
	}

	/** @return The element ID after which to insert the element in the set */
	protected ElementId getAfter() {
		return theAfter;
	}

	/** @return The element ID before which to insert the element in the set */
	protected ElementId getBefore() {
		return theBefore;
	}

	/** @return Whether to prefer inserting the element toward the beginning or end of the configured range */
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

	/**
	 * @param config The config to back the value
	 * @param until The until to release resources for the value
	 * @return The new entity
	 */
	protected E2 createValue(ObservableConfig config, Observable<?> until) {
		return theCreator.create(config, until);
	}
}
