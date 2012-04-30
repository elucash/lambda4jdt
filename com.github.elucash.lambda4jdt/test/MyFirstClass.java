package sendx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Евгений Лукаш
 */
public class MyFirstClass {

	@Retention(RetentionPolicy.SOURCE)
	@Target(ElementType.METHOD)
	@interface LambdaFolded {}
	
	@Retention(RetentionPolicy.SOURCE)
	@Target(ElementType.METHOD)
	@interface ClauseFolded {}

	interface Fun<T> {
		T get(Object it, int times);
	}

	interface Fun1 {
		void get(Map<String, String> it, int times);
	}

	interface Provider<T> {
		T get(int c);
	}

	public Provider<Provider<Provider<String>>> myProvider() {
		return new Provider<Provider<Provider<String>>>() {
			public Provider<Provider<String>> get(int c)/* => */{
				return new Provider<Provider<String>>() {
					public Provider<String> get(int c)/* => */{
						return new Provider<String>() {
							public String get(int c)/* => */{
								return "MyFavoriteStringFactory" + c;
							}
						};
					}
				};
			}
		};
	}

	Provider<Provider<Provider<String>>> myProvider = new Provider<Provider<Provider<String>>>() {
		public Provider<Provider<String>> get(int c)/* => */{
			return new Provider<Provider<String>>() {
				public Provider<String> get(int c)/* => */{
					return new Provider<String>() {
						public String get(int c)/* => */{
							return "MyFavoriteStringFactory" + c;
						}
					};
				}
			};
		}
	};

	public static <T> ExecutorService reg(Callable<Void> r) {
		return null;
	}

	public static void _executeSomeAction(int when) {

	}

	public static Runnable execute(Fun1 when) {
		return null;
	}

	public static void main(String... args) throws Exception {

		final Fun<String> function = new Fun<String>() {
			public String get(Object itIs, int times)// => //
			{
				String string = itIs.toString();
				return string.toLowerCase();
			}
		};

		String[] array = {};

		Arrays.sort(array, new Comparator<Object>() {
			public int compare(Object o1, Object o2)/* => */{
				return o1.hashCode() - o2.hashCode();
			}
		});

		final boolean a = true, b = true, c = true, d = true;
		MyFirstClass.reg(new Callable<Void>() {
			public Void call()/* => */throws Exception {
				return b | a ? null : null;
			}
		}).submit(new Callable<Integer>() {
			public Integer call()/* => */throws Exception {
				return c || d ? 22 : (int) 33232d;
			}
		});

		ExecutorService es = Executors.newCachedThreadPool();

		Future<Integer> future = es.submit(new Callable<Integer>() {
			public Integer call()/* => */throws Exception {
				return 1 + 4 + 2;
			}
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		final String[] array = { "java", "in", "disguise") };
		executor.execute(new Runnable() {
			public void run()/* => */{
				Arrays.sort(array, new Comparator<CharSequence>() {
					public int compare(CharSequence o1, CharSequence o2)/* => */{
						return o1.length() - o2.length();
					}
				});
				System.out.println(Arrays.toString(array));
			}
		});

		es.submit(new Runnable() {
			public void run()/* => */{
				_executeSomeAction(3);
			}
		});

		execute(new Fun1() {
			public void get(Map<String, List<List<String>>> it, int times)/* => */{
				if (times > 0) {
					it.toString();
					Arrays.asList((Object) null);
				}
			}
		});

		for (String string : new String[1]) {
			System.out.println("Fuck!!!" + string);
		}
		System.out.println(function + future.get().toString());
	}
}
