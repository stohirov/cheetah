package core.filter;

import annotations.Order;
import core.handler.Handler;
import http.HttpRequest;
import http.HttpResponse;
import java.util.Comparator;
import java.util.List;

public class FilteringHandler implements Handler {

  private final List<Filter> filters;
  private final Handler target;

  public FilteringHandler(List<? extends Filter> filters, Handler target) {
    this.filters = filters.stream()
        .map(Filter.class::cast)
        .sorted(Comparator.comparingInt(FilteringHandler::order))
        .toList();
    this.target = target;
  }

  public List<Filter> filters() {
    return filters;
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    return proceed(0, request);
  }

  private HttpResponse proceed(int index, HttpRequest request) throws Exception {
    if (index == filters.size()) {
      return target.handle(request);
    }

    return filters.get(index).filter(request, next -> proceed(index + 1, next));
  }

  private static int order(Filter filter) {
    Order order = filter.getClass().getAnnotation(Order.class);

    return order == null ? 0 : order.value();
  }

}
