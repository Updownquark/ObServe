package org.observe.config;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableTSContent.FullObservableTSContent;
import org.observe.config.ObservableTSContent.ObservableChildSet;
import org.observe.config.ObservableTSContent.ObservableTSChild;
import org.observe.config.ObservableTSContent.SimpleObservableTSContent;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Nameable;
import org.qommons.Stamped;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

public interface ObservableTextStructure extends Nameable, Transactable, Stamped {
	String getValue();
	ObservableTextStructure setValue(String value);

	ObservableTextStructure getParent();
	ElementId getParentChildRef();

	BetterList<ObservableTextStructure> getContent();

	Observable<ObservableStructureEvent> watch(ObservableTextPath path);

	default Observable<ObservableStructureEvent> watch(String pathFilter) {
		return watch(ObservableTextPath.createPath(pathFilter));
	}

	default SyncValueSet<ObservableTextStructure> getAllContent() {
		return getContent(ObservableTextPath.ANY_NAME);
	}

	default SyncValueSet<ObservableTextStructure> getContent(String path) {
		return getContent(ObservableTextPath.createPath(path));
	}

	default SyncValueSet<ObservableTextStructure> getContent(ObservableTextPath path) {
		ObservableCollection<ObservableTextStructure> children;
		if (path.getElements().size() == 1) {
			if (path.getLastElement().isMulti())
				children = new FullObservableTSContent(this);
			else
				children = new SimpleObservableTSContent(this, path.getLastElement());
		} else {
			ObservableTextPath last = path.getLast();
			TypeToken<ObservableCollection<ObservableTextStructure>> collType = TypeTokens.get().keyFor(ObservableCollection.class)
				.parameterized(ObservableTextStructure.class);
			ObservableValue<? extends ObservableTextStructure> descendant = observeDescendant(path.getParent());
			ObservableCollection<ObservableTextStructure> emptyChildren = ObservableCollection.of(ObservableTextStructure.class);
			children = ObservableCollection.flattenValue(descendant.map(collType,
				p -> p == null ? emptyChildren : p.getContent(last).getValues(), //
					opts -> opts.cache(true).reEvalOnUpdate(false).fireIfUnchanged(false)));
		}
		return new ObservableChildSet(this, path, children);
	}

	default ObservableTextStructure getChild(ObservableTextPath.ObservableTextPathElement el, boolean createIfAbsent,
		Consumer<ObservableTextStructure> preAddMod) {
		String pathName = el.getName();
		if (pathName.equals(ObservableTextPath.ANY_NAME) || pathName.equals(ObservableTextPath.ANY_DEPTH))
			throw new IllegalArgumentException("Variable paths not allowed for getChild");
		ObservableTextStructure found = null;
		for (ObservableTextStructure config : getContent()) {
			if (el.matches(config)) {
				found = config;
				break;
			}
		}
		if (found == null && createIfAbsent) {
			found = addChild(pathName, ch -> {
				for (Map.Entry<String, String> attr : el.getAttributes().entrySet())
					ch.addChild(attr.getKey(), atCh -> atCh.setValue(attr.getValue()));
				if (preAddMod != null)
					preAddMod.accept(ch);
			});
		}
		return found;
	}

	default String getPath() {
		if (getParent() != null)
			return getParent().getPath() + ObservableTextPath.PATH_SEPARATOR_STR + getName();
		else
			return getName();
	}

	default int getIndexInParent() {
		return getParentChildRef() == null ? null : getParent().getContent().getElementsBefore(getParentChildRef());
	}

	default ObservableValue<ObservableTextStructure> observeDescendant(String path) {
		return observeDescendant(ObservableTextPath.createPath(path));
	}

	default ObservableValue<ObservableTextStructure> observeDescendant(ObservableTextPath path) {
		return new ObservableTSChild(this, path);
	}

	default SettableValue<String> observeValue() {
		return observeValue(ObservableTextPath.EMPTY_PATH);
	}

	default SettableValue<String> observeValue(String path) {
		return observeValue(ObservableTextPath.createPath(path));
	}

	default SettableValue<String> observeValue(ObservableTextPath path) {
		return new ObservableTSContent.ObservableTSValue(this, path);
	}

	default String get(String path) {
		return get(ObservableTextPath.createPath(path));
	}

	default String get(ObservableTextPath path) {
		ObservableTextStructure config = getChild(path, false, null);
		return config == null ? null : config.getValue();
	}

	default ObservableTextStructure getChild(String path) {
		return getChild(path, false, null);
	}

	default ObservableTextStructure getChild(String path, boolean createIfAbsent, Consumer<ObservableTextStructure> preAddMod) {
		return getChild(ObservableTextPath.createPath(path), createIfAbsent, preAddMod);
	}

	default ObservableTextStructure getChild(ObservableTextPath path, boolean createIfAbsent, Consumer<ObservableTextStructure> preAddMod) {
		if (path == null)
			throw new IllegalArgumentException("No path given");
		try (Transaction t = lock(createIfAbsent, null)) {
			ObservableTextStructure ret = this;
			for (ObservableTextPath.ObservableTextPathElement el : path.getElements()) {
				ObservableTextStructure found = ret.getChild(el, createIfAbsent, preAddMod);
				if (found == null)
					return null;
				ret = found;
			}
			return ret;
		}
	}

	default ObservableTextStructure addChild(String name) {
		return addChild(name, null);
	}

	default ObservableTextStructure addChild(String name, Consumer<ObservableTextStructure> preAddMod) {
		return addChild(null, null, false, name, preAddMod);
	}

	ObservableTextStructure addChild(ObservableTextStructure after, ObservableTextStructure before, boolean first, String name,
		Consumer<ObservableTextStructure> preAddMod);

	ObservableTextStructure moveChild(ObservableTextStructure child, ObservableTextStructure after, ObservableTextStructure before,
		boolean first, Runnable afterRemove);

	default ObservableTextStructure set(String path, String value) {
		ObservableTextStructure child = getChild(path, value != null, //
			ch -> ch.setValue(value));
		if (value == null) {
			if (child != null)
				child.remove();
		} else if (child.getValue() != value)
			child.setValue(value);
		return this;
	}

	ObservableTextStructure copyFrom(ObservableTSContent source, boolean removeExtras);

	void remove();

	public static class ObservableStructureEvent extends Causable.AbstractCausable {
		public final CollectionChangeType changeType;
		public final boolean isMove;
		public final ObservableTextStructure eventTarget;
		public final List<ObservableTextStructure> relativePath;
		public final String oldName;
		public final String oldValue;

		public ObservableStructureEvent(CollectionChangeType changeType, boolean move, ObservableTextStructure eventTarget, String oldName,
			String oldValue, List<ObservableTextStructure> relativePath, Object cause) {
			super(cause);
			this.changeType = changeType;
			this.isMove = move;
			this.eventTarget = eventTarget;
			this.relativePath = relativePath;
			this.oldName = oldName;
			this.oldValue = oldValue;
		}

		public String getRelativePathString() {
			StringBuilder str = new StringBuilder();
			for (ObservableTextStructure p : relativePath) {
				if (str.length() > 0)
					str.append(ObservableTextPath.PATH_SEPARATOR);
				str.append(p.getName());
			}
			return str.toString();
		}

		public ObservableStructureEvent asFromChild() {
			return new ObservableStructureEvent(changeType, isMove, relativePath.get(0), oldName, oldValue,
				relativePath.subList(1, relativePath.size()), getCauses());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(eventTarget.getName());
			switch (changeType) {
			case add:
				str.append(":+").append(relativePath.isEmpty() ? "this" : getRelativePathString());
				break;
			case remove:
				str.append(":-").append(relativePath.isEmpty() ? "this" : getRelativePathString());
				break;
			case set:
				str.append(ObservableTextPath.PATH_SEPARATOR).append(relativePath.isEmpty() ? "this" : getRelativePathString());
				ObservableTextStructure changed = relativePath.isEmpty() ? eventTarget : relativePath.get(relativePath.size() - 1);
				if (!oldName.equals(changed.getName()))
					str.append(".name ").append(oldName).append("->").append(changed.getName());
				else
					str.append(".value ").append(oldValue).append("->").append(changed.getValue());
			}
			return str.toString();
		}
	}

	public static ObservableTextStructure
}
