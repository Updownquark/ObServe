package org.observe.supertest.collect;

import org.observe.Transformation;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * An abstract class that helps with any one-to-one collection link type that performs a mapping
 *
 * @param <S> The type of the source link
 * @param <T> The type of this link
 */
public abstract class AbstractMappedCollectionLink<S, T> extends OneToOneCollectionLink<S, T> {
	private final boolean isCached;
	private final boolean isInexactReversible;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness for this link
	 * @param cached Whether source and mapped values are cached by the collection
	 * @param inexactReversible Whether the reverse operation allows operations that do not satisfy the condition
	 *        map(reverse(target))==target
	 */
	public AbstractMappedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, boolean cached, boolean inexactReversible) {
		super(path, sourceLink, def, helper);
		isCached = cached;
		isInexactReversible = inexactReversible;
	}

	static boolean isInexactReversible(Transformation<?, ?> options) {
		if (!(options instanceof Transformation.ReversibleTransformation))
			return false;
		Transformation.ReversibleTransformation<?, ?> reversible = (Transformation.ReversibleTransformation<?, ?>) options;
		if (!(reversible.getReverse() instanceof Transformation.MappingSourceReplacingReverse))
			return false;
		return ((Transformation.MappingSourceReplacingReverse<?, ?>) reversible.getReverse()).isInexactReversible();
	}

	@Override
	public boolean is(ObservableChainLink.ChainLinkFlag flag) {
		switch (flag) {
		case INEXACT_REVERSIBLE:
			return isInexactReversible;
		default:
			return super.is(flag);
		}
	}

	@Override
	public boolean isAcceptable(T value) {
		if (!isReversible())
			throw new IllegalStateException();
		S reversed;
		try {
			reversed = reverse(value);
		} catch (RuntimeException e) {
			return false;
		}
		return getSourceLink().isAcceptable(reversed);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<S, T> element, T value) {
		if (isCached || !isReversible())
			return value;
		else
			return map(((ObservableCollectionLink<Object, S>) getSourceLink())
				.getUpdateValue((CollectionLinkElement<Object, S>) element.getFirstSource(), reverse(value)));
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		if (!isReversible()) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		} else if (!isInexactReversible && !getCollection().equivalence().elementEquals(map(reverse(value)), value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection, execute);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			if (isCached && getCollection().equivalence().elementEquals(derivedOp.getElement().getValue(), derivedOp.getValue())) {
				// Update, re-use the previous source value
				CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getFirstSource();
				S sourceValue;
				if (isReversible()) {
					// If the mapping is reversible, then the source operation for an update is valid either with the reverse-mapped value
					// or the re-used previous source value. So allow either.
					S reversed = reverse(derivedOp.getValue());
					if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(), reversed))
						sourceValue = reversed;
					else if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(),
						sourceEl.getValue()))
						sourceValue = sourceEl.getValue();
					else {
						sourceEl.error("Reverse operation produced " + sourceEl.getCollectionValue() + ", not " + reversed + " or "
							+ sourceEl.getValue());
						return;
					}
				} else
					sourceValue = sourceEl.getValue();
				getSourceLink().expect(new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), sourceValue),
					rejection, execute);
				return;
			}
			if (!isReversible()) {
				rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
				return;
			}
			S reversed = reverse(derivedOp.getValue());
			if (!isInexactReversible) {
				T reMapped = map(reversed);
				if (!getCollection().equivalence().elementEquals(reMapped, derivedOp.getValue())) {
					rejection.reject(StdMsg.ILLEGAL_ELEMENT);
					return;
				}
			}
			break;
		}
		super.expect(derivedOp, rejection, execute);
	}
}
