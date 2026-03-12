package fanstake.testapp.child;

import java.util.logging.Logger;

import fanstake.hotswap.Lifecycle;
import fanstake.testapp.Server;
import fanstake.testapp.parent.DoSomething;

public class Child implements DoSomething, Lifecycle {

  private static final Logger log = Logger.getLogger(Child.class.getName());

  @Override
  public void doSomething() {
    log.info(() -> "Child doSomething() " + Child.class.hashCode());
  }

  @Override
  public void start(Object parentService) {
    log.info("Starting child service");
    ((Server) parentService).setChild(this);
  }

  @Override
  public void stop(Object parentService) {
    log.info("Stopping child service");
    ((Server) parentService).setChild(null);
  }
}
