package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * <p>
 * A layout that positions components along the edges of the container with an optional single component filling the rest of the space.
 * </p>
 * <p>
 * This layout improves on basic border layouts in several ways:
 * <ul>
 * <li>This layout can accept any number of components along each edge. Edge components are arranged such that they appear closer to the
 * opposite edge of the container than all previous components with the same edge. Edge components stretch out to the edges of the
 * container, or to the edge of previous components along those edges.</li>
 * <li>The amount of space that each component takes up can be specified by layout constraints, and can be specified relative to the edges
 * or size of the container.</li>
 * </ul>
 * </p>
 * <p>
 * Only a single center component may be specified. If specified, the center component will occupy all the space not taken up by the edge
 * components. Size constraints on the center component are not allowed.
 * </p>
 */
public class QuickBorderLayout extends QuickLayout.Abstract {
	/** The XML name of this add-on */
	public static final String BORDER_LAYOUT = "border-layout";

	/** {@link QuickBorderLayout} definition */
	public static class Def extends QuickLayout.Def<QuickBorderLayout> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickBox.Def<?> getElement() {
			return (QuickBox.Def<?>) super.getElement();
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	/** {@link QuickBorderLayout} interpretation */
	public static class Interpreted extends QuickLayout.Interpreted<QuickBorderLayout> {
		/**
		 * @param definition The definition to interpret
		 * @param element The container widget whose contents to manage
		 */
		public Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
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
		public QuickBorderLayout create(QuickWidget element) {
			return new QuickBorderLayout(element);
		}
	}

	/** @param element The container whose contents to manage */
	protected QuickBorderLayout(QuickWidget element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** A region for a child to occupy in a container managed by a {@link QuickBorderLayout} */
	public enum Region {
		/**
		 * The center, taking up all the space left over from the border contents. Only one component in the container may occupy this
		 * region.
		 */
		Center,
		/** The top edge of the container */
		North,
		/** The bottom edge of the container */
		South,
		/** The right edge of the container */
		East,
		/** The left edge of the container */
		West
	}

	/** An add-on automatically inherited by children of a container managed by a {@link QuickBorderLayout} */
	public static class Child extends ExAddOn.Abstract<QuickWidget> {
		/** The XML name of this add-on */
		public static final String BORDER_LAYOUT_CHILD = "border-layout-child";

		/** {@link QuickBorderLayout} {@link Child} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = BORDER_LAYOUT_CHILD,
			interpretation = Interpreted.class,
			instance = Child.class)
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Child> {
			private Region theRegion;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The widget in the {@link QuickBorderLayout}-managed container
			 */
			public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
				super(type, element);
			}

			/** @return The region for the content widget to occupy in the container */
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
						session.attributes().get("region").getLocatedContent());
				}
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> element) {
				return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
			}
		}

		/** {@link QuickBorderLayout} {@link Child} interpretation */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, Child> {
			/**
			 * @param definition The definition to interpret
			 * @param element The widget in the {@link QuickBorderLayout}-maaged container
			 */
			protected Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
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

		/** @param element The widget in the {@link QuickBorderLayout}-managed container */
		protected Child(QuickWidget element) {
			super(element);
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		/** @return The region for the content widget to occupy in the container */
		public Region getRegion() {
			return theRegion;
		}

		@Override
		public void update(ExAddOn.Interpreted<? extends QuickWidget, ?> interpreted, QuickWidget element)
			throws ModelInstantiationException {
			super.update(interpreted, element);
			Child.Interpreted myInterpreted = (Child.Interpreted) interpreted;
			theRegion = myInterpreted.getDefinition().getRegion();
		}
	}
}
