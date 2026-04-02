package com.function.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

public class ClassGraphqlProvider {
    private final GraphQL graphQL;

    public ClassGraphqlProvider() {
        this(new ClassroomRepository());
    }

    ClassGraphqlProvider(ClassroomRepository repository) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse("""
            type Query {
              cursos: [Curso!]!
              curso(id: ID!): Curso
              alumnos: [Alumno!]!
              alumno(id: ID!): Alumno
              profesores: [Profesor!]!
            }

            type Curso {
              id: ID!
              nombre: String!
              nivel: String!
              profesor: Profesor!
              alumnos: [Alumno!]!
            }

            type Alumno {
              id: ID!
              nombre: String!
              edad: Int!
              curso: Curso!
            }

            type Profesor {
              id: ID!
              nombre: String!
              especialidad: String!
              cursos: [Curso!]!
            }
            """);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder
                .dataFetcher("cursos", environment -> repository.getCourses())
                .dataFetcher("curso", environment -> repository.findCourseById(environment.getArgument("id")).orElse(null))
                .dataFetcher("alumnos", environment -> repository.getStudents())
                .dataFetcher("alumno", environment -> repository.findStudentById(environment.getArgument("id")).orElse(null))
                .dataFetcher("profesores", environment -> repository.getTeachers()))
            .type("Curso", builder -> builder
                .dataFetcher("profesor", environment -> {
                    Curso curso = environment.getSource();
                    return repository.findTeacherById(curso.profesorId()).orElse(null);
                })
                .dataFetcher("alumnos", environment -> {
                    Curso curso = environment.getSource();
                    return repository.findStudentsByCourseId(curso.id());
                }))
            .type("Alumno", builder -> builder
                .dataFetcher("curso", environment -> {
                    Alumno alumno = environment.getSource();
                    return repository.findCourseById(alumno.cursoId()).orElse(null);
                }))
            .type("Profesor", builder -> builder
                .dataFetcher("cursos", environment -> {
                    Profesor profesor = environment.getSource();
                    return repository.findCoursesByTeacherId(profesor.id());
                }))
            .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);
        this.graphQL = GraphQL.newGraphQL(schema).build();
    }

    public ExecutionResult execute(String query) {
        return graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query(query)
                .build()
        );
    }
}
