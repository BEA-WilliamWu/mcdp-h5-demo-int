package com.hkbea.hth.bff.maintenance.filter;

import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.bff.exception.HTHBffErrorMapping;
import com.hkbea.hth.bff.maintenance.entity.MaintenanceWindow;
import com.hkbea.hth.bff.maintenance.service.MaintenanceWindowService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceWindowFilterTest {

    @Mock
    private MaintenanceWindowService maintenanceWindowService;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private MaintenanceWindowFilter filter;

    @BeforeEach
    void setUp() {
        new HTHBffErrorMapping(); // initialise MESSAGE_MAP static block
    }

    private MaintenanceWindow window(String message) {
        MaintenanceWindow window = new MaintenanceWindow();
        window.setWindowId(1L);
        window.setApiUri("payment/v1/localPayment");
        window.setMessage(message);
        return window;
    }

    private void stubRequestPath(String path) {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
    }

    @Test
    void inMaintenanceWindow_rejectsWithCustomMessage() {
        // AC1
        stubRequestPath("payment/v1/localPayment");
        when(maintenanceWindowService.findActive("payment/v1/localPayment"))
                .thenReturn(Optional.of(window("  Payment API down for maintenance  ")));

        ServiceException ex = assertThrows(ServiceException.class, () -> filter.filter(requestContext));
        assertEquals(HTHBffErrorMapping.MAINTENANCE_IN_PROGRESS, ex.getErrCode());
        assertEquals("Payment API down for maintenance", ex.getMessage());
    }

    @Test
    void inMaintenanceWindow_blankMessageFallsBackToDefault() {
        stubRequestPath("payment/v1/localPayment");
        when(maintenanceWindowService.findActive("payment/v1/localPayment"))
                .thenReturn(Optional.of(window("   ")));

        ServiceException ex = assertThrows(ServiceException.class, () -> filter.filter(requestContext));
        assertEquals(HTHBffErrorMapping.MAINTENANCE_IN_PROGRESS, ex.getErrCode());
        assertEquals(HTHBffErrorMapping.MESSAGE_MAP.get(HTHBffErrorMapping.MAINTENANCE_IN_PROGRESS), ex.getMessage());
    }

    @Test
    void outsideMaintenanceWindow_proceeds() {
        // AC2
        stubRequestPath("account/v1/balances/enquiry");
        when(maintenanceWindowService.findActive("account/v1/balances/enquiry"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }

    @Test
    void paymentEnquiryDuringHthMaintenanceUsesExistingOutageCode() {
        String path = "payment/v1/transactions/enquiry";
        stubRequestPath(path);
        MaintenanceWindow maintenance = window(null);
        maintenance.setApiUri(path);
        when(maintenanceWindowService.findActive(path)).thenReturn(Optional.of(maintenance));

        ServiceException ex = assertThrows(ServiceException.class, () -> filter.filter(requestContext));
        assertEquals(600210, ex.getErrCode());
        assertEquals("The requested API is under scheduled maintenance. Please try again later.", ex.getMessage());
    }

    @Test
    void paymentEnquiryOutsideHthMaintenanceProceeds() {
        String path = "payment/v1/transactions/enquiry";
        stubRequestPath(path);
        when(maintenanceWindowService.findActive(path)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> filter.filter(requestContext));
    }
}
