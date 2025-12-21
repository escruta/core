package com.escruta.core.services;

import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.User;
import com.escruta.core.repositories.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public User signUp(RegisterUserDto input) {
        User user = new User();
        user.setFullName(input.getFullName());
        user.setEmail(input.getEmail());
        user.setPassword(passwordEncoder.encode(input.getPassword()));

        return userRepository.save(user);
    }

    public User authenticate(LoginUserDto input) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    input.getEmail(),
                    input.getPassword()
            ));

            return userRepository.findByEmail(input.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User with the given email not found"));
        } catch (BadCredentialsException e) {
            return null;
        }
    }
}
