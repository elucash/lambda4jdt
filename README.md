lambda4jdt
==========

2012 version of lambda4jdt. Eclipse plugin to fold anonymous inner classes as lambdas in Java Editor.

Original description on http://code.google.com/p/lambda4jdt/.
Goals:
* Support for Eclipse Indigo 3.7.2
* Remove need for marker comment, simplify to one-size fits all
* No further development planned, no features, no Juno support etc

Install plugins and then goto
Preferences->Java->Editor->Folding, select folding to use "Lambda4jdt Folding".
Any newly opened Java editor will pickup lambda4jdt

Examples
<pre>
Executor e =...
e.execute {
   doSomething(a);
}
final int i = 1;//you still need final to access i in function
h.invoke(obj, () doSomethingWithIn(i));
Arrays.sort(array, (o1, o2) o1.hashCode() - o2.hashCode());

//Nested closure constructs are supported to some degree
interface Provider&lt;T> {
  T get(Object context);
}

public Provider&lt;Provider&lt;Provider&lt;String>>> myProvider() {
  return new Provider&lt;Provider&lt;Provider&lt;String>>>() {
    public Provider&lt;Provider&lt;String>> get(Object c) {
      return new Provider&lt;Provider&lt;String>>() {
        public Provider&lt;String> get(Object c) {
          return new Provider&lt;String>() {
            public String get(Object c) {
              return "MyFavoriteStringFactory" + c;
            }
          };
        }
      };
    }
  };
}
//....
// Which may be collapsed to..
public Provider&lt;Provider&lt;Provider&lt;String>>> myProvider() {
  return (c) (c) (c) "MyFavoriteStringFactory" + c;
}
</pre>