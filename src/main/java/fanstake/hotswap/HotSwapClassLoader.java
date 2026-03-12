package fanstake.hotswap;

import java.net.URLClassLoader;

public class HotSwapClassLoader extends URLClassLoader {

  // private static final Logger log = Logger.getLogger(HotSwapClassLoader.class.getName());

  private final String name;

  private final HotSwapResolver hotSwapResolver;

  static {
    registerAsParallelCapable();
  }

  public HotSwapClassLoader(String name, HotSwapResolver hotSwapResolver, ClassLoader parent) {
    super(HotSwapDirs.getWatchingPathsAsUrls(), parent);
    this.name = name;
    this.hotSwapResolver = hotSwapResolver;
  }

  public HotSwapClassLoader withParent(ClassLoader parent) {
    return new HotSwapClassLoader(name, hotSwapResolver, parent);
  }

  public HotSwapResolver getHotSwapResolver() {
    return hotSwapResolver;
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // log.debug("%s loadClass: %s, resolve=%s", this.name, name, resolve);

    synchronized (getClassLoadingLock(name)) {
      // First, check if the class has already been loaded
      var c = findLoadedClass(name);
      if (c != null) {
        // log.info("%s isLoaded: %s", this.name, name);
        return c;
      }

      if (hotSwapResolver.isSystemClass(name)) {
        // log.info("%s isSystemClass: %s", this.name, name);
        return super.loadClass(name, resolve);
      }

      if (hotSwapResolver.isHotSwapClass(name)) {
        // log.info("%s isHotSwapClass: %s".formatted(this.name, name));
        c = super.findClass(name);
        if (c != null) {
          // log.info("%s foundClass: %s".formatted(this.name, name));
          if (resolve) {
            // log.info("%s resolveClass: %s".formatted(this.name, name));
            resolveClass(c);
          }
          return c;
        } else {
          throw new ClassNotFoundException("HotSwap class not found: " + name);
        }
      }

      // log.info("%s parent.loadClass: %s, %s".formatted(this.name, getParent().toString(), name));
      return super.loadClass(name, resolve);
    }
  }

  @Override
  public String toString() {
    return name + "#" + System.identityHashCode(this);
  }

  @SuppressWarnings({ "unchecked", "UseSpecificCatch" })
  public static <T> T newInstance(String className) {
    try {
      final var constructor = Class.forName(className, true, Thread.currentThread().getContextClassLoader())
          .getConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to start child", e);
    }

  }

}
