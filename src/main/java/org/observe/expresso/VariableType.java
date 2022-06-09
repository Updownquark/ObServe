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

public interface VariableType {
	TypeToken<?> getType(ObservableModelSet models) throws QonfigInterpretationException;

	public static VariableType parseType(String text, ClassView cv) throws ParseException {
		text = text.replace(" ", "");
		int[] start = new int[1];
		VariableType parsed = Parsing.parseType(text, start, cv);
		if (start[0] != text.length())
			throw new ParseException("Bad type: " + text, start[0]);
		return parsed;
	}

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
					return new Composed(baseType, Collections.unmodifiableList(params));
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

	class Simple<T> implements VariableType {
		private final TypeToken<T> theType;

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

	class ModelType implements VariableType {
		private final String thePath;
		private final int theTypeIndex;

		public ModelType(String path, int typeIndex) {
			thePath = path;
			theTypeIndex = typeIndex;
		}

		public String getPath() {
			return thePath;
		}

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

	class Composed implements VariableType {
		private final Class<?> theBaseType;
		private final List<VariableType> theParameterTypes;

		public Composed(Class<?> baseType, List<VariableType> parameterTypes) {
			theBaseType = baseType;
			theParameterTypes = parameterTypes;
		}

		public Class<?> getBaseType() {
			return theBaseType;
		}

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
			else if (!(obj instanceof Composed))
				return false;
			Composed other = (Composed) obj;
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
