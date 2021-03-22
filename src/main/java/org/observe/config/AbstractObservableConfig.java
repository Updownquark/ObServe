package org.observe.config;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.qommons.Lockable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ElementId;

/** An abstract {@link ObservableConfig} class that takes care of some common implementation */
public abstract class AbstractObservableConfig implements ObservableConfig {
	private AbstractObservableConfig theParent;

	private volatile WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> theParsedItems;

	/** @param parent The parent for this config element */
	protected void initialize(AbstractObservableConfig parent) {
		theParent=parent;
	}

	/**
	 * @param name The name of the child to create
	 * @return The new child
	 */
	protected abstract AbstractObservableConfig createChild(String name);

	@Override
	public AbstractObservableConfig getParent() {
		return theParent;
	}

	@Override
	public Object getParsedItem(ObservableConfigParseSession session) {
		WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> parsedItems = theParsedItems;
		if (parsedItems == null)
			return null;
		WeakReference<Object> ref = parsedItems.get(session);
		return ref == null ? null : ref.get();
	}

	@Override
	public ObservableConfig withParsedItem(ObservableConfigParseSession session, Object item) {
		WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> parsedItems = theParsedItems;
		if (parsedItems == null) {
			synchronized (this) {
				parsedItems = theParsedItems;
				if (parsedItems == null) {
					theParsedItems = parsedItems = new WeakHashMap<>(3); // Don't imagine there will ever be much in there
				}
			}
		}
		parsedItems.compute(session, (s, old) -> {
			Object oldItem = old == null ? null : old.get();
			if (oldItem == item)
				return old;
			else
				return new WeakReference<>(item);
		});
		return this;
	}

	@Override
	public ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod) {
		return addChild(after, before, first, name, preAddMod, false);
	}

	private AbstractObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<? super AbstractObservableConfig> preAddMod, boolean move) {
		try (Transaction t = lock(true, null)) {
			AbstractObservableConfig child = createChild(name);
			child.theParent = this;
			if (preAddMod != null)
				preAddMod.accept(child);
			addChild(child, after, before, first, move);
			return child;
		}
	}

	/**
	 * @param child The child to add
	 * @param after The element to add the config after (or null to add it at the beginning)
	 * @param before The element to add the config before (or null to add it at the end)
	 * @param first Whether to prefer adding the child toward the beginning of the sequence between <code>after</code> and
	 *        <code>before</code>
	 * @param move Whether the addition is the last step in a move operation of a child from one position in this parent to another
	 */
	protected abstract void addChild(AbstractObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		boolean move);

	@Override
	public ObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove) {
		try (Transaction t = lock(true, null)) {
			if (child.getParent() != this)
				throw new NoSuchElementException("Config is not a child of this config");
			if (!child.getParentChildRef().isPresent())
				throw new NoSuchElementException("Config has already been removed");

			((AbstractObservableConfig) child)._remove(true);
			if (afterRemove != null)
				afterRemove.run();
			return addChild(after, before, first, child.getName(), newChild -> newChild._copyFrom(child, true, true), true);
		}
	}

	@Override
	public ObservableConfig copyFrom(ObservableConfig source, boolean removeExtras) {
		try (Transaction t = Lockable.lockAll(//
			Lockable.lockable(source, false, null), //
			Lockable.lockable(this, true, null))) {
			_copyFrom(source, removeExtras, false);
		}
		return this;
	}

	private void _copyFrom(ObservableConfig source, boolean removeExtras, boolean withParsedItems) {
		if (source instanceof AbstractObservableConfig && ((AbstractObservableConfig) source).theParsedItems != null && withParsedItems) {
			for (Map.Entry<ObservableConfigParseSession, WeakReference<Object>> pi : ((AbstractObservableConfig) source).theParsedItems
				.entrySet()) {
				Object value = pi.getValue().get();
				if (value != null)
					withParsedItem(pi.getKey(), value);
			}
		}
		if (!Objects.equals(getValue(), source.getValue()))
			setValue(source.getValue());
		List<AbstractObservableConfig> children = new ArrayList<>(getContent().size());
		children.addAll((BetterList<AbstractObservableConfig>) (BetterList<?>) getContent());
		CollectionUtils.synchronize(children, source.getContent(), (o1, o2) -> {
			return o1.getName().equals(o2.getName());
		}).adjust(new CollectionUtils.CollectionSynchronizer<AbstractObservableConfig, ObservableConfig>() {
			@Override
			public boolean getOrder(ElementSyncInput<AbstractObservableConfig, ObservableConfig> element) {
				return false;
			}

			@Override
			public ElementSyncAction leftOnly(ElementSyncInput<AbstractObservableConfig, ObservableConfig> element) {
				if (removeExtras)
					return element.remove();
				else
					return element.preserve();
			}

			@Override
			public ElementSyncAction rightOnly(ElementSyncInput<AbstractObservableConfig, ObservableConfig> element) {
				ObservableConfig before = element.getTargetIndex() == children.size() ? null : children.get(element.getTargetIndex());
				return element.useValue(addChild(null, before, false, element.getRightValue().getName(),
					newChild -> newChild._copyFrom(element.getRightValue(), removeExtras, withParsedItems), false));
			}

			@Override
			public ElementSyncAction common(ElementSyncInput<AbstractObservableConfig, ObservableConfig> element) {
				element.getLeftValue()._copyFrom(element.getRightValue(), removeExtras, withParsedItems);
				return element.preserve();
			}
		}, CollectionUtils.AdjustmentOrder.RightOrder);
	}

	@Override
	public void remove() {
		_remove(false);
	}

	private void _remove(boolean move) {
		try (Transaction t = lock(true, null)) {
			ElementId pcr = getParentChildRef();
			if (pcr == null || !pcr.isPresent())
				return;
			doRemove(move);
		}
		Map<?, ?> parsedItems = theParsedItems;
		if (parsedItems != null && !move)
			parsedItems.clear();
		theParent = null;
		_postRemove();
	}

	/**
	 * Removes this config element from its parent
	 *
	 * @param move Whether this is the first step in a movement operation of this child from one position to another in its parent's
	 *        children
	 */
	protected abstract void doRemove(boolean move);

	/** Called after a remove operation */
	protected abstract void _postRemove();
}
