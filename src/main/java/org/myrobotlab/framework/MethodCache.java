package org.myrobotlab.framework;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.myrobotlab.cache.LRUMethodCache;
import org.myrobotlab.codec.Codec;
import org.myrobotlab.codec.CodecUtils;
import org.myrobotlab.logging.LoggerFactory;
import org.slf4j.Logger;

/**
 * 
 * @author GroG
 * 
 *         A method cache whos purpose is to build a cache of methods to be
 *         accessed when needed for invoking. This cache is typically used for
 *         services and populated during Runtime.create({name},{Type}). It's a
 *         static resource and contains a single definition per {Type}.
 * 
 *         It has a single map of "all" MethodEntries per type, and several
 *         indexes to that map. The other utility indexes are supposed to be
 *         useful and relevant for service specific access of methods.
 * 
 *         The definition of "declared methods" is slightly different for Mrl
 *         services. "Declared methods" are service methods which are expected
 *         to be commonly used. The difference occurs often with abstract
 *         classes such as AbstractSpeechSynthesis. Polly's
 *         class.getDeclaredMethods() would NOT include all the useful methods
 *         commonly defined in AbstractSpeechSynthesis.
 * 
 *         FIXME - keys should be explicitly typed full signature with execution
 *         format e.g. method(
 * 
 * 
 *         The cache is built when new services are created. Method signatures
 *         are used as keys. The keys are string based. All parameters in key
 *         creation are "boxed", this leads to the ability to write actual
 *         functions with primitives e.g. doIt(int x, float y, ...) and invoking
 *         does not need to fail for it to be called directly.
 * 
 *         Ancestor classes are all indexed, so there is no "special" handling
 *         to call abstract class methods.
 * 
 *         Special indexes are created when a new service gets created that are
 *         explicitly applicable for remote procedure calls e.g. methods which
 *         contain interfaces in the parameters are not part of this index, if
 *         your creating a highly accessable method that you expect to be used
 *         remotely, you would make it with a String {name} reference as a
 *         parameter.
 *
 */
public class MethodCache {

  // FIXME - mostly interested in
  // NOT Object
  // RARELY Service
  // OFTEN ANYTHING DEFINED LOWER THAN THAT
  // WHICH MEANS - filter out Object
  // CREATE a "Service" Index
  // -> ALL OTHER METHODS ARE OF INTEREST
  class MethodIndex {
    // index for typeless resolution and invoking
    Map<String, List<MethodEntry>> methodOrdinalIndex = new TreeMap<>();

    // super index of all method entries
    Map<String, MethodEntry> methodsIndex = new TreeMap<>();

    Map<String, MethodEntry> remoteMethods = new TreeMap<>();
    // Map<String, List<MethodEntry>> declaredMethodOrdinalIndex = new
    // TreeMap<>();
  }

  private static MethodCache instance;

  public final static Logger log = LoggerFactory.getLogger(MethodCache.class);

  final public static Class<?> boxPrimitive(Class<?> clazz) {
    if (clazz == boolean.class) {
      return Boolean.class;
    } else if (clazz == char.class) {
      return Character.class;
    } else if (clazz == byte.class) {
      return Byte.class;
    } else if (clazz == short.class) {
      return Short.class;
    } else if (clazz == int.class) {
      return Integer.class;
    } else if (clazz == long.class) {
      return Long.class;
    } else if (clazz == float.class) {
      return Float.class;
    } else if (clazz == double.class) {
      return Double.class;
    } else if (clazz == void.class) {
      return Void.class;
    } else {
      log.error("unexpected type class conversion for class {}", clazz.getCanonicalName());
    }
    return null;
  }

  public static MethodCache getInstance() {
    if (instance != null) {
      return instance;
    }
    synchronized (MethodCache.class) {
      if (instance == null) {
        instance = new MethodCache();
        instance.excludeMethods.add("main");
        instance.excludeMethods.add("getMetaData");

      }
    }
    return instance;
  }
  /*
   * public static void main(String[] args) { try {
   * 
   * // LoggingFactory.init(Level.INFO);
   * 
   * MethodCache cache = MethodCache.getInstance(); //
   * cache.cacheMethodEntries(Runtime.class);
   * cache.cacheMethodEntries(Clock.class);
   * 
   * 
   * } catch(Exception e) { log.error("main threw", e); } }
   */

  Set<String> excludeMethods = new TreeSet<>();

  Map<String, MethodIndex> objectCache = new TreeMap<>();

  protected MethodCache() {
  }

  public void cacheMethodEntries(Class<?> object) {
    Set<Class<?>> exclude = new HashSet<>();
    exclude.add(Service.class);
    exclude.add(Object.class);
    cacheMethodEntries(object, exclude);
  }

  // public void cacheMethodEntries(Class<?> object, Class<?> maxSuperType,
  // Set<String> excludeMethods) {
  public void cacheMethodEntries(Class<?> object, Set<Class<?>> excludeFromDeclared) {

    if (objectCache.containsKey(object.getCanonicalName())) {
      log.info("already cached {} methods", object.getSimpleName());
      return;
    }

    long start = System.currentTimeMillis();
    MethodIndex mi = new MethodIndex();
    Method[] methods = object.getMethods();
    Method[] declaredMethods = object.getDeclaredMethods();
    log.info("caching {}'s {} methods and {} declared methods", object.getSimpleName(), methods.length, declaredMethods.length);
    for (Method m : methods) {
      // log.debug("processing {}", m.getName());
      Class<?>[] paramTypes = m.getParameterTypes();

      String key = getMethodKey(object, m);
      String ordinalKey = getMethodOrdinalKey(object, m);

      // FIXME - we are "building" an index, not at the moment - "using" the
      // index - so it should be "complete"
      // FIXME - other sub-indexes are "trimmed" for appropriate uses
      // should this use key ?? vs name ? or different class-less signature e.g.
      // main(String[])
      // if (excludeMethods.contains(m.getName())) {
      // continue;
      // }

      // search for interfaces in parameters - if there are any the method is
      // not applicable for remote invoking !

      MethodEntry me = new MethodEntry(m);
      mi.methodsIndex.put(key, me);

      if (!mi.methodOrdinalIndex.containsKey(ordinalKey)) {
        List<MethodEntry> mel = new ArrayList<>();
        mel.add(me);
        mi.methodOrdinalIndex.put(ordinalKey, mel);
      } else {
        List<MethodEntry> mel = mi.methodOrdinalIndex.get(ordinalKey);
        mel.add(me);
        // FIXME - output more info on collisions
        // log.warn("{} method ordinal parameters collision ", ordinalKey);
      }

      if (!excludeFromDeclared.contains(m.getDeclaringClass()) && !excludeMethods.contains(m.getName())) {

        boolean hasInterfaceInParamList = false;
        // exclude interfaces from this index - the preference in design would
        // be to have a
        // string {name} reference to refer to the service instance, however,
        // within in-process
        // python binding, using a reference to an interface is preferred
        for (Class<?> paramType : paramTypes) {
          if (paramType.isInterface()) {
            // skipping not applicable for remote invoking
            hasInterfaceInParamList = true;
            break;
          }
        }
        if (!hasInterfaceInParamList) {
          mi.remoteMethods.put(key, me);
        }
      }
      log.debug("processed {}", me);
    }

    // log.debug("cached {}'s {} methods and {} declared methods",
    // object.getSimpleName(), methods.length,
    // mi.remoteMethods.keySet().size());
    objectCache.put(object.getCanonicalName(), mi);
    log.info("cached {} {} methods with {} ordinal signatures in {} ms", object.getSimpleName(), mi.methodsIndex.size(), mi.methodOrdinalIndex.size(),
        System.currentTimeMillis() - start);
  }

  /**
   * clears all cache
   */
  public void clear() {
    objectCache.clear();
  }

  public int getObjectSize() {
    return objectCache.size();
  }

  public int getMethodSize() {
    int size = 0;
    for (MethodIndex mi : objectCache.values()) {
      size += mi.methodsIndex.size();
    }
    return size;
  }

  public Method getMethod(Class<?> object, String methodName, Class<?>... paramTypes) {
    String[] paramTypeNames = new String[paramTypes.length];
    for (int i = 0; i < paramTypes.length; ++i) {
      paramTypeNames[i] = paramTypes[i].getCanonicalName();
    }
    return getMethod(object.getCanonicalName(), methodName, paramTypeNames);
  }

  /**
   * Use case for in-process non-serialized messages with actual data parameters
   * 
   * @param objectType
   *          - the object to invoke against
   * @param methodName
   *          - method name
   * @param params
   *          - actual parameter
   * @return - the method to invoke
   */
  public Method getMethod(Class<?> objectType, String methodName, Object... params) {
    Class<?>[] paramTypes = null;
    if (params != null) {
      paramTypes = new Class<?>[params.length];
      for (int i = 0; i < params.length; ++i) {
        paramTypes[i] = params[i].getClass();
      }
    } else {
      paramTypes = new Class<?>[0];
    }
    return getMethod(objectType, methodName, paramTypes);
  }

  public Method getMethod(String jsonMsg) { // vs getMethod(String object,
                                            // String method, String[]
                                            // paramsToDecode
    // decode container

    // find ordinal signature match
    // if found & !collision
    // return method
    // if found and collision on ordinal - FIXME resolve somehow (test cases)
    // return method
    return null;
  }

  /**
   * A full string interface to get a method - although this is potentially a
   * easy method to use, the most common use case would be used by the framework
   * which will automatically supply fully qualified type names.
   * 
   * @param fullType
   * @param methodName
   * @param paramTypeNames
   * @return
   */
  public Method getMethod(String fullType, String methodName, String[] paramTypeNames) {
    /*
     * expected fully type names if (!fullType.contains(".")) { fullType =
     * "org.myrobotlab.service." + fullType; }
     */

    if (!objectCache.containsKey(fullType)) {
      log.error("{} not found in objectCache", fullType);
      return null;
    }

    MethodIndex mi = objectCache.get(fullType);

    // make a key
    String key = makeKey(fullType, methodName, paramTypeNames);

    // get the method - (from the super-map of all methods)
    if (!mi.methodsIndex.containsKey(key)) {
      // a key for the method might not exist because when a key is generated by
      // code
      // utilizing the MethodCache - super-types or interfaces might be used in
      // the parameter list
      // if this is the case we will look based on methods and ordinals
      String ordinalKey = getMethodOrdinalKey(fullType, methodName, paramTypeNames.length);
      List<MethodEntry> possibleMatches = mi.methodOrdinalIndex.get(ordinalKey);
      if (possibleMatches == null) {
        // log.error("there were no possible matches for ordinal key {} - does the method exist?", ordinalKey);
        // log.error("{}.{}.{}", fullType, methodName, paramTypeNames);
        return null;
      }
      if (possibleMatches.size() == 1) {
        // woohoo ! we're done - if there is a single match it makes the choice
        // easy ;)
        return possibleMatches.get(0).method;
      } else {
        // now it gets more complex
        // spin through the possibilites - see if all parameters can be coerced
        // into working
        for (MethodEntry me : possibleMatches) {
          boolean foundMatch = true;
          Class<?>[] definedTypes = me.method.getParameterTypes();
          for (int i = 0; i < paramTypeNames.length; ++i) {
            Class<?> paramClass = null;

            try {
              paramClass = Class.forName(paramTypeNames[i]);
            } catch (ClassNotFoundException e) {
              log.error("while attempting to parameter match {} was not found", paramTypeNames[i], e);
            }
            if (!definedTypes[i].isAssignableFrom(paramClass)) {
              // parameter coercion fails - check other possiblities
              foundMatch = false;
              continue;
            }
          }

          if (!foundMatch) {
            // one of the parameters could not be coerced
            // look at other methods
            continue;
          }
          // We made it through matching all parameters !
          // send back the winner - but lets cache the entry first
          // we will fill the cache here with a new explicit key
          key = makeKey(fullType, methodName, paramTypeNames);
          mi.methodsIndex.put(key, me);
          return me.method;
        }
      }
      log.error("method {} with key signature {} not found in methodsIndex", methodName, key);
      return null;
    }

    // easy - exact key match return method
    return mi.methodsIndex.get(key).method;
  }

  public Map<String, Map<String, MethodEntry>> getRemoteMethods() {
    Map<String, Map<String, MethodEntry>> ret = new TreeMap<>();
    for (String name : objectCache.keySet()) {
      ret.put(name, objectCache.get(name).remoteMethods);
    }
    return ret;
  }

  public Map<String, MethodEntry> getRemoteMethods(String type) {
    if (!type.contains(".")) {
      type = "org.myrobotlab.service." + type;
    }
    if (objectCache.containsKey(type)) {
      return objectCache.get(type).remoteMethods;
    }
    return null;
  }

  final public Object invokeOn(Object obj, String method, Object... params) {

    if (obj == null)

    {
      log.error("invokeOn object is null");
      return null;
    }

    Object retobj = null;
    Class<?> c = null;
    Class<?>[] paramTypes = null;

    try {
      c = obj.getClass();

      if (params != null) {
        paramTypes = new Class[params.length];
        for (int i = 0; i < params.length; ++i) {
          if (params[i] != null) {
            paramTypes[i] = params[i].getClass();
          } else {
            paramTypes[i] = null;
          }
        }
      }
      Method meth = null;

      // TODO - method cache map
      // can not auto-box or downcast with this method - getMethod will
      // return a "specific & exact" match based
      // on parameter types - the thing is we may have a typed signature
      // which will allow execution - but
      // if so we need to search

      // FIXME - WHY ISN'T METHOD CACHING USED HERE !!!

      // SECURITY - ??? can't be implemented here - need a full message
      meth = c.getMethod(method, paramTypes); // getDeclaredMethod zod !!!
      retobj = meth.invoke(obj, params);

      // put return object onEvent
      out(method, retobj);
    } catch (NoSuchMethodException e) {

      // cache key compute

      // TODO: validate what "params.toString()" returns.
      StringBuilder keyBuilder = new StringBuilder();
      if (paramTypes != null) {
        for (Object o : paramTypes) {
          keyBuilder.append(o);
        }
      }

      Method mC = LRUMethodCache.getInstance().getCacheEntry(obj, method, paramTypes);
      if (mC != null) {
        // We found a cached hit! lets invoke on that.
        try {
          retobj = mC.invoke(obj, params);
          // put return object onEvent
          out(method, retobj);
          // return
          return retobj;
        } catch (Exception e1) {
          log.error("boom goes method - could not find method in cache {}", mC.getName(), e1);
        }
      }

      // TODO - build method cache map from errors
      log.debug("no such method {}.{} - attempting upcasting", c.getSimpleName(), MethodEntry.getPrettySignature(method, paramTypes, null));

      // TODO - optimize with a paramter TypeConverter & Map
      // c.getMethod - returns on EXACT match - not "Working" match
      Method[] allMethods = c.getMethods(); // ouch
      log.debug("searching through {} methods", allMethods.length);

      for (Method m : allMethods) {
        String mname = m.getName();
        if (!mname.equals(method)) {
          continue;
        }

        Type[] pType = m.getGenericParameterTypes();
        // checking parameter lengths
        if (params == null && pType.length != 0 || pType.length != params.length) {
          continue;
        }
        try {
          log.debug("found appropriate method");
          retobj = m.invoke(obj, params);
          // put return object onEvent
          out(method, retobj);
          // we've found a match. put that in the cache.
          log.debug("caching method cache key");
          LRUMethodCache.getInstance().addCacheEntry(obj, method, paramTypes, m);
          return retobj;
        } catch (Exception e1) {
          log.error("boom goes method {}", m.getName());
          // Logging.logError(e1);
        }
      }

      log.error("did not find method - {}.{}({}) {}", obj, method, CodecUtils.getParameterSignature(params), paramTypes);
    } catch (Exception e) {
      log.error("{}", e.getClass().getSimpleName(), e);
    }

    return retobj;
  }

  private String getMethodKey(Class<?> object, Method method) {
    // make sure all parameters are boxed - and use those signature keys
    // msgs coming in will "always" be boxed so they will match this signature
    // keys
    Class<?>[] params = method.getParameterTypes();
    String[] paramTypes = new String[method.getParameterTypes().length];
    for (int i = 0; i < params.length; ++i) {
      Class<?> param = params[i];
      if (param.isPrimitive()) {
        paramTypes[i] = boxPrimitive(param).getCanonicalName();
      } else {
        paramTypes[i] = params[i].getCanonicalName();
      }
    }
    return makeKey(object.getCanonicalName(), method.getName(), paramTypes);
  }

  public String makeKey(Class<?> object, String methodName, Class<?>... paramTypes) {
    String[] paramTypeNames = new String[paramTypes.length];
    for (int i = 0; i < paramTypes.length; ++i) {
      paramTypeNames[i] = paramTypes[i].getCanonicalName();
    }
    return makeKey(object.getCanonicalName(), methodName, paramTypeNames);
  }

  public String makeKey(String fullType, String methodName, String[] paramTypes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < paramTypes.length; ++i) {
      sb.append(paramTypes[i]);
      if (i < paramTypes.length - 1) {
        sb.append(",");
      }
    }
    return String.format("%s.%s(%s)", fullType, methodName, sb);
  }

  private String getMethodOrdinalKey(Class<?> object, Method method) {
    return getMethodOrdinalKey(object.getCanonicalName(), method.getName(), method.getParameterTypes().length);
  }

  private String getMethodOrdinalKey(String fullType, String methodName, int parameterSize) {
    String key = String.format("%s.%s-%s", fullType, methodName, parameterSize);
    return key;
  }

  public void out(String method, Object o) {
    /*
     * Message m = Message.createMessage(this, null, method, o); // create a //
     * un-named // message // as output
     * 
     * if (m.sender.length() == 0) { m.sender = this.getName(); } if
     * (m.sendingMethod.length() == 0) { m.sendingMethod = method; } if (outbox
     * == null) {
     * log.info("******************OUTBOX IS NULL*************************");
     * return; } outbox.add(m);
     */
  }

  public List<MethodEntry> getOrdinalMethods(Class<?> object, String methodName, int parameterSize) {
    String objectKey = object.getCanonicalName();
    String ordinalKey = getMethodOrdinalKey(objectKey, methodName, parameterSize);
    MethodIndex methodIndex = objectCache.get(objectKey);
    if (methodIndex == null) {
      log.error("cannot find index of {} !", objectKey);
      return null;
    }
    return methodIndex.methodOrdinalIndex.get(ordinalKey);
  }

  public Object[] getDecodedParameters(Class<?> clazz, String methodName, Object[] encodedParams, Codec codec) {
    // get templates
    List<MethodEntry> possible = getOrdinalMethods(clazz, methodName, encodedParams.length);
    Object[] params = new Object[encodedParams.length];
    // iterate through templates - attempt to decode
    for (int p = 0; p < possible.size(); ++p) {
      Class<?>[] paramTypes = possible.get(p).getParameterTypes();
      try {
        for (int i = 0; i < encodedParams.length; ++i) {
          params[i] = codec.decode(encodedParams[i], paramTypes[i]);
        }
        // successfully decoded params
        return params;
      } catch (Exception e) {
        log.error("getDecodedParameters threw", e);
      }
      return null;
    }

    // if successful return new msg

    return null;
  }

  public static String formatParams(Object[] params) {
    StringBuilder sb = new StringBuilder();
    if (params != null) {
      for (int i = 0; i < params.length; ++i) {
        sb.append(params[i].getClass().getSimpleName());
        if (i < params.length - 1) {
          sb.append(", ");
        }
      }
    }
    return sb.toString();
  }

}
