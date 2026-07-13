package com.project.farming.global.jwtToken;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            // JWT 인증 시 userId(Long)으로 들어오면 처리
            Long userId = Long.parseLong(username);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("해당하는 사용자를 찾을 수 없습니다: " + userId));
            return new CustomUserDetails(user);
        } catch (NumberFormatException e) {
            // 폼 로그인 시 이메일(String)으로 들어오면 처리
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("해당 이메일의 사용자를 찾을 수 없습니다: " + username));
            return new CustomUserDetails(user);
        }
    }
}
