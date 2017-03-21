import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findVirtual(Test.class, "method", <caret>MethodType.methodType(String.class));
  }
}

class Test {
  public void method() {}
  public String method(String a) {return a;}
  public String method(String a, String... b) {return a;}
}