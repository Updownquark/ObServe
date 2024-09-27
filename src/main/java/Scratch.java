import static java.lang.StrictMath.log10;

/**
 * <p>
 * This file was never supposed to be committed, but it turns out it's easy to miss in big commits.
 * </p>
 *
 * <p>
 * This is a simple main class that I created to do tiny little one-off tests that are not worth saving. The content of this file at any
 * point in history is irrelevant, but I'm keeping it in here just for utility.
 * </p>
 */
public class Scratch {
	/**
	 * Main method. What it does at any point in time, who knows.
	 *
	 * @param args Command-line arguments, typically ignored
	 * @throws Throwable Hey, could happen
	 */
	public static void main(String... args) throws Throwable {
		double d = 31486;
		double f = 5E8;
		System.out.println("PL1=" + fspl1(d / 1000, f / 1E6));
		System.out.println("PL2=" + fspl2(d, f));
		// ObservableCollection<Integer> rows = ObservableCollection.<Integer> create()//
		// .with(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		// ObservableCollection<Integer> selection = ObservableCollection.create();
		// int[] next = new int[] { 11 };
		// EventQueue.invokeLater(() -> {
		// ObservableSwingUtils.systemLandF();
		// WindowPopulation.populateWindow(null, null, true, true)//
		// .withTitle("ObServe Scratch")//
		// .withVContent(p -> p.fill()//
		// .addTable(rows, table -> table.fill()//
		// .withColumn("Value", int.class, i -> i, null)//
		// .withSelection(selection)//
		// .withAdd(() -> next[0]++, null)//
		// .withRemove(vs -> rows.removeAll(vs), null)//
		// .dragSourceRow(d -> d.draggable(true))//
		// .dragAcceptRow(d -> d.draggable(true))//
		// )//
		// .addTable(selection, table -> table.fill()//
		// .withColumn("Value", int.class, i -> i, null)//
		// )//
		// )//
		// .run(null);
		// });
		// selection.onChange(evt -> System.out.println(evt));
	}

	static double fspl1(double km, double mhz) {
		double d = log10(km);
		double f = log10(mhz);
		System.out.println("D=" + d + ", F=" + f);
		return 32.44 + 20.0 * d + 20.0 * f;
	}

	static double fspl2(double m, double hz) {
		double d = log10(m);
		double f = log10(hz);
		System.out.println("D=" + d + ", F=" + f);
		return -147.56 + 20.0 * d + 20.0 * f;
	}
}
