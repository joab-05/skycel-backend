package com.skycel.backend.config;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        
        // Verificamos si ya existe el usuario ROOT
        if (usuarioRepository.findByUsernameAndActivoTrue("root").isEmpty()) {
            
            // Creamos una tienda por defecto (ID 1)
            Tienda matriz = Tienda.builder()
                    .nombre("Matriz Central")
                    .ubicacion("Sede Principal")
                    .esAlmacen(true)
                    .activo(true)
                    .build();
            
            entityManager.persist(matriz);
            
            // Creamos al usuario Root
            Usuario rootUser = Usuario.builder()
                    .username("root")
                    .password(passwordEncoder.encode("admin123")) // Contraseña por defecto
                    .nombreCompleto("Administrador del Sistema")
                    .tienda(matriz)
                    .rol(Rol.ROOT)
                    .activo(true)
                    .build();
            
            usuarioRepository.save(rootUser);
            
            System.out.println("=========================================================");
            System.out.println("SISTEMA INICIALIZADO: Se creó la tienda 'Matriz Central'");
            System.out.println("USUARIO ROOT CREADO: root / admin123");
            System.out.println("=========================================================");
        }
    }
}
