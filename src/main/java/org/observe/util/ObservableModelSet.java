package org.observe.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.qommons.QommonsUtils;

import com.google.common.reflect.TypeToken;

public interface ObservableModelSet {
	interface ValueContainer<T, X> extends Function<ModelSetInstance, X> {
		ModelType getModelType();

		TypeToken<T> getValueType();

		X get(ModelSetInstance extModels);

		@Override
		default X apply(ModelSetInstance extModels) {
			return get(extModels);
		}
	}

	interface MapContainer<K, V, X> extends ValueContainer<V, X> {
		TypeToken<K> getKeyType();
	}

	String getPath();

	ModelType getType(String path);

	ValueContainer<?, ?> getThing(String path) throws IllegalArgumentException;

	<T> ValueContainer<T, Observable<? extends T>> getEvent(String path, TypeToken<T> type) throws IllegalArgumentException;

	<T> ValueContainer<T, ObservableAction<? extends T>> getAction(String path, TypeToken<T> type) throws IllegalArgumentException;

	<T> ValueContainer<T, SettableValue<T>> getValue(String path, TypeToken<T> type) throws IllegalArgumentException;

	<T> ValueContainer<T, ObservableCollection<T>> getCollection(String path, TypeToken<T> type) throws IllegalArgumentException;

	<T> ValueContainer<T, ObservableSortedCollection<T>> getSortedCollection(String path, TypeToken<T> type)
		throws IllegalArgumentException;

	<T> ValueContainer<T, ObservableSet<T>> getSet(String path, TypeToken<T> type) throws IllegalArgumentException;

	<T> ValueContainer<T, ObservableSortedSet<T>> getSortedSet(String path, TypeToken<T> type) throws IllegalArgumentException;

	<K, V> MapContainer<K, V, ObservableMap<K, V>> getMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
		throws IllegalArgumentException;

	<K, V> MapContainer<K, V, ObservableSortedMap<K, V>> getSortedMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
		throws IllegalArgumentException;

	<K, V> MapContainer<K, V, ObservableMultiMap<K, V>> getMultiMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
		throws IllegalArgumentException;

	<K, V> MapContainer<K, V, ObservableSortedMultiMap<K, V>> getSortedMultiMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
		throws IllegalArgumentException;

	ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until);

	public static Builder build() {
		return new Builder(null, "");
	}

	public static ExternalModelSetBuilder buildExternal() {
		return new ExternalModelSetBuilder(null, "");
	}

	public static class ModelSetInstance {
		final Map<Default.Placeholder<?, ?, ?>, Object> theThings;
		private final Observable<?> theUntil;

		ModelSetInstance(Observable<?> until) {
			theThings = new LinkedHashMap<>();
			theUntil = until;
		}

		public Observable<?> getUntil() {
			return theUntil;
		}
	}

	public enum ModelType {
		Event(Observable.class),
		Action(ObservableAction.class),
		Value(SettableValue.class), //
		Collection(ObservableCollection.class),
		SortedCollection(ObservableSortedCollection.class, Collection),
		Set(ObservableSet.class, Collection),
		SortedSet(ObservableSortedSet.class, Collection, SortedCollection, Set), //
		Map(ObservableMap.class),
		SortedMap(ObservableSortedMap.class, Map), //
		MultiMap(ObservableMultiMap.class),
		SortedMultiMap(ObservableSortedMultiMap.class, MultiMap), //
		Model(ObservableModelSet.class);

		public final Class<?> type;
		public final Set<ModelType> superTypes;

		private ModelType(Class<?> type, ModelType... exts) {
			this.type = type;
			superTypes = QommonsUtils.unmodifiableDistinctCopy(exts);
		}

		public boolean isExtension(ModelType type) {
			return this == type || superTypes.contains(type);
		}

		public static ModelType of(Class<?> thing) {
			if (ObservableModelSet.class.isAssignableFrom(thing))
				return Model;
			else if (ObservableSortedMultiMap.class.isAssignableFrom(thing))
				return SortedMultiMap;
			else if (ObservableMultiMap.class.isAssignableFrom(thing))
				return MultiMap;
			else if (ObservableSortedMap.class.isAssignableFrom(thing))
				return SortedMap;
			else if (ObservableMap.class.isAssignableFrom(thing))
				return Map;
			else if (ObservableSortedSet.class.isAssignableFrom(thing))
				return SortedSet;
			else if (ObservableSet.class.isAssignableFrom(thing))
				return Set;
			else if (ObservableSortedCollection.class.isAssignableFrom(thing))
				return SortedCollection;
			else if (ObservableCollection.class.isAssignableFrom(thing))
				return Collection;
			else if (SettableValue.class.isAssignableFrom(thing))
				return Value;
			else if (ObservableAction.class.isAssignableFrom(thing))
				return Action;
			else if (Observable.class.isAssignableFrom(thing))
				return Event;
			else
				return null;
		}

		public static String printType(Object value) {
			return printType(of(value.getClass()));
		}

		public static String print(ModelType type) {
			return type == null ? "Unknown" : type.toString();
		}
	}

	public class ExternalModelSet {
		private final ExternalModelSet theRoot;
		private final String thePath;
		private final Map<String, Placeholder> theThings;

		ExternalModelSet(ExternalModelSet root, String path, Map<String, Placeholder> things) {
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = things;
		}

		private <T> T getThing(String path, ModelType type, TypeToken<?> valueType, TypeToken<?> keyType) throws IllegalArgumentException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				ExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getThing(path.substring(dot + 1), type, valueType, keyType);
			}
			Placeholder thing = theThings.get(path);
			if (thing == null)
				throw new IllegalArgumentException("No such " + type + " declared: '" + pathTo(path) + "'");
			else if (!thing.type.isExtension(type))
				throw new IllegalArgumentException("'" + pathTo(path) + "' is a " + ModelType.printType(thing) + ", not a " + type);
			if (type == ModelType.Event || type == ModelType.Action) {
				if (valueType != null && !valueType.isAssignableFrom(thing.valueType))
					throw new IllegalArgumentException(
						type + " '" + pathTo(path) + "' is typed " + thing.valueType + ", not compatible with " + valueType);
			} else if (valueType != null && !valueType.equals(thing.valueType))
				throw new IllegalArgumentException(type + " '" + pathTo(path) + "' is typed " + thing.valueType + ", not " + valueType);
			if (keyType != null && !keyType.equals(thing.keyType))
				throw new IllegalArgumentException(type + " '" + pathTo(path) + "' is key-typed " + thing.keyType + ", not " + keyType);
			return (T) thing.thing;
		}

		private ExternalModelSet getSubModel(String path) {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder subModel = theThings.get(modelName);
			if (subModel == null)
				throw new IllegalArgumentException("No such sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type != ModelType.Model)
				throw new IllegalArgumentException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return (ExternalModelSet) subModel.thing;
			else
				return ((ExternalModelSet) subModel.thing).getSubModel(path.substring(dot + 1));
		}

		private String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		public <T> Observable<? extends T> getEvent(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Event, type, null);
		}

		public <T> ObservableAction<? extends T> getAction(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Action, type, null);
		}

		public <T> SettableValue<T> getValue(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Value, type, null);
		}

		public <T> ObservableCollection<T> getCollection(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Collection, type, null);
		}

		public <T> ObservableSortedCollection<T> getSortedCollection(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.SortedCollection, type, null);
		}

		public <T> ObservableSet<T> getSet(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Set, type, null);
		}

		public <T> ObservableSortedSet<T> getSortedSet(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.SortedSet, type, null);
		}

		public <K, V> ObservableMap<K, V> getMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.Map, valueType, keyType);
		}

		public <K, V> ObservableSortedMap<K, V> getSortedMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.SortedMap, valueType, keyType);
		}

		public <K, V> ObservableMultiMap<K, V> getMultiMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.MultiMap, valueType, keyType);
		}

		public <K, V> ObservableSortedMultiMap<K, V> getSortedMultiMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.SortedMultiMap, valueType, keyType);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().thing instanceof ExternalModelSet)
					((ExternalModelSet) thing.getValue().thing).print(str, indent + 1);
				else {
					str.append('<');
					if (thing.getValue().keyType != null)
						str.append(thing.getValue().keyType).append(", ");
					str.append(thing.getValue().valueType).append('>');
				}
			}
		}

		static class Placeholder {
			final ModelType type;
			final TypeToken<?> valueType;
			final TypeToken<?> keyType;
			final Object thing;

			Placeholder(ModelType type, TypeToken<?> valueType, TypeToken<?> keyType, Object thing) {
				this.type = type;
				this.valueType = valueType;
				this.keyType = keyType;
				this.thing = thing;
			}
		}
	}

	public interface ValueGetter<T> {
		T get(ModelSetInstance models, ExternalModelSet extModels);
	}

	public class ExternalModelSetBuilder {
		private final ExternalModelSetBuilder theRoot;
		private final String thePath;
		private final Map<String, ExternalModelSet.Placeholder> theThings;

		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path) {
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = new LinkedHashMap<>();
		}

		private String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		public <T> ExternalModelSetBuilder withEvent(String name, TypeToken<T> type, Observable<? extends T> event) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.Event, type, null, event));
			return this;
		}

		public <T> ExternalModelSetBuilder withEvent(String name, TypeToken<T> type, Consumer<SimpleObservable.Builder> event) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			SimpleObservable.Builder builder = SimpleObservable.build();
			builder.withDescription(pathTo(name));
			if (event != null)
				event.accept(builder);
			return withEvent(name, type, builder.build());
		}

		public <T> ExternalModelSetBuilder withAction(String name, ObservableAction<? extends T> action) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.Action, action.getType(), null, action));
			return this;
		}

		public <T> ExternalModelSetBuilder withValue(String name, SettableValue<T> value) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.Value, value.getType(), null, value));
			return this;
		}

		public <T> ExternalModelSetBuilder withValue(String name, Class<T> type, Consumer<SettableValue.Builder<T>> value) {
			return withValue(name, TypeTokens.get().of(type), value);
		}

		public <T> ExternalModelSetBuilder withValue(String name, TypeToken<T> type, Consumer<SettableValue.Builder<T>> value) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			SettableValue.Builder<T> builder = SettableValue.build(type);
			builder.withDescription(pathTo(name));
			if (value != null)
				value.accept(builder);
			return withValue(name, builder.build());
		}

		public <T> ExternalModelSetBuilder withCollection(String name, ObservableCollection<T> collection) {
			ModelType type;
			if (collection instanceof ObservableSortedSet)
				type = ModelType.SortedSet;
			else if (collection instanceof ObservableSet)
				type = ModelType.Set;
			else if (collection instanceof ObservableSortedCollection)
				type = ModelType.SortedCollection;
			else
				type = ModelType.Collection;
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, collection.getType(), null, collection));
			return this;
		}

		public <T> ExternalModelSetBuilder withCollection(String name, Class<T> type, Consumer<ObservableCollectionBuilder<T, ?>> collection) {
			return withCollection(name, TypeTokens.get().of(type), collection);
		}

		public <T> ExternalModelSetBuilder withCollection(String name, TypeToken<T> type,
			Consumer<ObservableCollectionBuilder<T, ?>> collection) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableCollectionBuilder<T, ?> builder = ObservableCollection.build(type);
			builder.withDescription(pathTo(name));
			if (collection != null)
				collection.accept(builder);
			return withCollection(name, builder.build());
		}

		public <T> ExternalModelSetBuilder withSortedCollection(String name, ObservableSortedCollection<T> collection) {
			ModelType type;
			if (collection instanceof ObservableSortedSet)
				type = ModelType.SortedSet;
			else
				type = ModelType.SortedCollection;
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, collection.getType(), null, collection));
			return this;
		}

		public <T> ExternalModelSetBuilder withSortedCollection(String name, Class<T> type, Comparator<? super T> sorting,
			Consumer<ObservableCollectionBuilder.SortedBuilder<T, ?>> collection) {
			return withSortedCollection(name, TypeTokens.get().of(type), sorting, collection);
		}

		public <T> ExternalModelSetBuilder withSortedCollection(String name, TypeToken<T> type, Comparator<? super T> sorting,
			Consumer<ObservableCollectionBuilder.SortedBuilder<T, ?>> collection) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableCollectionBuilder.SortedBuilder<T, ?> builder = ObservableCollection.build(type).sortBy(sorting);
			builder.withDescription(pathTo(name));
			if (collection != null)
				collection.accept(builder);
			return withSortedCollection(name, builder.build());
		}

		public <T> ExternalModelSetBuilder withSet(String name, ObservableSet<T> set) {
			ModelType type;
			if (set instanceof ObservableSortedSet)
				type = ModelType.SortedSet;
			else
				type = ModelType.Set;
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, set.getType(), null, set));
			return this;
		}

		public <T> ExternalModelSetBuilder withSet(String name, Class<T> type,
			Consumer<ObservableCollectionBuilder.DistinctBuilder<T, ?>> collection) {
			return withSet(name, TypeTokens.get().of(type), collection);
		}

		public <T> ExternalModelSetBuilder withSet(String name, TypeToken<T> type,
			Consumer<ObservableCollectionBuilder.DistinctBuilder<T, ?>> collection) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableCollectionBuilder.DistinctBuilder<T, ?> builder = ObservableCollection.build(type).distinct();
			builder.withDescription(pathTo(name));
			if (collection != null)
				collection.accept(builder);
			return withSet(name, builder.build());
		}

		public <T> ExternalModelSetBuilder withSortedSet(String name, ObservableSortedSet<T> sortedSet) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.SortedSet, sortedSet.getType(), null, sortedSet));
			return this;
		}

		public <T> ExternalModelSetBuilder withSortedSet(String name, Class<T> type, Comparator<? super T> sorting,
			Consumer<ObservableCollectionBuilder.DistinctSortedBuilder<T, ?>> collection) {
			return withSortedSet(name, type, sorting, collection);
		}

		public <T> ExternalModelSetBuilder withSortedSet(String name, TypeToken<T> type, Comparator<? super T> sorting,
			Consumer<ObservableCollectionBuilder.DistinctSortedBuilder<T, ?>> collection) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableCollectionBuilder.DistinctSortedBuilder<T, ?> builder = ObservableCollection.build(type).distinctSorted(sorting);
			builder.withDescription(pathTo(name));
			if (collection != null)
				collection.accept(builder);
			return withSortedSet(name, builder.build());
		}

		public <K, V> ExternalModelSetBuilder withMap(String name, ObservableMap<K, V> map) {
			ModelType type;
			if (map instanceof ObservableSortedMap)
				type = ModelType.SortedMap;
			else
				type = ModelType.Map;
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, map.getValueType(), map.getKeyType(), map));
			return this;
		}

		public <K, V> ExternalModelSetBuilder withMap(String name, Class<K> keyType, Class<V> valueType,
			Consumer<ObservableMap.Builder<K, V, ?>> map) {
			return withMap(name, TypeTokens.get().of(keyType), TypeTokens.get().of(valueType), map);
		}

		public <K, V> ExternalModelSetBuilder withMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			Consumer<ObservableMap.Builder<K, V, ?>> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableMap.Builder<K, V, ?> builder = ObservableMap.build(keyType, valueType);
			builder.withDescription(pathTo(name));
			if (map != null)
				map.accept(builder);
			return withMap(name, builder.buildMap());
		}

		public <K, V> ExternalModelSetBuilder withSortedMap(String name, ObservableSortedMap<K, V> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.SortedMap, map.getValueType(), map.getKeyType(), map));
			return this;
		}

		public <K, V> ExternalModelSetBuilder withSortedMap(String name, Class<K> keyType, Class<V> valueType, Comparator<? super K> sorting,
			Consumer<ObservableSortedMap.Builder<K, V, ?>> map) {
			return withSortedMap(name, //
				TypeTokens.get().of(keyType), TypeTokens.get().of(valueType), //
				sorting, map);
		}

		public <K, V> ExternalModelSetBuilder withSortedMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			Comparator<? super K> sorting, Consumer<ObservableSortedMap.Builder<K, V, ?>> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableSortedMap.Builder<K, V, ?> builder = ObservableSortedMap.build(keyType, valueType, sorting);
			builder.withDescription(pathTo(name));
			if (map != null)
				map.accept(builder);
			return withSortedMap(name, builder.buildMap());
		}

		public <K, V> ExternalModelSetBuilder withMultiMap(String name, ObservableMultiMap<K, V> map) {
			ModelType type;
			if (map instanceof ObservableSortedMultiMap)
				type = ModelType.SortedMultiMap;
			else
				type = ModelType.MultiMap;
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, map.getValueType(), map.getKeyType(), map));
			return this;
		}

		public <K, V> ExternalModelSetBuilder withMultiMap(String name, Class<K> keyType, Class<V> valueType, Observable<?> until,
			Consumer<ObservableMultiMap.Builder<K, V>> map) {
			return withMultiMap(name, //
				TypeTokens.get().of(keyType), TypeTokens.get().of(valueType), //
				until, map);
		}

		public <K, V> ExternalModelSetBuilder withMultiMap(String name, TypeToken<K> keyType, TypeToken<V> valueType, Observable<?> until,
			Consumer<ObservableMultiMap.Builder<K, V>> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableMultiMap.Builder<K, V> builder = ObservableMultiMap.build(keyType, valueType);
			builder.withDescription(pathTo(name));
			if (map != null)
				map.accept(builder);
			return withMultiMap(name, builder.build(until));
		}

		public <K, V> ExternalModelSetBuilder withSortedMultiMap(String name, ObservableSortedMultiMap<K, V> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.SortedMultiMap, map.getValueType(), map.getKeyType(), map));
			return this;
		}

		public <K, V> ExternalModelSetBuilder withSortedMultiMap(String name, Class<K> keyType, Class<V> valueType,
			Comparator<? super K> sorting, Observable<?> until, Consumer<ObservableSortedMultiMap.Builder<K, V>> map) {
			return withSortedMultiMap(name, //
				TypeTokens.get().of(keyType), TypeTokens.get().of(valueType), //
				sorting, until, map);
		}

		public <K, V> ExternalModelSetBuilder withSortedMultiMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			Comparator<? super K> sorting, Observable<?> until, Consumer<ObservableSortedMultiMap.Builder<K, V>> map) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ObservableSortedMultiMap.Builder<K, V> builder = ObservableMultiMap.build(keyType, valueType).sortedBy(sorting);
			builder.withDescription(pathTo(name));
			if (map != null)
				map.accept(builder);
			return withSortedMultiMap(name, builder.build(until));
		}

		public ExternalModelSetBuilder addSubModel(String name) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder(theRoot, pathTo(name));
			theThings.put(name, new ExternalModelSet.Placeholder(ModelType.Model, null, null, subModel));
			return subModel;
		}

		public ExternalModelSet build() {
			return _build(null, thePath);
		}

		private ExternalModelSet _build(ExternalModelSet root, String path) {
			Map<String, ExternalModelSet.Placeholder> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			ExternalModelSet model = new ExternalModelSet(root, path, Collections.unmodifiableMap(things));
			if (root == null)
				root = model;
			for (Map.Entry<String, ExternalModelSet.Placeholder> thing : theThings.entrySet()) {
				if (thing.getValue().type == ModelType.Model)
					things.put(thing.getKey(), new ExternalModelSet.Placeholder(ModelType.Model, null, null, //
						((ExternalModelSetBuilder) thing.getValue().thing)._build(root, path + "." + thing.getKey())));
				else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}

	public class Default implements ObservableModelSet {
		protected final Default theRoot;
		protected final String thePath;
		protected final Map<String, Placeholder<?, ?, ?>> theThings;

		protected Default(Default root, String path, Map<String, Placeholder<?, ?, ?>> things) {
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = things;
		}

		private <K, V, T> MapContainer<K, V, T> getThing(String path, ModelType type, TypeToken<V> valueType, TypeToken<K> keyType)
			throws IllegalArgumentException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getThing(path.substring(dot + 1), type, valueType, keyType);
			}
			Placeholder<?, ?, ?> thing = theThings.get(path);
			if (thing == null)
				throw new IllegalArgumentException("No such " + type + " declared: '" + pathTo(path) + "'");
			else if (!thing.type.isExtension(type))
				throw new IllegalArgumentException("'" + pathTo(path) + "' is a " + ModelType.printType(thing) + ", not a " + type);
			if (type == ModelType.Event || type == ModelType.Action) {
				if (valueType != null && !valueType.isAssignableFrom(thing.valueType))
					throw new IllegalArgumentException(
						type + " '" + pathTo(path) + "' is typed " + thing.valueType + ", not compatible with " + valueType);
			} else if (valueType != null && !valueType.equals(thing.valueType))
				throw new IllegalArgumentException(type + " '" + pathTo(path) + "' is typed " + thing.valueType + ", not " + valueType);
			if (keyType != null && !keyType.equals(thing.keyType))
				throw new IllegalArgumentException(type + " '" + pathTo(path) + "' is key-typed " + thing.keyType + ", not " + keyType);
			return (MapContainer<K, V, T>) thing;
		}

		private Default getSubModel(String path) {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder<?, ?, ?> subModel = theThings.get(modelName);
			if (subModel == null)
				throw new IllegalArgumentException("No such sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type != ModelType.Model)
				throw new IllegalArgumentException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return subModel.model;
			else
				return subModel.model.getSubModel(path.substring(dot + 1));
		}

		String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String getPath() {
			return thePath;
		}

		@Override
		public ModelType getType(String path) {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getType(path.substring(dot + 1));
			}
			Placeholder<?, ?, ?> thing = theThings.get(path);
			if (thing == null)
				throw new IllegalArgumentException("No such value declared: '" + pathTo(path) + "'");
			return thing.type;
		}

		@Override
		public ValueContainer<?, ?> getThing(String path) throws IllegalArgumentException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getThing(path.substring(dot + 1));
			}
			Placeholder<?, ?, ?> thing = theThings.get(path);
			if (thing == null)
				throw new IllegalArgumentException("No such value declared: '" + pathTo(path) + "'");
			if (thing.model != null)
				throw new IllegalArgumentException(pathTo(path) + " is a model");
			return thing;
		}

		@Override
		public <T> ValueContainer<T, Observable<? extends T>> getEvent(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Event, type, null);
		}

		@Override
		public <T> ValueContainer<T, ObservableAction<? extends T>> getAction(String path, TypeToken<T> type)
			throws IllegalArgumentException {
			return getThing(path, ModelType.Action, type, null);
		}

		@Override
		public <T> ValueContainer<T, SettableValue<T>> getValue(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Value, type, null);
		}

		@Override
		public <T> ValueContainer<T, ObservableCollection<T>> getCollection(String path, TypeToken<T> type)
			throws IllegalArgumentException {
			return getThing(path, ModelType.Collection, type, null);
		}

		@Override
		public <T> ValueContainer<T, ObservableSortedCollection<T>> getSortedCollection(String path, TypeToken<T> type)
			throws IllegalArgumentException {
			return getThing(path, ModelType.SortedCollection, type, null);
		}

		@Override
		public <T> ValueContainer<T, ObservableSet<T>> getSet(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.Set, type, null);
		}

		@Override
		public <T> ValueContainer<T, ObservableSortedSet<T>> getSortedSet(String path, TypeToken<T> type) throws IllegalArgumentException {
			return getThing(path, ModelType.SortedSet, type, null);
		}

		@Override
		public <K, V> MapContainer<K, V, ObservableMap<K, V>> getMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.Map, valueType, keyType);
		}

		@Override
		public <K, V> MapContainer<K, V, ObservableSortedMap<K, V>> getSortedMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.SortedMap, valueType, keyType);
		}

		@Override
		public <K, V> MapContainer<K, V, ObservableMultiMap<K, V>> getMultiMap(String path, TypeToken<K> keyType, TypeToken<V> valueType)
			throws IllegalArgumentException {
			return getThing(path, ModelType.MultiMap, valueType, keyType);
		}

		@Override
		public <K, V> MapContainer<K, V, ObservableSortedMultiMap<K, V>> getSortedMultiMap(String path, TypeToken<K> keyType,
			TypeToken<V> valueType) throws IllegalArgumentException {
			return getThing(path, ModelType.SortedMultiMap, valueType, keyType);
		}

		@Override
		public ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until) {
			if (until == null)
				until = Observable.empty();
			ModelSetInstance modelSet = new ModelSetInstance(until);
			install(modelSet, extModel);
			return modelSet;
		}

		private void install(ModelSetInstance modelSet, ExternalModelSet extModel) {
			for (Placeholder<?, ?, ?> thing : theThings.values()) {
				if (thing.model != null)
					thing.model.install(modelSet, extModel);
				else
					modelSet.theThings.put(thing, thing.getter.get(modelSet, extModel));
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(thePath.isEmpty() ? "<root>" : thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder<?, ?, ?>> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().model != null)
					thing.getValue().model.print(str, indent + 1);
				else {
					str.append('<');
					if (thing.getValue().keyType != null)
						str.append(thing.getValue().keyType).append(", ");
					str.append(thing.getValue().valueType).append('>');
				}
			}
		}

		static class Placeholder<K, V, T> implements MapContainer<K, V, T> {
			final String thePath;
			final ModelType type;
			final TypeToken<V> valueType;
			final TypeToken<K> keyType;
			final ValueGetter<T> getter;
			final Default model;

			Placeholder(String path, ModelType type, TypeToken<V> valueType, TypeToken<K> keyType, ValueGetter<T> getter, Default model) {
				thePath = path;
				this.type = type;
				this.valueType = valueType;
				this.keyType = keyType;
				this.getter = getter;
				this.model = model;
			}

			@Override
			public ModelType getModelType() {
				return type;
			}

			@Override
			public TypeToken<K> getKeyType() {
				return keyType;
			}

			@Override
			public TypeToken<V> getValueType() {
				return valueType;
			}

			@Override
			public T get(ModelSetInstance extModels) {
				return (T) extModels.theThings.get(this);
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder(type.toString()).append('<');
				if (keyType != null)
					str.append(keyType).append(", ");
				return str.append(valueType).append(">@").append(thePath).toString();
			}
		}
	}

	public class Builder extends Default implements ObservableModelSet {
		private Builder(Builder root, String path) {
			super(root, path, new LinkedHashMap<>());
		}

		public <T> Builder withEvent(String name, TypeToken<T> type, ValueGetter<Observable<? extends T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Event, type, null, getter, null));
			return this;
		}

		public <T> Builder withAction(String name, TypeToken<T> type, ValueGetter<ObservableAction<? extends T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Action, type, null, getter, null));
			return this;
		}

		public <T> Builder withValue(String name, TypeToken<T> type, ValueGetter<SettableValue<T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Value, type, null, getter, null));
			return this;
		}

		public <T> Builder withCollection(String name, TypeToken<T> type, ValueGetter<ObservableCollection<T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Collection, type, null, getter, null));
			return this;
		}

		public <T> Builder withSortedCollection(String name, TypeToken<T> type, ValueGetter<ObservableSortedCollection<T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.SortedCollection, type, null, getter, null));
			return this;
		}

		public <T> Builder withSet(String name, TypeToken<T> type, ValueGetter<ObservableSet<T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Set, type, null, getter, null));
			return this;
		}

		public <T> Builder withSortedSet(String name, TypeToken<T> type, ValueGetter<ObservableSortedSet<T>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.SortedSet, type, null, getter, null));
			return this;
		}

		public <K, V> Builder withMap(String name, TypeToken<K> keyType, TypeToken<V> valueType, ValueGetter<ObservableMap<K, V>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Map, valueType, keyType, getter, null));
			return this;
		}

		public <K, V> Builder withSortedMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			ValueGetter<ObservableSortedMap<K, V>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.SortedMap, valueType, keyType, getter, null));
			return this;
		}

		public <K, V> Builder withMultiMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			ValueGetter<ObservableMultiMap<K, V>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException("A value of type " + theThings.get(name) + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.MultiMap, valueType, keyType, getter, null));
			return this;
		}

		public <K, V> Builder withSortedMultiMap(String name, TypeToken<K> keyType, TypeToken<V> valueType,
			ValueGetter<ObservableSortedMultiMap<K, V>> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.SortedMultiMap, valueType, keyType, getter, null));
			return this;
		}

		public Builder createSubModel(String name) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			Builder subModel = new Builder((Builder) theRoot, pathTo(name));
			theThings.put(name, new Placeholder<>(pathTo(name), ModelType.Model, null, null, null, subModel));
			return subModel;
		}

		public ObservableModelSet build() {
			return _build(null, thePath);
		}

		private Default _build(Default root, String path) {
			Map<String, Placeholder<?, ?, ?>> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			Default model = new Default(root, path, Collections.unmodifiableMap(things));
			if (root == null)
				root = model;
			for (Map.Entry<String, Placeholder<?, ?, ?>> thing : theThings.entrySet()) {
				if (thing.getValue().type == ModelType.Model)
					things.put(thing.getKey(), new Placeholder<Object, Object, ObservableModelSet>(thing.getValue().thePath, ModelType.Model,
						null, null, null, ((Builder) thing.getValue().model)._build(root, //
							(path.isEmpty() ? "" : path + ".") + thing.getKey())));
				else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}
}
