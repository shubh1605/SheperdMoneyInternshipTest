package com.shepherdmoney.interviewproject.repository;

import com.shepherdmoney.interviewproject.model.BalanceHistory;

import org.hibernate.mapping.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Crud Repository to store Balance History classes
 */
@Repository("BalanceHistoryRepo")
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, Integer> {
    
}