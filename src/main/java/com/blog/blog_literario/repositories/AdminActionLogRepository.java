package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.blog.blog_literario.model.AdminActionLog;

@Repository
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Integer> {
}
