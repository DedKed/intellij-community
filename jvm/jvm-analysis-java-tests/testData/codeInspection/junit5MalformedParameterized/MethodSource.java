import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MethodSourcePositive {
  @ParameterizedTest
  @MethodSource("stream")
  void simpleStream(int x, int y) {
    System.out.println(x + ", " + y);
  }

  @ParameterizedTest
  @MethodSource("iterable")
  void simpleIterable(int x, int y) {
    System.out.println(x + ", " + y);
  }

  @ParameterizedTest
  @MethodSource("iterator")
  void simpleIterator(int x, int y) {
    System.out.println(x + ", " + y);
  }

  @ParameterizedTest
  @MethodSource(value = {"stream", "iterator", "iterable"})
  void parametersArray(int x, int y) {
    System.out.println(x + ", " + y);
  }

  @ParameterizedTest
  @MethodSource({"stream", "iterator"})
  void implicitValueArray(int x, int y) {
    System.out.println(x + ", " + y);
  }

  @ParameterizedTest
  @MethodSource(value = "argumentsArrayProvider")
  void argumentsArray(int x, String s) {
    System.out.println(x + ", " + s);
  }

  @ParameterizedTest
  @MethodSource(value = "objectsArrayProvider")
  void objectsArray(int x, String s) {
    System.out.println(x + ", " + s);
  }

  @ParameterizedTest
  @MethodSource(value = "objects2DArrayProvider")
  void objects2DArray(int x, String s) {
    System.out.println(x + ", " + s);
  }

  @ParameterizedTest
  @MethodSource("doubleStreamProvider")
  void objectdoubleStreamsArray(double d) {
    System.out.println(d);
  }

  @ParameterizedTest
  @MethodSource("intStreamProvider")
  void intStream(int x) {
    System.out.println(x);
  }

  @ParameterizedTest
  @MethodSource("longStreamProvider")
  void longStream(long l) {
    System.out.println(l);
  }

  @ParameterizedTest
  @MethodSource("intStreamProvider")
  void injectTestInfo(int x, TestInfo testInfo) {
    System.out.println(x);
  }

  @ParameterizedTest
  @MethodSource("intStreamProvider")
  void injectTestReporter(int x, TestReporter testReporter) {
    System.out.println(x);
  }

  static Stream<Arguments> stream() {
    return null;
  }

  static Iterator<Arguments> iterator() {
    return null;
  }

  static Iterable<Arguments> iterable() {
    return null;
  }

  static Arguments[] argumentsArrayProvider() {
    return new Arguments[]{Arguments.of(1, "one")};
  }

  static Object[] objectsArrayProvider() {
    return new Object[]{Arguments.of(1, "one")};
  }

  static Object[][] objects2DArrayProvider() {
    return new Object[][]{{1, "s"}};
  }

  static DoubleStream doubleStreamProvider() {return null;}

  static IntStream intStreamProvider() {return null;}

  static LongStream longStreamProvider() {return null;}
}

class MethodSourceMalformed {
  @ParameterizedTest
  @MethodSource(value = { <warning descr="Method source 'a' must be static">"a"</warning>,
    <warning descr="Method source 'b' should have no parameters">"b"</warning>,
    <warning descr="Method source 'c' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">"c"</warning>,
    "d", 
    "MethodSourceMalformed.Foreign#e"})
  void testWithParams(Object s) { }

  @ParameterizedTest
  @MethodSource(value = {<warning descr="Cannot resolve target method source: 'unknown'">"unknown"</warning>})
  void unknownMethodSource(String s, int i) { }

  String[] a() {
    return new String[]{"a", "b"};
  }

  static String[] b(int i) {
    return new String[]{"a", "b"};
  }

  static Object c() {
    return new String[]{"a", "b"};
  }

  static Object[] d() {
    return new String[]{"a", "b"};
  }

  static class Foreign {
    static Object[] e() {
      return new String[]{"a", "b"};
    }
  }
}

interface MyInterface {
  String[] data();

  default List<String> methodSample() {
    return null;
  }

  @ParameterizedTest
  @MethodSource("methodSample")
  default void test(String value) { }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MyTestBaseClass implements MyInterface {
    @ParameterizedTest
    @MethodSource("data")
    void myTest(String param) { }
}

abstract class FooTwo {

  @ParameterizedTest
  @MethodSource("method1")
  void test(String value) {}
  List<String> method1() {
    return null;
  }
}

class BarTestT extends FooTwo {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarTestTwo extends FooTwo {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarTestThree extends FooThree {
  @Override
  List<String> method1() {
    return null;
  }
}

abstract class FooThree {

  @ParameterizedTest
  @MethodSource("method1")
  void test(String value) {}

  abstract List<String> method1();
}

class SomeTest extends Base {

  @ParameterizedTest
  @MethodSource
  void testSomething(String content) {}
}

class SomeTestTwo implements Inter {

  @ParameterizedTest
  @MethodSource
  void testSomething(String content) {}
}

interface Inter extends Inter2{ }

interface Inter2 {
  static Stream<String> testSomething() {
    return Stream.of("a", "aa");
  }
}


class Base {
  static Stream<String> testSomething() {
    return Stream.of("a", "aa");
  }
}


abstract class AbstrClass {
  @ParameterizedTest
  @MethodSource("provideValues")
  void checkValues(String value) {}
}

class FooTest extends AbstrClass {
  static Stream<String> provideValues() {
    return Stream.of("A", "B", "C");
  }
}

interface FooInterface {

  @ParameterizedTest
  @MethodSource("provideValues")
  default void checkValues(String value) {}
}

class FooTestTwo implements FooInterface {
  static Stream<String> provideValues() {
    return Stream.of("A", "B", "C");
  }
}