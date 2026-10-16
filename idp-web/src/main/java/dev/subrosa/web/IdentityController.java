package dev.subrosa.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class IdentityController {
	@GetMapping("/login")
	String login() {
		return "login";
	}

	@GetMapping("/")
	String home() {
		return "home";
	}
}
