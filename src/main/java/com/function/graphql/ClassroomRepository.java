package com.function.graphql;

import java.util.List;
import java.util.Optional;

public class ClassroomRepository {
    private final List<Profesor> profesores = List.of(
        new Profesor("p1", "Ana Ruiz", "APIs y microservicios"),
        new Profesor("p2", "Carlos Soto", "Modelado de datos")
    );

    private final List<Curso> cursos = List.of(
        new Curso("c1", "Introduccion a GraphQL", "Intermedio", "p1"),
        new Curso("c2", "Diseno de esquemas", "Avanzado", "p2")
    );

    private final List<Alumno> alumnos = List.of(
        new Alumno("a1", "Lucia Perez", 21, "c1"),
        new Alumno("a2", "Matias Vega", 23, "c1"),
        new Alumno("a3", "Sofia Torres", 22, "c2")
    );

    public List<Profesor> getTeachers() {
        return profesores;
    }

    public List<Curso> getCourses() {
        return cursos;
    }

    public List<Alumno> getStudents() {
        return alumnos;
    }

    public Optional<Profesor> findTeacherById(String id) {
        return profesores.stream()
            .filter(profesor -> profesor.id().equals(id))
            .findFirst();
    }

    public Optional<Curso> findCourseById(String id) {
        return cursos.stream()
            .filter(curso -> curso.id().equals(id))
            .findFirst();
    }

    public Optional<Alumno> findStudentById(String id) {
        return alumnos.stream()
            .filter(alumno -> alumno.id().equals(id))
            .findFirst();
    }

    public List<Curso> findCoursesByTeacherId(String profesorId) {
        return cursos.stream()
            .filter(curso -> curso.profesorId().equals(profesorId))
            .toList();
    }

    public List<Alumno> findStudentsByCourseId(String cursoId) {
        return alumnos.stream()
            .filter(alumno -> alumno.cursoId().equals(cursoId))
            .toList();
    }
}
