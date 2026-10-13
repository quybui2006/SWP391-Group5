package org.example.swp391group5;

import controller.RegisterController;
import dto.RegisterRequest;
import entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import repository.UserRepository;
import service.EmailService;
import service.OtpService;
import service.RegistrationService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterControllerTests {

    private RegistrationService registrationService;
    private UserRepository userRepository;
    private OtpService otpService;
    private EmailService emailService;
    private RegisterController controller;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        userRepository = mock(UserRepository.class);
        otpService = mock(OtpService.class);
        emailService = mock(EmailService.class);
        controller = new RegisterController(registrationService, userRepository, otpService, emailService);
    }

    @Test
    void register_SuccessRedirectsToVerify_AndSetsSessionEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Nguyen Van A");
        request.setEmail("user@example.com");
        request.setPassword("123456");
        request.setConfirmPassword("123456");

        User user = new User();
        user.setId(10L);
        user.setEmail("user@example.com");

        when(registrationService.register(any())).thenReturn(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(otpService.createRegistrationOtp(user)).thenReturn("654321");
        doNothing().when(emailService).sendEmail("user@example.com", "654321");

        MockHttpSession session = new MockHttpSession();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "form");

        String view = controller.register(request, errors, session, redirect);

        assertEquals("redirect:/register/verify", view);
        assertEquals("user@example.com", session.getAttribute("pendingRegistrationEmail"));
        assertTrue(redirect.getFlashAttributes().containsKey("message"));
    }

    @Test
    void verifyPage_PopulatesEmailIntoModel() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingRegistrationEmail", "user@example.com");
        Model model = new ConcurrentModel();

        String view = controller.verifyPage(session, model);

        assertEquals("register-verify", view);
        assertEquals("user@example.com", model.getAttribute("email"));
    }

    @Test
    void resendOtp_GeneratesNewOtpAndRedirectsWithFeedback() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingRegistrationEmail", "user@example.com");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        User user = new User();
        user.setId(10L);
        user.setEmail("user@example.com");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(otpService.createRegistrationOtp(user)).thenReturn("999888");
        doNothing().when(emailService).sendEmail("user@example.com", "999888");

        String view = controller.resendOtp(session, redirect);

        assertEquals("redirect:/register/verify", view);
        assertTrue(redirect.getFlashAttributes().containsKey("message"));
        verify(otpService).createRegistrationOtp(user);
    }

    @Test
    void verify_ValidCode_ClearsSessionAndRedirectsToLogin() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingRegistrationEmail", "user@example.com");
        Model model = new ConcurrentModel();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(otpService.verifyRegistrationOtp("user@example.com", "123456")).thenReturn(true);

        String view = controller.verify("123456", session, model, redirect);

        assertEquals("redirect:/login", view);
        assertNull(session.getAttribute("pendingRegistrationEmail"));
        assertTrue(redirect.getFlashAttributes().containsKey("success"));
    }

    @Test
    void verify_InvalidCode_ShowsError() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingRegistrationEmail", "user@example.com");
        Model model = new ConcurrentModel();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(otpService.verifyRegistrationOtp("user@example.com", "000000")).thenReturn(false);

        String view = controller.verify("000000", session, model, redirect);

        assertEquals("register-verify", view);
        assertTrue(model.containsAttribute("error"));
    }
}
