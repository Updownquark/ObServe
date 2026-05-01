package org.observe.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ConfiguredValueType;
import org.observe.config.ObservableValueSet;
import org.observe.config.SyncValueSet;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.data.types.EntityField;
import org.qommons.data.types.EntityType;
import org.qommons.data.types.EnumType;
import org.qommons.data.types.EnumValue;
import org.qommons.data.types.FieldType;
import org.qommons.data.values.GenericEntity;
import org.qommons.fn.FunctionUtils;
import org.qommons.fn.TriFunction;

import com.google.common.reflect.TypeToken;

public class ReflectedEntityValueType<E> implements ConfiguredValueType<E> {
	private final List<ReflectedEntityValueType<? super E>> theSupers;
	private final EntityType theGenericType;
	private final EntityReflector<E> theReflector;
	private final QuickMap<String, ReflectedFieldType<E, ?, ?>> theFields;

	public ReflectedEntityValueType(List<ReflectedEntityValueType<? super E>> supers, EntityType type, EntityReflector<E> reflector) {
		theSupers = supers;
		theGenericType = type;
		theReflector = reflector;
		QuickMap<String, ReflectedFieldType<E, ?, ?>> fields = reflector.getFields().keySet().createMap();
		int f = 0;
		for (EntityField<?> field : type.getFields()) {
			fields.put(f, new ReflectedFieldType<>(this, field));
			f++;
		}
		theFields = fields.unmodifiable();
	}

	public EntityType getGenericType() {
		return theGenericType;
	}

	public EntityReflector<E> getReflector() {
		return theReflector;
	}

	public boolean isAssignableFrom(ReflectedEntityValueType<?> other) {
		return theReflector.getType().isSupertypeOf(other.theReflector.getType());
	}

	@Override
	public TypeToken<E> getType() {
		return theReflector.getType();
	}

	@Override
	public List<? extends ReflectedEntityValueType<? super E>> getSupers() {
		return theSupers;
	}

	@Override
	public QuickMap<String, ? extends ReflectedFieldType<E, ?, ?>> getFields() {
		return theFields;
	}

	@Override
	public <F> ReflectedFieldType<E, ?, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException {
		EntityReflector.ReflectedField<E, F> field = theReflector.getField(fieldGetter);
		return (ReflectedFieldType<E, ?, F>) theFields.get(field.getFieldIndex());
	}

	@Override
	public boolean allowsCustomFields() {
		return false;
	}

	interface RealFieldValueProducer<G, R> {
		static final RealFieldValueProducer<?, ?> IDENTITY = new RealFieldValueProducer<Object, Object>() {
			@Override
			public Object genericToReal(Object genericValue, ReflectedEntitySet entitySet, GenericEntity fieldOwner) {
				return genericValue;
			}

			@Override
			public Function<Object, Object> asFunction() {
				return FunctionUtils.identity();
			}

			@Override
			public String toString() {
				return "identity";
			}
		};

		static <G, R> RealFieldValueProducer<G, R> identity() {
			return (RealFieldValueProducer<G, R>) IDENTITY;
		}

		static <G, R> RealFieldValueProducer<G, R> of(Function<G, R> function) {
			return new OfFunction<>(function);
		}

		static <G, R> RealFieldValueProducer<G, R> full(Full<G, R> full) {
			return full;
		}

		R genericToReal(G genericValue, ReflectedEntitySet entitySet, GenericEntity fieldOwner);

		Function<G, R> asFunction();

		static interface Full<G, R> extends RealFieldValueProducer<G, R> {
			@Override
			default Function<G, R> asFunction() {
				throw new IllegalStateException("This real field value producer requires the entity set and field owner arguments");
			}
		}

		static class OfFunction<G, R> implements RealFieldValueProducer<G, R> {
			private final Function<G, R> theFunction;

			OfFunction(Function<G, R> function) {
				theFunction = function;
			}

			@Override
			public R genericToReal(G genericValue, ReflectedEntitySet entitySet, GenericEntity fieldOwner) {
				return theFunction.apply(genericValue);
			}

			@Override
			public Function<G, R> asFunction() {
				return theFunction;
			}

			@Override
			public String toString() {
				return theFunction.toString();
			}
		}
	}

	private static TriFunction<?, ReflectedEntitySet, GenericEntity, ?> G2R_IDENTITY = FunctionUtils.printableTriFn((v, __, ___) -> v,
		"identity", null);

	<G, R> RealFieldValueProducer<G, R> mapGenericToReal(FieldType<G> genericType, TypeToken<R> realType, EntityField<G> field) {
		if (genericType instanceof FieldType.SimpleType || genericType == FieldType.BLOB) {
			if (realType.isPrimitive()) {
				R defaultValue = TypeTokens.get().getDefaultValue(realType);
				return RealFieldValueProducer.of(genericValue -> genericValue == null ? defaultValue : (R) genericValue);
			} else
				return RealFieldValueProducer.identity();
		} else if (genericType instanceof EntityType) {
			return RealFieldValueProducer
				.of(genericValue -> genericValue == null ? null : ((MappedEntity<? extends R>) genericValue).getRealEntity());
		} else if (genericType instanceof EnumType) {
			return mapToRealEnum(realType);
		} else if (genericType instanceof FieldType.CollectionType) {
			FieldType.CollectionType<?, ?> collType = (FieldType.CollectionType<?, ?>) genericType;
			TypeToken<?> elementType;
			if (ObservableValueSet.class.isAssignableFrom(TypeTokens.getRawType(realType)))
				elementType = realType.resolveType(ObservableValueSet.class.getTypeParameters()[0]);
			else
				elementType = realType.resolveType(Collection.class.getTypeParameters()[0]);
			Function<Object, Object> valueMap = mapGenericToReal((FieldType<Object>) collType.componentType,
				(TypeToken<Object>) elementType, null).asFunction();
			if (valueMap == FunctionUtils.identity())
				return RealFieldValueProducer.identity();
			Function<Object, Object> valueReverse = mapRealToGeneric((TypeToken<Object>) elementType,
				(FieldType<Object>) collType.componentType);
			Class<?> realRawType = TypeTokens.getRawType(realType);
			boolean valueSet = field != null && realRawType.isAssignableFrom(SyncValueSet.class)
				&& collType.componentType instanceof EntityType;
			RealFieldValueProducer<G, ObservableCollection<?>> genericToCollection;
			if (valueSet) {
				if (collType.isDistinct && collType.isSorted) {
					genericToCollection = RealFieldValueProducer.of(genericValue -> ((ObservableSortedSet<Object>) genericValue).flow()//
						.mapEquivalent(valueMap, valueReverse)//
						.collectPassive());
				} else {
					genericToCollection = RealFieldValueProducer.full((genericValue, entitySet, fieldOwner) -> {
						return ((ObservableCollection<GenericEntity>) genericValue).flow()//
							.distinctSorted((EntityType) collType.componentType, false)//
							.<Object> mapEquivalent((Function<GenericEntity, Object>) (Function<?, ?>) valueMap,
								(Function<Object, GenericEntity>) (Function<?, ?>) valueReverse)//
							.collectActive(((MappedEntity<?>) fieldOwner).getUntil());
					});
				}
			} else if (collType.isDistinct) {
				genericToCollection = RealFieldValueProducer.of(genericValue -> ((ObservableSet<Object>) genericValue).flow()//
					.mapEquivalent(valueMap, valueReverse)//
					.collectPassive());
			} else if (collType.isSorted) {
				genericToCollection = RealFieldValueProducer.of(genericValue -> ((ObservableSortedCollection<Object>) genericValue).flow()//
					.mapEquivalent(valueMap, valueReverse)//
					.collectPassive());
			} else {
				genericToCollection = RealFieldValueProducer.of(genericValue -> ((ObservableSet<Object>) genericValue).flow()//
					.transform(tx -> tx.cache(false)//
						.map(valueMap).withReverse(valueReverse))//
					.collectPassive());
			}
			if (ObservableCollection.class.isAssignableFrom(realRawType))
				return (RealFieldValueProducer<G, R>) genericToCollection;
			else if (valueSet) {
				if (field.getMapping() != null) {
					return RealFieldValueProducer.full((genericValue, entitySet, fieldOwner) -> {
						ObservableCollection<?> realCollection = genericToCollection.genericToReal(genericValue, entitySet, fieldOwner);
						return (R) entitySet.createMemberValueSet((EntityType) collType.componentType,
							(ObservableSortedSet<?>) realCollection, field.getMapping().mappedReferenceField, fieldOwner);
					});
				} else {
					return RealFieldValueProducer.full((genericValue, entitySet, fieldOwner) -> {
						ObservableCollection<?> realCollection = genericToCollection.genericToReal(genericValue, entitySet, fieldOwner);
						return (R) entitySet.createMemberValueSet((EntityType) collType.componentType,
							(ObservableSortedSet<?>) realCollection, null, null);
					});
				}
			} else
				throw new IllegalStateException("Unrecognized real entity type mapped to " + genericType + ": " + realType);
		} else if (genericType instanceof FieldType.MapType) {
			FieldType.MapType<Object, Object, ?> mapType = (FieldType.MapType<Object, Object, ?>) genericType;
			TypeToken<?> keyType = realType.resolveType(Map.class.getTypeParameters()[0]);
			Function<Object, Object> keyMap = mapGenericToReal(mapType.keyType, (TypeToken<Object>) keyType, null).asFunction();
			TypeToken<?> valueType = realType.resolveType(Map.class.getTypeParameters()[1]);
			Function<Object, Object> valueMap = mapGenericToReal(mapType.valueType, (TypeToken<Object>) valueType, null).asFunction();
			if (keyMap == FunctionUtils.identity() && valueMap == FunctionUtils.identity())
				return RealFieldValueProducer.identity();
			Function<Object, Object> keyReverse = mapRealToGeneric((TypeToken<Object>) keyType, mapType.keyType);
			Function<Object, Object> valueReverse = mapRealToGeneric((TypeToken<Object>) valueType, mapType.valueType);
			if (mapType.isSorted) {
				return RealFieldValueProducer
					.of(genericValue -> (R) new ObservableSortedMap.MappedSortedMap<>((ObservableSortedMap<Object, Object>) genericValue, //
						keyMap, keyReverse, valueMap, valueReverse));
			} else {
				return RealFieldValueProducer
					.of(genericValue -> (R) new ObservableMap.MappedMap<>((ObservableMap<Object, Object>) genericValue, //
						keyMap, keyReverse, valueMap, valueReverse));
			}
		} else if (genericType instanceof FieldType.MultiMapType) {
			FieldType.MultiMapType<Object, Object, ?> mapType = (FieldType.MultiMapType<Object, Object, ?>) genericType;
			TypeToken<?> keyType = realType.resolveType(Map.class.getTypeParameters()[0]);
			Function<Object, Object> keyMap = mapGenericToReal(mapType.keyType, (TypeToken<Object>) keyType, null).asFunction();
			TypeToken<?> valueType = realType.resolveType(Map.class.getTypeParameters()[1]);
			Function<Object, Object> valueMap = mapGenericToReal(mapType.valueType, (TypeToken<Object>) valueType, null).asFunction();
			if (keyMap == FunctionUtils.identity() && valueMap == FunctionUtils.identity())
				return RealFieldValueProducer.identity();
			Function<Object, Object> keyReverse = mapRealToGeneric((TypeToken<Object>) keyType, mapType.keyType);
			Function<Object, Object> valueReverse = mapRealToGeneric((TypeToken<Object>) valueType, mapType.valueType);
			Function<ObservableCollection.CollectionDataFlow<?, ?, Object>, ObservableCollection.CollectionDataFlow<?, ?, Object>> valueFlowMap;
			valueFlowMap = valueFlow -> {
				if (valueFlow instanceof ObservableCollection.DistinctDataFlow)
					return ((ObservableCollection.DistinctDataFlow<Object, Object, Object>) valueFlow).mapEquivalent(valueMap,
						valueReverse);
				else if (valueFlow instanceof ObservableCollection.SortedDataFlow)
					return ((ObservableCollection.SortedDataFlow<Object, Object, Object>) valueFlow).mapEquivalent(valueMap, valueReverse);
				else
					return valueFlow.transform(tx -> tx.cache(false)//
						.map(valueMap).withReverse(valueReverse));
			};
			if (mapType.isSorted) {
				return RealFieldValueProducer.of(genericValue -> (R) ((ObservableSortedMultiMap<Object, Object>) genericValue).flow()//
					.withStillSortedKeys(keyFlow -> keyFlow.mapEquivalent(keyMap, keyReverse))//
					.withValues(valueFlowMap)//
					.gatherPassive());
			} else {
				return RealFieldValueProducer.of(genericValue -> (R) ((ObservableMultiMap<Object, Object>) genericValue).flow()//
					.withKeys(keyFlow -> keyFlow.mapEquivalent(keyMap, keyReverse))//
					.withValues(valueFlowMap)//
					.gatherPassive());
			}
		} else
			throw new IllegalStateException("Unrecognized field type: " + genericType);
	}

	private static <G, R, E extends Enum<E>> RealFieldValueProducer<G, R> mapToRealEnum(TypeToken<?> type) {
		Class<E> enumType = (Class<E>) TypeTokens.getRawType(type);
		return RealFieldValueProducer
			.of(genericValue -> genericValue == null ? null : (R) Enum.valueOf(enumType, ((EnumValue) genericValue).getName()));
	}

	<R, G> Function<R, G> mapRealToGeneric(TypeToken<R> realType, FieldType<G> genericType) {
		if (genericType instanceof FieldType.SimpleType || genericType == FieldType.BLOB) {
			return (Function<R, G>) FunctionUtils.identity();
		} else if (genericType instanceof EntityType) {
			return realValue -> (G) EntityReflector.getAssociated(realValue, MappedEntity.ENTITY_ASSOC);
		} else if (genericType instanceof EnumType) {
			return realValue -> realValue == null ? null : (G) ((EnumType) genericType).getValue(((Enum<?>) realValue).name());
		} else
			return null;
	}

	@Override
	public String toString() {
		return theGenericType + "(" + theReflector.getType() + ")";
	}
}
