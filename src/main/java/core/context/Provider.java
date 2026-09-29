package core.context;

@FunctionalInterface
public interface Provider<T> {

  T get();

}
