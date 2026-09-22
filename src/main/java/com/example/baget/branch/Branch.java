package com.example.baget.branch;

import com.example.baget.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BranchNo")
    private Long branchNo;

    @Column(name = "BranchName", nullable = false, unique = true, length = 50)
    private String name;

    @ManyToMany(mappedBy = "allowedBranches")
    private Set<User> users;
}
