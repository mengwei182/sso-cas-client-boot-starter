package org.example.filter;

import org.jasig.cas.client.authentication.UrlPatternMatcherStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class AntPathMatcherStrategy implements UrlPatternMatcherStrategy {
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private String pattern;

    @Override
    public boolean matches(String url) {
        try {
            String path = new URI(url).getPath();
            List<String> ignoreUrls = Arrays.asList(pattern.split(","));
            Optional<String> optional = ignoreUrls.stream().filter(ignoreUrl -> antPathMatcher.match(ignoreUrl, path)).findAny();
            if (optional.isPresent()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}