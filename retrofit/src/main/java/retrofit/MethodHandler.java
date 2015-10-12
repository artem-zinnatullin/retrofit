/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit;

import com.squareup.okhttp.ResponseBody;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

final class MethodHandler<T> {
  @SuppressWarnings("unchecked")
  static MethodHandler<?> create(Retrofit retrofit, Method method) {
    CallAdapter<Object> callAdapter = (CallAdapter<Object>) createCallAdapter(method, retrofit);
    Type responseType = callAdapter.responseType();
    Converter<ResponseBody, Object> responseConverter =
        (Converter<ResponseBody, Object>) createResponseConverter(method, retrofit, responseType);
    RequestFactory requestFactory = RequestFactoryParser.parse(method, responseType, retrofit);
    String infoForException = collectInfoForException(method);
    return new MethodHandler<>(retrofit, requestFactory, callAdapter, responseConverter,
        infoForException);
  }

  private static CallAdapter<?> createCallAdapter(Method method, Retrofit retrofit) {
    Type returnType = method.getGenericReturnType();
    if (Utils.hasUnresolvableType(returnType)) {
      throw Utils.methodError(method,
          "Method return type must not include a type variable or wildcard: %s", returnType);
    }
    if (returnType == void.class) {
      throw Utils.methodError(method, "Service methods cannot return void.");
    }
    Annotation[] annotations = method.getAnnotations();
    try {
      return retrofit.callAdapter(returnType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create call adapter for %s", returnType);
    }
  }

  private static Converter<ResponseBody, ?> createResponseConverter(Method method,
      Retrofit retrofit, Type responseType) {
    Annotation[] annotations = method.getAnnotations();
    try {
      return retrofit.responseConverter(responseType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create converter for %s", responseType);
    }
  }

  private static String collectInfoForException(Method method) {
    Annotation[] annotations = method.getAnnotations();

    String httpMethod = null;
    String relativePathTemplate = null;

    for (Annotation annotation : annotations) {
      Map.Entry<String, String> httpMethodAndPathTemplate
          = Utils.parseHttpMethodAndRelativePathTemplate(annotation);

      if (httpMethodAndPathTemplate != null) {
        httpMethod = httpMethodAndPathTemplate.getKey();
        relativePathTemplate = httpMethodAndPathTemplate.getValue();
        break;
      }
    }

    return method.getDeclaringClass().getSimpleName()
        + "."
        + method.getName()
        + "()"
        + ", HTTP method = "
        + httpMethod
        + ", relative path template = "
        + relativePathTemplate;
  }



  private final Retrofit retrofit;
  private final RequestFactory requestFactory;
  private final CallAdapter<T> callAdapter;
  private final Converter<ResponseBody, T> responseConverter;

  // Should never include sensitive data such as query params, headers and so on.
  private final String infoForException;

  private MethodHandler(Retrofit retrofit, RequestFactory requestFactory,
      CallAdapter<T> callAdapter, Converter<ResponseBody, T> responseConverter,
      String infoForException) {
    this.retrofit = retrofit;
    this.requestFactory = requestFactory;
    this.callAdapter = callAdapter;
    this.responseConverter = responseConverter;
    this.infoForException = infoForException;
  }

  Object invoke(Object... args) {
    return callAdapter.adapt(new OkHttpCall<>(retrofit, requestFactory, responseConverter, args,
        infoForException));
  }
}
