package org.orztoys.mybatisplus.wquery;

import java.util.List;

import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebQueryConfiguration implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		if (resolvers.isEmpty()) {
			resolvers.add(0, new WebQueryResolver());
		} else {
			resolvers.set(0, new WebQueryResolver());
		}
	}

}
