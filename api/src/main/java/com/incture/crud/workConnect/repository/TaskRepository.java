package com.incture.crud.workConnect.repository;

import com.incture.crud.workConnect.entity.Task;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface TaskRepository extends JpaRepository<Task,Integer>{
	@Query(value = "SELECT e FROM Task e WHERE e.reciever = :email AND e.platform = :platform")
    List<Task> findByEmailPlatform(@Param("email") String email, @Param("platform") String platform);
	
	@Query(value = "SELECT e FROM Task e WHERE e.reciever = :email")
    List<Task> findByEmail(@Param("email") String email);
	
	@Query(value = "SELECT e.time FROM Task e ORDER BY e.time DESC LIMIT 1", nativeQuery=true)
    Date retrieveLast();
}
