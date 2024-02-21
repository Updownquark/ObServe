package org.observe.config;

import java.util.Collection;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.collect.CollectionElementMove;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/** An implementation of {@link ObservableConfig} that reflects all the values of another, but cannot be modified */
public class UnmodifiableObservableConfig extends AbstractObservableConfig {
	private static final ObservableConfigParseSession SESSION = new ObservableConfigParseSession();

	private final ObservableConfig theWrapped;

	private UnmodifiableObservableConfig(ObservableConfig wrapped) {
		theWrapped = wrapped;
	}

	@Override
	public String getName() {
		return theWrapped.getName();
	}

	@Override
	public boolean mayBeTrivial() {
		return theWrapped.mayBeTrivial();
	}

	@Override
	public ObservableConfig setTrivial(boolean mayBeTrivial) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public boolean isEventing() {
		return theWrapped.isEventing();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theWrapped.lock(false, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theWrapped.tryLock(false, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theWrapped.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return theWrapped.getCoreId();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theWrapped.getThreadConstraint();
	}

	@Override
	public long getStamp() {
		return theWrapped.getStamp();
	}

	@Override
	public String getValue() {
		return theWrapped.getValue();
	}

	@Override
	public ElementId getParentChildRef() {
		return theWrapped.getParentChildRef();
	}

	@Override
	public BetterList<ObservableConfig> getContent() {
		return new UnmodifiableChildList(theWrapped.getContent());
	}

	@Override
	public Observable<ObservableConfigEvent> watch(ObservableConfigPath path) {
		return theWrapped.watch(path).map(evt -> {
			return new ObservableConfig.ObservableConfigEvent(evt.changeType, evt.movement, evt.eventTarget.unmodifiable(), evt.oldName,
				evt.oldValue, //
				QommonsUtils.map2(evt.relativePath, ObservableConfig::unmodifiable), evt);
		});
	}

	@Override
	public String canAddChild(ObservableConfig after, ObservableConfig before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public String canMoveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before) {
		if (after != null && after.getParentChildRef().compareTo(child.getParentChildRef()) > 0)
			return StdMsg.UNSUPPORTED_OPERATION;
		if (before != null && before.getParentChildRef().compareTo(child.getParentChildRef()) < 0)
			return StdMsg.UNSUPPORTED_OPERATION;
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public String canRemove() {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public ObservableConfig setName(String name) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig setValue(String value) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove) {
		if (after != null && after.getParentChildRef().compareTo(child.getParentChildRef()) > 0)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		if (before != null && before.getParentChildRef().compareTo(child.getParentChildRef()) < 0)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		return child;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	protected AbstractObservableConfig createChild(String name) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void addChild(AbstractObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		CollectionElementMove move) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void doRemove(CollectionElementMove move) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void _postRemove() {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	public ObservableConfig unmodifiable() {
		return this;
	}

	static UnmodifiableObservableConfig unmodifiable(ObservableConfig config) {
		UnmodifiableObservableConfig unmodifiable = (UnmodifiableObservableConfig) config.getParsedItem(SESSION);
		if (unmodifiable == null) {
			unmodifiable = new UnmodifiableObservableConfig(config);
			config.withParsedItem(SESSION, unmodifiable);
		}
		return unmodifiable;
	}

	private static class UnmodifiableChildList implements BetterList<ObservableConfig> {
		private final BetterList<ObservableConfig> theBacking;

		UnmodifiableChildList(BetterList<ObservableConfig> backing) {
			theBacking = backing;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theBacking.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theBacking.getElementsAfter(id);
		}

		@Override
		public CollectionElement<ObservableConfig> getElement(ObservableConfig value, boolean first) {
			if (!(value instanceof UnmodifiableObservableConfig))
				return null;
			CollectionElement<ObservableConfig> el = theBacking.getElement(((UnmodifiableObservableConfig) value).theWrapped, first);
			return el == null ? null : new WrappedElement(el);
		}

		@Override
		public CollectionElement<ObservableConfig> getElement(ElementId id) {
			return new WrappedElement(theBacking.getElement(id));
		}

		@Override
		public CollectionElement<ObservableConfig> getTerminalElement(boolean first) {
			CollectionElement<ObservableConfig> el = theBacking.getTerminalElement(first);
			return el == null ? null : new WrappedElement(el);
		}

		@Override
		public CollectionElement<ObservableConfig> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<ObservableConfig> el = theBacking.getAdjacentElement(elementId, next);
			return el == null ? null : new WrappedElement(el);
		}

		@Override
		public MutableCollectionElement<ObservableConfig> mutableElement(ElementId id) {
			return new WrappedElement(theBacking.getElement(id));
		}

		@Override
		public BetterList<CollectionElement<ObservableConfig>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theBacking.getElementsBySource(sourceEl, sourceCollection), WrappedElement::new);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				sourceCollection = theBacking;
			return theBacking.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theBacking.getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(ObservableConfig value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<ObservableConfig> addElement(ObservableConfig value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			if (after != null && after.compareTo(valueEl) > 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			if (before != null && before.compareTo(valueEl) < 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			return null;
		}

		@Override
		public CollectionElement<ObservableConfig> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) throws UnsupportedOperationException, IllegalArgumentException {
			if (after != null && after.compareTo(valueEl) > 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			if (before != null && before.compareTo(valueEl) < 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return getElement(valueEl);
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theBacking.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theBacking.lock(false, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theBacking.tryLock(false, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theBacking.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theBacking.getCoreId();
		}

		@Override
		public boolean isEmpty() {
			return theBacking.isEmpty();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theBacking.getIdentity(), "unmodifiable");
		}

		@Override
		public int size() {
			return theBacking.size();
		}

		@Override
		public long getStamp() {
			return theBacking.getStamp();
		}

		@Override
		public CollectionElement<ObservableConfig> getElement(int index) throws IndexOutOfBoundsException {
			return new WrappedElement(theBacking.getElement(index));
		}

		@Override
		public void clear() {
			if (!isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		private class WrappedElement implements MutableCollectionElement<ObservableConfig> {
			private final CollectionElement<ObservableConfig> theBackingElement;

			WrappedElement(CollectionElement<ObservableConfig> backing) {
				theBackingElement = backing;
			}

			@Override
			public ElementId getElementId() {
				return theBackingElement.getElementId();
			}

			@Override
			public ObservableConfig get() {
				return unmodifiable(theBackingElement.get());
			}

			@Override
			public BetterCollection<ObservableConfig> getCollection() {
				return UnmodifiableChildList.this;
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(ObservableConfig value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(ObservableConfig value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}
}
