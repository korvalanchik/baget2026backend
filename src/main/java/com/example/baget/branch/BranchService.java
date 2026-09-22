package com.example.baget.branch;

import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final UsersRepository usersRepository;

//    public BranchService(final UsersRepository usersRepository) {
//        this.usersRepository = usersRepository;
//    }
    public List<BranchDTO> allowedBranches(String username) {

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return user.getAllowedBranches().stream()
                .map(b -> new BranchDTO(b.getBranchNo(), b.getName()))
                .toList();
    }

}
