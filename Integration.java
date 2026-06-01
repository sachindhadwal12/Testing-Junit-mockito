package com.example.employee;

import com.example.employee.entity.Employee;
import com.example.employee.repository.EmployeeRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration test. Spring is started normally but the JPA
 * data layer is disabled and the repository is replaced with a Mockito mock,
 * so no database connection is ever opened.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class EmployeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeRepository repository;

    private Employee alice;

    @BeforeEach
    void setUp() {
        alice = new Employee("Alice", "alice@example.com", "Engineering");
        alice.setId(1L);
    }

    @Test
    void getAll_returnsEmployeesFromRepository() throws Exception {
        Employee bob = new Employee("Bob", "bob@example.com", "Sales");
        bob.setId(2L);
        given(repository.findAll()).willReturn(List.of(alice, bob));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Bob"));
    }

    @Test
    void getById_returns404ViaGlobalHandler() throws Exception {
        given(repository.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")))
                .andExpect(jsonPath("$.path").value("/api/employees/99"));
    }

    @Test
    void create_persistsAndReturns201WithLocation() throws Exception {
        AtomicLong idGenerator = new AtomicLong(100);
        given(repository.save(any(Employee.class))).willAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(idGenerator.getAndIncrement());
            return e;
        });

        Employee input = new Employee("Carol", "carol@example.com", "Ops");

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/employees/100")))
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    void create_returns400WhenBeanValidationFails() throws Exception {
        Employee invalid = new Employee("", "not-an-email", "");

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void put_updatesExistingEmployee() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(alice));
        given(repository.save(any(Employee.class))).willAnswer(inv -> inv.getArgument(0));

        Employee updated = new Employee("Alice Smith", "alice.smith@example.com", "Platform");

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.department").value("Platform"));
    }

    @Test
    void patch_appliesPartialUpdate() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(alice));
        given(repository.save(any(Employee.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.department").value("Platform"));
    }

    @Test
    void patch_returns400ForUnknownField() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(alice));

        mockMvc.perform(patch("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salary\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("salary")));
    }

    @Test
    void delete_returns204AndCallsRepository() throws Exception {
        given(repository.existsById(1L)).willReturn(true);

        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        given(repository.existsById(99L)).willReturn(false);

        mockMvc.perform(delete("/api/employees/99"))
                .andExpect(status().isNotFound());
    }
}
