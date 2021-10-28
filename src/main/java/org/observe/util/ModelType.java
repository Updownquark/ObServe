package org.observe.util;

import com.google.common.reflect.TypeToken;

public abstract class ModelType<M> {
	public interface ModelConverter<M1, M2> {
		M2 convert(M1 model, ModelType<M2> targetType);
	}

	public static abstract class ModelInstanceType<M, MV extends M> {
		public abstract ModelType<M> getModelType();

		public abstract TypeToken<?> getType(int typeIndex);

		public static abstract class UnTyped<M> extends ModelInstanceType<M, M> {
			@Override
			public abstract ModelType.UnTyped<M> getModelType();

			@Override
			public TypeToken<?> getType(int typeIndex) {
				throw new IndexOutOfBoundsException(typeIndex + " of 0");
			}
		}

		public static abstract class SingleTyped<M, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<V> theValueType;

			SingleTyped(TypeToken<V> valueType) {
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.SingleTyped<M> getModelType();

			public TypeToken<V> getValueType() {
				return theValueType;
			}

			@Override
			public TypeToken<?> getType(int typeIndex) {
				if (typeIndex == 0)
					return theValueType;
				throw new IndexOutOfBoundsException(typeIndex + " of 1");
			}
		}

		public static abstract class DoubleTyped<M, K, V, MV extends M> extends ModelInstanceType<M, MV> {
			private final TypeToken<K> theKeyType;
			private final TypeToken<V> theValueType;

			DoubleTyped(TypeToken<K> keyType, TypeToken<V> valueType) {
				theKeyType = keyType;
				theValueType = valueType;
			}

			@Override
			public abstract ModelType.DoubleTyped<M> getModelType();

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			public TypeToken<V> getValueType() {
				return theValueType;
			}

			@Override
			public TypeToken<?> getType(int typeIndex) {
				switch (typeIndex) {
				case 0:
					return theKeyType;
				case 1:
					return theValueType;
				default:
					throw new IndexOutOfBoundsException(typeIndex + " of 1");
				}
			}
		}
	}

	// Event(Observable.class),
	// Action(ObservableAction.class),
	// Value(SettableValue.class), //
	// Collection(ObservableCollection.class),
	// SortedCollection(ObservableSortedCollection.class, Collection),
	// Set(ObservableSet.class, Collection),
	// SortedSet(ObservableSortedSet.class, Collection, SortedCollection, Set), //
	// Map(ObservableMap.class),
	// SortedMap(ObservableSortedMap.class, Map), //
	// MultiMap(ObservableMultiMap.class),
	// SortedMultiMap(ObservableSortedMultiMap.class, MultiMap), //
	// Model(ObservableModelSet.class);

	private final String theName;
	public final Class<M> modelType;
	private final int theTypeCount;

	protected ModelType(String name, Class<M> type, int typeCount) {
		theName = name;
		this.modelType = type;
		theTypeCount = typeCount;
	}

	protected void setupConversions(ConversionBuilder<M> builder) {
	}

	// public boolean isExtension(ModelType type) {
	// return this == type || superTypes.contains(type);
	// }
	//
	// public static ModelType of(Class<?> thing) {
	// if (ObservableModelSet.class.isAssignableFrom(thing))
	// return Model;
	// else if (ObservableSortedMultiMap.class.isAssignableFrom(thing))
	// return SortedMultiMap;
	// else if (ObservableMultiMap.class.isAssignableFrom(thing))
	// return MultiMap;
	// else if (ObservableSortedMap.class.isAssignableFrom(thing))
	// return SortedMap;
	// else if (ObservableMap.class.isAssignableFrom(thing))
	// return Map;
	// else if (ObservableSortedSet.class.isAssignableFrom(thing))
	// return SortedSet;
	// else if (ObservableSet.class.isAssignableFrom(thing))
	// return Set;
	// else if (ObservableSortedCollection.class.isAssignableFrom(thing))
	// return SortedCollection;
	// else if (ObservableCollection.class.isAssignableFrom(thing))
	// return Collection;
	// else if (SettableValue.class.isAssignableFrom(thing))
	// return Value;
	// else if (ObservableAction.class.isAssignableFrom(thing))
	// return Action;
	// else if (Observable.class.isAssignableFrom(thing))
	// return Event;
	// else
	// return null;
	// }

	// public static String printType(Object value) {
	// return printType(of(value.getClass()));
	// }

	public static String print(ModelType type) {
		return type == null ? "Unknown" : type.toString();
	}

	public static abstract class UnTyped<M> extends ModelType<M> {
		private final ModelInstanceType.UnTyped<M> theInstance;

		public UnTyped(String name, Class<M> type) {
			super(name, type, 0);
			theInstance = new ModelInstanceType.UnTyped<M>() {
				@Override
				public ModelType.UnTyped<M> getModelType() {
					return ModelType.UnTyped.this;
				}
			};
		}

		public ModelInstanceType.UnTyped<M> instance() {
			return theInstance;
		}
	}

	public static abstract class SingleTyped<M> extends ModelType<M> {
		public SingleTyped(String name, Class<M> type) {
			super(name, type, 1);
		}

		private <V, MV extends M> ModelInstanceType.SingleTyped<M, V, MV> createInstance(TypeToken<V> valueType) {
			return new ModelInstanceType.SingleTyped<M, V, MV>(valueType) {
				@Override
				public ModelType.SingleTyped<M> getModelType() {
					return ModelType.SingleTyped.this;
				}
			};
		}

		public abstract <V1, V2> M convertType(M source, TypeToken<V1> sourceType, TypeToken<V2> targetType);

		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(TypeToken<V> type) {
			return createInstance(type);
		}

		public <V> ModelInstanceType.SingleTyped<M, V, ?> forType(Class<V> type) {
			return forType(TypeTokens.get().of(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(TypeToken<V> type) {
			return forType(TypeTokens.get().getExtendsWildcard(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? extends V, ?> below(Class<V> type) {
			return below(TypeTokens.get().of(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(TypeToken<V> type) {
			return forType(TypeTokens.get().getSuperWildcard(type));
		}

		public <V> ModelInstanceType.SingleTyped<M, ? super V, ?> above(Class<V> type) {
			return above(TypeTokens.get().of(type));
		}
	}

	public static abstract class DoubleTyped<M> extends ModelType<M> {
		public DoubleTyped(String name, Class<M> type) {
			super(name, type, 2);
		}

		private <K, V, MV extends M> ModelInstanceType.DoubleTyped<M, K, V, MV> createInstance(TypeToken<K> keyType,
			TypeToken<V> valueType) {
			return new ModelInstanceType.DoubleTyped<M, K, V, MV>(keyType, valueType) {
				@Override
				public ModelType.DoubleTyped<M> getModelType() {
					return ModelType.DoubleTyped.this;
				}
			};
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(TypeToken<K> keyType, TypeToken<V> valueType) {
			return createInstance(keyType, valueType);
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, K, V, ?> forType(Class<K> keyType, Class<V> valueType) {
			return forType(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getExtendsWildcard(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? extends K, ? extends V, ?> below(Class<K> keyType, Class<V> valueType) {
			return below(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(TypeToken<K> keyType, TypeToken<V> valueType) {
			return forType(TypeTokens.get().getSuperWildcard(keyType), TypeTokens.get().getSuperWildcard(valueType));
		}

		public <K, V> ModelInstanceType.DoubleTyped<M, ? super K, ? super V, ?> above(Class<K> keyType, Class<V> valueType) {
			return above(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
		}
	}

	public interface ConversionBuilder<M> {
		<M2> ConversionBuilder<M> convertibleTo(ModelType<M2> modelType, ModelConverter<M, M2> converter);
	}
}
