package org.orztoys.mybatisplus.wquery;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public class WebQueryResolver implements HandlerMethodArgumentResolver, DisposableBean {

	private final org.apache.ibatis.logging.Log log = org.apache.ibatis.logging.LogFactory.getLog(WebQueryResolver.class);

	private final static String SPARATOR_COMMA = ",";

	private final static String SORT_ASC_PARAM_NAME = "sort";
	private final static String SORT_DESC_PARAM_NAME = "sort-desc";

	private final static ConcurrentMap<Class<?>, Map<String, Field>> CLAZZ_CACHE = new ConcurrentHashMap<>();
	private final static Map<Class<?>, QueryValueResolver> QUERY_RESOLVER_CACHE = new ConcurrentHashMap<>();

	@Override
	public void destroy() throws Exception {
		CLAZZ_CACHE.clear();
		QUERY_RESOLVER_CACHE.clear();
	}

	@Override
	protected void finalize() {
		try {
			this.destroy();
		} catch (Exception e) {
		}
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {

		boolean isGetMapping = false;

		if (parameter.getMethod().getAnnotation(GetMapping.class) == null) {
			RequestMapping ann2 = parameter.getMethod().getAnnotation(RequestMapping.class);
			if (ann2 != null && Arrays.asList(ann2.method()).contains(RequestMethod.GET)) {
				isGetMapping = true;
			}
		} else {
			isGetMapping = true;
		}

		if (isGetMapping) {
			return parameter.getParameterType() == QueryWrapper.class || IPage.class.isAssignableFrom(parameter.getParameterType());
		} else {
			return false;
		}

	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		if (parameter.getParameterType() == QueryWrapper.class) {
			return convertQuery(parameter, webRequest);
		} else if (IPage.class.isAssignableFrom(parameter.getParameterType())) {
			return convertPagination(parameter, webRequest);
		} else {
			return null;
		}

	}

	private IPage<?> convertPagination(MethodParameter parameter, NativeWebRequest webRequest) {
		try {
			IPage<?> pageWrapper = (IPage<?>) parameter.getParameterType().newInstance();

			Integer page = Integer.parseInt(Optional.ofNullable(webRequest.getParameter("page")).orElse("1"));
			Integer size = Integer.parseInt(Optional.ofNullable(webRequest.getParameter("size")).orElse("20"));

			pageWrapper.setCurrent(page < 1 ? 1 : page);
			pageWrapper.setSize(size <= 0 ? 20 : size);

			return pageWrapper;
		} catch (InstantiationException | IllegalAccessException e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	private QueryWrapper<?> convertQuery(MethodParameter parameter, NativeWebRequest webRequest) {
		QueryWrapper<?> query = new QueryWrapper<>();

		Class<?> paramClazz = null;
		Method pMethod = null;
		if (parameter.getExecutable() instanceof Method) {
			pMethod = (Method) parameter.getExecutable();

			List<Type> ptlist = Arrays.asList(pMethod.getGenericParameterTypes());

			Type queryType = ptlist.get(parameter.getParameterIndex());

			if (queryType instanceof ParameterizedType) {
				Type[] ts = ((ParameterizedType) queryType).getActualTypeArguments();
				paramClazz = (Class<?>) ts[0];

			}

		}

		if (paramClazz != null) {

			WebQuery ann = pMethod.getDeclaredAnnotation(WebQuery.class);

			Map<String, Field> fs = getFields(paramClazz);

			this.fillQuery(ann, fs, webRequest, query);
			this.fillLikeQuery(ann, fs, webRequest, query);
			this.fillSortAsc(ann, fs, webRequest, query);
			this.fillSortDesc(ann, fs, webRequest, query);

		}
		return query;
	}

	private Map<String, Field> getFields(Class<?> clazz) {

		if (clazz == null) {
			return Collections.emptyMap();
		}

		Map<String, Field> fs = CLAZZ_CACHE.get(clazz);
		if (fs == null) {
			fs = Optional.ofNullable(Arrays.asList(clazz.getDeclaredFields())).map(Collection::stream).orElseGet(java.util.stream.Stream::empty).filter(e -> {
				if (Modifier.isStatic(e.getModifiers())) {
					return false;
				} else if (Modifier.isFinal(e.getModifiers())) {
					return false;
				} else {
					return true;
				}
			}).collect(Collectors.toMap(e -> e.getName().toLowerCase(), e -> e));

			CLAZZ_CACHE.putIfAbsent(clazz, fs);

			log.debug(String.format("%s add to reflect cache", clazz.getName()));
		}

		return fs;
	}

	private String resolveName(QueryResolver[] resolvers, String name) {

		if (resolvers == null || resolvers.length == 0) {
			return name;
		}

		for (QueryResolver r : resolvers) {
			if (name.equalsIgnoreCase(r.name()) && r.inEntity() != null && r.inEntity().trim().length() > 0) {
				return r.inEntity();
			}
		}

		return name;
	}

	private String resolveValue(QueryResolver[] resolvers, String name, String value) {

		if (resolvers == null || resolvers.length == 0) {
			return value;
		}

		for (QueryResolver r : resolvers) {
			if (name.equalsIgnoreCase(r.name()) && r.value() != null && !r.value().isInterface()) {
				try {
					QueryValueResolver qr = QUERY_RESOLVER_CACHE.get(r.value());
					if (qr == null) {
						qr = QUERY_RESOLVER_CACHE.putIfAbsent(r.value(), r.value().newInstance());
					}
					value = qr.resolve(name, value);
					break;
				} catch (InstantiationException | IllegalAccessException e) {
					log.error(e.getMessage(), e);
				}
			}
		}

		return value;
	}

	private void fillLikeQuery(WebQuery ann, Map<String, Field> fs, NativeWebRequest request, QueryWrapper<?> query) {
		List<String> canQueryName;
		if (ann != null && ann.likes() != null && ann.likes().length > 0) {
			canQueryName = Arrays.asList(ann.likes()).stream().map(e -> {
				String a = e.toLowerCase();
				return a;
			}).collect(Collectors.toList());
		} else {
			canQueryName = Collections.emptyList();
		}
		for (Iterator<String> iter = request.getParameterNames(); iter.hasNext();) {
			String name = iter.next();
			Field f = fs.get(name.toLowerCase());
			if (f == null) {
				String n = resolveName(ann.value(), name).toLowerCase();
				f = fs.get(n);
			}

			if (f != null && canQueryName.contains(name.toLowerCase())) {
				String value = request.getParameter(name);

				value = resolveValue(ann.value(), name, value);

				if (value == null || value.trim().length() == 0) {
					continue;
				}

				String tbCol = formatToTbColumn(f);
				if (CharSequence.class.isAssignableFrom(f.getType())) {
					try {
						query.like(tbCol, value);
					} catch (ClassCastException | SecurityException | IllegalArgumentException e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}
	}

	private void fillQuery(WebQuery ann, Map<String, Field> fs, NativeWebRequest request, QueryWrapper<?> query) {
		List<String> canQueryName;
		if (ann != null && ann.querys() != null && ann.querys().length > 0) {
			canQueryName = Arrays.asList(ann.querys()).stream().map(e -> {
				String a = e.toLowerCase();
				return a;
			}).collect(Collectors.toList());
		} else {
			canQueryName = Collections.emptyList();
		}
		for (Iterator<String> iter = request.getParameterNames(); iter.hasNext();) {
			String name = iter.next();
			Field f = fs.get(name.toLowerCase());
			if (f == null) {
				f = fs.get(resolveName(ann.value(), name).toLowerCase());
			}

			if (f != null && canQueryName.contains(name.toLowerCase())) {
				String value = request.getParameter(name);

				value = resolveValue(ann.value(), name, value);

				if (value == null || value.trim().length() == 0) {
					continue;
				}

				String tbCol = formatToTbColumn(f);
				if (Number.class.isAssignableFrom(f.getType()) && value.matches("[\\-\\d\\+\\.]+")) {
					try {
						Constructor<?> con = f.getType().getDeclaredConstructor(String.class);
						if (con != null) {
							query.eq(tbCol, con.newInstance(value));
						} else {
							query.eq(tbCol, value);
						}

					} catch (ClassCastException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						log.error(e.getMessage(), e);
					}
				} else {
					query.eq(tbCol, value);
				}
			}
		}
	}

	private String formatToTbColumn(Field f) {
		StringBuffer orignalName = new StringBuffer(f.getName());
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < orignalName.length(); i++) {
			if (i == 0 || i == orignalName.length() - 1) {
				sb.append(orignalName.charAt(i));
			} else if (orignalName.charAt(i) >= 'A' && orignalName.charAt(i) <= 'Z') {
				sb.append("_").append(String.valueOf(orignalName.charAt(i)).toLowerCase());
			} else {
				sb.append(orignalName.charAt(i));
			}
		}

		return sb.toString();

	}

	private void fillSortAsc(WebQuery ann, Map<String, Field> fs, NativeWebRequest request, QueryWrapper<?> query) {
		List<String> sortName;
		if (ann != null && ann.asc() != null && ann.asc().length > 0) {
			sortName = Arrays.asList(ann.asc()).stream().map(e -> e.toLowerCase()).collect(Collectors.toList());
		} else {
			sortName = Collections.emptyList();
		}
		String reqSort = request.getParameter(SORT_ASC_PARAM_NAME);

		if (!sortName.isEmpty() && reqSort != null && reqSort.trim().length() > 0) {

			List<String> reqAsc = Arrays.asList(reqSort.split(SPARATOR_COMMA)).stream().filter(e -> sortName.contains(e.toLowerCase())).collect(Collectors.toList());

			if (!reqAsc.isEmpty()) {
				query.orderBy(true, true, reqAsc);
			}
		}
	}

	private void fillSortDesc(WebQuery ann, Map<String, Field> fs, NativeWebRequest request, QueryWrapper<?> query) {
		List<String> sortName;
		if (ann != null && ann.desc() != null && ann.desc().length > 0) {
			sortName = Arrays.asList(ann.desc()).stream().map(e -> e.toLowerCase()).collect(Collectors.toList());
		} else {
			sortName = Collections.emptyList();
		}
		String reqSort = request.getParameter(SORT_DESC_PARAM_NAME);
		if (!sortName.isEmpty()) {

			List<String> reqDesc = Arrays.asList(reqSort.split(SPARATOR_COMMA)).stream().filter(e -> sortName.contains(e.toLowerCase())).collect(Collectors.toList());

			if (!reqDesc.isEmpty()) {
				query.orderBy(true, false, sortName);
			}
		}
	}
}
