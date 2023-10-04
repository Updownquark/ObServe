package org.observe.quick.base;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
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
		public Interpreted interpret(ExElement.Interpreted<? extends QuickBox> element) {
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
		public Class<QuickBorderLayout> getInstanceType() {
			return QuickBorderLayout.class;
		}

		@Override
		public QuickBorderLayout create(QuickBox element) {
			return new QuickBorderLayout(element);
		}
	}

	public QuickBorderLayout(QuickBox element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	public enum Region {
		Center, North, South, East, West
	}

	public static class Child extends ExAddOn.Abstract<QuickWidget> {
		public static final String BORDER_LAYOUT_CHILD = "border-layout-child";

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = BORDER_LAYOUT_CHILD,
			interpretation = Interpreted.class,
			instance = Child.class)
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Child> {
			private Region theRegion;

			public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
				super(type, element);
			}

			@QonfigAttributeGetter("region")
			public Region getRegion() {
				return theRegion;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
				super.update(session, element);
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
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends QuickWidget> element) {
				return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
			}
		}

		public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, Child> {
			public Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public Class<Child> getInstanceType() {
				return Child.class;
			}

			@Override
			public Child create(QuickWidget element) {
				return new Child(element);
			}
		}

		private Region theRegion;

		public Child(QuickWidget element) {
			super(element);
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		public Region getRegion() {
			return theRegion;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted) {
			super.update(interpreted);
			Child.Interpreted myInterpreted = (Child.Interpreted) interpreted;
			theRegion = myInterpreted.getDefinition().getRegion();
		}
	}
}
