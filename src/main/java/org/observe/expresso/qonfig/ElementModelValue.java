package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.JavaExpressoParser;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.VariableType;
import org.qommons.Named;
import org.qommons.SelfDescribed;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigValueDef;
import org.qommons.config.QonfigValueType;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

/**
 * A model value declared on an element definition (see the with-element-model add-on in the Expresso-Base toolkit)
 *
 * @param <M> The model type of the value
 */
public interface ElementModelValue<M> extends ObservableModelSet.IdentifiableCompiledValue<M>, Named {
	/** The definition of a dynamic model value */
	public static class Identity implements Named, SelfDescribed {
		private final QonfigElementOrAddOn theOwner;
		private final String theName;
		private final QonfigChildDef theSourceChild;
		private final QonfigAttributeDef theNameAttribute;
		private final VariableType theType;
		private final QonfigAttributeDef theSourceAttribute;
		private final boolean isSourceValue;
		private final CompiledExpression theValue;
		private final QonfigElement theDeclaration;

		/**
		 * @param owner The type declaring the value
		 * @param name The declared name of the value (may be null if <code>nameAttribute</code> is defined)
		 * @param sourceChild The name of the child on this value's type that the {@link #getNameAttribute()} (and source, if the value is
		 *        from an attribute or value) comes from
		 * @param nameAttribute The attribute that determines the name of this value (null if <code>name</code> is defined)
		 * @param type The string representation of the type for this value. Null if the type is variable.
		 * @param sourceAttribute If defined, the this value will be that evaluated from this expression-typed attribute
		 * @param sourceValue If true, this value will be that evaluated from the expression-typed {@link QonfigElement#getValue()} of the
		 *        element
		 * @param declaration The element that declares this value
		 */
		public Identity(QonfigElementOrAddOn owner, String name, QonfigChildDef sourceChild, QonfigAttributeDef nameAttribute,
			VariableType type, QonfigAttributeDef sourceAttribute, boolean sourceValue, CompiledExpression value,
			QonfigElement declaration) {
			theOwner = owner;
			theName = name;
			theSourceChild = sourceChild;
			theNameAttribute = nameAttribute;
			theType = type;
			theSourceAttribute = sourceAttribute;
			isSourceValue = sourceValue;
			theValue = value;
			theDeclaration = declaration;
		}

		/** @return The type declaring this value */
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		/** @return The declared name of the value (may be null if <code>nameAttribute</code> is defined) */
		@Override
		public String getName() {
			if (theName != null)
				return theName;
			else
				return "{" + theNameAttribute + "}";
		}

		/** @return The attribute that determines the name of this value (null if <code>name</code> is defined) */
		public QonfigAttributeDef getNameAttribute() {
			return theNameAttribute;
		}

		/** @return The type for this value. Null if the type is variable. */
		public VariableType getType() {
			return theType;
		}

		/**
		 * @return The name of the child on this value's type that the {@link #getNameAttribute()} (and source, if the value is from an
		 *         attribute or value) comes from
		 */
		public QonfigChildDef getSourceChild() {
			return theSourceChild;
		}

		/** @return If defined, the this value will be that evaluated from this expression-typed attribute */
		public QonfigAttributeDef getSourceAttribute() {
			return theSourceAttribute;
		}

		/** @return If true, this value will be that evaluated from the expression-typed {@link QonfigElement#getValue()} of the element */
		public boolean isSourceValue() {
			return isSourceValue;
		}

		public CompiledExpression getValue() {
			return theValue;
		}

		/** @return The metadata that is the declaration for this dynamic value */
		public QonfigElement getDeclaration() {
			return theDeclaration;
		}

		@Override
		public String getDescription() {
			StringBuilder str = new StringBuilder(theOwner.toString()).append('.');
			if (theName != null)
				str.append(theName);
			else
				str.append('{').append(theNameAttribute.getName()).append('}');
			String descrip = theDeclaration.getDescription();
			if (descrip != null) {
				if (descrip.startsWith("<html>"))
					str.insert(0, "<html>").append(":<br />").append(descrip);
				else
					str.append(": ").append(descrip);
			}
			return str.toString();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theOwner.toString()).append('.');
			if (theName != null)
				str.append(theName);
			else
				str.append('{').append(theNameAttribute.getName()).append('}');
			if (theType != null)
				str.append('(').append(theType).append(')');
			if (theSourceAttribute != null)
				str.append("<=").append(theSourceAttribute);
			else if (isSourceValue)
				str.append("<=<value>");
			return str.toString();
		}
	}

	/** @return The declared definition of this dynamic value */
	public Identity getDeclaration();

	@Override
	default String getName() {
		return getDeclaration().getName();
	}

	/** A cache of element model values for a document */
	public class Cache {
		private static class ElementModelData {
			Set<QonfigElementOrAddOn> types = new HashSet<>();
			QonfigElementOrAddOn withElementModel;
			QonfigChildDef elementModel;
			QonfigChildDef modelValue;
			QonfigAttributeDef nameAttr;
			QonfigAttributeDef nameAttrAttr;
			QonfigValueType identifierType;
			QonfigAttributeDef typeAttr;
			QonfigAttributeDef sourceAttr;
		}

		private ConcurrentHashMap<QonfigElementOrAddOn, Map<String, Identity>> theDynamicValues = new ConcurrentHashMap<>();

		/**
		 * @param expresso The toolkit to get expresso types from
		 * @param type The element type to get the dynamic values for
		 * @param values The map to add the dynamic values into
		 * @return The identities/definitions of all dynamic values defined on the given type, grouped by name/name attribute
		 */
		public Map<String, Identity> getDynamicValues(QonfigToolkit expresso, QonfigElementOrAddOn type, Map<String, Identity> values,
			ErrorReporting reporting) throws QonfigInterpretationException {
			return getDynamicValues(expresso, null, type, values, reporting);
		}

		private Map<String, Identity> getDynamicValues(QonfigToolkit expresso, ElementModelData modelData, QonfigElementOrAddOn type,
			Map<String, Identity> values, ErrorReporting reporting) throws QonfigInterpretationException {
			if (modelData == null)
				modelData = new ElementModelData();
			if (modelData.withElementModel == null)
				modelData.withElementModel = expresso.getElementOrAddOn("with-element-model");
			if (!modelData.types.add(type) || !modelData.withElementModel.isAssignableFrom(type))
				return values == null ? Collections.emptyMap() : values;
			Map<String, Identity> found = theDynamicValues.get(type);
			if (found == null) {
				synchronized (Cache.class) {
					found = theDynamicValues.get(type);
					if (found == null) {
						found = compileDynamicValues(expresso, modelData, type, reporting);
						theDynamicValues.put(type, found);
					}
				}
			}
			if (values == null)
				values = new LinkedHashMap<>();
			values.putAll(found);
			return values;
		}

		private Map<String, Identity> compileDynamicValues(QonfigToolkit expresso, ElementModelData modelData, QonfigElementOrAddOn type,
			ErrorReporting reporting) throws QonfigInterpretationException {
			Map<String, Identity> values = new LinkedHashMap<>();
			if (type.getSuperElement() != null)
				getDynamicValues(expresso, modelData, type.getSuperElement(), values, reporting);
			for (QonfigAddOn inh : type.getInheritance())
				getDynamicValues(expresso, modelData, inh, values, reporting);
			if (modelData.elementModel == null)
				modelData.elementModel = expresso.getMetaChild("with-element-model", "element-model");
			QonfigElement metadata = type.getMetadata().getRoot().getChildrenByRole().get(modelData.elementModel.getDeclared()).peekFirst();
			if (metadata != null) {
				if (modelData.modelValue == null) {
					modelData.modelValue = expresso.getChild("element-model", "value");
					modelData.nameAttr = expresso.getAttribute("named", "name");
					modelData.nameAttrAttr = expresso.getAttribute("element-model-value", "name-attribute");
					modelData.identifierType = expresso.getAttributeType("identifier");
					modelData.typeAttr = expresso.getAttribute("typed", "type");
					modelData.sourceAttr = expresso.getAttribute("element-model-value", "source-attribute");
				}
				ExpressoParser parser = new JavaExpressoParser();
				for (QonfigElement elValue : metadata.getChildrenByRole().get(modelData.modelValue.getDeclared())) {
					String name = elValue.getAttributeText(modelData.nameAttr);
					String nameAttrS = elValue.getAttributeText(modelData.nameAttrAttr);
					CompiledExpression value;
					if (elValue.getValue() != null) {
						try {
							LocatedPositionedContent content = new LocatedPositionedContent.Default(elValue.getValue().fileLocation,
								elValue.getValue().position);
							value = new CompiledExpression(parser.parse(((QonfigExpression) elValue.getValue().value).text), elValue,
								content, null);
						} catch (ExpressoParseException e) {
							reporting.at(elValue.getFilePosition()).error("Could not parse value expression", e);
							continue;
						}
					} else
						value = null;
					QonfigChildDef sourceChild;
					QonfigAttributeDef nameAttr;
					if (nameAttrS != null) {
						if (!"$".equals(name))
							throw new IllegalArgumentException("Cannot specify both name and name-attribute on an internal model value");
						name = null;
						int slash = nameAttrS.lastIndexOf('/');
						try {
							if (slash < 0) { // Attribute is on the element itself
								sourceChild = null;
								nameAttr = getAttribute(type, nameAttrS);
							} else {
								sourceChild = getChild(type, nameAttrS.substring(0, slash));
								nameAttr = getAttribute(sourceChild, nameAttrS.substring(slash + 1));
							}
						} catch (IllegalArgumentException e) {
							reporting
							.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(),
								elValue.getAttributes().get(modelData.nameAttrAttr).position.subSequence(1, nameAttrS.length() - 1)))
							.error(e.getMessage(), e);
							continue;
						}
						if (nameAttr.getType() != modelData.identifierType) {
							reporting
							.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(),
								elValue.getAttributes().get(modelData.nameAttrAttr).position.subSequence(1, nameAttrS.length() - 1)))
							.error("name-attribute (" + nameAttr + ") must refer to an attribute of type 'identifier', not "
								+ nameAttr.getType());
						}
						// Just don't define the value if not specified
						// else if (nameAttr.getSpecification() != SpecificationType.Required && nameAttr.getDefaultValue() == null)
						// throw new IllegalArgumentException(
						// "name-attribute " + nameAttr + " must either be required or specify a default");

					} else {
						sourceChild = null;
						nameAttr = null;
					}
					QonfigValue typeName = elValue.getAttributes().get(modelData.typeAttr);
					VariableType valueType = typeName == null ? null
						: VariableType.parseType(new LocatedPositionedContent.Default(typeName.fileLocation, typeName.position));
					String sourceAttrS = elValue.getAttributeText(modelData.sourceAttr);
					QonfigAttributeDef sourceAttr;
					boolean sourceValue;
					if (value != null) {
						if (sourceAttrS != null)
							reporting.at(elValue.getFilePosition()).error("Both a source-attribute and a value given");
						sourceValue = false;
						sourceAttr = null;
					} else if (sourceAttrS == null) {
						sourceValue = false;
						sourceAttr = null;
					} else if (sourceAttrS.isEmpty()) {
						QonfigValueDef valueDef = sourceChild != null ? sourceChild.getType().getValue() : type.getValue();
						if (valueDef == null) {
							reporting
							.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(),
								elValue.getAttributes().get(modelData.nameAttrAttr).position.subSequence(1, nameAttrS.length() - 1)))
							.error("Empty source-attribute specified, but no value defined on "
								+ (sourceChild != null ? sourceChild : type));
							continue;
						} else if (!(valueDef.getType() instanceof QonfigValueType.Custom)
							|| !(((QonfigValueType.Custom) valueDef.getType()).getCustomType() instanceof ExpressionValueType)) {
							reporting
							.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(),
								elValue.getAttributes().get(modelData.nameAttrAttr).position.subSequence(1, nameAttrS.length() - 1)))
							.error("Empty source-attribute must refer to an element value of type 'expression', not "
								+ valueDef.getType());
							continue;
						}

						sourceValue = true;
						sourceAttr = null;
					} else {
						sourceValue = false;
						sourceAttr = sourceChild != null ? getAttribute(sourceChild, sourceAttrS) : getAttribute(type, sourceAttrS);
						if (!(sourceAttr.getType() instanceof QonfigValueType.Custom)
							|| !(((QonfigValueType.Custom) sourceAttr.getType()).getCustomType() instanceof ExpressionValueType)) {
							reporting
							.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(),
								elValue.getAttributes().get(modelData.nameAttrAttr).position.subSequence(1, nameAttrS.length() - 1)))
							.error("source-attribute (" + sourceAttr + ") must refer to an attribute of type 'expression', not "
								+ sourceAttr.getType());
							continue;
						}
					}
					Identity newModelValue = new Identity(type, name, sourceChild, nameAttr, valueType, sourceAttr, sourceValue, value,
						elValue);
					Identity overridden = values.get(newModelValue.getName());
					if (overridden != null) {
						reporting.at(LocatedPositionedContent.of(metadata.getDocument().getLocation(), elValue.getFilePosition()))
						.error("Type " + type + " declares a dynamic value '" + newModelValue.getName()
						+ "' that clashes with the value of the same name declared by " + overridden.getOwner());
						continue;
					}
					values.put(newModelValue.getName(), newModelValue);
				}
			}
			return Collections.unmodifiableMap(values);
		}

		private QonfigChildDef getChild(QonfigElementOrAddOn type, String childName) {
			QonfigChildDef child;
			int dot = childName.indexOf('.');
			if (dot >= 0) {
				child = type.getDeclarer().getChild(childName.substring(0, dot), childName.substring(dot + 1));
				if (!type.isAssignableFrom(child.getOwner()))
					throw new IllegalArgumentException(type + " is not a " + child.getOwner() + " and has no child " + child);
			} else {
				child = type.getChild(childName);
				if (child == null)
					throw new IllegalArgumentException("No such child " + type + "." + childName);
			}
			return child;
		}

		private QonfigAttributeDef getAttribute(QonfigElementOrAddOn type, String attrName) {
			QonfigAttributeDef attr = getAttribute(type.getDeclarer(), attrName);
			if (attr != null)
				return attr;
			attr = type.getAttribute(attrName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + type + "." + attrName);
			return attr;
		}

		private QonfigAttributeDef getAttribute(QonfigChildDef child, String attrName) {
			QonfigAttributeDef attr = getAttribute(child.getDeclarer(), attrName);
			if (attr != null)
				return attr;
			attr = child.getType().getAttribute(attrName);
			if (attr == null) {
				for (QonfigAddOn inh : child.getInheritance()) {
					attr = inh.getAttribute(attrName);
					if (attr != null)
						break;
				}
			}
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + child + "." + attrName);
			return attr;
		}

		private QonfigAttributeDef getAttribute(QonfigToolkit toolkit, String attrName) {
			int dot = attrName.indexOf('.');
			if (dot >= 0)
				return toolkit.getAttribute(attrName.substring(0, dot), attrName.substring(dot + 1));
			else
				return null;
		}
	}
}