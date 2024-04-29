package com.shepherdmoney.interviewproject.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.CreateUserPayload;

@RestController
public class UserController {

    private final UserRepository userRepository;

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/user")
    public ResponseEntity<Integer> createUser(@RequestBody CreateUserPayload payload) {
        try {
            User newUser = new User();
            newUser.setName(payload.getName());
            newUser.setEmail(payload.getEmail());
            User savedUser = userRepository.save(newUser);
            return ResponseEntity.ok(savedUser.getId());
        }  catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(-1);
        }
    }

    @DeleteMapping("/user")
    public ResponseEntity<String> deleteUser(@RequestParam int userId) {
        try {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
                return ResponseEntity.ok("User deleted successfully");
            } else {
                return ResponseEntity.badRequest().body("User with ID " + userId + " does not exist");
            }
        }  catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("An unexpected error occurred "+e);
        }
    }
}
