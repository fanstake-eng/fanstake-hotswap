package fanstake.hotswap;

public interface ServiceBuilder {

  public String serviceStartClassname();

  public HotSwapClassLoader getClassLoader(ClassLoader parent);
}
