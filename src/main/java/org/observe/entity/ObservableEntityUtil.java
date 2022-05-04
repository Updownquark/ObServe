package org.observe.entity;

/** Utilities used by the org.observe.entity package */
public class ObservableEntityUtil {
	/**
	 * @param superType An entity type
	 * @param subType Another entity type
	 * @return Whether <code>superType</code> is the same as or a super type of <code>subType</code>
	 */
	public static boolean isAssignableFrom(ObservableEntityType<?> superType, ObservableEntityType<?> subType) {
		if (superType == subType)
			return true;
		for (ObservableEntityType<?> subSuper : subType.getSupers()) {
			if (isAssignableFrom(superType, subSuper))
				return true;
		}
		return false;
	}

	/**
	 * @param superField A field type
	 * @param subField Another field type
	 * @return Whether <code>subField</code> is an override of <code>superField</code>
	 */
	public static boolean isOverride(ObservableEntityFieldType<?, ?> superField, ObservableEntityFieldType<?, ?> subField) {
		if (superField == subField)
			return true;
		for (ObservableEntityFieldType<?, ?> subSuper : subField.getOverrides()) {
			if (isOverride(superField, subSuper))
				return true;
		}
		return false;
	}
}
