package dev.subrosa.demo.oidc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class DemoConfiguration {
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .oauth2Login(Customizer.withDefaults())
        .build();
  }
}
