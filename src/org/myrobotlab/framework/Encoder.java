package org.myrobotlab.framework;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.service.interfaces.ServiceInterface;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * handles all encoding and decoding of MRL messages or api(s) assumed context -
 * services can add an assumed context as a prefix
 * /api/returnEncoding/inputEncoding/service/method/param1/param2/ ...
 * 
 * xmpp for example assumes (/api/string/gson)/service/method/param1/param2/ ...
 * 
 * scheme = alpha *( alpha | digit | "+" | "-" | "." ) Components of all URIs:
 * [<scheme>:]<scheme-specific-part>[#<fragment>]
 * http://stackoverflow.com/questions/3641722/valid-characters-for-uri-schemes
 */
public class Encoder {

	public final static Logger log = LoggerFactory.getLogger(Encoder.class);

	// uri schemes
	public final static String SCHEME_MRL = "mrl";
	public final static String SCHEME_BASE64 = "base64";
	
	public final static String TYPE_JSON = "json";
	public final static String TYPE_REST = "rest";

	// disableHtmlEscaping to prevent encoding or "=" -
	private transient static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").setPrettyPrinting().disableHtmlEscaping().create();
	public final static String API_REST_PREFIX = "api";

	public static final Set<Class<?>> WRAPPER_TYPES = new HashSet<Class<?>>(Arrays.asList(Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class,
			Float.class, Double.class, Void.class));

	public static final Set<String> WRAPPER_TYPES_CANONICAL = new HashSet<String>(Arrays.asList(Boolean.class.getCanonicalName(), Character.class.getCanonicalName(),
			Byte.class.getCanonicalName(), Short.class.getCanonicalName(), Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Float.class.getCanonicalName(),
			Double.class.getCanonicalName(), Void.class.getCanonicalName()));

	final static HashMap<String, Method> methodCache = new HashMap<String, Method>();

	/**
	 * a method signature map based on name and number of methods - the String[]
	 * will be the keys into the methodCache A method key is generated by input
	 * from some encoded protocol - the method key is object name + method name
	 * + parameter number - this returns a full method signature key which is
	 * used to look up the method in the methodCache
	 */
	final static HashMap<String, ArrayList<Method>> methodOrdinal = new HashMap<String, ArrayList<Method>>();

	final static HashSet<String> objectsCached = new HashSet<String>();
	
	public final static String toJson(Object o){
		return gson.toJson(o);
	}
	
	public final static String toJson(Object o, Class<?> clazz){
		return gson.toJson(o, clazz);
	}

	public final static <T extends Object> T fromJson(String json, Class<T> clazz){
		return gson.fromJson(json, clazz);
	}
	
	public static boolean isWrapper(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz);
	}

	public static boolean isWrapper(String className) {
		return WRAPPER_TYPES_CANONICAL.contains(className);
	}

	public static boolean setJSONPrettyPrinting(boolean b) {
		if (b) {
			gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").setPrettyPrinting().disableHtmlEscaping().create();
		} else {
			gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss.SSS").disableHtmlEscaping().create();
		}
		return b;
	}

	/**
	 * most lossy protocols need conversion of parameters into correctly typed
	 * elements this method is used to query a candidate method to see if a
	 * simple conversion is possible
	 * 
	 * @return
	 */
	public static boolean isSimpleType(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz) || clazz == String.class;
	}

	public static Message decodeURI(URI uri) {
		log.info(String.format("authority %s", uri.getAuthority())); // gperry:blahblah@localhost:7777
		log.info(String.format("     host %s", uri.getHost())); // localhost
		log.info(String.format("     port %d", uri.getPort())); // 7777
		log.info(String.format("     path %s", uri.getPath()));
		log.info(String.format("    query %s", uri.getQuery())); // /api/string/gson/runtime/getUptime
		log.info(String.format("   scheme %s", uri.getScheme())); // http
		log.info(String.format(" userInfo %s", uri.getUserInfo())); // gperry:blahblah

		Message msg = decodePathInfo(uri.getPath(), API_REST_PREFIX, null);

		return msg;
	}

	public static final Message decodePathInfo(String pathInfo) {
		return decodePathInfo(pathInfo, API_REST_PREFIX, null);
	}
	// TODO optimization of HashSet combinations of supported encoding instead
	// of parsing...
	// e.g. HashMap<String> supportedEncoding.containsKey(
	public static final Message decodePathInfo(String pathInfo, String apiTag, String encodingTag) {
		
		// minimal /(api|tag)/name/method
		// ret encoding /(api|tag)/encoding/name/method
		
		if (pathInfo == null) {
			log.error("pathInfo is null");
			return null;
		}

		String[] parts = pathInfo.split("/");
		
		if (parts.length < 4){
			log.error(String.format("%s - not enough parts - requires minimal 3", pathInfo));
			return null;
		}
		
		// FIXME allow parts[x + offset] with apiTag == null
		if (apiTag != null && !apiTag.equals(parts[1])){
			log.error(String.format("apiTag %s specified but %s in ordinal", apiTag, parts[0]));
			return null;
		}

		// FIXME INVOKING VS PUTTING A MESSAGE ON THE BUS
		Message msg = new Message();
		msg.name = parts[2];
		msg.method = parts[3];
		
		if (parts.length > 4) {
			// FIXME - ALL STRINGS AT THE MOMENT !!!
			String[] jsonParams = new String[parts.length - 4];
			System.arraycopy(parts, 4, jsonParams, 0, parts.length - 4);
			ServiceInterface si = org.myrobotlab.service.Runtime.getService(msg.name);
			msg.data = TypeConverter.getTypedParamsFromJson(si.getClass(), msg.method, jsonParams);
		}

		return msg;
	}

	// TODO
	// public static Object encode(Object, encoding) - dispatches appropriately

	public static String msgToGson(Message msg) {
		return gson.toJson(msg, Message.class);
	}

	public static Message gsonToMsg(String gsonData) {
		return (Message) gson.fromJson(gsonData, Message.class);
	}

	public static final Message base64ToMsg(String base64) {
		String data = base64;
		if (base64.startsWith(String.format("%s://", SCHEME_BASE64))) {
			data = base64.substring(SCHEME_BASE64.length() + 3);
		}
		final ByteArrayInputStream dataStream = new ByteArrayInputStream(Base64.decodeBase64(data));
		try {
			final ObjectInputStream objectStream = new ObjectInputStream(dataStream);
			Message msg = (Message) objectStream.readObject();
			return msg;
		} catch (Exception e) {
			Logging.logException(e);
			return null;
		}
	}

	public static final String msgToBase64(Message msg) {
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		try {
			final ObjectOutputStream objectStream = new ObjectOutputStream(dataStream);
			objectStream.writeObject(msg);
			objectStream.close();
			dataStream.close();
			String base64 = String.format("%s://%s", SCHEME_BASE64, new String(Base64.encodeBase64(dataStream.toByteArray())));
			return base64;
		} catch (Exception e) {
			log.error(String.format("couldnt seralize %s", msg));
			Logging.logException(e);
			return null;
		}
	}

	static final public String getParameterSignature(Object[] data) {
		if (data == null) {
			// return "null";
			return "";
		}

		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < data.length; ++i) {
			if (data[i] != null) {
				Class<?> c = data[i].getClass(); // not all data types are safe
													// toString() e.g.
													// SerializableImage
				if (c == String.class || c == Integer.class || c == Boolean.class || c == Float.class || c == MRLListener.class) {
					ret.append(data[i].toString());
				} else {
					String type = data[i].getClass().getCanonicalName();
					String shortTypeName = type.substring(type.lastIndexOf(".") + 1);
					ret.append(shortTypeName);
				}

				if (data.length != i + 1) {
					ret.append(",");
				}
			} else {
				ret.append("null");
			}

		}
		return ret.toString();

	}

	public static String type(String type) {
		int pos0 = type.indexOf(".");
		if (pos0 > 0) {
			return type;
		}
		return String.format("org.myrobotlab.service.%s", type);
	}

	public static boolean tryParseInt(String string) {
		try {
			Integer.parseInt(string);
			return true;
		} catch (Exception e) {

		}
		return false;
	}
	
	// FIXME !!! - encoding for Message ----> makeMethodKey(Message msg)

	static public String makeMethodKey(String fullObjectName, String methodName, Class<?>[] paramTypes) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < paramTypes.length; ++i) {
			sb.append("/");
			sb.append(paramTypes[i].getCanonicalName());
		}
		return String.format("%s/%s%s", fullObjectName, methodName, sb.toString());
	}

	static public String makeMethodOrdinalKey(String fullObjectName, String methodName, int paramCount) {
		return String.format("%s/%s/%d", fullObjectName, methodName, paramCount);
	}

	// LOSSY Encoding (e.g. xml & gson - which do not encode type information)
	// can possibly
	// give us the parameter count - from the parameter count we can grab method
	// candidates
	// @return is a arraylist of keys !!!

	static public ArrayList<Method> getMethodCandidates(String serviceType, String methodName, int paramCount) {
		if (!objectsCached.contains(serviceType)) {
			loadObjectCache(serviceType);
		}

		String ordinalKey = makeMethodOrdinalKey(serviceType, methodName, paramCount);
		if (!methodOrdinal.containsKey(ordinalKey)) {
			log.error(String.format("cant find matching method candidate for %s.%s %d params", serviceType, methodName, paramCount));
			return null;
		}
		return methodOrdinal.get(ordinalKey);
	}

	// FIXME - axis's Method cache - loads only requested methods
	// this would probably be more gracefull than batch loading as I am doing..
	// http://svn.apache.org/repos/asf/webservices/axis/tags/Version1_2RC2/java/src/org/apache/axis/utils/cache/MethodCache.java
	static public void loadObjectCache(String serviceType) {
		try {
			objectsCached.add(serviceType);
			Class<?> clazz = Class.forName(serviceType);
			Method[] methods = clazz.getMethods();
			for (int i = 0; i < methods.length; ++i) {
				Method m = methods[i];
				Class<?>[] types = m.getParameterTypes();

				String ordinalKey = makeMethodOrdinalKey(serviceType, m.getName(), types.length);
				String methodKey = makeMethodKey(serviceType, m.getName(), types);

				if (!methodOrdinal.containsKey(ordinalKey)) {
					ArrayList<Method> keys = new ArrayList<Method>();
					keys.add(m);
					methodOrdinal.put(ordinalKey, keys);
				} else {
					methodOrdinal.get(ordinalKey).add(m);
				}

				if (log.isDebugEnabled()) {
					log.debug(String.format("loading %s into method cache", methodKey));
				}
				methodCache.put(methodKey, m);
			}
		} catch (Exception e) {
			Logging.logException(e);
		}
	}

	// concentrator data coming from decoder
	static public Method getMethod(String serviceType, String methodName, Object[] params) {
		return getMethod("org.myrobotlab.service", serviceType, methodName, params);
	}

	// --- xml codec begin ------------------
	// inbound parameters are probably strings or xml bits encoded in some way -
	// need to match
	// ordinal first

	// real encoded data ??? getMethodFromXML getMethodFromJson - all resolve to
	// this getMethod with class form
	// encoded data.. YA !
	static public Method getMethod(String pkgName, String objectName, String methodName, Object[] params) {
		String fullObjectName = String.format("%s.%s", pkgName, objectName);
		return null;
	}
	
	static public final String getCallBack(String methodName){
		String callback = String.format("on%s%s", methodName.substring(0, 1).toUpperCase(), methodName.substring(1));
		return callback;
	}

	static public final byte[] getBytes(Object o) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(5000);
		ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		os.flush();
		os.writeObject(o);
		os.flush();
		return byteStream.toByteArray();
	}

	public static void toJsonFile(Object o, String filename) throws IOException {
		FileOutputStream fos = new FileOutputStream(new File(filename));
		fos.write(gson.toJson(o).getBytes());
		fos.close();
	}

	static public String getServiceType(String inType) {
		if (inType == null){ return null; }
		if (inType.contains(".")){ return inType;}
		return String.format("org.myrobotlab.service.%s", inType);
	}
	
	// === method signatures begin ===
	
	static public String toCamelCase(String s){
		   String[] parts = s.split("_");
		   String camelCaseString = "";
		   for (String part : parts){
		      camelCaseString = camelCaseString + toCCase(part);
		   }
		   return String.format("%s%s", camelCaseString.substring(0, 1).toLowerCase(), camelCaseString.substring(1));
		}

	static public String toCCase(String s) {
		    return s.substring(0, 1).toUpperCase() +
		               s.substring(1).toLowerCase();
		}
	
	static public String toUnderScore(String camelCase){
		return toUnderScore(camelCase, false);
	}
	static public String toUnderScore(String camelCase, Boolean toLowerCase){

		byte[] a = camelCase.getBytes();
		boolean lastLetterLower = false;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; ++i) {
			boolean currentCaseUpper = Character.isUpperCase(a[i]);

			Character newChar = null;
			if (toLowerCase != null){
				if (toLowerCase){
					newChar = (char) Character.toLowerCase(a[i]);
				} else {
					newChar = (char) Character.toUpperCase(a[i]);
				}
			} else {
				newChar = (char)a[i];
			}
					
			sb.append(String.format("%s%c", (lastLetterLower && currentCaseUpper) ? "_" : "", newChar));
			lastLetterLower = !currentCaseUpper;
		}
		
		return sb.toString();

	}

	// === method signatures end ===
}
