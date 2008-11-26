/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This is the main class for using Gson. Gson is typically used by first constructing a
 * Gson instance and then invoking {@link #toJson(Object)} or {@link #fromJson(String, Class)}
 * methods on it.
 *
 * <p>You can create a Gson instance by invoking {@code new Gson()} if the default configuration
 * is all you need. You can also use {@link GsonBuilder} to build a Gson instance with various
 * configuration options such as versioning support, pretty printing, custom
 * {@link JsonSerializer}s, {@link JsonDeserializer}s, and {@link InstanceCreator}s.</p>
 *
 * <p>Here is an example of how Gson is used for a simple Class:
 *
 * <pre>
 * Gson gson = new Gson(); // Or use new GsonBuilder().create();
 * MyType target = new MyType();
 * String json = gson.toJson(target); // serializes target to Json
 * MyType target2 = gson.fromJson(json, MyType.class); // deserializes json into target2
 * </pre></p>
 *
 * <p>If the object that your are serializing/deserializing is a {@code ParameterizedType}
 * (i.e. contains at least one type parameter and may be an array) then you must use the
 * {@link #toJson(Object, Type)} or {@link #fromJson(String, Type)} method.  Here is an
 * example for serializing and deserialing a {@code ParameterizedType}:
 *
 * <pre>
 * Type listType = new TypeToken<List<String>>() {}.getType();
 * List<String> target = new LinkedList<String>();
 * target.add("blah");
 *
 * Gson gson = new Gson();
 * String json = gson.toJson(target, listType);
 * List<String> target2 = gson.fromJson(json, listType);
 * </pre></p>
 *
 * <p>See the <a href="https://sites.google.com/site/gson/gson-user-guide">Gson User Guide</a>
 * for a more complete set of examples.</p>
 *
 * @see com.google.gson.reflect.TypeToken
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
public final class Gson {

  //TODO(inder): get rid of all the registerXXX methods and take all such parameters in the
  // constructor instead. At the minimum, mark those methods private.

  private static final String NULL_STRING = "null";
  // Default instances of plug-ins
  static final ModifierBasedExclusionStrategy DEFAULT_MODIFIER_BASED_EXCLUSION_STRATEGY =
      new ModifierBasedExclusionStrategy(true, new int[] { Modifier.TRANSIENT, Modifier.STATIC });
  static final JsonFormatter DEFAULT_JSON_FORMATTER = new JsonCompactFormatter();
  static final FieldNamingStrategy DEFAULT_NAMING_POLICY =
      new SerializedNameAnnotationInterceptingNamingPolicy(new JavaFieldNamingPolicy());

  static final Logger logger = Logger.getLogger(Gson.class.getName());

  private final ExclusionStrategy strategy;
  private final FieldNamingStrategy fieldNamingPolicy;
  private final MappedObjectConstructor objectConstructor;

  /** Map containing Type or Class objects as keys */
  private final ParameterizedTypeHandlerMap<JsonSerializer<?>> serializers;

  /** Map containing Type or Class objects as keys */
  private final ParameterizedTypeHandlerMap<JsonDeserializer<?>> deserializers;

  private final JsonFormatter formatter;
  private final boolean serializeNulls;

  /**
   * Constructs a Gson object with default configuration. The default configuration has the
   * following settings:
   * <ul>
   *   <li>The JSON generated by <code>toJson</code> methods is in compact representation. This
   *   means that all the unneeded white-space is removed. You can change this behavior with
   *   {@link GsonBuilder#setPrettyPrinting()}. </li>
   *   <li>The generated JSON omits all the fields that are null. Note that nulls in arrays are
   *   kept as is since an array is an ordered list. Moreover, if a field is not null, but its
   *   generated JSON is empty, the field is kept. You can configure Gson to serialize null values
   *   by setting {@link GsonBuilder#serializeNulls()}.</li>
   *   <li>Gson provides default serialization and deserialization for Enums, {@link Map},
   *   {@link java.net.URL}, {@link java.net.URI}, {@link java.util.Locale}, {@link java.util.Date},
   *   {@link java.math.BigDecimal}, and {@link java.math.BigInteger} classes. If you would prefer
   *   to change the default representation, you can do so by registering a type adapter through
   *   {@link GsonBuilder#registerTypeAdapter(Type, Object)}. </li>
   *   <li>The default Date format is same as {@link java.text.DateFormat#DEFAULT}. This format 
   *   ignores the millisecond portion of the date during serialization. You can change
   *   this by invoking {@link GsonBuilder#setDateFormat(int)} or
   *   {@link GsonBuilder#setDateFormat(String)}. </li>
   *   <li>By default, Gson ignores the {@link com.google.gson.annotations.Expose} annotation.
   *   You can enable Gson to serialize/deserialize only those fields marked with this annotation
   *   through {@link GsonBuilder#excludeFieldsWithoutExposeAnnotation()}. </li>
   *   <li>By default, Gson ignores the {@link com.google.gson.annotations.Since} annotation. You
   *   can enable Gson to use this annotation through {@link GsonBuilder#setVersion(double)}.</li>
   *   <li>The default field naming policy for the output Json is same as in Java. So, a Java class
   *   field <code>versionNumber</code> will be output as <code>&quot;versionNumber@quot;</code> in
   *   Json. The same rules are applied for mapping incoming Json to the Java classes. You can
   *   change this policy through {@link GsonBuilder#setFieldNamingPolicy(FieldNamingPolicy)}.</li>
   *   <li>By default, Gson excludes <code>transient</code> or <code>static</code> fields from
   *   consideration for serialization and deserialization. You can change this behavior through
   *   {@link GsonBuilder#excludeFieldsWithModifiers(int...)}.</li>
   * </ul>
   */
  public Gson() {
    this(createExclusionStrategy(VersionConstants.IGNORE_VERSIONS), DEFAULT_NAMING_POLICY);
  }

  /**
   * Constructs a Gson object with the specified version and the mode of operation while
   * encountering inner class references.
   */
  Gson(ExclusionStrategy strategy, FieldNamingStrategy fieldNamingPolicy) {
    this(strategy, fieldNamingPolicy, createObjectConstructor(DefaultTypeAdapters.DEFAULT_INSTANCE_CREATORS),
        DEFAULT_JSON_FORMATTER, false,
        DefaultTypeAdapters.DEFAULT_SERIALIZERS, DefaultTypeAdapters.DEFAULT_DESERIALIZERS);
  }

  Gson(ExclusionStrategy strategy, FieldNamingStrategy fieldNamingPolicy, 
      MappedObjectConstructor objectConstructor, JsonFormatter formatter, boolean serializeNulls,
      ParameterizedTypeHandlerMap<JsonSerializer<?>> serializers,
      ParameterizedTypeHandlerMap<JsonDeserializer<?>> deserializers) {
    this.strategy = strategy;
    this.fieldNamingPolicy = fieldNamingPolicy;
    this.objectConstructor = objectConstructor;
    this.formatter = formatter;
    this.serializeNulls = serializeNulls;
    this.serializers = serializers;
    this.deserializers = deserializers;
  }

  static MappedObjectConstructor createObjectConstructor(
      ParameterizedTypeHandlerMap<InstanceCreator<?>> instanceCreators) {
    MappedObjectConstructor objectConstructor = new MappedObjectConstructor();
    for (Map.Entry<Type, InstanceCreator<?>> entry : instanceCreators.entrySet()) {
      objectConstructor.register(entry.getKey(), entry.getValue());
    }
    return objectConstructor;
  }

  private ObjectNavigatorFactory createDefaultObjectNavigatorFactory() {
    return new ObjectNavigatorFactory(strategy, fieldNamingPolicy);
  }

  private static ExclusionStrategy createExclusionStrategy(double version) {
    List<ExclusionStrategy> strategies = new LinkedList<ExclusionStrategy>();
    strategies.add(new InnerClassExclusionStrategy());
    strategies.add(DEFAULT_MODIFIER_BASED_EXCLUSION_STRATEGY);
    if (version != VersionConstants.IGNORE_VERSIONS) {
      strategies.add(new VersionExclusionStrategy(version));
    }
    return new DisjunctionExclusionStrategy(strategies);
  }

  /**
   * This method serializes the specified object into its equivalent Json representation.
   * This method should be used when the specified object is not a generic type. This method uses
   * {@link Class#getClass()} to get the type for the specified object, but the
   * {@code getClass()} loses the generic type information because of the Type Erasure feature
   * of Java. Note that this method works fine if the any of the object fields are of generic type,
   * just the object itself should not be of a generic type. If the object is of generic type, use
   * {@link #toJson(Object, Type)} instead. If you want to write out the object to a
   * {@link Writer}, use {@link #toJson(Object, Appendable)} instead.
   *
   * @param src the object for which Json representation is to be created setting for Gson
   * @return Json representation of {@code src}.
   */
  public String toJson(Object src) {
    if (src == null) {
      return serializeNulls ? NULL_STRING : "";
    }
    return toJson(src, src.getClass());
  }

  /**
   * This method serializes the specified object, including those of generic types, into its
   * equivalent Json representation. This method must be used if the specified object is a generic
   * type. For non-generic objects, use {@link #toJson(Object)} instead. If you want to write out
   * the object to a {@link Appendable}, use {@link #toJson(Object, Type, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link com.google.gson.reflect.TypeToken} class. For example,
   * to get the type for {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @return Json representation of {@code src}
   */
  public String toJson(Object src, Type typeOfSrc) {
    StringWriter writer = new StringWriter();
    toJson(src, typeOfSrc, writer);
    return writer.toString();
  }

  /**
   * This method serializes the specified object into its equivalent Json representation.
   * This method should be used when the specified object is not a generic type. This method uses
   * {@link Class#getClass()} to get the type for the specified object, but the
   * {@code getClass()} loses the generic type information because of the Type Erasure feature
   * of Java. Note that this method works fine if the any of the object fields are of generic type,
   * just the object itself should not be of a generic type. If the object is of generic type, use
   * {@link #toJson(Object, Type, Appendable)} instead.
   *
   * @param src the object for which Json representation is to be created setting for Gson
   * @param writer Writer to which the Json representation needs to be written
   * @since 1.2
   */
  public void toJson(Object src, Appendable writer) {
    try {
      if (src != null) {
        toJson(src, src.getClass(), writer);
      } else if (serializeNulls) {
        writeOutNullString(writer);
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * This method serializes the specified object, including those of generic types, into its
   * equivalent Json representation. This method must be used if the specified object is a generic
   * type. For non-generic objects, use {@link #toJson(Object, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link com.google.gson.reflect.TypeToken} class. For example,
   * to get the type for {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @param writer Writer to which the Json representation of src needs to be written.
   * @since 1.2
   */
  public void toJson(Object src, Type typeOfSrc, Appendable writer) {
    try {
      if (src != null) {
        JsonSerializationContext context = new JsonSerializationContextDefault(
            createDefaultObjectNavigatorFactory(), serializeNulls, serializers);
        JsonElement jsonElement = context.serialize(src, typeOfSrc);

        //TODO(Joel): instead of navigating the "JsonElement" inside the formatter, do it here.
        formatter.format(jsonElement, writer, serializeNulls);
      } else {
        if (serializeNulls) {
          writeOutNullString(writer);
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * This method deserializes the specified Json into an object of the specified class. It is not
   * suitable to use if the specified class is a generic type since it will not have the generic
   * type information because of the Type Erasure feature of Java. Therefore, this method should not
   * be used if the desired type is a generic type. Note that this method works fine if the any of
   * the fields of the specified object are generics, just the object itself should not be a
   * generic type. For the cases when the object is of generic type, invoke
   * {@link #fromJson(String, Type)}. If you have the Json in a {@link Reader} instead of
   * a String, use {@link #fromJson(Reader, Class)} instead.
   *
   * @param <T> the type of the desired object
   * @param json the string from which the object is to be deserialized
   * @param classOfT the class of T
   * @return an object of type T from the string
   * @throws JsonParseException if json is not a valid representation for an object of type
   * classOfT
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(String json, Class<T> classOfT) throws JsonParseException {
    T target = (T) fromJson(json, (Type) classOfT);
    return target;
  }

  /**
   * This method deserializes the specified Json into an object of the specified type. This method
   * is useful if the specified object is a generic type. For non-generic objects, use
   * {@link #fromJson(String, Class)} instead. If you have the Json in a {@link Reader} instead of
   * a String, use {@link #fromJson(Reader, Type)} instead.
   *
   * @param <T> the type of the desired object
   * @param json the string from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src. You can obtain this type by using the
   * {@link com.google.gson.reflect.TypeToken} class. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @return an object of type T from the string
   * @throws JsonParseException if json is not a valid representation for an object of type typeOfT
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(String json, Type typeOfT) throws JsonParseException {
    StringReader reader = new StringReader(json);
    T target = (T) fromJson(reader, typeOfT);
    return target;
  }

  /**
   * This method deserializes the Json read from the specified reader into an object of the
   * specified class. It is not suitable to use if the specified class is a generic type since it
   * will not have the generic type information because of the Type Erasure feature of Java.
   * Therefore, this method should not be used if the desired type is a generic type. Note that
   * this method works fine if the any of the fields of the specified object are generics, just the
   * object itself should not be a generic type. For the cases when the object is of generic type,
   * invoke {@link #fromJson(Reader, Type)}. If you have the Json in a String form instead of a
   * {@link Reader}, use {@link #fromJson(String, Class)} instead.
   *
   * @param <T> the type of the desired object
   * @param json the reader producing the Json from which the object is to be deserialized.
   * @param classOfT the class of T
   * @return an object of type T from the string
   * @throws JsonParseException if json is not a valid representation for an object of type
   * classOfT
   * @since 1.2
   */
  public <T> T fromJson(Reader json, Class<T> classOfT) throws JsonParseException {
    T target = classOfT.cast(fromJson(json, (Type) classOfT));
    return target;
  }

  /**
   * This method deserializes the Json read from the specified reader into an object of the
   * specified type. This method is useful if the specified object is a generic type. For
   * non-generic objects, use {@link #fromJson(Reader, Class)} instead. If you have the Json in a
   * String form instead of a {@link Reader}, use {@link #fromJson(String, Type)} instead.
   *
   * @param <T> the type of the desired object
   * @param json the reader producing Json from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src. You can obtain this type by using the
   * {@link com.google.gson.reflect.TypeToken} class. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @return an object of type T from the json
   * @throws JsonParseException if json is not a valid representation for an object of type typeOfT
   * @since 1.2
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(Reader json, Type typeOfT) throws JsonParseException {
    try {
      JsonParser parser = new JsonParser(json);
      JsonElement root = parser.parse();
      JsonDeserializationContext context = new JsonDeserializationContextDefault(
          createDefaultObjectNavigatorFactory(), deserializers, objectConstructor);
      T target = (T) context.deserialize(root, typeOfT);
      return target;
    } catch (TokenMgrError e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    } catch (ParseException e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    } catch (StackOverflowError e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    } catch (OutOfMemoryError e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    }
  }

  /**
   * Appends the {@link #NULL_STRING} to the {@code writer} object.
   *
   * @param writer the object to append the null value to
   */
  private void writeOutNullString(Appendable writer) throws IOException {
    writer.append(NULL_STRING);
  }
  
  @Override 
  public String toString() {
	StringBuilder sb = new StringBuilder("{");
    sb.append("serializeNulls:").append(serializeNulls);
	sb.append(",serializers:").append(serializers);
	sb.append(",deserializers:").append(deserializers);
	// using the name instanceCreator instead of ObjectConstructor since the users of Gson are 
	// more familiar with the concept of Instance Creators. Moreover, the objectConstructor is
	// just a utility class around instance creators, and its toString() only displays them.
    sb.append(",instanceCreators:").append(objectConstructor);
	sb.append("}");
	return sb.toString();
  }
}
