package core.routing;

import annotations.PathVariable;
import annotations.ReqBody;
import annotations.ReqParam;
import convert.StringConverter;
import http.HttpException;
import http.HttpRequest;
import http.HttpStatus;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import json.Json;
import json.JsonException;

@FunctionalInterface
interface ArgumentBinder {

  Object bind(HttpRequest request, Map<String, String> pathVariables);

  static ArgumentBinder forParameter(Parameter parameter, PathPattern pattern) {
    if (parameter.getType() == HttpRequest.class) {
      return (request, pathVariables) -> request;
    }

    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable != null) {
      return pathVariableBinder(parameter, pathVariable, pattern);
    }

    ReqParam reqParam = parameter.getAnnotation(ReqParam.class);
    if (reqParam != null) {
      return requestParamBinder(parameter, reqParam);
    }

    if (parameter.isAnnotationPresent(ReqBody.class)) {
      return requestBodyBinder(parameter);
    }

    throw new IllegalArgumentException("Parameter '" + parameter.getName() + "' of "
        + describe(parameter) + " needs @PathVariable, @ReqParam or @ReqBody");
  }

  private static ArgumentBinder pathVariableBinder(
      Parameter parameter, PathVariable annotation, PathPattern pattern) {
    String name = resolveName(parameter, annotation.value(), annotation.name());
    Class<?> type = requireConvertible(parameter, parameter.getType());

    if (!pattern.hasVariable(name)) {
      throw new IllegalArgumentException(
          "Path " + pattern + " has no variable '" + name + "' used by " + describe(parameter));
    }

    return (request, pathVariables) ->
        convert(pathVariables.get(name), type, "path variable '" + name + "'");
  }

  private static ArgumentBinder requestParamBinder(Parameter parameter, ReqParam annotation) {
    String name = resolveName(parameter, annotation.value(), annotation.paramName());
    boolean optional = parameter.getType() == Optional.class;
    Class<?> type = requireConvertible(parameter,
        optional ? optionalElementType(parameter) : parameter.getType());

    if (!optional && !annotation.required() && type.isPrimitive()) {
      throw new IllegalArgumentException(
          "Optional query parameter '" + name + "' cannot be primitive in " + describe(parameter));
    }

    String description = "query parameter '" + name + "'";

    return (request, pathVariables) -> {
      Optional<Object> value = request.queryParam(name)
          .map(raw -> convert(raw, type, description));

      if (optional) {
        return value;
      }

      if (value.isEmpty() && annotation.required()) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Missing " + description);
      }

      return value.orElse(null);
    };
  }

  private static ArgumentBinder requestBodyBinder(Parameter parameter) {
    Class<?> type = parameter.getType();
    Type genericType = parameter.getParameterizedType();

    if (type == byte[].class) {
      return (request, pathVariables) -> request.body();
    }

    if (type == String.class) {
      return (request, pathVariables) -> request.bodyAsString();
    }

    return (request, pathVariables) -> {
      String body = request.bodyAsString();
      if (body.isBlank()) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Request body is required");
      }

      try {
        return Json.read(body, genericType);
      } catch (JsonException e) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid request body: " + e.getMessage(),
            e);
      }
    };
  }

  private static Object convert(String value, Class<?> type, String description) {
    try {
      return StringConverter.convert(value, type);
    } catch (IllegalArgumentException e) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid " + description + ": " + value, e);
    }
  }

  private static String resolveName(Parameter parameter, String... candidates) {
    for (String candidate : candidates) {
      if (!candidate.isBlank()) {
        return candidate;
      }
    }

    if (!parameter.isNamePresent()) {
      throw new IllegalArgumentException("Cannot infer a name for a parameter of "
          + describe(parameter) + "; name it explicitly or compile with -parameters");
    }

    return parameter.getName();
  }

  private static Class<?> requireConvertible(Parameter parameter, Class<?> type) {
    if (!StringConverter.supports(type)) {
      throw new IllegalArgumentException(
          "Unsupported parameter type " + type.getName() + " in " + describe(parameter));
    }

    return type;
  }

  private static Class<?> optionalElementType(Parameter parameter) {
    if (parameter.getParameterizedType() instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
      return element;
    }

    throw new IllegalArgumentException("Optional needs a concrete type in " + describe(parameter));
  }

  private static String describe(Parameter parameter) {
    var executable = parameter.getDeclaringExecutable();

    return executable.getDeclaringClass().getSimpleName() + "." + executable.getName();
  }

}
