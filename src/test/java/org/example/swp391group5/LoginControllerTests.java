package org.example.swp391group5;

import controller.LoginController;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.Model;
import service.LoginService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginControllerTests {

    @Test
    void shopOwnerLoginRedirectsToExistingDashboard() {
        LoginService loginService = mock(LoginService.class);
        when(loginService.login("owner@example.com", "secret"))
                .thenReturn(Optional.of(new LoginService.LoginResult(7L, "Shop Owner", "SHOP_OWNER")));
        var controller = new LoginController(loginService);
        var request = new MockHttpServletRequest();

        String result = controller.login("owner@example.com", "secret", request, mock(Model.class));

        assertEquals("redirect:/shopowner/home", result);
        HttpSession session = request.getSession(false);
        assertEquals(7L, session.getAttribute("userId"));
        assertEquals("SHOP_OWNER", session.getAttribute("role"));
    }
}
