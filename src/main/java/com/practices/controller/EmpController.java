package com.practices.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.practices.entity.Employee;
import com.practices.service.EmployeeService;

@CrossOrigin
@RestController
public class EmpController {

	private EmployeeService empService;
	EmpController(EmployeeService empService){
		this.empService = empService;
	}
	
	@PostMapping("employee/save")
	public Employee saveEmployee(@RequestBody Employee emp){
		return empService.saveEmployee(emp);
	}
	
	@GetMapping("employee/getall")
	public List<Employee> getEmployee(){
		return empService.getAllEmployee();
	}
	
	@PutMapping("employee/{id}")
	public Employee updateEmployee(@PathVariable Long id, @RequestBody Employee emp){
		return empService.updateEmployee(id, emp);
	}
	
	@DeleteMapping("employee/{id}")
	public void deleteEmployee(@PathVariable Long id){
		empService.deleteEmployee(id);
	}

}
