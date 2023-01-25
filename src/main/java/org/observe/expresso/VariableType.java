package org.observe.expresso;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigFilePosition;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.SimpleXMLParser.ContentPosition;

import com.google.common.reflect.TypeToken;

/**
 * Supports type simple type parsing, a well as parsing by a model value's type. E.g. model.map{1} might parse the value type for the map at
 * model.map.
 */
public interface VariableType {
	/**
	 * @param models The models to use to get the type
	 * @return This type, evaluated for the given models
	 * @throws QonfigEvaluationException If this type could not be evaluated with the given models
	 */
	TypeToken<?> getType(ObservableModelSet models) throws QonfigEvaluationException;

	/**
	 * Parses a {@link VariableType}
	 *
	 * @param text The text to parse the type from
	 * @param cv The class view to get the type from
	 * @return The new type
	 * @throws QonfigInterpretationException If the type could not be parsed
	 */
	public static VariableType parseType(String text, ClassView cv, String file, ContentPosition position)
		throws QonfigInterpretationException {
		text = text.replace(" ", "");
		int[] start = new int[1];
		VariableType parsed = Parsing.parseType(text, start, cv, file, position);
		if (start[0] != text.length())
			throw new QonfigInterpretationException("Bad type: " + text, //
				position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), 0);
		return parsed;
	}

	/** Parsing logic for the {@link VariableType} class */
	class Parsing {
		static VariableType parseType(String text, int[] start, ClassView cv, String file, ContentPosition position)
			throws QonfigInterpretationException {
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
						throw new QonfigInterpretationException("Bad type (more expected): " + text, //
							position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), c - start[0]);
					if (text.charAt(c) >= '0' && text.charAt(c) <= '9') {
						String modelName = text.substring(start[0], c);
						start[0] = c + 1;
						int typeIndex = parseInt(text, start);
						if (start[0] == text.length() || (text.charAt(start[0]) != '>' && text.charAt(start[0]) != '}'))
							throw new QonfigInterpretationException("'>' or '}' expected", //
								position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), c - start[0]);
						start[0]++;
						return new ModelType(modelName, typeIndex,
							position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), c - start[0]);
					}
					Class<?> baseType = cv.getType(text.substring(start[0], c));
					if (baseType == null)
						throw new QonfigInterpretationException("Unrecognized type '" + text.substring(start[0], c), //
							position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), c - start[0]);
					List<VariableType> params = new ArrayList<>();
					paramLoop: while (true) {
						start[0] = c + 1;
						params.add(parseType(text, start, cv, file, position == null ? null : position));
						if (start[0] == text.length())
							throw new QonfigInterpretationException("'>' or '}' expected", //
								position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0])), c - start[0]);
						c = start[0];
						switch (text.charAt(c)) {
						case ',':
							continue;
						case '<':
						case '}':
							start[0]++;
							break paramLoop;
						default:
							throw new QonfigInterpretationException("'>' or '}' expected", //
								position == null ? null : new QonfigFilePosition(file, position.getPosition(c)), 0);
						}
					}
					return new Parameterized(baseType, Collections.unmodifiableList(params));
					// TODO [ ]
				default:
				}
			}

			VariableType type;
			try {
				type = new Simple<>(TypeTokens.get().parseType(text.substring(start[0])));
			} catch (ParseException e) {
				throw new QonfigInterpretationException(e.getMessage(), e, //
					position == null ? null : new QonfigFilePosition(file, position.getPosition(start[0] + e.getErrorOffset())), 0);
			}
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
			theType = type;
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
		private final QonfigFilePosition thePosition;
		private final int theLength;

		/**
		 * @param path The path of the model value to get the type of
		 * @param typeIndex The type parameter index of the type to get
		 */
		public ModelType(String path, int typeIndex, QonfigFilePosition position, int length) {
			thePath = path;
			theTypeIndex = typeIndex;
			thePosition = position;
			theLength = length;
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
		public TypeToken<?> getType(ObservableModelSet models) throws QonfigEvaluationException {
			ModelInstanceType<?, ?> type;
			try {
				type = models.getComponent(thePath).getType();
			} catch (ModelException e) {
				throw new QonfigEvaluationException(e.getMessage(), e, thePosition, theLength);
			}
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
		public TypeToken<?> getType(ObservableModelSet models) throws QonfigEvaluationException {
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
