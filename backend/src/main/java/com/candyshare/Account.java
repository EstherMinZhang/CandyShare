package com.candyshare;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "candyshare_users")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, unique = true, length = 160)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 300)
    private String contactDetails;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {}
    public Account(String name, String email, String passwordHash, String contactDetails) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.contactDetails = contactDetails;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getContactDetails() { return contactDetails; }
}
