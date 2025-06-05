package com.mysite.applyhome.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
@RequestMapping("/user")
public class UserController {

	private final UserService userService;

	@PostMapping("/signup")
	public String signup(@ModelAttribute UserCreateForm userCreateForm) {
		try {
			System.out.println(userCreateForm.getUsername());
			userService.create(userCreateForm.getUsername(),
					userCreateForm.getResidentId(), userCreateForm.getPasswordConfirm());
		} catch (DataIntegrityViolationException e) {
			e.printStackTrace();
			return "signup_form";
		} catch (Exception e) {
			e.printStackTrace();
			return "signup_form";
		}

		return "redirect:/";
	}
}
