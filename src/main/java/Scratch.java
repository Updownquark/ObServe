import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

import org.qommons.threading.QommonsTimer;

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
		File file = File.createTempFile("scratch", ".txt");
		QommonsTimer.getCommonInstance().execute(() -> {
			System.out.print("Reading...");
			System.out.flush();
			try (InputStream in = new FileInputStream(file)) {
				for (int b = in.read(); b >= 0; b = in.read()) {
				}
			} catch (IOException e) {
				System.out.println("err: " + e);
				return;
			}
			System.out.println("read");
		}, Duration.ofMillis(10), false);

		Thread.sleep(3000);
		System.out.flush();
		try (OutputStream out = new FileOutputStream(file)) {
			System.out.print("write...");
			Thread.sleep(5000);
			System.out.print("writing...");
			out.write(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 });
			System.out.println("written");
		}
		Thread.sleep(1000);
	}
}
