package org.observe.expresso;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/**
 * Supports type simple type parsing, a well as parsing by a model value's type. E.g. model.map{1} might parse the value type for the map at
 * model.map.
 */
public interface VariableType {
	/**
	 * @param env The interpreted environment to use to interpret this type
	 * @return This type, evaluated for the given models
	 * @throws ExpressoInterpretationException If this type could not be evaluated with the given models
	 */
	TypeToken<?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

	/** @return The content that was used to parse this type */
	LocatedPositionedContent getContent();

	/** @return Whether this type depends on the models passed to {@link #getType(InterpretedExpressoEnv)} */
	boolean isModelDependent();

	/**
	 * Parses a {@link VariableType}
	 *
	 * @param content The content to parse the type from
	 * @return The new type
	 * @throws QonfigInterpretationException If the type could not be parsed
	 */
	public static VariableType parseType(LocatedPositionedContent content) throws QonfigInterpretationException {
		int[] start = new int[1];
		VariableType parsed = Parsing.parseType(content, start);
		if (start[0] != content.length())
			throw new QonfigInterpretationException("Bad type: " + content, content == null ? null : content.getPosition(start[0]), 0);
		return parsed;
	}

	/** Parsing logic for the {@link VariableType} class */
	class Parsing {
		static VariableType parseType(LocatedPositionedContent content, int[] start) throws QonfigInterpretationException {
			int c = start[0];
			for (; c < content.length(); c++) {
				switch (content.charAt(c)) {
				case '>':
					// '{' and '}' aren't used for java types, and '<' in particular can't be used in XML as-is,
					// so this flexibility makes it easier to specify generic types in XML
				case '}':
				case ',':
					Simple param = new Simple(content.subSequence(start[0], c));
					start[0] = c;
					return param;
				case '<':
				case '{':
					if (c == content.length() - 1)
						throw new QonfigInterpretationException("Bad type (more expected): " + content, //
							content.getPosition(start[0]), c - start[0]);
					int paramsOffset = c;
					if (content.charAt(c) >= '0' && content.charAt(c) <= '9') {
						String modelName = content.toString().substring(start[0], c);
						int typeIndex = parseInt(content, start);
						if (start[0] == content.length() || (content.charAt(start[0]) != '>' && content.charAt(start[0]) != '}'))
							throw new QonfigInterpretationException("'>' or '}' expected", //
								content.getPosition(start[0]), c - start[0]);
						start[0]++;
						return new ModelType(content.subSequence(c), modelName, typeIndex, paramsOffset);
					}
					String baseTypeName = content.toString().substring(start[0], c);
					List<VariableType> params = new ArrayList<>();
					paramLoop: while (true) {
						start[0] = c + 1;
						params.add(parseType(content, start));
						if (start[0] == content.length())
							throw new QonfigInterpretationException("'>' or '}' expected", //
								content == null ? null : content.getPosition(start[0]), c - start[0]);
						c = start[0];
						switch (content.charAt(c)) {
						case ',':
							continue;
						case '>':
						case '}':
							start[0]++;
							break paramLoop;
						default:
							throw new QonfigInterpretationException("'>' or '}' expected", content.getPosition(c), 0);
						}
					}
					return new Parameterized(content, baseTypeName, Collections.unmodifiableList(params), paramsOffset);
					// TODO [ ]
				default:
				}
			}

			VariableType type = new Simple(content.subSequence(start[0]));
			start[0] = content.length();
			return type;
		}

		private static int parseInt(CharSequence text, int[] start) {
			int i = 0;
			while (text.charAt(start[0]) >= '0' && text.charAt(start[0]) <= '9') {
				i = i * 10 + text.charAt(start[0]) - '0';
				start[0]++;
			}
			return i;
		}
	}

	/** A simple type that does not depend on a model */
	class Simple implements VariableType {
		private final LocatedPositionedContent theContent;
		private final String theTypeName;

		/** @param content The name of the type */
		public Simple(LocatedPositionedContent content) {
			theContent = content;
			theTypeName = content.toString().replaceAll("\\s+", "");
		}

		@Override
		public LocatedPositionedContent getContent() {
			return theContent;
		}

		@Override
		public TypeToken<?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			Class<?> clazz;
			try {
				clazz = TypeTokens.getRawType(env.getClassView().parseType(theTypeName));
			} catch (ParseException e) {
				throw new ExpressoInterpretationException(e.getMessage(), //
					theContent.getPosition(e.getErrorOffset()), theContent.length() - e.getErrorOffset());
			}
			return TypeTokens.get().of(clazz);
		}

		@Override
		public boolean isModelDependent() {
			return false;
		}

		@Override
		public int hashCode() {
			return theContent.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof Simple && theTypeName.equals(((Simple) obj).theTypeName);
		}

		@Override
		public String toString() {
			return theTypeName;
		}
	}

	/** A type that depends on a model */
	class ModelType implements VariableType {
		private final LocatedPositionedContent theContent;
		private final String theModelPath;
		private final int theTypeIndex;
		private final int theIndexOffset;

		/**
		 * @param modelPath The path of the model value to get the type of
		 * @param typeIndex The type parameter index of the type to get
		 * @param content The position in the file where this type was specified
		 * @param indexOffset The offset of the model type index from the beginning of the content
		 */
		public ModelType(LocatedPositionedContent content, String modelPath, int typeIndex, int indexOffset) {
			theContent = content;
			theModelPath = modelPath;
			theTypeIndex = typeIndex;
			theIndexOffset = indexOffset;
		}

		@Override
		public LocatedPositionedContent getContent() {
			return theContent;
		}

		/** @return The path of the model value to get the type from */
		public String getModelPath() {
			return theModelPath;
		}

		/** @return The type parameter index of the type to get */
		public int getTypeIndex() {
			return theTypeIndex;
		}

		@Override
		public TypeToken<?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ModelInstanceType<?, ?> type;
			try {
				type = env.getModels().getComponent(theModelPath).interpreted().getType();
			} catch (ModelException e) {
				throw new ExpressoInterpretationException(e.getMessage(), theContent.getPosition(0), theContent.length(), e);
			}
			if (theTypeIndex >= type.getModelType().getTypeCount())
				throw new ExpressoInterpretationException("Model value '" + theModelPath + "' is of type " + type + ", with "
					+ type.getModelType().getTypeCount() + " types. {" + theTypeIndex + "} is invalid",
					theContent.getPosition(theIndexOffset), 0);
			return type.getType(theTypeIndex);
		}

		@Override
		public boolean isModelDependent() {
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theModelPath, theTypeIndex);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ModelType))
				return false;
			ModelType other = (ModelType) obj;
			return theModelPath.equals(other.theModelPath) && theTypeIndex == other.theTypeIndex;
		}

		@Override
		public String toString() {
			return theModelPath + "<" + theTypeIndex + ">";
		}
	}

	/** A parameterized type whose type parameters are {@link VariableType}s */
	class Parameterized implements VariableType {
		private final LocatedPositionedContent theContent;
		private final String theBaseTypeName;
		private final List<VariableType> theParameterTypes;
		private final int theParametersOffset;

		/**
		 * @param content The positioned content from which the type was parsed
		 * @param baseTypeName The name of the raw type to parameterize
		 * @param parameterTypes The parameter types
		 * @param paramsOffset The integer offset of the start of the parameters from the beginning of the content
		 */
		public Parameterized(LocatedPositionedContent content, String baseTypeName, List<VariableType> parameterTypes, int paramsOffset) {
			theContent = content;
			theBaseTypeName = baseTypeName;
			theParameterTypes = parameterTypes;
			theParametersOffset = paramsOffset;
		}

		@Override
		public LocatedPositionedContent getContent() {
			return theContent;
		}

		/** @return The raw type that is here parameterized */
		public String getBaseTypeName() {
			return theBaseTypeName;
		}

		/** @return The parameter types */
		public List<VariableType> getParameterTypes() {
			return theParameterTypes;
		}

		@Override
		public TypeToken<?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			Class<?> baseClass = env.getClassView().getType(theBaseTypeName);
			if (baseClass == null)
				throw new ExpressoInterpretationException("Unrecognized type '" + theBaseTypeName, //
					theContent.getPosition(0), theBaseTypeName.length());
			else if (baseClass.getTypeParameters().length != theParameterTypes.size())
				throw new ExpressoInterpretationException(
					theBaseTypeName + " has " + baseClass.getTypeParameters().length + " parameter"
						+ (baseClass.getTypeParameters().length == 1 ? "" : "s") + ", not " + theParameterTypes.size()
						+ ". Cannot be parameterized with <" + StringUtils.print(", ", theParameterTypes, Object::toString) + ">", //
						theContent.getPosition(theParametersOffset), 0);
			TypeToken<?>[] params = new TypeToken[theParameterTypes.size()];
			for (int i = 0; i < theParameterTypes.size(); i++)
				params[i] = theParameterTypes.get(i).getType(env);
			return TypeTokens.get().keyFor(baseClass).parameterized(params);
		}

		@Override
		public boolean isModelDependent() {
			for (VariableType param : theParameterTypes) {
				if (param.isModelDependent())
					return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theBaseTypeName, theParameterTypes);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Parameterized))
				return false;
			Parameterized other = (Parameterized) obj;
			return theBaseTypeName.equals(other.theBaseTypeName) && theParameterTypes.equals(other.theParameterTypes);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theBaseTypeName);
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
