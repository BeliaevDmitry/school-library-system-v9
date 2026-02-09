package ru.school.library.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.school.library.entity.*;
import ru.school.library.repo.*;

import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class BootstrapService {
    private final BuildingRepository buildings;
    private final UserRepository users;
    private final SubjectRepository subjects;
    private final PasswordEncoder encoder;


    @PostConstruct
    public void init() {
        // 8 корпусов по умолчанию (1..8)
        if (buildings.count() == 0) {
            // Центральный склад / общешкольный фонд
            Building central = new Building();
            central.setCode("0");
            central.setName("Центральный фонд");
            buildings.save(central);

            IntStream.rangeClosed(1, 8).forEach(i -> {
                Building b = new Building();
                b.setCode(String.valueOf(i));
                b.setName("Корпус " + i);
                buildings.save(b);
            });
        }

        if (subjects.count() == 0) {
            for (String s : new String[]{"Математика","Русский язык","Литература","Окружающий мир","Английский язык"}) {
                Subject sub = new Subject();
                sub.setName(s);
                subjects.save(sub);
            }
        }

        if (users.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(encoder.encode("admin"));
            admin.setRole(User.Role.ADMIN);
            users.save(admin);

            Building b1 = buildings.findByCode("1").orElseThrow();
            User lib1 = new User();
            lib1.setUsername("lib1");
            lib1.setPasswordHash(encoder.encode("lib1"));
            lib1.setRole(User.Role.LIBRARIAN);
            lib1.setBuilding(b1);
            users.save(lib1);
        }
    }
}
