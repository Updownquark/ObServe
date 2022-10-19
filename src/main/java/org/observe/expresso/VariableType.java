package org.observe.expresso;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Supports type simple type parsing, a well as parsing by a model value's type. E.g. model.map{1} might parse the value type for the map at
 * model.map.
 */
public interface VariableType {
	/**
	 * @param models The models to use to get the type
	 * @return This type, evaluated for the given models
	 * @throws QonfigInterpretationException If this type could not be evaluated with the given models
	 */
	TypeToken<?> getType(ObservableModelSet models) throws QonfigInterpretationException;

	/**
	 * Parses a {@link VariableType}
	 *
	 * @param text The text to parse the type from
	 * @param cv The class view to get the type from
	 * @return The new type
	 * @throws ParseException If the type could not be parsed
	 */
	public static VariableType parseType(String text, ClassView cv) throws ParseException {
		text = text.replace(" ", "");
		int[] start = new int[1];
		VariableType parsed = Parsing.parseType(text, start, cv);
		if (start[0] != text.length())
			throw new ParseException("Bad type: " + text, start[0]);
		return parsed;
	}

	/** Parsing logic for the {@link VariableType} class */
	class Parsing {
		static VariableType parseType(String text, int[] start, ClassView cv) throws ParseException {
			int c = start[0];
			for (; c < text.length(); c++) {
				switch (text.charAt(c)) {
				case '>':
					// '{' and '}' aren't used for java types, and '<' in particular can't be used in XML as-is,
					// so this flexibility makes it easier to specify generic types in XML
				case '}':
				case ',':
					start[0] = c + 1;
					break;
				case '<':
				case '{':
					if (c == text.length() - 1)
						throw new ParseException("Bad type (more expected): " + text, c);
					if (text.charAt(c) >= '0' && text.charAt(c) <= '9') {
						String modelName = text.substring(start[0], c);
						start[0] = c + 1;
						int typeIndex = parseInt(text, start);
						if (start[0] == text.length() || (text.charAt(start[0]) != '>' && text.charAt(start[0]) != '}'))
							throw new ParseException("'>' or '}' expected", start[0]);
						start[0]++;
						return new ModelType(modelName, typeIndex);
					}
					Class<?> baseType = cv.getType(text.substring(start[0], c));
					if (baseType == null)
						throw new ParseException("Unrecognized type '" + text.substring(start[0], c), start[0]);
					List<VariableType> params = new ArrayList<>();
					paramLoop: while (true) {
						start[0] = c + 1;
						params.add(parseType(text, start, cv));
						if (start[0] == text.length())
							throw new ParseException("'>' or '}' expected", start[0]);
						c = start[0];
						switch (text.charAt(c)) {
						case ',':
							continue;
						case '<':
						case '}':
							start[0]++;
							break paramLoop;
						default:
							throw new ParseException("'>' or '}' expected", c);
						}
					}
					return new Parameterized(baseType, Collections.unmodifiableList(params));
					// TODO [ ]
				default:
				}
			}

			VariableType type = new Simple<>(TypeTokens.get().parseType(text.substring(start[0])));
			start[0] = text.length();
			return type;
		}

		private static int parseInt(String text, int[] start) {
			int i = 0;
			while (text.charAt(start[0]) >= '0' && text.charAt(start[0]) <= '9') {
				i = i * 10 + text.charAt(start[0]) - '0';
				start[0]++;
			}
			return i;
		}
	}

	/**
	 * A simple type that does not depend on a model
	 *
	 * @param <T> The type
	 */
	class Simple<T> implements VariableType {
		private final TypeToken<T> theType;

		/** @param type The type */
		public Simple(TypeToken<T> type) {
			theType=type;
		}

		@Override
		public TypeToken<T> getType(ObservableModelSet models) {
			return theType;
		}

		@Override
		public int hashCode() {
			return theType.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof Simple && theType.equals(((Simple<?>) obj).theType);
		}

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	/** A type that depends on a model */
	class ModelType implements VariableType {
		private final String thePath;
		private final int theTypeIndex;

		/**
		 * @param path The path of the model value to get the type of
		 * @param typeIndex The type parameter index of the type to get
		 */
		public ModelType(String path, int typeIndex) {
			thePath = path;
			theTypeIndex = typeIndex;
		}

		/** @return The path of the model value to get the type from */
		public String getPath() {
			return thePath;
		}

		/** @return The type parameter index of the type to get */
		public int getTypeIndex() {
			return theTypeIndex;
		}

		@Override
		public TypeToken<?> getType(ObservableModelSet models) throws QonfigInterpretationException {
			ModelInstanceType<?, ?> type = models.get(thePath, true).getType();
			return type.getType(theTypeIndex);
		}

		@Override
		public int hashCode() {
			return Objects.hash(thePath, theTypeIndex);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ModelType))
				return false;
			ModelType other = (ModelType) obj;
			return thePath.equals(other.thePath) && theTypeIndex == other.theTypeIndex;
		}

		@Override
		public String toString() {
			return thePath + "<" + theTypeIndex + ">";
		}
	}

	/** A parameterized type whose type parameters are {@link VariableType}s */
	class Parameterized implements VariableType {
		private final Class<?> theBaseType;
		private final List<VariableType> theParameterTypes;

		/**
		 * @param baseType The raw type to parameterize
		 * @param parameterTypes The parameter types
		 */
		public Parameterized(Class<?> baseType, List<VariableType> parameterTypes) {
			theBaseType = baseType;
			theParameterTypes = parameterTypes;
		}

		/** @return The raw type that is here parameterized */
		public Class<?> getBaseType() {
			return theBaseType;
		}

		/** @return The parameter types */
		public List<VariableType> getParameterTypes() {
			return theParameterTypes;
		}

		@Override
		public TypeToken<?> getType(ObservableModelSet models) throws QonfigInterpretationException {
			TypeToken<?>[] params = new TypeToken[theParameterTypes.size()];
			for (int i = 0; i < theParameterTypes.size(); i++)
				params[i] = theParameterTypes.get(i).getType(models);
			return TypeTokens.get().keyFor(theBaseType).parameterized(params);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theBaseType, theParameterTypes);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Parameterized))
				return false;
			Parameterized other = (Parameterized) obj;
			return theBaseType.equals(other.theBaseType) && theParameterTypes.equals(other.theParameterTypes);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theBaseType.toString());
			str.append('<');
			for (int i = 0; i < theParameterTypes.size(); i++) {
				if (i > 0)
					str.append(", ");
				str.append(theParameterTypes.get(i));
			}
			str.append('>');
			return str.toString();
		}
	}
}
