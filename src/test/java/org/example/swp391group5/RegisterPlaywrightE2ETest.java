package org.example.swp391group5;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Sprint 1 - Module Xác thực & Tài khoản")
@Feature("Đăng ký tài khoản (RegisterController E2E)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RegisterPlaywrightE2ETest {

    private static final String BASE_URL = "http://localhost:8080";

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void initPlaywright() {
        playwright = Playwright.create();
        // Mở trình duyệt Chromium thật để quan sát quá trình chạy
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(300));
    }

    @AfterAll
    void tearDownPlaywright() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void setupContext() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void cleanupContext(TestInfo testInfo) {
        if (page != null) {
            // Tự động chụp ảnh màn hình đính kèm trực tiếp vào báo cáo Allure
            try {
                byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                Allure.addAttachment("Screenshot: " + testInfo.getDisplayName(), "image/png",
                        new ByteArrayInputStream(screenshotBytes), ".png");
            } catch (Exception ignored) {
            }
        }
        if (context != null) context.close();
    }

    @Test
    @Order(1)
    @Story("US01 - Lựa chọn loại tài khoản")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC01 - Hiển thị mặc định Khách hàng và chuyển đổi sang Người bán hàng")
    void testRoleTabSwitching() {
        Allure.step("1. Truy cập trang đăng ký " + BASE_URL + "/register", () -> {
            page.navigate(BASE_URL + "/register");
        });

        Allure.step("2. Kiểm tra tab mặc định là KHÁCH HÀNG", () -> {
            assertThat(page.locator("#registerTitle")).hasText("Đăng ký khách hàng");
            assertThat(page.locator("#roleInput")).hasValue("CUSTOMER");
            assertThat(page.locator("#submitBtn")).hasText("Đăng ký tài khoản Khách hàng");
        });

        Allure.step("3. Click chuyển sang tab BÁN HÀNG", () -> {
            page.locator("#tabShopOwner").click();
        });

        Allure.step("4. Kiểm tra UI cập nhật theo vai trò SHOP_OWNER", () -> {
            assertThat(page.locator("#registerTitle")).hasText("Đăng ký Người bán hàng");
            assertThat(page.locator("#roleInput")).hasValue("SHOP_OWNER");
            assertThat(page.locator("#submitBtn")).hasText("Đăng ký tài khoản Bán hàng");
        });
    }

    @Test
    @Order(2)
    @Story("US01 - Lựa chọn loại tài khoản")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC02 - Truy cập qua đường dẫn ?role=seller tự động chọn tab Bán hàng")
    void testRegisterRoleParamSeller() {
        Allure.step("1. Mở trang với tham số ?role=seller", () -> {
            page.navigate(BASE_URL + "/register?role=seller");
        });

        Allure.step("2. Kiểm tra form khởi tạo sẵn vai trò Người bán hàng", () -> {
            assertThat(page.locator("#registerTitle")).hasText("Đăng ký Người bán hàng");
            assertThat(page.locator("#roleInput")).hasValue("SHOP_OWNER");
        });
    }

    @Test
    @Order(3)
    @Story("US02 - Kiểm tra tính hợp lệ của dữ liệu đầu vào (Validation)")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC03 - Báo lỗi khi mật khẩu xác nhận không khớp")
    void testPasswordMismatchValidation() {
        Allure.step("1. Nhập thông tin đăng ký với confirmPassword khác password", () -> {
            page.navigate(BASE_URL + "/register");
            page.locator("#fullName").fill("Nguyễn Văn Test");
            page.locator("#email").fill("mismatch_" + System.currentTimeMillis() + "@gmail.com");
            page.locator("#phone").fill("0912345678");
            page.locator("#password").fill("Password123@");
            page.locator("#confirmPassword").fill("Password999@");
            page.locator("#submitBtn").click();
        });

        Allure.step("2. Xác thực thông báo lỗi 'Mật khẩu xác nhận không khớp' xuất hiện", () -> {
            Locator errorMsg = page.locator(".login-error:has-text('Mật khẩu xác nhận không khớp')");
            assertThat(errorMsg).isVisible();
        });
    }

    @Test
    @Order(4)
    @Story("US03 - Bảo vệ phiên xác minh OTP")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC04 - Truy cập trực tiếp /register/verify khi chưa điền form bị chuyển hướng")
    void testDirectAccessToVerifyRedirects() {
        Allure.step("1. Truy cập trực tiếp /register/verify mà không qua bước đăng ký", () -> {
            page.navigate(BASE_URL + "/register/verify");
        });

        Allure.step("2. Kiểm tra bị redirect quay về /register do thiếu session", () -> {
            page.waitForURL("**/register");
            assertTrue(page.url().endsWith("/register"));
        });
    }

    @Test
    @Order(5)
    @Story("US04 - Luồng đăng ký gửi OTP và nhập sai mã")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC05 - Đăng ký hợp lệ -> Chuyển đến trang OTP -> Báo lỗi khi nhập OTP sai")
    void testSuccessfulRegisterAndInvalidOtp() {
        String testEmail = "freshfruit_user_" + System.currentTimeMillis() + "@example.com";

        Allure.step("1. Điền form đăng ký hợp lệ và submit", () -> {
            page.navigate(BASE_URL + "/register");
            page.locator("#fullName").fill("Nguyễn Kiểm Thử");
            page.locator("#email").fill(testEmail);
            page.locator("#phone").fill("0987654321");
            page.locator("#password").fill("Matkhau123@");
            page.locator("#confirmPassword").fill("Matkhau123@");
            page.locator("#submitBtn").click();
        });

        Allure.step("2. Chờ chuyển hướng tới /register/verify và kiểm tra email", () -> {
            page.waitForURL("**/register/verify");
            assertTrue(page.url().contains("/register/verify"));
            assertThat(page.locator(".login-card")).containsText(testEmail);
        });

        Allure.step("3. Nhập mã OTP sai (000000) và nhấn Xác minh", () -> {
            page.locator("#code").fill("000000");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Xác minh & Kích hoạt")).click();
        });

        Allure.step("4. Xác thực thông báo lỗi 'Mã sai hoặc đã hết hạn'", () -> {
            Locator otpError = page.locator(".login-error");
            assertThat(otpError).isVisible();
            assertThat(otpError).containsText("Mã sai hoặc đã hết hạn");
        });
    }

    @Test
    @Order(6)
    @Story("US04 - Gửi lại mã xác minh OTP")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC06 - Kiểm tra tính năng Gửi lại mã OTP trên trang verify")
    void testResendOtp() {
        String testEmail = "resend_e2e_" + System.currentTimeMillis() + "@example.com";

        Allure.step("1. Đăng ký thông tin để có session pendingRegistrationEmail", () -> {
            page.navigate(BASE_URL + "/register");
            page.locator("#fullName").fill("Nguyễn Resend OTP");
            page.locator("#email").fill(testEmail);
            page.locator("#password").fill("Matkhau123@");
            page.locator("#confirmPassword").fill("Matkhau123@");
            page.locator("#submitBtn").click();
            page.waitForURL("**/register/verify");
        });

        Allure.step("2. Nhấn nút 'Gửi lại mã'", () -> {
            page.locator("#btnResendOtp").click();
        });

        Allure.step("3. Xác nhận thông báo thông tin mã xác minh mới hiển thị", () -> {
            Locator notice = page.locator(".login-notice");
            assertThat(notice).isVisible();
        });
    }
}
