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

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the main class for using Gson. Gson is typically used by first constructing a
 * Gson object and then invoking {@link #toJson(Object)} or {@link #fromJson(Class, String)}
 * methods on it.
 * <p>You can also use {@link GsonBuilder} to build a Gson instance with various configuration
 * options such as versioning support, custom {@link JsonSerializer}s, {@link JsonDeserializer}s,
 * and {@link InstanceCreator}.</p>
 * Here is an example of how Gson is used:<br>
 * <pre>
 * Gson gson = new Gson(); // Or use new GsonBuilder().create(); <br>
 * MyType target = new MyType();<br>
 * String json = gson.toJson(target); // serializes target to Json<br>
 * MyType target2 = gson.fromJson(MyType.class, json); // deserializes json into target2<br>
 * </pre>
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
public final class Gson {
  private static Logger logger = Logger.getLogger(Gson.class.getName());

  private final ObjectNavigatorFactory navigatorFactory;
  private final MappedObjectConstructor objectConstructor;
  private final TypeAdapter typeAdapter;

  /** Map containing Type or Class objects as keys */
  private final ParameterizedTypeHandlerMap<JsonSerializer<?>> serializers =
      new ParameterizedTypeHandlerMap<JsonSerializer<?>>();

  /** Map containing Type or Class objects as keys */
  private final ParameterizedTypeHandlerMap<JsonDeserializer<?>> deserializers =
      new ParameterizedTypeHandlerMap<JsonDeserializer<?>>();

  private final JsonFormatter formatter;

  static final TypeAdapter DEFAULT_TYPE_ADAPTER =
      new TypeAdapterNotRequired(new PrimitiveTypeAdapter());

  static final ModifierBasedExclusionStrategy DEFAULT_MODIFIER_BASED_EXCLUSION_STRATEGY =
      new ModifierBasedExclusionStrategy(true, new int[] { Modifier.TRANSIENT, Modifier.STATIC });

  static final JsonFormatter DEFAULT_JSON_FORMATTER = new JsonCompactFormatter();

  /**
   * Constructs a Gson object that skips inner class references and ignores
   * all object versioning information
   */
  public Gson() {
    this(new ObjectNavigatorFactory(createExclusionStrategy(VersionConstants.IGNORE_VERSIONS)),
        new MappedObjectConstructor(), DEFAULT_TYPE_ADAPTER, DEFAULT_JSON_FORMATTER);
  }

  /**
   * Constructs a Gson object with the specified version and the mode of operation while
   * encountering inner class references.
   *
   * @param factory the object navigator factory to use when creating a new {@link ObjectNavigator}
   * instance.
   */
  Gson(ObjectNavigatorFactory factory) {
    this(factory, new MappedObjectConstructor(), DEFAULT_TYPE_ADAPTER, DEFAULT_JSON_FORMATTER);
  }

  Gson(ObjectNavigatorFactory factory, MappedObjectConstructor objectConstructor,
      TypeAdapter typeAdapter, JsonFormatter formatter) {
    this.navigatorFactory = factory;
    this.objectConstructor = objectConstructor;
    this.typeAdapter = typeAdapter;
    this.formatter = formatter;

    Map<Type, JsonSerializer<?>> defaultSerializers =
      DefaultJsonSerializers.getDefaultSerializers();
    for (Map.Entry<Type, JsonSerializer<?>> entry : defaultSerializers.entrySet()) {
      registerSerializer(entry.getKey(), entry.getValue());
    }

    Map<Type, JsonDeserializer<?>> defaultDeserializers =
        DefaultJsonDeserializers.getDefaultDeserializers();
    for (Map.Entry<Type, JsonDeserializer<?>> entry : defaultDeserializers.entrySet()) {
      registerDeserializer(entry.getKey(), entry.getValue());
    }

    Map<Type, InstanceCreator<?>> defaultInstanceCreators =
        DefaultInstanceCreators.getDefaultInstanceCreators();
    for (Map.Entry<Type, InstanceCreator<?>> entry : defaultInstanceCreators.entrySet()) {
      objectConstructor.register(entry.getKey(), entry.getValue());
    }
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
   * Configures Gson to use a custom {@link InstanceCreator} for the specified class. If an
   * instance creator was previously registered for the specified class, it is overwritten. You
   * should use this method if you want to register a single instance creator for all generic types
   * mapping to a single raw type. If you want different handling for different generic types of a
   * single raw type, use {@link #registerInstanceCreator(Type, InstanceCreator)} instead.
   *
   * @param <T> the type for which instance creator is being registered
   * @param classOfT The class definition for the type T
   * @param instanceCreator the instance creator for T
   */
  <T> void registerInstanceCreator(Class<T> classOfT,
      InstanceCreator<? extends T> instanceCreator) {
    registerInstanceCreator((Type) classOfT, instanceCreator);
  }

  /**
   * Configures Gson to use a custom {@link InstanceCreator} for the specified type. If an instance
   * creator was previously registered for the specified class, it is overwritten. Since this method
   * takes a type instead of a Class object, it can be used to register a specific handler for a
   * generic type corresponding to a raw type. If you want to have common handling for all generic
   * types corresponding to a raw type, use {@link #registerInstanceCreator(Class, InstanceCreator)}
   * instead.
   *
   * @param <T> the type for which instance creator is being registered
   * @param typeOfT The Type definition for T
   * @param instanceCreator the instance creator for T
   */
  <T> void registerInstanceCreator(Type typeOfT,
      InstanceCreator<? extends T> instanceCreator) {
    objectConstructor.register(typeOfT, instanceCreator);
  }

  /**
   * Configures Gson to use a custom JSON serializer for the specified class. You should use this
   * method if you want to register a common serializer for all generic types corresponding to a
   * raw type. If you want different handling for different generic types corresponding to a raw
   * type, use {@link #registerSerializer(Type, JsonSerializer)} instead.
   *
   * @param <T> the type for which the serializer is being registered
   * @param classOfT The class definition for the type T
   * @param serializer the custom serializer
   */
  <T> void registerSerializer(Class<T> classOfT, JsonSerializer<T> serializer) {
    registerSerializer((Type) classOfT, serializer);
  }

  /**
   * Configures Gson to use a custom JSON serializer for the specified type. You should use this
   * method if you want to register different serializers for different generic types corresponding
   * to a raw type. If you want common handling for all generic types corresponding to a raw type,
   * use {@link #registerSerializer(Class, JsonSerializer)} instead.
   *
   * @param <T> the type for which the serializer is being registered
   * @param typeOfT The type definition for T
   * @param serializer the custom serializer
   */
  <T> void registerSerializer(Type typeOfT, final JsonSerializer<T> serializer) {
    if (serializers.hasSpecificHandlerFor(typeOfT)) {
      logger.log(Level.WARNING, "Overriding the existing Serializer for " + typeOfT);
    }
    serializers.register(typeOfT, serializer);
  }

  /**
   * Configures Gson to use a custom JSON deserializer for the specified class. You should use this
   * method if you want to register a common deserializer for all generic types corresponding to a
   * raw type. If you want different handling for different generic types corresponding to a raw
   * type, use {@link #registerDeserializer(Type, JsonDeserializer)} instead.
   *
   * @param <T> the type for which the deserializer is being registered
   * @param classOfT The class definition for the type T
   * @param deserializer the custom deserializer
   */
  <T> void registerDeserializer(Class<T> classOfT, JsonDeserializer<T> deserializer) {
    registerDeserializer((Type) classOfT, deserializer);
  }

  /**
   * Configures Gson to use a custom JSON deserializer for the specified type. You should use this
   * method if you want to register different deserializers for different generic types
   * corresponding to a raw type. If you want common handling for all generic types corresponding to
   * a raw type, use {@link #registerDeserializer(Class, JsonDeserializer)} instead.
   *
   * @param <T> the type for which the deserializer is being registered
   * @param typeOfT The type definition for T
   * @param deserializer the custom deserializer
   * @return GsonBuilder to apply the Builder pattern
   */
  <T> void registerDeserializer(Type typeOfT, final JsonDeserializer<T> deserializer) {
    if (deserializers.hasSpecificHandlerFor(typeOfT)) {
      logger.log(Level.WARNING, "Overriding the existing Deserializer for " + typeOfT);
    }
    deserializers.register(typeOfT, deserializer);
  }

  /**
   * This method creates JSON representation of the specified object. It  uses the
   * {@link Class#getClass()} method to get the type information for the specified object.
   * However, {@link Class#getClass()} loses the generic type information because of the Type
   * Erasure feature of Java. Therefore, this method should not be used if the specified object is
   * a generic type. Note that this method works fine if the any of the fields of the specified
   * object are generics, just the object itself should not be a generic type. For the cases when
   * the object is of generic type, invoke {@link #toJson(Object, Type)} instead.
   *
   * @param src the object for which JSON representation is to be created setting for Gson
   * @return JSON representation of src
   */
  public String toJson(Object src) {
    if (src == null) {
      return "";
    }
    return toJson(src, src.getClass());
  }

  /**
   * This method is useful if the specified object is a generic type. For non-generic objects,
   * use {@link #toJson(Object)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link com.google.gson.reflect.TypeToken} class. For example,
   * to get the type for <code>Collection&lt;Foo&gt;</code>, you should use<br>
   * <code>Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType()</code>
   * @return JSON representation of src
   */
  public String toJson(Object src, Type typeOfSrc) {
    if (src == null) {
      return "";
    }
    JsonSerializationContext context =
        new JsonSerializationContextDefault(navigatorFactory, serializers);
    JsonElement jsonElement = context.serialize(src, typeOfSrc);
    StringWriter writer = new StringWriter();
    formatter.format(jsonElement, new PrintWriter(writer));
    return jsonElement == null ? "" : writer.toString();
  }

  /**
   * This method deserializes the specified JSON into an object of the specified class. It is not
   * suitable to use if the specified class is a generic type since it will not have the generic
   * type information because of the Type Erasure feature of Java. Therefore, this method should not
   * be used if the desired type is a generic type. Note that this method works fine if the any of
   * the fields of the specified object are generics, just the object itself should not be a
   * generic type. For the cases when the object is of generic type, invoke
   * {@link #fromJson(Type, String)}.
   *
   * @param <T> the type of the desired object
   * @param classOfT the class of T
   * @param json the string from which the object is to be deserialized
   * @return an object of type T from the string
   * @throws JsonParseException if json is not a valid representation for an object of type
   * classOfT.
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(Class<T> classOfT, String json) throws JsonParseException {
    return (T) fromJson((Type) classOfT, json);
  }

  /**
   * This method is useful if the specified object is a generic type. For non-generic objects,
   * use {@link #fromJson(Class, String)} instead.
   *
   * @param <T> the type of the desired object
   * @param typeOfT The specific genericized type of src. You can obtain
   * this type by using the {@link com.google.gson.reflect.TypeToken} class. For example,
   * to get the type for <code>Collection&lt;Foo&gt;</code>, you should use<br>
   * <code>Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType()</code>
   * @param json the string from which the object is to be deserialized
   * @return an object of type T from the string
   * @throws JsonParseException if json is not a valid representation for an object of type typeOfT.
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(Type typeOfT, String json) throws JsonParseException {
    try {
      StringReader reader = new StringReader(json);
      JsonParser parser = new JsonParser(reader);
      JsonElement root = parser.parse();
      JsonDeserializationContext context = new JsonDeserializationContextDefault(navigatorFactory,
          deserializers, objectConstructor, typeAdapter);
      return (T) context.deserialize(typeOfT, root);
    } catch (TokenMgrError e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    } catch (ParseException e) {
      throw new JsonParseException("Failed parsing JSON source: " + json + " to Json", e);
    }
  }
}
