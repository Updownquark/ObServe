package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickBorderLayout extends QuickLayout.Abstract {
	public static class Def extends QuickLayout.Def<QuickBorderLayout> {
		public Def(QonfigAddOn type, QuickBox.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickBox.Def<?> getElement() {
			return (QuickBox.Def<?>) super.getElement();
		}

		@Override
		public Def update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			return this;
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<? extends QuickBox> element) {
			return new Interpreted(this, (QuickBox.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends QuickLayout.Interpreted<QuickBorderLayout> {
		public Interpreted(Def definition, QuickBox.Interpreted<? extends QuickBox> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickBox.Interpreted<?> getElement() {
			return (QuickBox.Interpreted<?>) super.getElement();
		}

		@Override
		public Interpreted update(InterpretedModelSet models) throws ExpressoInterpretationException {
			return this;
		}

		@Override
		public QuickBorderLayout create(QuickBox element) {
			return new QuickBorderLayout(this, element);
		}
	}

	public QuickBorderLayout(Interpreted interpreted, QuickBox element) {
		super(interpreted, element);
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
	}

	@Override
	public QuickBorderLayout update(ModelSetInstance models) throws ModelInstantiationException {
		return this;
	}

	public enum Region {
		Center, North, South, East, West
	}

	public static class Child extends QuickAddOn.Abstract<QuickWidget> {
		public static class Def extends QuickAddOn.Def.Abstract<QuickWidget, Child> {
			private Region theRegion;

			public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
				super(type, element);
			}

			public Region getRegion() {
				return theRegion;
			}

			@Override
			public Def update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				String regionStr = session.getAttributeText("region");
				switch (regionStr) {
				case "center":
					theRegion = Region.Center;
					break;
				case "north":
					theRegion = Region.North;
					break;
				case "south":
					theRegion = Region.South;
					break;
				case "east":
					theRegion = Region.East;
					break;
				case "west":
					theRegion = Region.West;
					break;
				default:
					throw new QonfigInterpretationException("Unrecognized region attribute: '" + regionStr,
						session.getAttributeValuePosition("region", 0), regionStr.length());
				}
				return this;
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<? extends QuickWidget> element) {
				return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
			}
		}

		public static class Interpreted extends QuickAddOn.Interpreted.Abstract<QuickWidget, Child> {
			public Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public Interpreted update(InterpretedModelSet models) throws ExpressoInterpretationException {
				return this;
			}

			@Override
			public Child create(QuickWidget element) {
				return new Child(this, element);
			}
		}

		public Child(Interpreted interpreted, QuickWidget element) {
			super(interpreted, element);
		}

		@Override
		public Interpreted getInterpreted() {
			return (Interpreted) super.getInterpreted();
		}

		@Override
		public Child update(ModelSetInstance models) throws ModelInstantiationException {
			return this;
		}
	}
}
