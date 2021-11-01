package org.observe.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.qommons.QommonsUtils;

public class ClassView {
	private final List<ClassLoader> theClassLoaders;
	private final Set<String> theImportedTypes;
	private final Set<String> theWildcardImports;

	private ClassView(List<ClassLoader> classLoaders, Set<String> importedTypes, Set<String> wildcardImports) {
		theClassLoaders = classLoaders;
		theImportedTypes = importedTypes;
		theWildcardImports = wildcardImports;
	}

	public Field getImportedStaticField(String fieldName) {
		// TODO
		return null;
	}

	public Class<?> getType(String typeName) {
		// TODO
		return null;
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final List<ClassLoader> theClassLoaders;
		private final Set<String> theImportedTypes;
		private final Set<String> theWildcardImports;

		Builder() {
			theClassLoaders = new ArrayList<>(3);
			theImportedTypes = new LinkedHashSet<>();
			theWildcardImports = new LinkedHashSet<>();
		}

		public Builder withClassLoader(ClassLoader cl) {
			theClassLoaders.add(cl);
			return this;
		}

		public Builder withImport(String importedType) {
			theImportedTypes.add(importedType);
			return this;
		}

		public Builder withWildcardImport(String wildcardImport) {
			theWildcardImports.add(wildcardImport);
			return this;
		}

		public ClassView build() {
			return new ClassView(//
				theClassLoaders.isEmpty() ? Collections.unmodifiableList(Arrays.asList(Thread.currentThread().getContextClassLoader()))
					: QommonsUtils.unmodifiableCopy(theClassLoaders), //
					QommonsUtils.unmodifiableDistinctCopy(theImportedTypes), //
					QommonsUtils.unmodifiableDistinctCopy(theWildcardImports));
		}
	}
}
