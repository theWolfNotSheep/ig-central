package co.uk.wolfnotsheep.platform.identity.services.impl;

import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import com.mongodb.lang.NonNull;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserLoadingService implements
        UserDetailsService {

    MongoUserRepository userRepo;

    public UserLoadingService(MongoUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {

        String notFoundMsg = "Username (" + username + ") not found";

        return userRepo
                .findByEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(notFoundMsg));

    }

}
