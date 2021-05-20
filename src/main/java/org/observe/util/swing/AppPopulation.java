package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
import org.observe.config.ObservableConfigPath;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.QommonsUtils.TimePrecision;
import org.qommons.TimeUtils;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CircularArrayList;
import org.qommons.config.QommonsConfig;
import org.qommons.io.FileBackups;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.qommons.threading.QommonsTimer;
import org.xml.sax.SAXException;

public class AppPopulation {
	public static class ObservableUiBuilder extends WindowPopulation.DefaultWindowBuilder<JFrame, ObservableUiBuilder> {
		private File theConfigDir;
		private String theConfigName;
		private List<String> theOldConfigNames;
		private URL theErrorReportLink;
		private BiConsumer<StringBuilder, Boolean> theErrorReportInstructions;
		private Consumer<ObservableConfig> theConfigInit;
		private boolean isCloseWithoutSaveEnabled;
		private volatile boolean isClosingWithoutSave;
		private AboutDialogBuilder<?> theAboutDialog;

		public ObservableUiBuilder() {
			super(new JFrame(), Observable.empty(), true);
		}

		public ObservableUiBuilder withConfigDir(String configLocation) {
			return withConfigDir(new File(configLocation));
		}

		public ObservableUiBuilder withConfigDir(File configLocation) {
			theConfigDir = configLocation;
			return this;
		}

		public ObservableUiBuilder withConfig(String configName) {
			theConfigName = configName;
			return this;
		}

		public ObservableUiBuilder withOldConfig(String configName) {
			if (theOldConfigNames == null)
				theOldConfigNames = new LinkedList<>();
			theOldConfigNames.add(configName);
			return this;
		}

		public ObservableUiBuilder withConfigInit(Consumer<ObservableConfig> configInit) {
			theConfigInit = configInit;
			return this;
		}

		/**
		 * @param configInit The resource path to an initial XML configuration to populate
		 * @return This builder
		 */
		public ObservableUiBuilder withConfigInit(Class<?> clazz, String configInit) {
			return withConfigInit(config -> {
				QommonsConfig initConfig;
				try {
					initConfig = QommonsConfig.fromXml(clazz.getResource(configInit));
				} catch (IOException e) {
					System.err.println("Could not find initial config");
					e.printStackTrace();
					return;
				}
				populate(config, initConfig);
			});
		}

		private void populate(ObservableConfig config, QommonsConfig initConfig) {
			config.setName(initConfig.getName());
			config.setValue(initConfig.getValue());
			SyncValueSet<? extends ObservableConfig> subConfigs = config.getAllContent();
			int configIdx = 0;
			for (QommonsConfig initSubConfig : initConfig.subConfigs()) {
				if (configIdx < subConfigs.getValues().size())
					populate(subConfigs.getValues().get(configIdx), initSubConfig);
				else
					populate(config.addChild(initSubConfig.getName()), initSubConfig);
				configIdx++;
			}
		}

		public ObservableUiBuilder withErrorReporting(String link, BiConsumer<StringBuilder, Boolean> instructions) {
			try {
				return withErrorReporting(new URL(link), instructions);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Bad URL: " + link);
			}
		}

		public ObservableUiBuilder withErrorReporting(URL link, BiConsumer<StringBuilder, Boolean> instructions) {
			theErrorReportLink = link;
			theErrorReportInstructions = instructions;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Report Error or Request Feature", __ -> {
					if (theErrorReportInstructions != null) {
						WindowPopulation.populateDialog(null, null, true)//
						.withTitle("Report Error/Request Feature")//
						.withVContent(content -> {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
							if (theErrorReportInstructions != null) {
								StringBuilder msg = new StringBuilder("<html>");
								theErrorReportInstructions.accept(msg, false);
								content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
							}
						}).run(getWindow());
					} else {
						try {
							Desktop.getDesktop().browse(theErrorReportLink.toURI());
						} catch (IOException | URISyntaxException | RuntimeException e) {
							JOptionPane.showMessageDialog(getWindow(), "Unable to open browser. Go to " + theErrorReportLink,
								"Unable To Open Browser", JOptionPane.ERROR_MESSAGE);
							e.printStackTrace();
						}
					}
				}, null);
			}));
			return this;
		}

		public ObservableUiBuilder enableCloseWithoutSave() {
			isCloseWithoutSaveEnabled = true;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Close Without Save", __ -> {
					if (JOptionPane.showConfirmDialog(getWindow(),
						"<html>This will cause all changes since the app was opened to be discarded.<br>Close the app?",
						"Exit Without Saving?", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
						return;
					isClosingWithoutSave = true;
					System.exit(0);
				}, null);
			}));
			return this;
		}

		private static final Pattern VERSION_PATTERN = Pattern.compile("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)");

		public ObservableUiBuilder withAbout(Class<?> appClass, Consumer<AboutDialogBuilder<?>> about) {
			if (theAboutDialog == null) {
				theAboutDialog = new AboutDialogBuilder<>(this, appClass, new JDialog(), Observable.empty(), false)//
					.withTitle("About " + (getTitle() == null ? "" : getTitle().get()))//
					.modal(true);
			}
			about.accept(theAboutDialog);
			boolean[] first = new boolean[] { true };
			withMenuBar(bar -> bar.withMenu("Help", helpMenu -> helpMenu.withAction("About", __ -> {
				if (first[0]) {
					theAboutDialog.run(getWindow());
					first[0] = false;
				} else
					theAboutDialog.getWindow().setVisible(true);
			}, null)));
			return this;
		}

		public ObservableUiBuilder systemLandF() {
			ObservableSwingUtils.systemLandF();
			return this;
		}

		public JFrame build(Function<ObservableConfig, Component> app) {
			build((config, onBuilt) -> onBuilt.accept(app.apply(config)));
			return getWindow();
		}

		public void build(BiConsumer<ObservableConfig, Consumer<Component>> app) {
			ObservableConfig config = ObservableConfig.createRoot("config");
			String configName = theConfigName;
			if (configName != null) {
				File configDir = theConfigDir;
				if (configDir == null) {
					String configProp = System.getProperty(configName + ".config");
					if (configProp != null)
						configDir = new File(configProp);
					else
						configDir = new File(configName).getAbsoluteFile();
				}
				if (!configDir.exists() && !configDir.mkdirs())
					throw new IllegalStateException("Could not create config directory " + configDir.getPath());
				else if (!configDir.isDirectory())
					throw new IllegalStateException("Not a directory: " + configDir.getPath());
				File configFile = new File(configDir, configName + ".xml");
				FileBackups backups = new FileBackups(FileUtils.better(configFile));

				if (!configFile.exists() && theOldConfigNames != null) {
					boolean found = false;
					for (String oldConfigName : theOldConfigNames) {
						File oldConfigFile = new File(configDir, oldConfigName);
						if (oldConfigFile.exists()) {
							if (!oldConfigFile.renameTo(configFile)) {
								System.err.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
							}
							backups.renamedFrom(FileUtils.better(oldConfigFile));
							found = true;
							break;
						}
						if (!found) {
							oldConfigFile = new File(configDir.getParentFile(), oldConfigName + "/" + oldConfigName + ".xml");
							if (oldConfigFile.exists()) {
								if (!oldConfigFile.renameTo(configFile)) {
									System.err.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
								}
								backups.renamedFrom(FileUtils.better(oldConfigFile));
								found = true;
								break;
							}
						}
					}
				}

				if (theErrorReportLink != null || theErrorReportInstructions != null) {
					String errorFileName;
					int lastDot = configFile.getName().lastIndexOf('.');
					if (lastDot >= 0)
						errorFileName = configFile.getName().substring(0, lastDot) + ".errors.txt";
					else
						errorFileName = configFile.getName() + ".errors.txt";
					File errorFile = new File(configFile.getParentFile(), errorFileName);
					new SystemOutputHandler(configFile, errorFile);
				}

				ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
				boolean loaded = false;
				if (configFile.exists()) {
					try {
						try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
							ObservableConfig.readXml(config, configStream, encoding);
						}
						config.setName(configName);
						loaded = true;
					} catch (IOException | SAXException e) {
						System.out.println("Could not read config file " + configFile.getPath());
						e.printStackTrace(System.out);
					}
				}
				withMenuBar(mb -> mb.withMenu("File", menu -> menu.withAction("Backup...", __ -> {
					restoreBackup(false, null, backups, () -> {
						WindowPopulation.populateDialog(null, null, true)//
							.withTitle("Backup Restored")//
							.modal(true)//
							.withVContent(content -> {
								content.addLabel(null, "Please restart the " + getTitle(), null);
							}).run(getWindow());
						isClosingWithoutSave = true;
						System.exit(0);
					}, () -> {
					});
				}, null)));
				if (loaded)
					build2(config, configFile, backups, app);
				else if (!backups.getBackups().isEmpty()) {
					restoreBackup(true, config, backups, () -> {
						config.setName(configName);
						build2(config, configFile, backups, app);
					}, () -> {
						config.setName(configName);
						if (theConfigInit != null)
							theConfigInit.accept(config);
						build2(config, configFile, backups, app);
					});
				}
			} else {
				boolean[] printed = new boolean[1];
				config.watch(ObservableConfigPath.buildPath(ObservableConfigPath.ANY_NAME).multi(true).build()).act(__ -> {
					if (!printed[0]) {
						System.out.println("WARNING: This application has not configured config persistence");
						printed[0] = true;
					}
				});
				if (theErrorReportLink != null || theErrorReportInstructions != null) {
					File errorFile = new File("App.errors.txt");
					new SystemOutputHandler(null, errorFile);
				}
				build2(config, null, null, app);
			}
		}

		private void build2(ObservableConfig config, File configFile, FileBackups backups,
			BiConsumer<ObservableConfig, Consumer<Component>> app) {
			if (configFile != null) {
				ObservableConfigPersistence<IOException> actuallyPersist = ObservableConfig.toFile(configFile,
					ObservableConfig.XmlEncoding.DEFAULT);
				boolean[] persistenceQueued = new boolean[1];
				ObservableConfigPersistence<IOException> persist = new ObservableConfig.ObservableConfigPersistence<IOException>() {
					@Override
					public void persist(ObservableConfig config2) throws IOException {
						try {
							if (persistenceQueued[0] && !isClosingWithoutSave) {
								actuallyPersist.persist(config2);
								backups.fileChanged();
							}
						} finally {
							persistenceQueued[0] = false;
						}
					}
				};
				config.persistOnShutdown(persist, ex -> {
					System.err.println("Could not persist UI config");
					ex.printStackTrace();
				});
				QommonsTimer timer = QommonsTimer.getCommonInstance();
				Object key = new Object() {
					@Override
					public String toString() {
						return config.getName() + " persistence";
					}
				};
				Duration persistDelay = Duration.ofSeconds(2);
				config.watch(ObservableConfigPath.buildPath(ObservableConfigPath.ANY_NAME).multi(true).build()).act(__ -> {
					persistenceQueued[0] = true;
					timer.doAfterInactivity(key, () -> {
						try {
							persist.persist(config);
						} catch (IOException ex) {
							System.err.println("Could not persist UI config");
							ex.printStackTrace();
						}
					}, persistDelay);
				});
			}
			Runnable buildApp = () -> {
				app.accept(config, ui -> {
					if (EventQueue.isDispatchThread())
						build3(config, ui);
					else {
						try {
							EventQueue.invokeAndWait(() -> {
								build3(config, ui);
							});
						} catch (InvocationTargetException | InterruptedException e) {
							throw new IllegalStateException(e);
						}
					}
				});
			};
			if (EventQueue.isDispatchThread())
				buildApp.run();
			else
				EventQueue.invokeLater(buildApp);
		}

		private void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup,
			Runnable onNoBackup) {
			BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
			if (backupTimes == null || backupTimes.isEmpty()) {
				if (onNoBackup != null)
					onNoBackup.run();
				return;
			}
			SettableValue<Instant> selectedBackup = SettableValue.build(Instant.class).safe(false).build();
			Format<Instant> PAST_DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy",
				opts -> opts.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeTimeEvaluation.PAST));
			JFrame[] frame = new JFrame[1];
			boolean[] backedUp = new boolean[1];
			frame[0] = WindowPopulation.populateWindow(null, null, false, false)//
				.withTitle(getTitle().get() == null ? "Backup" : getTitle().get() + " Backup").withIcon(getIcon())//
				.withVContent(content -> {
					if (fromError)
						content.addLabel(null, "Your configuration is missing or has been corrupted", null);
					TimeUtils.RelativeTimeFormat durationFormat = TimeUtils.relativeFormat();
					content.addLabel(null, "Please choose a backup to restore", null)//
					.addTable(ObservableCollection.of(TypeTokens.get().of(Instant.class), backupTimes.reverse()), table -> {
						table.fill()
						.withColumn("Date", Instant.class, t -> t,
							col -> col.formatText(PAST_DATE_FORMAT::format).withWidths(100, 250, 500))//
						.withColumn("Age", Instant.class, t -> t,
							col -> col.formatText(t -> durationFormat.print(t)).withWidths(100, 250, 500))//
						.withSelection(selectedBackup, true);
					}).addButton("Backup", __ -> {
						isClosingWithoutSave = true;
						try {
							backups.restore(selectedBackup.get());
							if (config != null)
								populate(config, QommonsConfig
									.fromXml(QommonsConfig.getRootElement(backups.getBackup(selectedBackup.get()).read())));
							backedUp[0] = true;
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							isCloseWithoutSaveEnabled = false;
						}
						frame[0].setVisible(false);
					}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
				}).run(null).getWindow();
			frame[0].addComponentListener(new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					if (backedUp[0]) {
						if (onBackup != null)
							onBackup.run();
					} else {
						if (onNoBackup != null)
							onNoBackup.run();
					}
				}
			});
		}

		public static class AboutDialogBuilder<A extends AboutDialogBuilder<A>> extends WindowPopulation.DefaultDialogBuilder<JDialog, A> {
			private final Class<?> theAppClass;
			private final SettableValue<String> theCurrentVersion;
			private Supplier<Version> theLatestReleaseGetter;
			private Version theLatestRelease;
			private final ObservableValue<Version> theLatestVersionValue;
			private final SettableValue<Consumer<Version>> theUpgrader;

			AboutDialogBuilder(ObservableUiBuilder app, Class<?> appClass, JDialog dialog, Observable<?> until, boolean disposeOnClose) {
				super(dialog, until, disposeOnClose);
				theAppClass = appClass;
				theCurrentVersion = SettableValue.build(String.class).safe(false).build();
				theUpgrader = SettableValue.build(TypeTokens.get().keyFor(Consumer.class).<Consumer<Version>> parameterized(Version.class))
					.safe(false).build();
				SimpleObservable<Object> shown = SimpleObservable.build().safe(false).build();
				shown.act(__ -> {
					if (theLatestReleaseGetter != null)
						theLatestRelease = theLatestReleaseGetter.get();
					else
						theLatestRelease = null;
				});
				theLatestVersionValue = ObservableValue.of(TypeTokens.get().of(Version.class), () -> theLatestRelease, //
					() -> 1, // Hopefully nobody asks for the stamp
					shown);
				getWindow().addComponentListener(new ComponentAdapter() {
					@Override
					public void componentShown(ComponentEvent e) {
						shown.onNext(e);
					}
				});

				withVContent(content -> {
					content.addLabel("Current Version:", theCurrentVersion.map(String.class, v -> (v == null ? "Unknown" : v)), Format.TEXT,
						label -> label.visibleWhen(theCurrentVersion.map(v -> v != null)));
					content.addVPanel(lvp -> lvp.fill().visibleWhen(theLatestVersionValue.map(v -> v != null))
						.decorate(deco -> deco.withTitledBorder("Latest Version", Color.black))//
						.addLabel(null, theLatestVersionValue.map(v -> v == null ? "" : v.name), Format.TEXT, label -> label.fill())//
						.addLabel(null, theLatestVersionValue.map(v -> v == null ? "" : v.title), Format.TEXT, label -> label.fill())//
						.addTextArea(null,
							SettableValue.asSettable(theLatestVersionValue.map(v -> v == null ? "" : wrap(v.description)), d -> null),
							Format.TEXT, label -> label.fill().fillV().modifyEditor(ed -> ed.asHtml().setEditable(false)))//
						.addButton("Upgrade", __ -> {
							theUpgrader.get().accept(theLatestRelease);
						}, btn -> btn.visibleWhen(theUpgrader
							.transform(tx -> tx.combineWith(theCurrentVersion).combineWith(theLatestVersionValue).combine((u, cv, lv) -> {
								return u != null && cv != null && lv != null;
							}))))//
						);
				});
				theLatestVersionValue.noInitChanges().act(__ -> {
					EventQueue.invokeLater(() -> {
						if (getWindow().isVisible()) {
							getWindow().pack();
							getWindow().setLocationRelativeTo(app.getWindow());
						}
					});
				});
				if ("true".equals(System.getProperty("show.app.version"))) {
					EventQueue.invokeLater(() -> {
						if (theLatestReleaseGetter != null)
							theLatestRelease = theLatestReleaseGetter.get();
						else
							theLatestRelease = null;
						run(app.getWindow());
					});
				}
			}

			private static String wrap(String str) {
				if (str == null || str.length() <= WRAP_LENGTH) {
					return str;
				}
				StringBuilder s = new StringBuilder("<html>");
				int start = 0;
				int newLine = str.indexOf('\n');
				while (newLine >= 0) {
					wrap(str.substring(start, newLine), s);
					s.append("<br>");
					start = newLine + 1;
					newLine = str.indexOf('\n', start);
				}
				wrap(str.substring(start), s);
				return s.toString();
			}

			private static int WRAP_LENGTH = 80;

			private static void wrap(String str, StringBuilder into) {
				if (str.length() <= WRAP_LENGTH) {
					into.append(str);
				}
				int start = 0;
				while (str.length() - start > WRAP_LENGTH) {
					int c = start + WRAP_LENGTH;
					int i;
					for (i = 0; i < 15; i++) {
						if (Character.isWhitespace(str.charAt(c - i)))
							break;
					}
					if (i < 15) {
						into.append(str.substring(start, c - i));
						start = c - i + 1;
					} else {
						into.append(str.substring(start, c));
						start = c;
					}
					into.append("<br>");
				}
				into.append(str.substring(start));
			}

			public A withCurrentVersionFromManifest() {
				Package pkg = theAppClass.getPackage();
				String version = null;
				while (pkg != null && (version = pkg.getImplementationVersion()) == null) {
					int dotIdx = pkg.getName().lastIndexOf('.');
					if (dotIdx < 0)
						break;
					pkg = Package.getPackage(pkg.getName().substring(0, dotIdx));
				}
				withCurrentVersion(version == null ? "Unknown" : version);
				return (A) this;
			}

			public A withCurrentVersion(String currentVersion) {
				theCurrentVersion.set(currentVersion, null);
				return (A) this;
			}

			public A withLatestVersion(Supplier<Version> latestVersion) {
				theLatestReleaseGetter = latestVersion;
				return (A) this;
			}

			public A withUpgrade(Consumer<Version> upgrade) {
				theUpgrader.set(upgrade, null);
				return (A) this;
			}
		}

		static class SystemOutput {
			final byte[] content;
			final Instant time;
			final boolean error;

			SystemOutput(byte[] content, Instant time, boolean error) {
				this.content = content;
				this.time = time;
				this.error = error;
			}
		}

		class SystemOutputHandler {
			private final File theConfigFile;
			private final File theErrorFile;
			private final ByteArrayOutputStream theBytes;
			private final Duration theAccumulationTime;
			private final Duration theErrorCascadeTolerance;
			private final CircularArrayList<SystemOutput> theOutput;
			private volatile Instant theLastWriteTime;
			private volatile boolean theLastWriteError;
			private volatile boolean isWritingError;

			SystemOutputHandler(File configFile, File errorFile) {
				theConfigFile = configFile;
				theErrorFile = errorFile;
				theBytes = new ByteArrayOutputStream(64 * 1028);
				theAccumulationTime = Duration.ofSeconds(2);
				theErrorCascadeTolerance = Duration.ofMillis(100);
				theOutput = CircularArrayList.build().build();

				System.setOut(new HandlerPrintStream(System.out, false));
				System.setErr(new HandlerPrintStream(System.err, true));
			}

			synchronized void report(int b, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b);
			}

			synchronized void report(byte[] b, int off, int len, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b, off, len);
			}

			private void startReport(boolean error) {
				Instant now = Instant.now();
				boolean timeDiff = !now.equals(theLastWriteTime);
				if (timeDiff) {
					if (!isWritingError) {
						SystemOutput output = theOutput.peekFirst();
						while (output != null && output.time.plus(theAccumulationTime).compareTo(now) < 0) {
							theOutput.removeFirst();
							output = theOutput.peekFirst();
						}
					}
					theLastWriteTime = now;
				}
				if (theBytes.size() == 0) {//
				} else if (error != theLastWriteError || timeDiff) {
					byte[] output = theBytes.toByteArray();
					theBytes.reset();
					theOutput.add(new SystemOutput(output, now, error));
				}
				if (error) {
					isWritingError = true;
					QommonsTimer.getCommonInstance().doAfterInactivity(this, this::alertUser, theErrorCascadeTolerance);
				}
			}

			private void alertUser() {
				if (!EventQueue.isDispatchThread()) {
					try {
						EventQueue.invokeAndWait(this::alertUser);
					} catch (InvocationTargetException | InterruptedException e) {
						e.printStackTrace(System.out);
					}
					return;
				}
				isWritingError = false;
				try (OutputStream out = new BufferedOutputStream(new FileOutputStream(theErrorFile));
					Writer writer = new OutputStreamWriter(out)) {
					boolean lastLineEnd = true;
					boolean lastError = false;
					Instant lastTime = Instant.ofEpochMilli(0);
					SystemOutput output = theOutput.pollFirst();
					while (output != null) {
						boolean timeDiff = !lastTime.equals(output.time);
						if (lastError != output.error || (lastLineEnd && timeDiff)) {
							writer.append('[').append(output.error ? "ERR" : "OUT").append(' ');
							writer.append(QommonsUtils.printRelativeTime(output.time.toEpochMilli(), lastTime.toEpochMilli(),
								TimePrecision.MILLIS, TimeZone.getDefault(), 0, null));
							writer.append("] ");
							writer.flush();
						}
						lastError = output.error;
						lastTime = output.time;
						out.write(output.content);
						byte lastChar = output.content[output.content.length - 1];
						lastLineEnd = lastChar == '\n' || lastChar == '\r';
						output = theOutput.pollFirst();
					}
					WindowPopulation.populateDialog(null, null, true)//
					.withTitle("Unhandled Application Error")//
					.withVContent(content -> {
						String title = getTitle() == null ? null : getTitle().get();
						if (title != null)
							content.addLabel(null, ObservableValue.of(title + " has encountered an error."), Format.TEXT, null);
						else
							content.addLabel(null, ObservableValue.of("An error has been encountered."), Format.TEXT, null);
						if (theErrorReportLink != null) {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
						} else
							content.addLabel(null, ObservableValue.of("Please report it."), Format.TEXT, null);
						content.addLabel(null,
							ObservableValue.of("<html>Please attach the captured error output file: <a href=\"thisDoesntMatter.html\">"
								+ theErrorFile.getPath() + "</a>"),
							Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										/* We don't have Java 9, but this hack seems to work
										 * For some reason, I was encountering theErrorFile.getParentFile()==null
										 * So I had to work around it
										 * getPath() was also not returning parents
										 * String path = theErrorFile.toString();
										 * JOptionPane.showMessageDialog(null, path);
										 * int lastSlash = path.lastIndexOf('/');
										 * if (lastSlash < 0 || lastSlash < path.lastIndexOf('\\'))
										 * lastSlash = path.lastIndexOf('\\');
										 * if (lastSlash < 0)
										 * return; // ??
										 * String parentPath = path.substring(0, lastSlash);
										 */
										// Desktop.getDesktop().browse(new File(parentPath).toURI());
										Desktop.getDesktop().browse(theErrorFile.getAbsoluteFile().getParentFile().toURI());
									} catch (IOException | RuntimeException e) {
										e.printStackTrace();
									}
								});
							});
						if (theErrorReportInstructions != null) {
							StringBuilder msg = new StringBuilder("<html>");
							theErrorReportInstructions.accept(msg, true);
							content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
						}
						if (theConfigFile != null) {
							String msg = "<html>You may want to consider backing up your data file (" + theConfigFile.getPath() + ")";
							if (isCloseWithoutSaveEnabled)
								msg += "<br>" + " or closing the app without saving your changes (File->Close Without Save)";
							content.addLabel(null, ObservableValue.of(msg), Format.TEXT, null);
						}
					}).run(getWindow());
				} catch (IOException e) {
					System.err.println("Could not write error output");
					e.printStackTrace();
				}
			}

			class HandlerPrintStream extends PrintStream {
				HandlerPrintStream(PrintStream systemStream, boolean error) {
					super(new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							systemStream.write(b);
							report(b, error);
						}

						@Override
						public void write(byte[] b) throws IOException {
							systemStream.write(b);
							report(b, 0, b.length, error);
						}

						@Override
						public void write(byte[] b, int off, int len) throws IOException {
							systemStream.write(b, off, len);
							report(b, off, len, error);
						}
					});
				}
			}
		}

		private JFrame build3(ObservableConfig config, Component ui) {
			return withBounds(config).withContent(ui).run(null).getWindow();
		}
	}

	public static class Version {
		public final String name;
		public final String title;
		public final String description;

		public Version(String name, String title, String description) {
			this.name = name;
			this.title = title;
			this.description = description;
		}
	}
}
