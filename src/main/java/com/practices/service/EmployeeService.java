package com.practices.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;

@Service
public class EmployeeService {
	
	private EmployeeRepo empRepo;	
	EmployeeService(EmployeeRepo empRepo){
		this.empRepo = empRepo;
	}
	
	public Employee saveEmployee(Employee emp){
		
		return empRepo.save(emp);
	}

	public List<Employee> getAllEmployee() {
		return empRepo.findAll();
	}
	
	public Employee updateEmployee(Long id, Employee emp) {
		Employee existingEmployee = empRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
		
		existingEmployee.setName(emp.getName());
		existingEmployee.setDepartment(emp.getDepartment());
		
		return empRepo.save(existingEmployee);
	}
	
	public void deleteEmployee(Long id) {
		empRepo.deleteById(id);
	}

}
