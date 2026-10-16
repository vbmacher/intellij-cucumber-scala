import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;
import java.util.concurrent.*;
import scala.Option;
import scala.runtime.AbstractFunction2;
import com.github.vbmacher.intellij.cucumber.scala.CucumberDebugContext$;

public class CucumberDebuggerProbe {
  public static void main(String[] args) throws Exception {
    var connector = Bootstrap.virtualMachineManager().defaultConnector();
    var arguments = connector.defaultArguments();
    arguments.get("options").setValue("-classpath \"" + args[0] + "\"");
    arguments.get("main").setValue("io.cucumber.core.cli.Main --threads " + args[2] + " --glue cucumber.examples.scalacalculator \"" + args[1] + "\"");
    var vm = connector.launch(arguments);
    try {
    var prepare = vm.eventRequestManager().createClassPrepareRequest();
    prepare.addClassFilter("cucumber.examples.scalacalculator.StepDefinitions");
    prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
    prepare.enable();
    int matched = 0;
    var rows = new TreeSet<Integer>();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
    boolean ended = false;
    while (!ended && System.nanoTime() < deadline) {
      var events = vm.eventQueue().remove(1000);
      if (events == null) continue;
      for (Event event: events) {
        if (event instanceof ClassPrepareEvent loaded) {
          for (var location: loaded.referenceType().locationsOfLine(14)) {
            var request = vm.eventRequestManager().createBreakpointRequest(location);
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
          }
        }
        if (event instanceof BreakpointEvent hit) {
          var location = CucumberDebugContext$.MODULE$.read(hit.thread(), new AbstractFunction2<ObjectReference,String,Option<Value>>() {
            public Option<Value> apply(ObjectReference receiver, String name) {
              try {
                var method = receiver.referenceType().methodsByName(name).stream().filter(m -> m.argumentTypeNames().isEmpty()).findFirst().orElseThrow();
                return Option.apply(receiver.invokeMethod(hit.thread(), method, List.of(), ObjectReference.INVOKE_SINGLE_THREADED));
              } catch(Exception e) { throw new RuntimeException(e); }
            }
          });
          if (location.nonEmpty()) {
            var observed = location.get();
            System.out.println("CONTEXT " + observed.uri() + ":step=" + observed.step() + ":example=" + observed.example());
            if (observed.step() != 6) throw new AssertionError("Wrong step");
            rows.add(observed.example());
            if (observed.example() == 12) matched++;
          }
        }
        if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) ended = true;
      }
      if (!ended) events.resume();
    }
    if (!ended) { vm.dispose(); throw new AssertionError("Timed out"); }
    if (!rows.equals(new TreeSet<>(List.of(11,12,13))) || matched != 1)
      throw new AssertionError("rows=" + rows + ", selected hits=" + matched);
    System.out.println("PASS (" + args[2] + " threads): shared implementation selected once for example 12; observed rows " + rows);
    } finally {
      try { vm.dispose(); } catch(VMDisconnectedException ignored) {}
      vm.process().destroyForcibly();
    }
  }
}
