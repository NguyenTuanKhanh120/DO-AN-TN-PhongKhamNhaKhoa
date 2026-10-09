package com.dentalclinic.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.service.dto.CreateDentalServiceRequest;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.dto.UpdateDentalServiceRequest;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DentalServiceManagementServiceTest {

    @Mock
    private DentalServiceRepository dentalServiceRepository;

    private DentalServiceManagementService service;

    @BeforeEach
    void setUp() {
        service = new DentalServiceManagementService(dentalServiceRepository);
    }

    @Test
    void shouldCreateActiveServiceWithNormalizedData() {
        CreateDentalServiceRequest request = new CreateDentalServiceRequest(
                "  Cạo   vôi răng  ",
                "  Làm sạch cao răng  ",
                new BigDecimal("300000.00")
        );
        when(dentalServiceRepository.existsByName("Cạo vôi răng")).thenReturn(false);
        when(dentalServiceRepository.saveAndFlush(any(DentalService.class))).thenAnswer(invocation -> {
            DentalService dentalService = invocation.getArgument(0);
            dentalService.setId(UUID.randomUUID());
            return dentalService;
        });

        DentalServiceResponse response = service.createService(request);

        ArgumentCaptor<DentalService> captor = ArgumentCaptor.forClass(DentalService.class);
        verify(dentalServiceRepository).saveAndFlush(captor.capture());
        DentalService persisted = captor.getValue();
        assertThat(persisted.getName()).isEqualTo("Cạo vôi răng");
        assertThat(persisted.getDescription()).isEqualTo("Làm sạch cao răng");
        assertThat(persisted.getPrice()).isEqualByComparingTo("300000.00");
        assertThat(persisted.isActive()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectDuplicateServiceName() {
        CreateDentalServiceRequest request = new CreateDentalServiceRequest(
                "Cạo vôi răng",
                null,
                new BigDecimal("300000.00")
        );
        when(dentalServiceRepository.existsByName("Cạo vôi răng")).thenReturn(true);

        assertThatThrownBy(() -> service.createService(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_NAME_ALREADY_EXISTS);

        verify(dentalServiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldUpdateServiceWithoutChangingItsStatus() {
        DentalService dentalService = dentalService(false);
        when(dentalServiceRepository.findById(dentalService.getId())).thenReturn(Optional.of(dentalService));
        when(dentalServiceRepository.existsByNameAndIdNot("Tẩy trắng răng", dentalService.getId()))
                .thenReturn(false);

        DentalServiceResponse response = service.updateService(
                dentalService.getId(),
                new UpdateDentalServiceRequest(
                        "  Tẩy   trắng răng  ",
                        "  Mô tả mới  ",
                        new BigDecimal("1200000.00")
                )
        );

        assertThat(dentalService.getName()).isEqualTo("Tẩy trắng răng");
        assertThat(dentalService.getDescription()).isEqualTo("Mô tả mới");
        assertThat(dentalService.getPrice()).isEqualByComparingTo("1200000.00");
        assertThat(dentalService.isActive()).isFalse();
        assertThat(response.active()).isFalse();
        verify(dentalServiceRepository).saveAndFlush(dentalService);
    }

    @Test
    void shouldSoftDisableService() {
        DentalService dentalService = dentalService(true);
        when(dentalServiceRepository.findById(dentalService.getId())).thenReturn(Optional.of(dentalService));

        DentalServiceResponse response = service.updateStatus(dentalService.getId(), false);

        assertThat(dentalService.isActive()).isFalse();
        assertThat(response.active()).isFalse();
        verify(dentalServiceRepository).saveAndFlush(dentalService);
        verify(dentalServiceRepository, never()).delete(any(DentalService.class));
    }

    @Test
    void shouldReturnPagedServicesUsingStableSort() {
        DentalService dentalService = dentalService(true);
        PageRequest pageable = PageRequest.of(
                1,
                5,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        );
        when(dentalServiceRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<DentalService>>any(),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(dentalService), pageable, 6));

        PageResponse<DentalServiceResponse> response = service.getServices("cạo vôi", true, 1, 5);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(dentalService.getId());
            assertThat(item.name()).isEqualTo(dentalService.getName());
        });
    }

    @Test
    void shouldFailWhenServiceDoesNotExist() {
        UUID serviceId = UUID.randomUUID();
        when(dentalServiceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getService(serviceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy dịch vụ nha khoa");
    }

    private DentalService dentalService(boolean active) {
        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Cạo vôi răng");
        dentalService.setDescription("Làm sạch cao răng");
        dentalService.setPrice(new BigDecimal("300000.00"));
        dentalService.setActive(active);
        dentalService.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        dentalService.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return dentalService;
    }
}
