package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;

public class TypeConversionException extends Exception {
	private final ModelInstanceType<?, ?> theSourceType;
	private final ModelInstanceType<?, ?> theTargetType;

	public TypeConversionException(String expression, ModelInstanceType<?, ?> sourceType, ModelInstanceType<?, ?> targetType) {
		super("Cannot convert '" + expression + "' " + toConvertString(sourceType, targetType));
		theSourceType = sourceType;
		theTargetType = targetType;
	}

	private static String toConvertString(ModelInstanceType<?, ?> sourceType, ModelInstanceType<?, ?> targetType) {
		if (sourceType.getModelType() == ModelTypes.Value && targetType.getModelType() == ModelTypes.Value)
			return "(" + sourceType.getType(0) + ") to " + targetType.getType(0);
		else
			return "(" + sourceType + ") to " + targetType;
	}

	public ModelInstanceType<?, ?> getSourceType() {
		return theSourceType;
	}

	public ModelInstanceType<?, ?> getTargetType() {
		return theTargetType;
	}
}
