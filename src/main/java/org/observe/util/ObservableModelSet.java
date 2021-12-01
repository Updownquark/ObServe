package org.observe.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.util.ModelType.ModelInstanceType;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;

public interface ObservableModelSet {
	interface ValueContainer<M, MV extends M> extends Function<ModelSetInstance, MV> {
		ModelInstanceType<M, MV> getType();

		MV get(ModelSetInstance models);

		@Override
		default MV apply(ModelSetInstance models) {
			return get(models);
		}
	}

	static <M, MV extends M> ValueContainer<M, MV> container(Function<ModelSetInstance, MV> value, ModelInstanceType<M, MV> type) {
		class SyntheticValueContainer implements ValueContainer<M, MV> {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return type;
			}

			private Function<ModelSetInstance, MV> getValue() {
				return value;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return value.apply(extModels);
			}

			@Override
			public int hashCode() {
				return value.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SyntheticValueContainer))
					return false;
				return value.equals(((SyntheticValueContainer) obj).getValue());
			}

			@Override
			public String toString() {
				return value.toString();
			}
		}
		return new SyntheticValueContainer();
	}

	ObservableModelSet getParent();

	String getPath();

	String pathTo(String name);

	ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException;

	default <M> ValueContainer<M, ?> get(String path, ModelType<M> type) throws QonfigInterpretationException {
		ValueContainer<?, ?> thing = get(path, true);
		if (thing.getType().getModelType() != type)
			throw new QonfigInterpretationException(path + " is a " + thing.getType() + ", not a " + type);
		return (ValueContainer<M, ?>) thing;
	}

	default <M, MV extends M> ValueContainer<M, MV> get(String path, ModelInstanceType<M, MV> type)
		throws QonfigInterpretationException {
		ValueContainer<Object, Object> thing = (ValueContainer<Object, Object>) get(path, true);
		if (type == null)
			return (ValueContainer<M, MV>) thing;
		return thing.getType().as(thing, type);
	}

	ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until);

	public static Builder build() {
		return new Default.DefaultBuilder(null, null, "");
	}

	public static ExternalModelSetBuilder buildExternal() {
		return new ExternalModelSetBuilder(null, "");
	}

	public abstract class ModelSetInstance {
		public abstract Object getModelConfiguration();

		public abstract Observable<?> getUntil();

		protected abstract Object getThing(Default.Placeholder<?, ?> placeholder);
	}

	public static WrappedBuilder wrap(ObservableModelSet wrapped) {
		return new DefaultWrapped.DWBuilder(wrapped);
	}

	public class ExternalModelSet {
		final ExternalModelSet theRoot;
		private final String thePath;
		final Map<String, Placeholder> theThings;

		ExternalModelSet(ExternalModelSet root, String path, Map<String, Placeholder> things) {
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = things;
		}

		public String getPath() {
			return thePath;
		}

		public <M> M get(String path, ModelInstanceType<?, ?> type) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				ExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.get(path.substring(dot + 1), type);
			}
			Placeholder thing = theThings.get(path);
			if (thing == null)
				throw new QonfigInterpretationException("No such " + type + " declared: '" + pathTo(path) + "'");
			ModelType.ModelInstanceConverter<Object, M> converter = (ModelType.ModelInstanceConverter<Object, M>) thing.type.convert(type);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert " + path + " (" + thing.type + ") to " + type);
			return converter.convert(thing.thing);
		}

		private ExternalModelSet getSubModel(String path) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder subModel = theThings.get(modelName);
			if (subModel == null)
				throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type.getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return (ExternalModelSet) subModel.thing;
			else
				return ((ExternalModelSet) subModel.thing).getSubModel(path.substring(dot + 1));
		}

		String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().thing instanceof ExternalModelSet)
					((ExternalModelSet) thing.getValue().thing).print(str, indent + 1);
			}
		}

		static class Placeholder {
			final ModelInstanceType<?, ?> type;
			final Object thing;

			Placeholder(ModelInstanceType<?, ?> type, Object thing) {
				this.type = type;
				this.thing = thing;
			}
		}
	}

	public interface ValueGetter<T> {
		T get(ModelSetInstance models, ExternalModelSet extModels);
	}

	public class ExternalModelSetBuilder extends ExternalModelSet {
		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path) {
			super(root, path, new LinkedHashMap<>());
		}

		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item)
			throws QonfigInterpretationException {
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, item));
			return this;
		}

		public ExternalModelSetBuilder addSubModel(String name) throws QonfigInterpretationException {
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name));
			theThings.put(name, new ExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			return subModel;
		}

		public ExternalModelSet build() {
			return _build(null, getPath());
		}

		private ExternalModelSet _build(ExternalModelSet root, String path) {
			Map<String, ExternalModelSet.Placeholder> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			ExternalModelSet model = new ExternalModelSet(root, path, Collections.unmodifiableMap(things));
			if (root == null)
				root = model;
			for (Map.Entry<String, ExternalModelSet.Placeholder> thing : theThings.entrySet()) {
				if (thing.getValue().type.getModelType() == ModelTypes.Model)
					things.put(thing.getKey(), new ExternalModelSet.Placeholder(ModelTypes.Model.instance(), //
						((ExternalModelSetBuilder) thing.getValue().thing)._build(root, path + "." + thing.getKey())));
				else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}

	public interface Builder extends ObservableModelSet {
		Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration);

		<M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter);

		Builder createSubModel(String name);

		ObservableModelSet build();
	}

	public class Default implements ObservableModelSet {
		private final Default theParent;
		protected final Default theRoot;
		protected final String thePath;
		protected final Map<String, Placeholder<?, ?>> theThings;
		protected final Function<ModelSetInstance, ?> theModelConfiguration;

		protected Default(Default root, Default parent, String path, Map<String, Placeholder<?, ?>> things,
			Function<ModelSetInstance, ?> modifiableConfiguration) {
			theRoot = root == null ? this : root;
			theParent = parent;
			thePath = path;
			theThings = things;
			theModelConfiguration = modifiableConfiguration;
		}

		@Override
		public Default getParent() {
			return theParent;
		}

		@Override
		public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot), required);
				return subModel == null ? null : subModel.get(path.substring(dot + 1), required);
			}
			ValueContainer<?, ?> thing = theThings.get(path);
			if (thing == null && theRoot != this) {
				Placeholder<?, ?> p = theRoot.theThings.get(path);
				if (p != null && p.getModel() != null)
					return p;
			}
			if (thing == null && required)
				throw new QonfigInterpretationException("No such value " + path);
			return thing;
		}

		private Default getSubModel(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder<?, ?> subModel = theThings.get(modelName);
			if (subModel == null) {
				if (required)
					throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
				else
					return null;
			} else if (subModel.getType().getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.getType() + ", not a Model");
			if (dot < 0)
				return subModel.getModel();
			else
				return subModel.getModel().getSubModel(path.substring(dot + 1), required);
		}

		@Override
		public String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String getPath() {
			return thePath;
		}

		@Override
		public ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until) {
			if (until == null)
				until = Observable.empty();
			ModelSetInstanceImpl modelSet = new ModelSetInstanceImpl(until);
			install(modelSet, extModel);
			return modelSet;
		}

		private void install(ModelSetInstanceImpl modelSet, ExternalModelSet extModel) {
			Object modelConfig = theModelConfiguration == null ? null : theModelConfiguration.apply(modelSet);
			modelSet.setModelConfiguration(modelConfig);
			for (Placeholder<?, ?> thing : theThings.values()) {
				if (thing.getModel() != null) {
					thing.getModel().install(modelSet, extModel);
					modelSet.setModelConfiguration(modelConfig);
				} else
					modelSet.theThings.put(thing, thing.getGetter().get(modelSet, extModel));
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(thePath.isEmpty() ? "<root>" : thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().getType());
				if (thing.getValue().getModel() != null)
					thing.getValue().getModel().print(str, indent + 1);
			}
		}

		interface Placeholder<M, MV extends M> extends ValueContainer<M, MV> {
			String getPath();

			Default getModel();

			ValueGetter<MV> getGetter();
		}

		static class PlaceholderImpl<M, MV extends M> implements Placeholder<M, MV> {
			private final String thePath;
			private final ModelInstanceType<M, MV> theType;
			private final ValueGetter<MV> theGetter;
			private final Default theModel;

			PlaceholderImpl(String path, ModelInstanceType<M, MV> type, ValueGetter<MV> getter, Default model) {
				thePath = path;
				this.theType = type;
				this.theGetter = getter;
				this.theModel = model;
			}

			@Override
			public String getPath() {
				return thePath;
			}

			@Override
			public Default getModel() {
				return theModel;
			}

			@Override
			public ValueGetter<MV> getGetter() {
				return theGetter;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return (MV) extModels.getThing(this);
			}

			@Override
			public String toString() {
				return new StringBuilder(theType.toString()).append('@').append(thePath).toString();
			}
		}

		private static class ModelSetInstanceImpl extends ModelSetInstance {
			final Map<Default.Placeholder<?, ?>, Object> theThings;
			private Object theModelConfiguration;
			private final Observable<?> theUntil;

			ModelSetInstanceImpl(Observable<?> until) {
				theThings = new LinkedHashMap<>();
				theUntil = until;
			}

			@Override
			protected Object getThing(Placeholder<?, ?> placeholder) {
				return theThings.get(placeholder);
			}

			@Override
			public Object getModelConfiguration() {
				return theModelConfiguration;
			}

			void setModelConfiguration(Object modelConfiguration) {
				theModelConfiguration = modelConfiguration;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}
		}

		static class DefaultBuilder extends Default implements Builder {
			private Function<ModelSetInstance, ?> theModelConfiguration;

			private DefaultBuilder(DefaultBuilder root, DefaultBuilder parent, String path) {
				super(root, parent, path, new LinkedHashMap<>(), null);
			}

			@Override
			public Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration) {
				theModelConfiguration = modelConfiguration;
				return this;
			}

			@Override
			public <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter) {
				if (theThings.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theThings.get(name).getType() + " has already been added as '" + name + "'");
				theThings.put(name, new PlaceholderImpl<>(pathTo(name), type, getter, null));
				return this;
			}

			@Override
			public Builder createSubModel(String name) {
				Placeholder<?, ?> thing = theThings.get(name);
				if (thing == null) {
					DefaultBuilder subModel = new DefaultBuilder((DefaultBuilder) theRoot, this, pathTo(name));
					theThings.put(name, new PlaceholderImpl<>(pathTo(name), ModelTypes.Model.instance(), null, subModel));
					return subModel;
				} else if (thing.getModel() != null)
					return (Builder) thing.getModel();
				else
					throw new IllegalArgumentException("A value of type " + thing.getType() + " has already been added as '" + name + "'");
			}

			@Override
			public ObservableModelSet build() {
				return _build(null, null, thePath);
			}

			private Default _build(Default root, Default parent, String path) {
				Map<String, Placeholder<?, ?>> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
				Default model = create(root, parent, path, things, theModelConfiguration);
				if (root == null)
					root = model;
				for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
					if (thing.getValue().getType().getModelType() == ModelTypes.Model)
						things.put(thing.getKey(),
							new PlaceholderImpl<>(thing.getValue().getPath(), ModelTypes.Model.instance(), null,
								((DefaultBuilder) thing.getValue().getModel())._build(root, model, //
									(path.isEmpty() ? "" : path + ".") + thing.getKey())));
					else
						things.put(thing.getKey(), thing.getValue());
				}
				return model;
			}

			protected Default create(Default root, Default parent, String path, Map<String, Placeholder<?, ?>> things,
				Function<ModelSetInstance, ?> modelConfiguration) {
				return new Default(root, parent, path, Collections.unmodifiableMap(things), modelConfiguration);
			}
		}
	}

	public interface Wrapped extends ObservableModelSet {
		WrappedInstanceBuilder wrap(ModelSetInstance msi);
	}

	public interface ModelValuePlaceholder<M, MV extends M> extends ValueContainer<M, MV>, Named {
	}

	public interface WrappedBuilder extends Builder {
		<M, MV extends M> ModelValuePlaceholder<M, MV> withPlaceholder(String name, ModelInstanceType<M, MV> type);

		@Override
		Wrapped build();
	}

	public interface WrappedInstanceBuilder {
		<M, MV extends M> WrappedInstanceBuilder with(ModelValuePlaceholder<M, MV> placeholder, MV value);

		ModelSetInstance build() throws IllegalStateException;
	}

	public class DefaultWrapped extends Default implements Wrapped {
		private final ObservableModelSet theWrapped;
		private final Map<String, ModelValuePlaceholderImpl<?, ?>> thePlaceholders;

		DefaultWrapped(DefaultWrapped root, DefaultWrapped parent, String path, Map<String, Placeholder<?, ?>> things,
			ObservableModelSet wrapped, Function<ModelSetInstance, ?> modelConfiguration,
			Map<String, ModelValuePlaceholderImpl<?, ?>> placeholders) {
			super(root, parent, path, things, modelConfiguration);
			theWrapped = wrapped;
			thePlaceholders = placeholders;
		}

		@Override
		public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
			ValueContainer<?, ?> thing = null;
			if (path.indexOf(',') < 0)
				thing = thePlaceholders.get(path);
			if (thing == null)
				thing = super.get(path, false);
			if (thing == null)
				thing = theWrapped.get(path, required); // Let the wrapped model throw the exception if not found
			return thing;
		}

		@Override
		public ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until) {
			throw new UnsupportedOperationException("Not supported for wrapped model set");
		}

		@Override
		public WrappedInstanceBuilder wrap(ModelSetInstance msi) {
			return new WrappedInstanceBuilderImpl(msi, thePlaceholders.values());
		}

		static class DWBuilder extends Default.DefaultBuilder implements WrappedBuilder {
			private final ObservableModelSet theWrapped;
			private final Map<String, ModelValuePlaceholderImpl<?, ?>> thePlaceholders;

			public DWBuilder(ObservableModelSet wrapped) {
				super(null, null, ""); // Currently, wrapped models are only supported for the root
				theWrapped = wrapped;
				thePlaceholders = new LinkedHashMap<>();
			}

			@Override
			public <M, MV extends M> ModelValuePlaceholder<M, MV> withPlaceholder(String name, ModelInstanceType<M, MV> type) {
				if (name.indexOf(',') >= 0)
					throw new IllegalArgumentException("Bad variable name: " + name);
				if (thePlaceholders.get(name) != null)
					throw new IllegalArgumentException("A placeholder named '" + name + "' is already added");
				ModelValuePlaceholderImpl<M, MV> placeholder = new ModelValuePlaceholderImpl<>(name, type);
				thePlaceholders.put(name, placeholder);
				return placeholder;
			}

			@Override
			public DefaultWrapped build() {
				return (DefaultWrapped) super.build();
			}

			@Override
			protected Default create(Default root, Default parent, String path, Map<String, Default.Placeholder<?, ?>> things,
				Function<ModelSetInstance, ?> modelConfiguration) {
				return new DefaultWrapped((DefaultWrapped) root, (DefaultWrapped) parent, path, things, theWrapped, modelConfiguration,
					QommonsUtils.unmodifiableCopy(thePlaceholders));
			}
		}

		static class ModelValuePlaceholderImpl<M, MV extends M> implements ModelValuePlaceholder<M, MV>, Placeholder<M, MV> {
			private final String theName;
			private final ModelInstanceType<M, MV> theType;

			ModelValuePlaceholderImpl(String name, ModelInstanceType<M, MV> type) {
				theName = name;
				theType = type;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public MV get(ModelSetInstance models) {
				return (MV) models.getThing(this);
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public String getPath() {
				return theName;
			}

			@Override
			public Default getModel() {
				return null;
			}

			@Override
			public ValueGetter<MV> getGetter() {
				throw new IllegalStateException();
			}

			@Override
			public String toString() {
				return theName + " (" + theType + ")";
			}
		}

		static class WrappedInstanceBuilderImpl implements WrappedInstanceBuilder {
			private static Object UNFULFILLED = new Object();

			private final ModelSetInstance theWrapped;
			private final Map<ModelValuePlaceholderImpl<?, ?>, Object> theValues;

			WrappedInstanceBuilderImpl(ModelSetInstance wrapped, Collection<ModelValuePlaceholderImpl<?, ?>> placeholders) {
				theWrapped = wrapped;
				theValues = new LinkedHashMap<>();
				for (ModelValuePlaceholderImpl<?, ?> placeholder : placeholders)
					theValues.put(placeholder, UNFULFILLED);
			}

			@Override
			public <M, MV extends M> WrappedInstanceBuilder with(ModelValuePlaceholder<M, MV> placeholder, MV value) {
				Object old = theValues.get(placeholder);
				if (old == null)
					throw new IllegalArgumentException("Unrecognized placeholder: " + placeholder);
				if (!placeholder.getType().getModelType().modelType.isInstance(value))
					throw new IllegalArgumentException(
						"Bad model value type " + value.getClass().getName() + " for placeholder " + placeholder);
				theValues.put((ModelValuePlaceholderImpl<?, ?>) placeholder, value);
				return this;
			}

			@Override
			public ModelSetInstance build() throws IllegalStateException {
				for (Map.Entry<ModelValuePlaceholderImpl<?, ?>, Object> value : theValues.entrySet()) {
					if (value.getValue() == UNFULFILLED)
						throw new IllegalStateException("Placeholder " + value.getKey() + " is unfulfilled");
				}
				return new WrappedMSI(theWrapped, QommonsUtils.unmodifiableCopy(theValues));
			}
		}

		static class WrappedMSI extends ModelSetInstance {
			private final ModelSetInstance theWrapped;
			private final Map<ModelValuePlaceholderImpl<?, ?>, Object> thePlaceholderValues;

			WrappedMSI(ModelSetInstance wrapped, Map<ModelValuePlaceholderImpl<?, ?>, Object> placeholderValues) {
				theWrapped = wrapped;
				thePlaceholderValues = placeholderValues;
			}

			@Override
			public Object getModelConfiguration() {
				return theWrapped.getModelConfiguration();
			}

			@Override
			public Observable<?> getUntil() {
				return theWrapped.getUntil();
			}

			@Override
			protected Object getThing(Default.Placeholder<?, ?> placeholder) {
				Object thing = null;
				if (placeholder instanceof ModelValuePlaceholderImpl)
					thing = thePlaceholderValues.get(placeholder);
				if (thing == null)
					thing = theWrapped.getThing(placeholder);
				return thing;
			}
		}
	}
}
