package com.nitramite.porssiohjain.auth;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthInterceptorTest {
    private final AuthService auth = mock();
    private final AuthContext context = new AuthContext();
    private final AuthInterceptor interceptor = new AuthInterceptor(auth, context);

    @AfterEach
    void cleanup() {
        context.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void directDemoLoginCannotWriteOrForgeSupportAccess(String method) throws Exception {
        var demo = AccountEntity.builder().id(2L).demo(true).build();
        when(auth.authenticate("demo-token")).thenReturn(demo);
        var request = new MockHttpServletRequest(method, "/resource");
        request.addHeader("Authorization", "demo-token");
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler()));
        assertEquals(403, response.getStatus());
        assertEquals("Demo account is read-only.", response.getContentAsString());
        assertNull(context.getAccountId());
        assertFalse(context.isImpersonating());

        request.addHeader("X-View-As-Account", "3");
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), handler()));
        // Even an inconsistent demo+admin account must not receive the exception.
        demo.setAdmin(true);
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), handler()));
        verify(auth, never()).getAccount(anyLong());
        assertNull(context.getAccountId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void authenticatedAdminCanMaintainDemoWithoutChangingItsIdentity(String method) throws Exception {
        var admin = AccountEntity.builder().id(1L).admin(true).build();
        var demo = AccountEntity.builder().id(2L).demo(true).build();
        when(auth.authenticate("admin-token")).thenReturn(admin);
        when(auth.getAccount(2L)).thenReturn(demo);
        var request = new MockHttpServletRequest(method, "/resource");
        request.addHeader("Authorization", "admin-token");
        request.addHeader("X-View-As-Account", "2");
        var response = new MockHttpServletResponse();
        var handler = handler();

        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals(2L, context.getAccountId());
        assertTrue(context.isDemoAccount());
        assertTrue(context.isImpersonating());
        assertFalse(context.isAdminAccount());
        assertTrue(demo.isDemo());
        assertFalse(demo.isAdmin());
        interceptor.afterCompletion(request, response, handler, null);
        assertNull(context.getAccountId());
        assertFalse(context.isImpersonating());
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new ResourceController(), ResourceController.class.getDeclaredMethod("write"));
    }

    @RequireAuth
    static class ResourceController {
        public void write() {}
    }
}
