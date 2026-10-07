package com.ecommerce.gateway.configs;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.gateway")
public class GatewaySecurityProperties {
  private List<RouteRule> openApi = new ArrayList<>();
  private List<RouteRule> routeRules = new ArrayList<>();

  public List<RouteRule> getOpenApi() {
    return openApi;
  }

  public void setOpenApi(List<RouteRule> openApi) {
    this.openApi = openApi;
  }

  public List<RouteRule> getRouteRules() {
    return routeRules;
  }

  public void setRouteRules(List<RouteRule> routeRules) {
    this.routeRules = routeRules;
  }

  public static class RouteRule {
    private String method;
    private String path;
    private List<String> authorities = new ArrayList<>();

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public List<String> getAuthorities() {
      return authorities;
    }

    public void setAuthorities(List<String> authorities) {
      this.authorities = authorities;
    }
  }
}
