package dev.subrosa.demo.oidc;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoController {
  @GetMapping("/")
  String home(@AuthenticationPrincipal OidcUser user, Model model) {
    model.addAttribute("claims", user.getClaims());
    return "claims";
  }
}
