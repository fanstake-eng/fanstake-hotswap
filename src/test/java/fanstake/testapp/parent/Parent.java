package fanstake.testapp.parent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Warning: This class shouldn't hold any direct reference to the `Child` class.
 * (Except for generics, which are erased at runtime)
 */
public class Parent {

  private static final Logger log = Logger.getLogger(Parent.class.getName());

  private DoSomething child;

  private final CountDownLatch childInitialized = new CountDownLatch(1);

  public void setChild(DoSomething child) {
    log.fine("Setting child: %s".formatted(child));
    this.child = child;
    childInitialized.countDown();
  }

  public void doSomething() {
    try {
      log.info("Parent doSomething()");
      if (childInitialized.await(2, TimeUnit.SECONDS)) {
        child.doSomething();
      } else {
        log.warning("Child initialization timed out.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
