package io.bootique;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.ServiceLoader;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import io.bootique.command.CommandOutcome;
import io.bootique.env.DefaultEnvironment;
import io.bootique.log.BootLogger;
import io.bootique.log.DefaultBootLogger;

import joptsimple.OptionException;

/**
 * A main launcher class of Bootique. To start a Bootique app, you may write
 * your main method as follows:
 * 
 * <pre>
 * public static void main(String[] args) {
 * 	Bootique.app(args).commands(_optional_commands_).modules(_optional_extensions_).run();
 * }
 * </pre>
 * 
 * or
 * 
 * <pre>
 * public static void main(String[] args) {
 * 	Bootique.app(args).commands(_optional_commands_).autoLoadModules().run();
 * }
 * </pre>
 * 
 * or just use this class as a main app class.
 */
public class Bootique {

	protected static Module createModule(Class<? extends Module> moduleType) {
		try {
			return moduleType.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException("Error instantiating Module of type: " + moduleType.getName(), e);
		}
	}

	static String[] mergeArrays(String[] a1, String[] a2) {
		if (a1.length == 0) {
			return a2;
		}

		if (a2.length == 0) {
			return a1;
		}

		String[] merged = new String[a1.length + a2.length];
		System.arraycopy(a1, 0, merged, 0, a1.length);
		System.arraycopy(a2, 0, merged, a1.length, a2.length);

		return merged;
	}

	static String[] toArray(Collection<String> collection) {
		return collection.toArray(new String[collection.size()]);
	}

	protected Collection<BQModuleProvider> providers;
	private String[] args;
	private boolean autoLoadModules;
	private BootLogger bootLogger;

	/**
	 * A reusable main method that auto-loads available modules and runs
	 * Bootique stack. Useful for apps that don't care to customize their
	 * "main()".
	 * 
	 * @param args
	 *            app arguments passed by the shell.
	 * @since 0.17
	 */
	public static void main(String[] args) {
		Bootique.app(args).autoLoadModules().run();
	}

	/**
	 * Starts a builder of Bootique runtime.
	 * 
	 * @param args
	 *            command-line arguments.
	 * @return Bootique object that can be customized and then executed as an
	 *         app via the {@link #run()} method.
	 */
	public static Bootique app(String... args) {
		if (args == null) {
			args = new String[0];
		}

		return new Bootique(args);
	}

	/**
	 * Starts a builder of Bootique runtime, initializing it with provided
	 * command-line arguments.
	 * 
	 * @since 0.17
	 * @param args
	 *            command-line arguments.
	 * @return Bootique object that can be customized and then executed as an
	 *         app via the {@link #run()} method.
	 */
	public static Bootique app(Collection<String> args) {
		if (args == null) {
			args = Collections.emptyList();
		}

		return app(toArray(Objects.requireNonNull(args)));
	}

	private Bootique(String[] args) {
		this.args = args;
		this.providers = new ArrayList<>();
		this.autoLoadModules = false;
		this.bootLogger = createBootLogger();
	}

	/**
	 * Optionally overrides Bootique BootLogger.
	 * 
	 * @since 0.12
	 * @return this instance of Bootique.
	 */
	public Bootique bootLogger(BootLogger bootLogger) {
		this.bootLogger = bootLogger;
		return this;
	}

	/**
	 * Appends extra values to Bootique CLI arguments.
	 * 
	 * @since 0.17
	 * @param args
	 *            extra args to pass to Bootique.
	 * @return this instance of Bootique
	 */
	public Bootique args(String... args) {
		if (args != null) {
			this.args = Bootique.mergeArrays(this.args, args);
		}
		return this;
	}

	/**
	 * Appends extra values to Bootique CLI arguments.
	 * 
	 * @since 0.17
	 * @param args
	 *            extra args to pass to Bootique.
	 * @return this instance of Bootique
	 */
	public Bootique args(Collection<String> args) {
		if (args != null) {
			this.args = Bootique.mergeArrays(this.args, Bootique.toArray(args));
		}
		return this;
	}

	/**
	 * Instructs Bootique to load any modules available on class-path that
	 * expose {@link BQModuleProvider} provider. Auto-loaded modules will be
	 * used in default configuration. Factories within modules will of course be
	 * configured dynamically from YAML.
	 * <p>
	 * <i>Use with caution, you may load more modules than you expected. Make
	 * sure only needed Bootique dependencies are included on class-path. If in
	 * doubt, switch to explicit Module loading via
	 * {@link #modules(Class...)}</i>.
	 * 
	 * @return this Bootique instance
	 * @see BQModuleProvider
	 */
	public Bootique autoLoadModules() {
		this.autoLoadModules = true;
		return this;
	}

	/**
	 * @param moduleType
	 *            custom Module class to add to Bootique DI runtime.
	 * @return this Bootique instance
	 * @since 0.8
	 * @see #autoLoadModules()
	 */
	public Bootique module(Class<? extends Module> moduleType) {
		Objects.requireNonNull(moduleType);
		providers.add(() -> createModule(moduleType));
		return this;
	}

	/**
	 * Adds an array of Module types to the Bootique DI runtime. Each type will
	 * be instantiated by Bootique and added to the Guice DI container.
	 * 
	 * @param moduleTypes
	 *            custom Module classes to add to Bootique DI runtime.
	 * @return this Bootique instance
	 * @since 0.8
	 * @see #autoLoadModules()
	 */
	@SafeVarargs
	public final Bootique modules(Class<? extends Module>... moduleTypes) {
		Arrays.asList(moduleTypes).forEach(m -> module(m));
		return this;
	}

	public Bootique module(Module m) {
		Objects.requireNonNull(m);
		providers.add(new BQModuleProvider() {

			@Override
			public Module module() {
				return m;
			}

			@Override
			public String name() {
				return "Bootique";
			}
		});
		return this;
	}

	/**
	 * Adds an array of Modules to the Bootique DI runtime.
	 * 
	 * @param modules
	 *            an array of modules to add to Bootiqie DI runtime.
	 * @return this instance of {@link Bootique}.
	 */
	public Bootique modules(Module... modules) {
		Arrays.asList(modules).forEach(m -> module(m));
		return this;
	}

	/**
	 * Adds a Module generated by the provider. Provider may optionally specify
	 * that the Module overrides services in some other Module.
	 * 
	 * @since 0.12
	 * @param moduleProvider
	 *            a provider of Module and override spec.
	 * @return this instance of {@link Bootique}.
	 */
	public Bootique module(BQModuleProvider moduleProvider) {
		Objects.requireNonNull(moduleProvider);
		providers.add(moduleProvider);
		return this;
	}

	/**
	 * Starts an API call chain to override an array of Modules.
	 * 
	 * @param overriddenTypes
	 *            an array of modules whose bindings should be overridden.
	 * @return {@link BQModuleOverrideBuilder} object to specify a Module
	 *         overriding other modules.
	 */
	@SafeVarargs
	public final BQModuleOverrideBuilder override(Class<? extends Module>... overriddenTypes) {
		return new BQModuleOverrideBuilder() {

			@Override
			public Bootique with(Class<? extends Module> moduleType) {

				providers.add(new BQModuleProvider() {

					@Override
					public Module module() {
						return createModule(moduleType);
					}

					@Override
					public Collection<Class<? extends Module>> overrides() {
						return Arrays.asList(overriddenTypes);
					}
				});

				return Bootique.this;
			}

			@Override
			public Bootique with(Module module) {
				providers.add(new BQModuleProvider() {

					@Override
					public Module module() {
						return module;
					}

					@Override
					public Collection<Class<? extends Module>> overrides() {
						return Arrays.asList(overriddenTypes);
					}
				});

				return Bootique.this;
			}
		};
	}

	/**
	 * Creates and returns an instance of {@link BQRuntime} that contains all
	 * Bootique services, commands, etc. This method is only needed if you need
	 * to run your code manually, process {@link CommandOutcome} or don't want
	 * Bootique to call {@link System#exit(int)}. Normally you should consider
	 * using {@link #run()} instead.
	 * 
	 * @since 0.13
	 * @return a new {@link BQRuntime} instance that contains all Bootique
	 *         services, commands, etc.
	 * @see Bootique#run()
	 */
	public BQRuntime createRuntime() {
		Injector injector = createInjector();
		return createRuntime(injector);
	}

	/**
	 * @deprecated since 0.13 in favor of {@link #createRuntime()}.
	 * @return a newly created runtime.
	 */
	@Deprecated
	public BQRuntime runtime() {
		return createRuntime();
	}

	/**
	 * Creates and runs {@link BQRuntime}, and processing its output. This
	 * method is a rough alternative to "runtime().getRunner().run().exit()". In
	 * most cases calling it would result in the current JVM process to
	 * terminate.
	 * <p>
	 * If you don't want your app to shutdown after executing Bootique, you may
	 * manually obtain {@link BQRuntime} by calling {@link #createRuntime()},
	 * and run it from your code without calling "exit()".
	 */
	public void run() {

		BQRuntime runtime = createRuntime();
		runtime.addJVMShutdownHook();

		CommandOutcome o = run(runtime);

		// report error
		if (!o.isSuccess()) {

			if (o.getMessage() != null) {
				runtime.getBootLogger().stderr(
						String.format("Error running command '%s': %s", runtime.getArgsAsString(), o.getMessage()));
			} else {
				runtime.getBootLogger().stderr(String.format("Error running command '%s'", runtime.getArgsAsString()));
			}

			if (o.getException() != null) {
				runtime.getBootLogger().stderr("Command exception", o.getException());
			}
		}

		o.exit();
	}

	protected CommandOutcome run(BQRuntime runtime) {
		try {
			return runtime.getRunner().run();
		}
		// handle startup Guice exceptions
		catch (ProvisionException e) {

			// TODO: a dependency on JOPT OptionException shouldn't be here
			return (e.getCause() instanceof OptionException) ? CommandOutcome.failed(1, e.getCause().getMessage())
					: CommandOutcome.failed(1, e);
		}
	}

	protected BQRuntime createRuntime(Injector injector) {
		return new BQRuntime(injector);
	}

	protected BootLogger createBootLogger() {
		return new DefaultBootLogger(System.getProperty(DefaultEnvironment.TRACE_PROPERTY) != null);
	}

	protected Injector createInjector() {

		Collection<BQModuleProvider> providers = new ArrayList<>();
		providers.add(coreModuleProvider());

		providers.addAll(builderProviders());

		if (autoLoadModules) {
			providers.addAll(autoLoadedProviders());
		}

		Collection<Module> modules = new ModuleMerger(bootLogger).getModules(providers);
		return Guice.createInjector(modules);
	}

	protected Collection<BQModuleProvider> builderProviders() {
		return providers;
	}

	protected BQModuleProvider coreModuleProvider() {
		return new BQModuleProvider() {

			@Override
			public Module module() {
				return BQCoreModule.builder().args(args).bootLogger(bootLogger).build();
			}

			@Override
			public String name() {
				return "Bootique";
			}
		};
	}

	protected Collection<BQModuleProvider> autoLoadedProviders() {
		Collection<BQModuleProvider> modules = new ArrayList<>();
		ServiceLoader.load(BQModuleProvider.class).forEach(p -> modules.add(p));
		return modules;
	}
}
