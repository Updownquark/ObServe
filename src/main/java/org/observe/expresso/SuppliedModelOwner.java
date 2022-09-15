package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElementOrAddOn} */
public class SuppliedModelOwner {
	public static final String WITH_ELEMENT_MODELS = "with-element-model";
	private static final IdentityHashMap<QonfigElementOrAddOn, SuppliedModelOwner> ELEMENT_SUPPLIED_VALUES = new IdentityHashMap<>();

	public static synchronized SuppliedModelOwner of(QonfigElementOrAddOn element, AbstractQIS<?> session, QonfigToolkit expressoCore)
		throws QonfigInterpretationException {
		SuppliedModelOwner owner = ELEMENT_SUPPLIED_VALUES.get(element);
		if (owner != null)
			return owner;
		else if (session == null || expressoCore == null)
			return null;
		QonfigAddOn withElementModels = expressoCore.getAddOn(WITH_ELEMENT_MODELS);
		if (!withElementModels.isAssignableFrom(element))
			return null;

		List<SuppliedModelOwner> parents = new ArrayList<>();
		QonfigElementOrAddOn superEl = element.getSuperElement();
		if (superEl != null) {
			SuppliedModelOwner parent = of(superEl, session, expressoCore);
			if (parent != null)
				parents.add(parent);
		}
		for (QonfigAddOn inh : element.getInheritance()) {
			SuppliedModelOwner parent = of(inh, session, expressoCore);
			if (parent != null)
				parents.add(parent);
		}
		if (parents.isEmpty())
			parents = Collections.emptyList();
		else
			parents = Collections.unmodifiableList(parents);
		Map<String, SuppliedModelValue<?, ?>> declaredModelValues = new LinkedHashMap<>();
		Map<String, SuppliedModelValue<?, ?>> modelValues = new LinkedHashMap<>();
		owner = new SuppliedModelOwner(element, parents, Collections.unmodifiableMap(declaredModelValues),
			Collections.unmodifiableMap(modelValues));

		QonfigElement modelEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(withElementModels.getMetaSpec().getChild("element-model").getDeclared()).peekFirst();
		QonfigToolkit toolkit = withElementModels.getDeclarer();
		if (modelEl != null) {
			QonfigAttributeDef.Declared nameAttr = toolkit.getAddOn("named").getAttribute("name").getDeclared();
			for (QonfigElement modelV : modelEl.getChildrenInRole(expressoCore, WITH_ELEMENT_MODELS, "value")) {
				String name = modelV.getAttributeText(nameAttr);
				if (declaredModelValues.containsKey(name)) {
					session.withError("Multiple model values named '" + name + "' declared");
					continue;
				}
				ExpressoQIS exModelV = session.intepretRoot(modelV).as(ExpressoQIS.class);
				ModelInstanceType<?, ?> type = exModelV.interpret(Expresso.ExtModelValue.class).getType(exModelV);
				declaredModelValues.put(name, new SuppliedModelValue<>(owner, modelV, name, type));
			}
		}

		modelValues.putAll(declaredModelValues);

		for (SuppliedModelOwner parent : parents) {
			for (SuppliedModelValue<?, ?> mv : parent.getModelValues().values()) {
				if (modelValues.containsKey(mv.getName()))
					session.withWarning("Model value " + mv + " is name-eclipsed by " + modelValues.get(mv.getName()));
				else
					modelValues.putIfAbsent(mv.getName(), mv);
			}
		}
		ELEMENT_SUPPLIED_VALUES.put(element, owner);
		return owner;
	}

	private final QonfigElementOrAddOn theElement;
	private final List<SuppliedModelOwner> theSuperElements;
	private final Map<String, SuppliedModelValue<?, ?>> theDeclaredModelValues;
	private final Map<String, SuppliedModelValue<?, ?>> theModelValues;

	SuppliedModelOwner(QonfigElementOrAddOn element, List<SuppliedModelOwner> superElements, //
		Map<String, SuppliedModelValue<?, ?>> declaredModelValues, Map<String, SuppliedModelValue<?, ?>> modelValues) {
		theElement = element;
		theSuperElements = superElements;
		theDeclaredModelValues = declaredModelValues;
		theModelValues = modelValues;
	}

	public QonfigElementOrAddOn getElement() {
		return theElement;
	}

	public List<SuppliedModelOwner> getSuperElements() {
		return theSuperElements;
	}

	public Map<String, SuppliedModelValue<?, ?>> getDeclaredModelValues() {
		return theDeclaredModelValues;
	}

	public Map<String, SuppliedModelValue<?, ?>> getModelValues() {
		return theModelValues;
	}

	public SuppliedModelValue<?, ?> getModelValue(String name) {
		int dot = name.indexOf('.');
		if (dot < 0) {
			SuppliedModelValue<?, ?> mv = theDeclaredModelValues.get(name);
			if (mv != null)
				return mv;
			mv = theModelValues.get(name);
			if (mv == null)
				throw new IllegalArgumentException("No such style model value: " + theElement + "." + name);
			return mv;
		} else {
			String elName = name.substring(0, dot);
			QonfigElementOrAddOn el = theElement.getDeclarer().getElementOrAddOn(elName);
			if (el == null)
				throw new IllegalArgumentException("No such element or add-on '" + elName + "'");
			SuppliedModelOwner owner;
			try {
				owner = of(el, null, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
			if (owner == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return owner.getModelValue(name.substring(dot + 1));
		}
	}

	public <M, MV extends M> SuppliedModelValue<M, MV> getModelValue(String name, ModelInstanceType<M, MV> type) {
		SuppliedModelValue<?, ?> mv = getModelValue(name);
		if (!mv.getType().equals(type))
			throw new IllegalArgumentException("Model value" + theElement + "." + name + " is a " + mv.getType() + ", not a " + type);
		return (SuppliedModelValue<M, MV>) mv;
	}

	public <M, MV extends M> ValueContainer<M, MV> getModelValueAs(String name, ModelInstanceType<M, MV> type) {
		SuppliedModelValue<Object, Object> mv = (SuppliedModelValue<Object, Object>) getModelValue(name);
		ModelInstanceConverter<Object, M> converter = mv.getType().convert(type);
		if (converter == null)
			throw new IllegalArgumentException("Model value" + theElement + "." + name + " is a " + mv.getType() + ", not a " + type);
		return new ModelType.ConvertedValue<>(mv, type, converter);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getModelValueAs(String name, TypeToken<T> type) {
		return getModelValue(name, ModelTypes.Value.forType(type));
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getModelValueAs(String name, Class<T> type) {
		return getModelValueAs(name, TypeTokens.get().of(type));
	}

	@Override
	public String toString() {
		return theElement.toString();
	}
}
