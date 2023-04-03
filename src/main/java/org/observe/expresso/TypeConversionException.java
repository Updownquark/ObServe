package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;

/**
 * Thrown from {@link ObservableModelSet.ValueContainer#as(ModelInstanceType)} if the value could not be converted to a value of the
 * specified type
 */
public class TypeConversionException extends Exception {
	private final ModelInstanceType<?, ?> theSourceType;
	private final ModelInstanceType<?, ?> theTargetType;

	/**
	 * @param expression The expression whose conversion failed
	 * @param sourceType The type of the expression
	 * @param targetType The type to which conversion was attempted
	 */
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

	/** @return The type of the expression for which conversion was attempted */
	public ModelInstanceType<?, ?> getSourceType() {
		return theSourceType;
	}

	/** @return The type to which conversion was attempted */
	public ModelInstanceType<?, ?> getTargetType() {
		return theTargetType;
	}
}
