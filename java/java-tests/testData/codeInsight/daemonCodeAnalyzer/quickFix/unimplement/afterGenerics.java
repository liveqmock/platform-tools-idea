// "Unimplement Interface" "true"
class A {
  public String toString() {
    return super.toString();
  }

}

interface II<T> {
  void foo(T ty);
}