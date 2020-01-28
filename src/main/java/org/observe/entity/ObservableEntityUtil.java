package org.observe.entity;

public class ObservableEntityUtil {
	public static boolean isAssignableFrom(ObservableEntityType<?> superType, ObservableEntityType<?> subType) {
		if (superType == subType)
			return true;
		for (ObservableEntityType<?> subSuper : subType.getSupers()) {
			if (isAssignableFrom(superType, subSuper))
				return true;
		}
		return false;
	}

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
