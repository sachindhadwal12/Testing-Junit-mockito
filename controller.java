package com.example.employee.controller;

import com.example.employee.entity.Employee;
import com.example.employee.exception.EmployeeNotFoundException;
import com.example.employee.exception.GlobalExceptionHandler;
import com.example.employee.service.EmployeeService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeService service;

    private Employee alice;

    @BeforeEach
    void setUp() {
        alice = new Employee("Alice", "alice@example.com", "Engineering");
        alice.setId(1L);
    }

    @Test
    void getAll_returns200WithList() throws Exception {
        given(service.getAll()).willReturn(List.of(alice));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        given(service.getById(1L)).willReturn(alice);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.department").value("Engineering"));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        given(service.getById(99L)).willThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("99")))
                .andExpect(jsonPath("$.path").value("/api/employees/99"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        Employee input = new Employee("Bob", "bob@example.com", "Sales");
        Employee saved = new Employee("Bob", "bob@example.com", "Sales");
        saved.setId(42L);
        given(service.create(any(Employee.class))).willReturn(saved);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/employees/42")))
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void create_returns400WhenValidationFails() throws Exception {
        Employee invalid = new Employee("", "not-an-email", "");

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void create_returns400WhenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void create_returns409WhenEmailConflicts() throws Exception {
        Employee input = new Employee("Alice", "alice@example.com", "Engineering");
        willThrow(new DataIntegrityViolationException("duplicate"))
                .given(service).create(any(Employee.class));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WhenSuccessful() throws Exception {
        Employee updated = new Employee("Alice Smith", "alice.smith@example.com", "Platform");
        updated.setId(1L);
        given(service.update(eq(1L), any(Employee.class))).willReturn(updated);

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.department").value("Platform"));
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        Employee body = new Employee("X", "x@example.com", "Y");
        given(service.update(eq(99L), any(Employee.class)))
                .willThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(put("/api/employees/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns200WithPartialUpdate() throws Exception {
        Employee patched = new Employee("Alice", "alice@example.com", "Platform");
        patched.setId(1L);
        given(service.patch(eq(1L), any())).willReturn(patched);

        mockMvc.perform(patch("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Platform"));
    }

    @Test
    void patch_returns400ForUnknownField() throws Exception {
        given(service.patch(eq(1L), any()))
                .willThrow(new IllegalArgumentException("Unknown field: salary"));

        mockMvc.perform(patch("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salary\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("salary")));
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        doThrow(new EmployeeNotFoundException(99L)).when(service).delete(99L);

        mockMvc.perform(delete("/api/employees/99"))
                .andExpect(status().isNotFound());
    }
}
