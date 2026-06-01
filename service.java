package com.example.employee.service;

import com.example.employee.entity.Employee;
import com.example.employee.exception.EmployeeNotFoundException;
import com.example.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository repository;

    @InjectMocks
    private EmployeeService service;

    private Employee alice;

    @BeforeEach
    void setUp() {
        alice = new Employee("Alice", "alice@example.com", "Engineering");
        alice.setId(1L);
    }

    @Nested
    @DisplayName("getAll()")
    class GetAll {
        @Test
        void returnsAllEmployees() {
            when(repository.findAll()).thenReturn(List.of(alice));

            List<Employee> result = service.getAll();

            assertThat(result).containsExactly(alice);
            verify(repository).findAll();
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {
        @Test
        void returnsEmployeeWhenFound() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));

            Employee result = service.getById(1L);

            assertThat(result).isSameAs(alice);
        }

        @Test
        void throwsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {
        @Test
        void persistsAndReturnsSavedEntity() {
            Employee input = new Employee("Bob", "bob@example.com", "Sales");
            when(repository.save(any(Employee.class))).thenAnswer(inv -> {
                Employee e = inv.getArgument(0);
                e.setId(42L);
                return e;
            });

            Employee saved = service.create(input);

            assertThat(saved.getId()).isEqualTo(42L);
            assertThat(saved.getName()).isEqualTo("Bob");
        }

        @Test
        void stripsClientSuppliedId() {
            Employee input = new Employee("Eve", "eve@example.com", "Ops");
            input.setId(999L);
            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            when(repository.save(captor.capture())).thenReturn(input);

            service.create(input);

            assertThat(captor.getValue().getId()).isNull();
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {
        @Test
        void replacesAllMutableFields() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));
            when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            Employee changes = new Employee("Alice Smith", "alice.smith@example.com", "Platform");
            Employee result = service.update(1L, changes);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Alice Smith");
            assertThat(result.getEmail()).isEqualTo("alice.smith@example.com");
            assertThat(result.getDepartment()).isEqualTo("Platform");
        }

        @Test
        void throwsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, alice))
                    .isInstanceOf(EmployeeNotFoundException.class);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("patch()")
    class Patch {
        @Test
        void updatesOnlyProvidedFields() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));
            when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            Employee result = service.patch(1L, Map.of("department", "Platform"));

            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("alice@example.com");
            assertThat(result.getDepartment()).isEqualTo("Platform");
        }

        @Test
        void ignoresIdField() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));
            when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            Employee result = service.patch(1L, Map.of("id", 555));

            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        void rejectsUnknownField() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> service.patch(1L, Map.of("salary", 100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("salary");
            verify(repository, never()).save(any());
        }

        @Test
        void rejectsNonStringValueForStringField() {
            when(repository.findById(1L)).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> service.patch(1L, Map.of("name", 123)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {
        @Test
        void deletesWhenExists() {
            when(repository.existsById(1L)).thenReturn(true);

            service.delete(1L);

            verify(repository, times(1)).deleteById(1L);
        }

        @Test
        void throwsWhenMissing() {
            when(repository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(EmployeeNotFoundException.class);
            verify(repository, never()).deleteById(any());
        }
    }
}
