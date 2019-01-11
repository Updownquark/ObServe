package org.observe.entity.impl;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityUtils;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldType;
import org.qommons.collect.ParameterSet.ParameterMap;

class ObservableEntityImpl<E> implements ObservableEntity<E> {
	private final ObservableEntityTypeImpl<E> theType;
	private final EntityIdentity<? super E> theId;
	private final E theEntity;
	private final ParameterMap<ObservableEntityField<? super E, ?>> theFields;

	ObservableEntityImpl(ObservableEntityTypeImpl<E> type, EntityIdentity<? super E> id,
		ParameterMap<ObservableEntityField<? super E, ?>> fields) {
		theType = type;
		theId = id;
		theFields = fields;
		theEntity = type.getEntityType() == null ? null
			: (E) Proxy.newProxyInstance(type.getEntityType().getClassLoader(), new Class[] { type.getEntityType() },
				new ObservableEntityInvocationHandler<>(this));
	}

	@Override
	public ObservableEntityTypeImpl<E> getType() {
		return theType;
	}

	@Override
	public EntityIdentity<? super E> getId() {
		return theId;
	}

	@Override
	public <F> ObservableEntityField<? super E, F> getField(ObservableEntityFieldType<? super E, F> fieldType) {
		ObservableEntityField<? super E, ?> field = theFields.get(fieldType.getName());
		if (field == null)
			throw new IllegalArgumentException("Field type " + fieldType + " does not apply to entity of type " + theType);
		return (ObservableEntityField<? super E, F>) field;
	}

	@Override
	public E getEntity() {
		return theEntity;
	}

	@Override
	public int hashCode() {
		return theId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ObservableEntity && theId.equals(((ObservableEntity<?>) obj).getId());
	}

	@Override
	public String toString() {
		return theId.toString();
	}

	private static class ObservableEntityInvocationHandler<E> implements InvocationHandler {
		private final ObservableEntityImpl<E> theObservableEntity;

		ObservableEntityInvocationHandler(ObservableEntityImpl<E> observableEntity) {
			theObservableEntity = observableEntity;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch (method.getName()) {
			case "hashCode":
				if (args.length == 0)
					return theObservableEntity.hashCode();
				break;
			case "equals":
				if (args.length == 1) {
					if (args[0] == null || Proxy.isProxyClass(args[0].getClass()))
						return Boolean.FALSE;
					InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
					if (handler instanceof ObservableEntityInvocationHandler)
						return theObservableEntity.equals(((ObservableEntityInvocationHandler<?>) handler).theObservableEntity);
				}
				break;
			case "toString":
				if(args.length==0)
					return theObservableEntity.toString();
			}
			if(args.length==0){
				String fieldName=EntityUtils.fieldFromGetter(method.getName());
				if(fieldName!=null){
					ObservableEntityField<? super E, ?> field= theObservableEntity.theFields.get(fieldName);
					if(field!=null)
						return field.get();
				}
			} else if(args.length==1){
				String fieldName=EntityUtils.fieldFromSetter(method.getName());
				if(fieldName!=null){
					ObservableEntityField<? super E, ?> field= theObservableEntity.theFields.get(fieldName);
					if(field!=null){
						Object oldValue = ((ObservableEntityField<? super E, Object>) field).set(args[0], null);
						if (method.getReturnType() != void.class)
							return oldValue;
						else
							return null;
					}
				}
			}
			// Hopefully this is a default method implemented by the entity interface itself
			MethodHandle handle = theObservableEntity.getType().getDefaultMethod(method);
			if (handle == null)
				throw new IllegalStateException("No way to invoke " + method);
			return handle.bindTo(this).invoke(args);
		}
	}
}
