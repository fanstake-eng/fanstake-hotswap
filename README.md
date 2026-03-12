# fanstake-hotswap

A hotswapping classloader for fast development iteration in Java — no JVM restart required.

## How it works

`HotSwapWatcher` monitors the classpath for `.class` file changes. When a change is detected, it tears down the affected services and restarts them with a fresh classloader, reloading only the designated hotswap classes while keeping stable/system classes in the parent classloader.

Services are arranged in a **layered stack**: each layer has its own `HotSwapClassLoader` and `Lifecycle` instance. When a class changes, only the affected layer and everything above it is restarted.

## Key classes

| Class / Interface | Role |
|---|---|
| `HotSwapWatcher` | Watches classpath dirs for changes, manages the service lifecycle |
| `HotSwapClassLoader` | Custom `URLClassLoader` that loads hotswap classes fresh from disk |
| `HotSwapResolver` | Decides which classes are hotswapped vs. delegated to the parent |
| `HotSwapDirs` | Discovers watchable directories from the JVM classpath |
| `Lifecycle` | Interface your service implements (`start` / `stop`) |
| `ServiceBuilder` | Factory that creates a classloader and names the entry-point class |

## Usage

1. Implement `Lifecycle` in your service entry-point class:

```java
public class MyServer implements Lifecycle {
    @Override public void start(Object parentService) { /* ... */ }
    @Override public void stop(Object parentService)  { /* ... */ }
}
```

2. Create a `ServiceBuilder` and start the watcher:

```java
var builder = new ServiceBuilder() {
    @Override public String serviceStartClassname() {
        return "com.example.MyServer";
    }
    @Override public HotSwapClassLoader getClassLoader(ClassLoader parent) {
        var resolver = new HotSwapResolver();
        resolver.addHotSwapClassPrefix("com.example.");
        return new HotSwapClassLoader("MyServer", resolver, parent);
    }
};

var watcher = new HotSwapWatcher(Main.class.getClassLoader(), builder);
watcher.start();
```

3. Recompile your classes (e.g. with `mvn compile` or your IDE's incremental compiler). The watcher picks up changes automatically and restarts just what changed.

Multiple layers can be composed — e.g. a parent service and a child service each with independent hotswap scopes (see `src/test/java/fanstake/testapp/Main.java`).

## Build & run

```sh
# Build
mvn package

# Run the test app
java -cp target/classes:target/test-classes fanstake.testapp.Main

# Trigger a reload (simulates a recompile)
just touch-parent   # reload parent layer
just touch-child    # reload child layer
```

> Requires Java 21+. No runtime dependencies.

## License

Apache 2.0
