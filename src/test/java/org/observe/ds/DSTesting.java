package org.observe.ds;

import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ds.impl.DefaultDependencyService;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Transactable;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/** Tests the default implementation of {@link DependencyService} */
public class DSTesting {
	/** Tests basic, single-{@link Dependency#getMinimum() minimum}, non-{@link Dependency#isDynamic() dynamic} functionality */
	@Test
	public void testBasicStatic() {
		DefaultDependencyService<String> dds = new DefaultDependencyService<>(Transactable.NONE);

		SimpleService one = new SimpleService("1");
		SimpleService two = new SimpleService("2");
		SimpleService three = new SimpleService("3");
		SimpleService four = new SimpleService("4");
		SimpleService five = new SimpleService("5");

		ComponentController<String> a = validate(dds.inject("A", __ -> "A")//
			.depends(one, null)//
			.depends(two, null)//
			.provides(three, __ -> three)//
			.disposeWhenInactive(__ -> {
			})//
			.build());
		ComponentController<String> b = validate(dds.inject("B", __ -> "B")//
			.depends(two, null)//
			.depends(three, null)//
			.provides(four, __0 -> four)//
			.disposeWhenInactive(__ -> {
			})//
			.build());
		ComponentController<String> c = validate(dds.inject("C", __ -> "C")//
			.depends(three, null)//
			.depends(four, null)//
			.provides(five, __ -> five)//
			.disposeWhenInactive(__ -> {
			})//
			.build());

		ComponentController<String> d = validate(dds.inject("D", __ -> "D")//
			.depends(five, null)//
			.disposeWhenInactive(__ -> {
			})//
			.build());

		print(dds);
		Assert.assertEquals(ComponentStage.Defined, a.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, b.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, c.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, d.getStage().get());

		ComponentController<String> e = validate(dds.inject("E", __ -> "E")//
			.provides(one, __ -> one)//
			.provides(two, __ -> two)//
			.disposeWhenInactive(__ -> {
			})//
			.build());

		Assert.assertEquals(ComponentStage.Defined, a.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, b.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, c.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, d.getStage().get());
		Assert.assertEquals(ComponentStage.Defined, e.getStage().get());

		dds.init();
		print(dds);

		Assert.assertEquals(ComponentStage.Satisfied, a.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, b.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, c.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, d.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, e.getStage().get());

		System.out.println("Setting C to unavailable");
		c.setAvailable(false);
		print(dds);

		Assert.assertEquals(ComponentStage.Satisfied, a.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, b.getStage().get());
		Assert.assertEquals(ComponentStage.Unsatisfied, d.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, e.getStage().get());

		System.out.println("Setting C to available");
		c.setAvailable(true);
		print(dds);

		Assert.assertEquals(ComponentStage.Satisfied, a.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, b.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, c.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, d.getStage().get());
		Assert.assertEquals(ComponentStage.Satisfied, e.getStage().get());

		dds.close();
		print(dds);

		Assert.assertEquals(0, dds.getComponents().size());

		Assert.assertEquals(ComponentStage.Removed, a.getStage().get());
		Assert.assertEquals(ComponentStage.Removed, b.getStage().get());
		Assert.assertEquals(ComponentStage.Removed, c.getStage().get());
		Assert.assertEquals(ComponentStage.Removed, d.getStage().get());
		Assert.assertEquals(ComponentStage.Removed, e.getStage().get());
	}

	static void print(DependencyService<?> dds) {
		System.out.println("DS Components:");
		for (DSComponent<?> comp : dds.getComponents()) {
			System.out.println(
				comp.getName() + ": " + comp.getProvided() + (comp.isAvailable().get() ? "(" : "(x, ") + comp.getStage().get() + ")");
			for (Dependency<?, ?> dep : comp.getDependencies().values()) {
				System.out.println("\t" + dep + dep.getProviders());
			}
		}
	}

	/** Creates random numbers of randomly-configured components, exercising all functionality of the service */
	@Test
	public void randomTest() {
		TestHelper.createTester(DSTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
		.withPlacemarks("op").withMaxCaseDuration(Duration.ofSeconds(10)).withRandomCases(500).execute().throwErrorIfFailed();
	}

	static class DSTestable implements Testable {
		@Override
		public void accept(TestHelper helper) {
			DefaultDependencyService<String> dds = new DefaultDependencyService<>(Transactable.NONE);

			int serviceCount = helper.getInt(1, helper.getInt(3, 10));
			int componentCount = helper.getInt(1, helper.getInt(3, 10));

			if (helper.isReproducing())
				System.out.println(serviceCount + " services, " + componentCount + " components");

			List<SimpleService> services = new ArrayList<>(serviceCount);
			List<ComponentController<String>> components = new ArrayList<>(componentCount);

			BetterSet<Dependency<String, ?>> path = BetterHashSet.build().build();
			Boolean[] initialized = new Boolean[1];
			for (int i = 0; i < serviceCount; i++)
				services.add(new SimpleService("" + i));
			int[] componentIndex = new int[1];
			for (componentIndex[0] = 0; componentIndex[0] < componentCount; componentIndex[0]++) {
				String name = "" + (char) ('A' + componentIndex[0]);
				components.add(configureComponent(//
					dds.inject(name, __ -> name), components.size(), services, helper, initialized, path));
			}

			checkState(dds, false, path);
			if (helper.isReproducing())
				System.out.println("initializing");
			helper.placemark();
			initialized[0] = null;
			dds.init();
			initialized[0] = true;
			if (helper.isReproducing())
				System.out.println("initialized");
			checkState(dds, true, path);

			int ops = helper.getInt(10, 100);
			try {
				for (int i = 0; i < ops; i++) {
					if (helper.isReproducing())
						print(dds);
					helper.createAction()//
					.or(1, () -> { // Toggle availability
						if (components.isEmpty())
							return;
						ComponentController<String> comp = components.get(helper.getInt(0, components.size()));
						boolean available = !comp.isAvailable().get();
						if (helper.isReproducing())
							System.out.println("Changing " + comp.getName() + " to " + (available ? "" : "un") + "available");
						comp.setAvailable(available);
						Assert.assertEquals(available, comp.isAvailable().get());
					}).or(0.01, () -> {// Remove component
						if (components.isEmpty())
							return;
						ComponentController<String> comp = components.get(helper.getInt(0, components.size()));
						if (helper.isReproducing())
							System.out.println("Removing " + comp.getName());
						comp.remove();
						Assert.assertFalse(dds.getComponents().contains(comp));
						components.remove(comp);
					}).or(0.01, () -> {// Add component
						String name = "" + (char) ('A' + componentIndex[0]);
						if (helper.isReproducing())
							System.out.println("Adding " + name);
						components.add(
							configureComponent(dds.inject(name, __ -> name), components.size(), services, helper, initialized, path));
						componentIndex[0]++;
					})//
					.execute("op");
					checkState(dds, true, path);
				}
			} catch (AssertionError e) {
				System.err.println("On error:");
				print(dds);
				throw e;
			}
		}

		private ComponentController<String> configureComponent(DSComponent.Builder<String> builder, int componentCount,
			List<SimpleService> services, TestHelper helper, Boolean[] initialized, BetterSet<Dependency<String, ?>> path) {
			int satisfied = helper.getInt(0, helper.getInt(1, services.size()));
			BitSet used = new BitSet();
			// Random dependencies
			for (int i = 0; i < satisfied; i++) {
				int s;
				do {
					s = helper.getInt(0, services.size());
				} while (used.get(s));
				used.set(s);
				builder.depends(services.get(s), dep -> configureDependency(dep, componentCount, helper));
			}
			// Random provided services
			used.clear();
			for (int i = 0; i < satisfied; i++) {
				int s;
				do {
					s = helper.getInt(0, services.size());
				} while (used.get(i));
				used.set(i);
				int fs = s;
				builder.provides(services.get(s), __ -> services.get(fs));
			}
			boolean available = helper.getBoolean(0.9);
			builder.initiallyAvailable(available);
			if (helper.isReproducing()) {
				System.out.println(builder.getName() + ": " + builder.getProvided().keySet() + (available ? "" : "(x)"));
				for (Dependency<String, ?> dep : builder.getDependencies().values()) {
					System.out.println("\t" + dep);
				}
			}
			builder.disposeWhenInactive(__ -> {
			});
			ComponentController<String> component = builder.build();
			Assert.assertEquals(available, component.isAvailable().get());
			component.getStage().noInitChanges().act(__ -> checkComponent(component, initialized[0], path, true));
			return validate(component);
		}

		private void configureDependency(Dependency.Builder<String, SimpleService> dep, int componentCount, TestHelper helper) {
			dep.dynamic(helper.getBoolean(0.1));
			if (helper.getBoolean(0.25))
				dep.minimum(helper.getInt(0, helper.getInt(2, componentCount)));
		}

		private void checkState(DependencyService<String> dds, Boolean initialized, BetterSet<Dependency<String, ?>> path) {
			for (DSComponent<String> comp : dds.getComponents()) {
				checkComponent(comp, initialized, path, false);
			}
		}

		private void checkComponent(DSComponent<String> comp, Boolean initialized, BetterSet<Dependency<String, ?>> path,
			boolean transition) {
			DependencyService<String> dds = comp.getDependencyService();
			switch (comp.getStage().get()) {
			case Defined:
				if (initialized != null)
					Assert.assertFalse("Component " + comp.getName() + " should not be Defined after initialization", initialized);
				break;
			case Unavailable:
			case Satisfied:
				break;
			case Unsatisfied:
				if (initialized != null && comp.isAvailable().get())
					Assert.assertTrue("Component " + comp.getName() + " should not be " + comp.getStage().get() + " before initialization",
						initialized);
				break;
			case PreSatisfied:
				Assert.assertTrue("Component " + comp.getName() + " should not be PreSatisfied except during transition", transition);
				break;
			case Removed:
				Assert.assertFalse("Removed component " + comp.getName() + " should not be present in the component list",
					dds.getComponents().contains(comp));
				Assert.assertNull(comp.getComponentValue());
				return;
			}
			if (!comp.isAvailable().get()) {
				Assert.assertNull(comp.getComponentValue());
				switch (comp.getStage().get()) {
				case PreSatisfied:
				case Satisfied:
					throw new AssertionError("Unavailable component with status " + comp.getStage().get());
				default:
				}
				return; // Inactive components should not be checked
			}
			int unsatisfied = 0;
			for (Dependency<String, ?> dep : comp.getDependencies().values()) {
				int count = 0;
				for (DSComponent<String> comp2 : dds.getComponents()) {
					if (!comp2.isAvailable().get())
						continue;
					switch (comp2.getStage().get()) {
					case Defined:
					case Removed:
					case Unsatisfied:
						continue;
					default:
					}
					if (comp2.getComponentValue() != comp.getComponentValue() && comp2.getProvided().contains(dep.getTarget()))
						count++;
				}
				if (!transition && count != dep.getProviders().size() && comp.getStage().get() == ComponentStage.Satisfied//
					&& !dep.isDynamic())
					Assert.assertEquals("Incorrect providers for " + dep + " " + dep.getProviders(), count, dep.getProviders().size());
				if (count < dep.getMinimum())
					unsatisfied++;
			}
			switch (comp.getStage().get()) {
			case Defined:
			case Unsatisfied:
				if (initialized != null && initialized) {
					Assert.assertNull(comp.getComponentValue());
					if (!transition && unsatisfied == 0)
						Assert.assertNotEquals(comp.getComponentValue(), 0, unsatisfied);
				}
				break;
			case Satisfied: // This should be followed regardless
				if (unsatisfied != 0)
					Assert.assertEquals(comp.getComponentValue(), 0, unsatisfied);
				break;
			default:// Handled at top
			}
			// Check for dynamic cycles that should have been activated
			if (!transition && initialized != null && initialized && comp.isAvailable().get()//
				&& comp.getStage().get().isActive() != shouldBeSatisfied(dds, comp, path)) {
				throw new AssertionError("Dynamic cycle detection failed: " + comp.getName() + " is " + comp.getStage().get());
			}
		}

		private boolean shouldBeSatisfied(DependencyService<String> dds, DSComponent<String> comp, BetterSet<Dependency<String, ?>> path) {
			for (Dependency<String, ?> dep : comp.getDependencies().values()) {
				boolean[] added = new boolean[1];
				ElementId el = path.getOrAdd(dep, null, null, false, null, () -> added[0] = true).getElementId();
				if (!added[0]) {
					// In a cycle, but is it dynamic?
					for (CollectionElement<Dependency<String, ?>> dep2 = path.getElement(el); dep2 != null; dep2 = path
						.getAdjacentElement(dep2.getElementId(), true)) {
						if (dep2.get().isDynamic())
							return true;
					}
					return false;
				}
				int needed = dep.getMinimum();
				try {
					if (needed <= 0)
						continue;
					for (DSComponent<String> comp2 : dds.getComponents()) {
						if (!comp2.isAvailable().get())
							continue;
						if (comp2.getComponentValue() != comp.getComponentValue() && comp2.getProvided().contains(dep.getTarget())//
							&& shouldBeSatisfied(dds, comp2, path)) {
							needed--;
							if (needed == 0)
								break;
						}
					}
				} finally {
					path.remove(dep);
				}
				if (needed > 0)
					return false;
			}
			return true;
		}
	}

	static ComponentController<String> validate(ComponentController<String> comp) {
		CausableKey key = Causable.key((__, ___) -> checkComponent(comp));
		Observable<?> removed = comp.getStage().noInitChanges().filter(evt -> evt.getNewValue() == ComponentStage.Removed).take(1);
		Observable.or(comp.getStage().changes(), comp.isAvailable().noInitChanges(), //
			Observable.or(comp.getDependencies().values().stream().map(dep -> dep.getProviders().flow().refreshEach(comp2 -> {
				return Observable.or(comp2.getStage().noInitChanges(), comp2.isAvailable().noInitChanges());
			}).collect().simpleChanges())
				.collect(Collectors.toList()).toArray(new Observable[0])))
			.takeUntil(removed)//
		.act(cause -> {
			// All providers known to the dependencies must be available and satisfied (or pre-satisfied) at all times
			for (Dependency<String, ?> dep : comp.getDependencies().values()) {
				for (DSComponent<String> comp2 : dep.getProviders()) {
					if (!comp2.isAvailable().get())
						Assert.assertTrue("Unavailable dependency satisfier: " + dep + ": " + comp2, comp2.isAvailable().get());
					switch (comp2.getStage().get()) {
					case Defined:
					case Removed:
					case Unsatisfied:
						Assert.assertFalse("Non-satisfied dependency satisfier: " + dep + ": " + comp2, true);
						break;
					default:
					}
				}
			}
			((Causable) cause).getRootCausable().onFinish(key);
		});
		return comp;
	}

	private static void checkComponent(ComponentController<String> comp) {
		boolean satisfied = true;
		switch (comp.getStage().get()) {
		case Defined:
		case Unsatisfied:
			satisfied = false;
			Assert.assertNull(comp.getComponentValue());
			return; // Inactive components don't need to be checked
		case Unavailable:
			Assert.assertFalse(comp.getName(), comp.isAvailable().get());
			break;
		case PreSatisfied:
			throw new AssertionError("Component " + comp.getName() + " should not be PreSatisfied outside of a transition");
		case Satisfied:
			satisfied = true;
			break;
		case Removed:
			Assert.assertNull(comp.getComponentValue());
			return;
		}
		if (!comp.isAvailable().get() && comp.getStage().get() != ComponentStage.Unavailable)
			throw new AssertionError("Unavailable component " + comp + " with status " + comp.getStage().get());
		boolean unsatisfied = false;
		// All providers known to the dependencies must be available and satisfied (or pre-satisfied) at all times
		for (Dependency<String, ?> dep : comp.getDependencies().values()) {
			for (DSComponent<String> comp2 : dep.getProviders()) {
				Assert.assertTrue(comp2.isAvailable().get());
				switch (comp2.getStage().get()) {
				case Defined:
				case Removed:
				case Unsatisfied:
					Assert.assertFalse("Non-satisfied dependency satisfier " + dep + ": " + comp2, true);
					break;
				default:
				}
			}
			if (dep.getProviders().size() < dep.getMinimum()) {
				if (satisfied)
					Assert.assertFalse(satisfied);
				unsatisfied = true;
			}
		}
		if (satisfied) {
			if (unsatisfied)
				Assert.assertFalse(unsatisfied);
			if (comp.isAvailable().get())
				Assert.assertEquals(comp.getName(), comp.getComponentValue());
			else
				Assert.assertNull(comp.getComponentValue());
		} else {
			if (!unsatisfied)
				Assert.assertTrue(unsatisfied);
			if (comp.getComponentValue() != null)
				Assert.assertNull(comp.getComponentValue());
		}
	}

	static class SimpleService implements Service<SimpleService> {
		private final String theName;

		SimpleService(String name) {
			theName = name;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public Class<SimpleService> getServiceType() {
			return SimpleService.class;
		}

		@Override
		public String toString() {
			return theName;
		}
	}
}
