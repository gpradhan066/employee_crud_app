package com.practices.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.practices.entity.Employee;

public interface EmployeeRepo extends JpaRepository<Employee, Long> {

}
